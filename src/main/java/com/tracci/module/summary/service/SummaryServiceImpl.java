package com.tracci.module.summary.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.common.response.ErrorCode;
import com.tracci.integration.cos.FileUrlResolver;
import com.tracci.module.expense.enums.ExpenseCategory;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.file.entity.File;
import com.tracci.module.file.repository.FileMapper;
import com.tracci.module.journal.entity.JournalEntry;
import com.tracci.module.journal.entity.JournalPhoto;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.trip.entity.ItineraryNode;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripDay;
import com.tracci.module.trip.enums.TripStatus;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripDayMapper;
import com.tracci.module.trip.service.TripAccessService;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.support.TripDestinationCoverProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SummaryServiceImpl implements SummaryService {

    private static final int HIGHLIGHT_LOG_LIMIT = 5;
    private static final int FEATURED_PHOTO_LIMIT = 9;
    private static final String SUMMARY_CURRENCY = "CNY";

    private final TripDayMapper tripDayMapper;
    private final ItineraryNodeMapper itineraryNodeMapper;
    private final ExpenseMapper expenseMapper;
    private final JournalEntryMapper journalEntryMapper;
    private final JournalPhotoMapper journalPhotoMapper;
    private final FileMapper fileMapper;
    private final FileUrlResolver fileUrlResolver;
    private final TripDestinationCoverProvider destinationCoverProvider;
    private final TripAccessService tripAccessService;

    @Override
    @Transactional(readOnly = true)
    public SummaryResp getSummary(String tripId) {
        String userId = LoginUserContext.requireUserId();
        Trip trip = tripAccessService.requirePermission(tripId, userId, TripPermission.READ).trip();

        List<TripDay> days = findTripDays(tripId);
        List<ItineraryNode> nodes = findItineraryNodes(tripId);
        List<JournalEntry> entries = findJournalEntries(tripId, userId);
        Map<String, JournalEntry> entriesById = entries.stream()
                .collect(Collectors.toMap(JournalEntry::getId, Function.identity()));
        List<JournalPhoto> photos = findJournalPhotos(tripId, entriesById.keySet());
        Map<String, File> filesById = findUploadedFilesByPhotoList(photos);
        Map<String, List<SummaryResp.PhotoItem>> photosByEntryId = groupPhotosByEntryId(photos, filesById);
        BigDecimal totalExpense = zeroIfNull(
                expenseMapper.sumVisibleAmountCnyByTripIdAndUserId(tripId, userId));
        List<ExpenseMapper.CategorySummary> categorySummaries =
                expenseMapper.categoryVisibleByTripIdAndUserId(tripId, userId);
        if (categorySummaries == null) {
            categorySummaries = List.of();
        }

        long dayCount = calculateDayCount(trip);
        long itineraryCount = nodes.size();
        long journalCount = entries.size();
        long photoCount = photosByEntryId.values().stream().mapToLong(List::size).sum();
        long expenseCount = categorySummaries.stream().mapToLong(ExpenseMapper.CategorySummary::cnt).sum();
        long highlightCount = entries.stream().filter(this::isHighlight).count();

        return new SummaryResp(
                toTripInfo(trip, dayCount),
                new SummaryResp.Stats(
                        dayCount,
                        itineraryCount,
                        journalCount,
                        photoCount,
                        expenseCount,
                        formatMoney(totalExpense),
                        SUMMARY_CURRENCY,
                        highlightCount),
                toExpenseSummary(totalExpense, categorySummaries),
                toHighlightLogs(entries, photosByEntryId),
                toUserFeaturedPhotos(photosByEntryId),
                toRecommendedPhotos(entries, photosByEntryId),
                toDailyTimeline(trip, days, nodes, entries, photosByEntryId)
        );
    }

    private List<TripDay> findTripDays(String tripId) {
        return tripDayMapper.selectList(new QueryWrapper<TripDay>()
                .eq("trip_id", tripId)
                .orderByAsc("day_index"));
    }

    private List<ItineraryNode> findItineraryNodes(String tripId) {
        return itineraryNodeMapper.selectList(new QueryWrapper<ItineraryNode>()
                .eq("trip_id", tripId)
                .orderByAsc("sort_order")
                .orderByAsc("created_at"));
    }

    private List<JournalEntry> findJournalEntries(String tripId, String userId) {
        return journalEntryMapper.selectList(new QueryWrapper<JournalEntry>()
                .eq("trip_id", tripId)
                .and(w -> w.eq("visibility", "all")
                        .or()
                        .eq("user_id", userId))
                .orderByDesc("occurred_at")
                .orderByDesc("created_at"));
    }

    private List<JournalPhoto> findJournalPhotos(String tripId, Set<String> entryIds) {
        if (entryIds.isEmpty()) {
            return List.of();
        }
        return journalPhotoMapper.selectList(new QueryWrapper<JournalPhoto>()
                .eq("trip_id", tripId)
                .in("journal_entry_id", entryIds)
                .orderByAsc("journal_entry_id")
                .orderByAsc("sort_order")
                .orderByAsc("created_at"));
    }

    private Map<String, File> findUploadedFilesByPhotoList(List<JournalPhoto> photos) {
        List<String> fileIds = photos.stream()
                .map(JournalPhoto::getFileId)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (fileIds.isEmpty()) {
            return Map.of();
        }
        return fileMapper.selectBatchIds(fileIds).stream()
                .filter(file -> "UPLOADED".equals(file.getStatus()))
                .collect(Collectors.toMap(File::getId, Function.identity(), (left, right) -> left));
    }

    private Map<String, List<SummaryResp.PhotoItem>> groupPhotosByEntryId(
            List<JournalPhoto> photos,
            Map<String, File> filesById) {
        Map<String, List<SummaryResp.PhotoItem>> result = new LinkedHashMap<>();
        for (JournalPhoto photo : photos) {
            File file = filesById.get(photo.getFileId());
            var ciUrls = fileUrlResolver.resolveUrls(file);
            if (!StringUtils.hasText(ciUrls.url())) {
                continue;
            }
            result.computeIfAbsent(photo.getJournalEntryId(), key -> new ArrayList<>())
                    .add(new SummaryResp.PhotoItem(
                            photo.getId(),
                            photo.getFileId(),
                            photo.getJournalEntryId(),
                            ciUrls.url(),
                            ciUrls.thumbnailUrl(),
                            ciUrls.previewUrl(),
                            ciUrls.exportUrl(),
                            ciUrls.storyPortraitUrl(),
                            ciUrls.storyCardUrl(),
                            ciUrls.squareUrl(),
                            ciUrls.watermarkedUrl(),
                            file.getWidth(),
                            file.getHeight(),
                            photo.getSortOrder(),
                            Boolean.TRUE.equals(photo.getIsFeatured())));
        }
        result.values().forEach(items -> items.sort(Comparator
                .comparing(SummaryResp.PhotoItem::sortOrder, Comparator.nullsLast(Integer::compareTo))));
        return result;
    }

    private SummaryResp.TripInfo toTripInfo(Trip trip, long dayCount) {
        SummaryResp.CoverPhoto coverPhoto = resolveCoverPhoto(trip);
        return new SummaryResp.TripInfo(
                trip.getId(),
                trip.getTitle(),
                trip.getTitle(),
                formatDate(trip.getStartDate()),
                formatDate(trip.getEndDate()),
                dayCount,
                calculateStatus(trip),
                coverPhoto);
    }

    private SummaryResp.CoverPhoto resolveCoverPhoto(Trip trip) {
        if (StringUtils.hasText(trip.getCoverFileId())) {
            File file = fileMapper.selectById(trip.getCoverFileId());
            if (file != null && "UPLOADED".equals(file.getStatus())) {
                var ciUrls = fileUrlResolver.resolveUrls(file);
                if (StringUtils.hasText(ciUrls.url())) {
                    return new SummaryResp.CoverPhoto(
                            file.getId(),
                            ciUrls.url(),
                            ciUrls.thumbnailUrl(),
                            ciUrls.previewUrl(),
                            ciUrls.exportUrl(),
                            ciUrls.storyPortraitUrl(),
                            ciUrls.storyCardUrl(),
                            ciUrls.squareUrl(),
                            ciUrls.watermarkedUrl(),
                            file.getWidth(),
                            file.getHeight());
                }
            }
        }
        return destinationCoverProvider.resolveSummaryCover(trip).orElse(null);
    }

    private SummaryResp.ExpenseSummary toExpenseSummary(
            BigDecimal totalExpense,
            List<ExpenseMapper.CategorySummary> categorySummaries) {
        List<SummaryResp.ExpenseCategoryStat> categoryStats = categorySummaries.stream()
                .map(summary -> new SummaryResp.ExpenseCategoryStat(
                        summary.category(),
                        categoryName(summary.category()),
                        formatMoney(summary.amountCny()),
                        formatPercentage(summary.amountCny(), totalExpense),
                        summary.cnt()))
                .toList();
        return new SummaryResp.ExpenseSummary(formatMoney(totalExpense), SUMMARY_CURRENCY, categoryStats);
    }

    private List<SummaryResp.LogItem> toHighlightLogs(
            List<JournalEntry> entries,
            Map<String, List<SummaryResp.PhotoItem>> photosByEntryId) {
        return entries.stream()
                .filter(this::isHighlight)
                .sorted(entryComparatorDesc())
                .limit(HIGHLIGHT_LOG_LIMIT)
                .map(entry -> toLogItem(entry, photosByEntryId))
                .toList();
    }

    private List<SummaryResp.PhotoItem> toUserFeaturedPhotos(
            Map<String, List<SummaryResp.PhotoItem>> photosByEntryId) {
        return photosByEntryId.values().stream()
                .flatMap(List::stream)
                .filter(SummaryResp.PhotoItem::userFeatured)
                .sorted(Comparator.comparing(SummaryResp.PhotoItem::sortOrder, Comparator.nullsLast(Integer::compareTo)))
                .limit(FEATURED_PHOTO_LIMIT)
                .toList();
    }

    private List<SummaryResp.PhotoItem> toRecommendedPhotos(
            List<JournalEntry> entries,
            Map<String, List<SummaryResp.PhotoItem>> photosByEntryId) {
        Map<String, JournalEntry> entriesById = entries.stream()
                .collect(Collectors.toMap(JournalEntry::getId, Function.identity()));
        List<SummaryPhotoCandidate> candidates = photosByEntryId.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream()
                        .map(photo -> new SummaryPhotoCandidate(photo, entriesById.get(entry.getKey()))))
                .sorted(candidateComparator())
                .toList();

        Map<String, SummaryResp.PhotoItem> uniquePhotos = new LinkedHashMap<>();
        for (SummaryPhotoCandidate candidate : candidates) {
            if (candidate.entry() == null) {
                continue;
            }
            if (candidate.photo().userFeatured()) {
                continue;
            }
            uniquePhotos.putIfAbsent(candidate.photo().fileId(), candidate.photo());
            if (uniquePhotos.size() >= FEATURED_PHOTO_LIMIT) {
                break;
            }
        }
        return new ArrayList<>(uniquePhotos.values());
    }

    private List<SummaryResp.DailyTimelineItem> toDailyTimeline(
            Trip trip,
            List<TripDay> days,
            List<ItineraryNode> nodes,
            List<JournalEntry> entries,
            Map<String, List<SummaryResp.PhotoItem>> photosByEntryId) {
        Map<String, TripDay> daysById = days.stream()
                .collect(Collectors.toMap(TripDay::getId, Function.identity(), (left, right) -> left));
        Map<LocalDate, List<SummaryResp.TimelineItineraryItem>> nodesByDate = new LinkedHashMap<>();
        for (ItineraryNode node : nodes) {
            TripDay day = daysById.get(node.getTripDayId());
            LocalDate date = day != null ? day.getDate() : null;
            if (date == null) {
                continue;
            }
            nodesByDate.computeIfAbsent(date, key -> new ArrayList<>()).add(toTimelineItinerary(node));
        }

        Map<LocalDate, List<SummaryResp.LogItem>> logsByDate = new LinkedHashMap<>();
        for (JournalEntry entry : entries) {
            if (entry.getOccurredAt() == null) {
                continue;
            }
            // occurredAt is UTC LocalDateTime; group by Asia/Shanghai business day
            // so stage-overview / stats align with iOS trip-detail day rails.
            LocalDate businessDate = DateTimeUtil.toBusinessDate(entry.getOccurredAt());
            if (businessDate == null) {
                continue;
            }
            logsByDate.computeIfAbsent(businessDate, key -> new ArrayList<>())
                    .add(toLogItem(entry, photosByEntryId));
        }
        logsByDate.values().forEach(logs -> logs.sort(logComparatorAsc()));

        Map<LocalDate, Integer> timelineDates = buildTimelineDates(trip, days, nodesByDate.keySet(), logsByDate.keySet());
        List<SummaryResp.DailyTimelineItem> result = new ArrayList<>();
        for (Map.Entry<LocalDate, Integer> dateEntry : timelineDates.entrySet()) {
            result.add(new SummaryResp.DailyTimelineItem(
                    dateEntry.getKey().toString(),
                    dateEntry.getValue(),
                    nodesByDate.getOrDefault(dateEntry.getKey(), List.of()),
                    logsByDate.getOrDefault(dateEntry.getKey(), List.of())));
        }
        return result;
    }

    private Map<LocalDate, Integer> buildTimelineDates(
            Trip trip,
            List<TripDay> days,
            Set<LocalDate> itineraryDates,
            Set<LocalDate> logDates) {
        Map<LocalDate, Integer> dates = new LinkedHashMap<>();
        if (!days.isEmpty()) {
            days.stream()
                    .sorted(Comparator.comparing(TripDay::getDayIndex, Comparator.nullsLast(Integer::compareTo)))
                    .forEach(day -> dates.put(day.getDate(), safeDayIndex(day.getDayIndex(), dates.size() + 1)));
        } else if (trip.getStartDate() != null && trip.getEndDate() != null && !trip.getEndDate().isBefore(trip.getStartDate())) {
            LocalDate date = trip.getStartDate();
            int dayIndex = 1;
            while (!date.isAfter(trip.getEndDate())) {
                dates.put(date, dayIndex++);
                date = date.plusDays(1);
            }
        }

        List<LocalDate> extraDates = new ArrayList<>();
        extraDates.addAll(itineraryDates);
        extraDates.addAll(logDates);
        extraDates.stream().distinct().sorted().forEach(date -> dates.putIfAbsent(date, dates.size() + 1));
        return dates;
    }

    private SummaryResp.TimelineItineraryItem toTimelineItinerary(ItineraryNode node) {
        return new SummaryResp.TimelineItineraryItem(
                node.getId(),
                node.getType(),
                node.getTitle(),
                node.getAddress(),
                DateTimeUtil.toTimeString(node.getStartTime()),
                DateTimeUtil.toTimeString(node.getEndTime()),
                com.tracci.module.trip.support.ItineraryNodeDetailsSupport.summaryLine(
                        node.getType(), node.getDetailsJson()));
    }

    private SummaryResp.LogItem toLogItem(
            JournalEntry entry,
            Map<String, List<SummaryResp.PhotoItem>> photosByEntryId) {
        return new SummaryResp.LogItem(
                entry.getId(),
                entry.getContent(),
                entry.getLocationName(),
                DateTimeUtil.toInstantString(entry.getOccurredAt()),
                entry.getIsHighlight(),
                photosByEntryId.getOrDefault(entry.getId(), List.of()));
    }

    private Comparator<JournalEntry> entryComparatorDesc() {
        return Comparator
                .comparing(JournalEntry::getOccurredAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(JournalEntry::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .reversed();
    }

    private Comparator<SummaryResp.LogItem> logComparatorAsc() {
        return Comparator.comparing(SummaryResp.LogItem::logTime, Comparator.nullsLast(String::compareTo));
    }

    private Comparator<SummaryPhotoCandidate> candidateComparator() {
        return Comparator
                .comparing((SummaryPhotoCandidate candidate) -> isHighlight(candidate.entry())).reversed()
                .thenComparing(candidate -> candidate.entry() != null ? candidate.entry().getOccurredAt() : null,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(candidate -> candidate.photo().sortOrder(), Comparator.nullsLast(Integer::compareTo));
    }

    private boolean isHighlight(JournalEntry entry) {
        return entry != null && Boolean.TRUE.equals(entry.getIsHighlight());
    }

    private long calculateDayCount(Trip trip) {
        if (trip.getStartDate() == null || trip.getEndDate() == null || trip.getEndDate().isBefore(trip.getStartDate())) {
            return 0;
        }
        return ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }

    private String calculateStatus(Trip trip) {
        return TripStatus.resolveValue(DateTimeUtil.todayBusiness(), trip.getStartDate(), trip.getEndDate(), trip.getStatus());
    }

    private String categoryName(String category) {
        return ExpenseCategory.displayNameOf(category);
    }

    private String formatPercentage(BigDecimal amount, BigDecimal total) {
        if (amount == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.00";
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String formatMoney(BigDecimal amount) {
        return zeroIfNull(amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private int safeDayIndex(Integer dayIndex, int fallback) {
        return dayIndex == null ? fallback : dayIndex;
    }

    private record SummaryPhotoCandidate(SummaryResp.PhotoItem photo, JournalEntry entry) {
    }
}
