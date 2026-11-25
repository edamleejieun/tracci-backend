package com.tracci.module.export.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.export.dto.response.ExportAssetsResp;
import com.tracci.module.export.dto.response.ItineraryExportAssetsResp;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.summary.service.SummaryService;
import com.tracci.module.trip.dto.response.ItineraryDayResp;
import com.tracci.module.trip.dto.response.ItineraryNodeBriefResp;
import com.tracci.module.trip.dto.response.ItineraryNodeLocationResp;
import com.tracci.module.trip.dto.response.ItineraryResp;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.enums.ItineraryNodeType;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.service.ItineraryService;
import com.tracci.module.trip.service.TripAccessService;
import com.tracci.module.trip.enums.TripPermission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private static final Set<String> VALID_TEMPLATES = Set.of("travel-cover", "film-collage", "minimal-summary");
    private static final Set<String> VALID_FORMATS = Set.of("square", "portrait");
    private static final String DEFAULT_TEMPLATE = "travel-cover";
    private static final String DEFAULT_FORMAT = "square";
    private static final String CURRENCY_CNY = "CNY";
    private static final int PHOTO_LIMIT = 3;
    private static final int CATEGORY_LIMIT = 3;
    private static final String WATERMARK_TEXT = "by Tracci";
    private static final String VALID_TEMPLATE_MESSAGE = "travel-cover / film-collage / minimal-summary";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Map<String, String> NODE_TYPE_NAMES = Map.of(
            ItineraryNodeType.ATTRACTION.value(), "景点",
            ItineraryNodeType.RESTAURANT.value(), "餐饮",
            ItineraryNodeType.TRANSPORT.value(), "交通",
            ItineraryNodeType.HOTEL.value(), "住宿",
            ItineraryNodeType.SHOPPING.value(), "购物",
            ItineraryNodeType.OTHER.value(), "其他");

    private final SummaryService summaryService;
    private final ItineraryNodeMapper itineraryNodeMapper;
    private final ItineraryService itineraryService;
    private final TripAccessService tripAccessService;

    @Override
    @Transactional(readOnly = true)
    public ExportAssetsResp getExportAssets(String tripId, String template, String format) {
        String userId = LoginUserContext.requireUserId();
        String resolvedTemplate = resolveTemplate(template);
        String resolvedFormat = resolveFormat(format);

        SummaryResp summary = summaryService.getSummary(tripId);

        List<SummaryResp.PhotoItem> selectedPhotos = new ArrayList<>();
        selectedPhotos.addAll(summary.userFeaturedPhotos());
        selectedPhotos.addAll(summary.recommendedPhotos());
        List<ExportAssetsResp.PhotoItem> photos = toExportPhotos(selectedPhotos.stream().distinct().toList());
        String coverPhoto = photos.isEmpty() ? null : chooseExportCoverUrl(photos.getFirst());
        List<String> visitedPlaces = visitedPlaces(tripId, userId);

        return new ExportAssetsResp(
                new ExportAssetsResp.TripInfo(
                        summary.trip().id(),
                        summary.trip().title(),
                        summary.trip().destination(),
                        summary.trip().startDate(),
                        summary.trip().endDate(),
                        summary.stats().dayCount(),
                        coverPhoto),
                new ExportAssetsResp.Stats(
                        summary.stats().itineraryCount(),
                        summary.stats().journalCount(),
                        summary.stats().photoCount(),
                        summary.stats().expenseCount(),
                        parseDecimal(summary.stats().totalExpenseAmount()),
                        calculateAverageDailyExpense(summary),
                        summary.stats().currency() != null ? summary.stats().currency() : CURRENCY_CNY),
                photos,
                visitedPlaces,
                toExportCategoryStats(summary.expenseSummary().categoryStats()),
                new ExportAssetsResp.Watermark(true, WATERMARK_TEXT),
                new ExportAssetsResp.ExportOptions(resolvedTemplate, resolvedFormat, defaultContentOptions()));
    }

    @Override
    @Transactional(readOnly = true)
    public ItineraryExportAssetsResp getItineraryExportAssets(String tripId, String dates) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access =
                tripAccessService.requirePermission(tripId, userId, TripPermission.READ);
        Trip trip = access.trip();
        ItineraryResp itinerary = itineraryService.getItinerary(tripId);
        Set<LocalDate> selectedDates = parseSelectedDates(dates);

        List<ItineraryExportAssetsResp.DayItem> days = itinerary.days().stream()
                .map(this::toExportDay)
                .toList();

        long totalDayCount = days.size();
        long totalNodeCount = days.stream().mapToLong(ItineraryExportAssetsResp.DayItem::nodeCount).sum();
        long selectedDayCount;
        long selectedNodeCount;
        if (selectedDates.isEmpty()) {
            selectedDayCount = totalDayCount;
            selectedNodeCount = totalNodeCount;
        } else {
            selectedDayCount = days.stream()
                    .filter(day -> selectedDates.contains(LocalDate.parse(day.date(), DATE_FMT)))
                    .count();
            selectedNodeCount = days.stream()
                    .filter(day -> selectedDates.contains(LocalDate.parse(day.date(), DATE_FMT)))
                    .mapToLong(ItineraryExportAssetsResp.DayItem::nodeCount)
                    .sum();
        }

        return new ItineraryExportAssetsResp(
                toItineraryExportTripInfo(trip),
                days,
                new ItineraryExportAssetsResp.Meta(
                        totalDayCount,
                        totalNodeCount,
                        selectedDayCount,
                        selectedNodeCount),
                new ItineraryExportAssetsResp.Watermark(true, WATERMARK_TEXT));
    }

    private ItineraryExportAssetsResp.TripInfo toItineraryExportTripInfo(Trip trip) {
        return new ItineraryExportAssetsResp.TripInfo(
                trip.getId(),
                trip.getTitle(),
                resolveDestinationName(trip),
                trip.getStartDate().toString(),
                trip.getEndDate().toString(),
                totalDays(trip.getStartDate(), trip.getEndDate()));
    }

    private String resolveDestinationName(Trip trip) {
        if (StringUtils.hasText(trip.getDestinationName())) {
            return trip.getDestinationName();
        }
        return trip.getTitle();
    }

    private long totalDays(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    private Set<LocalDate> parseSelectedDates(String dates) {
        if (dates == null || dates.isBlank()) {
            return Set.of();
        }
        Set<LocalDate> parsed = new HashSet<>();
        for (String raw : dates.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                parsed.add(LocalDate.parse(trimmed, DATE_FMT));
            } catch (DateTimeParseException ex) {
                throw new BizException(
                        ErrorCode.PARAM_INVALID,
                        "无效的日期参数: " + trimmed + "，格式必须为 yyyy-MM-dd");
            }
        }
        return parsed;
    }

    private ItineraryExportAssetsResp.DayItem toExportDay(ItineraryDayResp day) {
        List<ItineraryExportAssetsResp.NodeItem> nodes = day.nodes() == null
                ? List.of()
                : day.nodes().stream().map(this::toExportNode).toList();
        int dayIndex = day.dayIndex() != null ? day.dayIndex() : 0;
        return new ItineraryExportAssetsResp.DayItem(
                day.id(),
                dayIndex,
                day.date(),
                nodes.size(),
                nodes);
    }

    private ItineraryExportAssetsResp.NodeItem toExportNode(ItineraryNodeBriefResp node) {
        ItineraryNodeLocationResp location = node.location();
        String locationName = location != null && StringUtils.hasText(location.name())
                ? location.name()
                : null;
        String locationAddress = resolveLocationAddress(node, location);
        String detailSummary = null;
        if (node.details() != null && !node.details().isEmpty()) {
            try {
                detailSummary = com.tracci.module.trip.support.ItineraryNodeDetailsSupport.summaryLine(
                        node.type(),
                        com.tracci.module.trip.support.ItineraryNodeDetailsSupport.toJson(node.details()));
            } catch (Exception ignored) {
                detailSummary = null;
            }
        }
        return new ItineraryExportAssetsResp.NodeItem(
                node.id(),
                node.title(),
                node.type(),
                nodeTypeName(node.type()),
                node.startTime(),
                node.endTime(),
                locationName,
                locationAddress,
                node.note(),
                node.sortOrder(),
                detailSummary);
    }

    private String resolveLocationAddress(ItineraryNodeBriefResp node, ItineraryNodeLocationResp location) {
        if (location != null && StringUtils.hasText(location.address())) {
            return location.address();
        }
        if (StringUtils.hasText(node.address())) {
            return node.address();
        }
        if (location != null && StringUtils.hasText(location.name())) {
            return location.name();
        }
        return null;
    }

    private String nodeTypeName(String type) {
        if (type == null || type.isBlank()) {
            return NODE_TYPE_NAMES.get(ItineraryNodeType.OTHER.value());
        }
        return NODE_TYPE_NAMES.getOrDefault(type, NODE_TYPE_NAMES.get(ItineraryNodeType.OTHER.value()));
    }

    private String chooseExportCoverUrl(ExportAssetsResp.PhotoItem photo) {
        if (photo.exportUrl() != null && !photo.exportUrl().isBlank()) {
            return photo.exportUrl();
        }
        if (photo.previewUrl() != null && !photo.previewUrl().isBlank()) {
            return photo.previewUrl();
        }
        if (photo.thumbnailUrl() != null && !photo.thumbnailUrl().isBlank()) {
            return photo.thumbnailUrl();
        }
        return photo.url();
    }

    private String resolveTemplate(String template) {
        if (template == null || template.isBlank()) {
            return DEFAULT_TEMPLATE;
        }
        if (!VALID_TEMPLATES.contains(template)) {
            throw new BizException(
                    ErrorCode.PARAM_INVALID,
                    "无效的模板参数: " + template + "，合法值: " + VALID_TEMPLATE_MESSAGE);
        }
        return template;
    }

    private String resolveFormat(String format) {
        if (format == null || format.isBlank()) {
            return DEFAULT_FORMAT;
        }
        if (!VALID_FORMATS.contains(format)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "无效的格式参数: " + format);
        }
        return format;
    }

    private List<ExportAssetsResp.PhotoItem> toExportPhotos(List<SummaryResp.PhotoItem> selectedPhotos) {
        if (selectedPhotos == null || selectedPhotos.isEmpty()) {
            return List.of();
        }
        return IntStream.range(0, Math.min(selectedPhotos.size(), PHOTO_LIMIT))
                .mapToObj(i -> {
                    SummaryResp.PhotoItem src = selectedPhotos.get(i);
                    return new ExportAssetsResp.PhotoItem(
                            src.fileId(),
                            src.journalId(),
                            src.url(),
                            src.thumbnailUrl(),
                            src.previewUrl(),
                            src.exportUrl(),
                            src.width(),
                            src.height(),
                            i + 1);
                })
                .toList();
    }

    private List<ExportAssetsResp.ExpenseCategoryStat> toExportCategoryStats(
            List<SummaryResp.ExpenseCategoryStat> categoryStats) {
        if (categoryStats == null || categoryStats.isEmpty()) {
            return List.of();
        }
        return categoryStats.stream()
                .limit(CATEGORY_LIMIT)
                .map(stat -> new ExportAssetsResp.ExpenseCategoryStat(
                        stat.category(),
                        stat.categoryName(),
                        parseDecimal(stat.amount()),
                        parseDecimal(stat.percentage()),
                        stat.count()))
                .toList();
    }

    private List<String> visitedPlaces(String tripId, String userId) {
        List<String> places = itineraryNodeMapper.selectAttractionTitlesByTripIdAndUserId(
                tripId,
                userId,
                ItineraryNodeType.ATTRACTION.value());
        return places == null ? List.of() : places;
    }

    private ExportAssetsResp.ContentOptions defaultContentOptions() {
        return new ExportAssetsResp.ContentOptions(
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                "none");
    }

    private BigDecimal calculateAverageDailyExpense(SummaryResp summary) {
        long dayCount = summary.stats().dayCount();
        if (dayCount <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal total = parseDecimal(summary.stats().totalExpenseAmount());
        return total.divide(BigDecimal.valueOf(dayCount), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
    }
}
