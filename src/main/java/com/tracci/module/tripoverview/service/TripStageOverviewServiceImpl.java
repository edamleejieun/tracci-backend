package com.tracci.module.tripoverview.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.summary.service.SummaryService;
import com.tracci.module.trip.dto.response.TripWeatherResponse;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.enums.TripMemberRole;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.service.TripAccessService;
import com.tracci.module.trip.service.TripWeatherService;
import com.tracci.module.tripoverview.dto.response.TripStageOverviewResp;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TripStageOverviewServiceImpl implements TripStageOverviewService {

    private static final String CURRENCY_CNY = "CNY";
    private static final String ZERO_MONEY = "0.00";
    private static final String DENSITY_RULE_VERSION = "nodes_per_trip_day_v1";
    private static final String PACE_RULE_VERSION = "daily_node_count_v1";
    private static final long SUGGESTION_LOOKBACK_DAYS = 2;

    private final TripMapper tripMapper;
    private final TripAccessService tripAccessService;
    private final SummaryService summaryService;
    private final ExpenseMapper expenseMapper;
    private final TripWeatherService tripWeatherService;
    private final Clock clock;

    @Autowired
    public TripStageOverviewServiceImpl(
            TripMapper tripMapper,
            TripAccessService tripAccessService,
            SummaryService summaryService,
            ExpenseMapper expenseMapper,
            TripWeatherService tripWeatherService) {
        this(tripMapper, tripAccessService, summaryService, expenseMapper, tripWeatherService, Clock.systemDefaultZone());
    }

    TripStageOverviewServiceImpl(
            TripMapper tripMapper,
            TripAccessService tripAccessService,
            SummaryService summaryService,
            ExpenseMapper expenseMapper,
            TripWeatherService tripWeatherService,
            Clock clock) {
        this.tripMapper = tripMapper;
        this.tripAccessService = tripAccessService;
        this.summaryService = summaryService;
        this.expenseMapper = expenseMapper;
        this.tripWeatherService = tripWeatherService;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public TripStageOverviewResp getOverview(
            String requestedTripId,
            String clientPlatform,
            String requestedTimeZone) {
        String userId = LoginUserContext.requireUserId();
        TimeZoneResolution timeZone = resolveTimeZone(requestedTimeZone);
        LocalDate today = LocalDate.now(clock.withZone(timeZone.zoneId()));
        FocusSelection focus = selectFocusTrip(userId, requestedTripId, today);
        if (focus.trip() == null) {
            return emptyOverview(today, timeZone);
        }

        Trip trip = focus.trip();
        TripStageOverviewResp.Permissions permissions = resolvePermissions(trip, userId);
        String phase = phaseOf(trip, today);
        SummaryResp summary = summaryService.getSummary(trip.getId());
        Map<LocalDate, BigDecimal> expenseByDate = dailyExpenses(trip.getId(), userId);
        List<DayFacts> days = buildDayFacts(summary, expenseByDate);
        TripStageOverviewResp.MemoryMaterial memory = buildMemory(summary, days);

        TripStageOverviewResp.PlanningInsight planning = null;
        TripStageOverviewResp.OngoingInsight ongoing = null;
        TripStageOverviewResp.CompletedInsight completed = null;
        if ("PLANNING".equals(phase)) {
            planning = buildPlanning(trip, days, today, clientPlatform, timeZone.zoneId());
        } else if ("ONGOING".equals(phase)) {
            ongoing = buildOngoing(trip, days, today, permissions.canAddJournal(), summary);
        } else {
            completed = buildCompleted(summary, days);
        }

        return new TripStageOverviewResp(
                today.toString(),
                timeZone.zoneId().getId(),
                timeZone.fallbackApplied(),
                phase,
                toFocusTrip(trip),
                new TripStageOverviewResp.Selection(focus.mode(), focus.reason(), focus.candidateCount(), focus.candidates()),
                permissions,
                planning,
                ongoing,
                memory,
                completed,
                buildActions(phase, trip.getId(), planning, ongoing, permissions));
    }

    private FocusSelection selectFocusTrip(String userId, String requestedTripId, LocalDate today) {
        List<Trip> candidates = safeList(tripMapper.listMemberTrips(userId));
        List<TripStageOverviewResp.CandidateTrip> candidateTrips = candidates.stream()
                .sorted(Comparator.comparing(Trip::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Trip::getId, Comparator.nullsLast(String::compareTo)))
                .limit(20)
                .map(trip -> new TripStageOverviewResp.CandidateTrip(
                        trip.getId(),
                        trip.getTitle(),
                        StringUtils.hasText(trip.getDestinationName()) ? trip.getDestinationName() : trip.getTitle(),
                        formatDate(trip.getStartDate()),
                        formatDate(trip.getEndDate()),
                        tripDays(trip),
                        phaseOf(trip, today),
                        StringUtils.hasText(trip.getCoverFileId()) ? "USER_UPLOAD" : "NONE"))
                .toList();

        if (StringUtils.hasText(requestedTripId)) {
            Trip requested = tripAccessService.requirePermission(
                    requestedTripId.trim(), userId, TripPermission.READ).trip();
            return new FocusSelection(requested, "EXPLICIT", "REQUESTED_TRIP", candidates.size(), candidateTrips);
        }
        if (candidates.isEmpty()) {
            return new FocusSelection(null, "DEFAULT", "NO_TRIP", 0, List.of());
        }
        Comparator<Trip> stableUpdated = Comparator
                .comparing(Trip::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Trip::getId, Comparator.nullsLast(String::compareTo));
        List<Trip> ongoing = candidates.stream().filter(trip -> isOngoing(trip, today))
                .sorted(Comparator.comparing(Trip::getStartDate, Comparator.reverseOrder()).thenComparing(stableUpdated))
                .toList();
        if (!ongoing.isEmpty()) {
            return new FocusSelection(ongoing.getFirst(), "DEFAULT", "ONGOING_TRIP", candidates.size(), candidateTrips);
        }
        List<Trip> planning = candidates.stream()
                .filter(trip -> trip.getStartDate() != null && trip.getStartDate().isAfter(today))
                .sorted(Comparator.comparing(Trip::getStartDate).thenComparing(stableUpdated)).toList();
        if (!planning.isEmpty()) {
            return new FocusSelection(planning.getFirst(), "DEFAULT", "NEAREST_PLANNING_TRIP", candidates.size(), candidateTrips);
        }
        List<Trip> completed = candidates.stream()
                .filter(trip -> trip.getEndDate() != null && trip.getEndDate().isBefore(today))
                .sorted(Comparator.comparing(Trip::getEndDate, Comparator.reverseOrder()).thenComparing(stableUpdated))
                .toList();
        if (!completed.isEmpty()) {
            return new FocusSelection(completed.getFirst(), "DEFAULT", "LATEST_COMPLETED_TRIP", candidates.size(), candidateTrips);
        }
        return new FocusSelection(candidates.stream().sorted(stableUpdated).findFirst().orElse(null),
                "DEFAULT", "LATEST_UPDATED_TRIP", candidates.size(), candidateTrips);
    }

    private TripStageOverviewResp emptyOverview(LocalDate today, TimeZoneResolution timeZone) {
        return new TripStageOverviewResp(
                today.toString(), timeZone.zoneId().getId(), timeZone.fallbackApplied(), "NO_TRIP", null,
                new TripStageOverviewResp.Selection("DEFAULT", "NO_TRIP", 0, List.of()),
                null, null, null, null, null,
                List.of(new TripStageOverviewResp.ActionItem("CREATE_TRIP", "创建第一趟旅程", null, null)));
    }

    private List<DayFacts> buildDayFacts(SummaryResp summary, Map<LocalDate, BigDecimal> expenseByDate) {
        return safeList(summary.dailyTimeline()).stream()
                .map(day -> {
                    LocalDate date = LocalDate.parse(day.date());
                    return new DayFacts(
                            date,
                            day.dayIndex(),
                            safeList(day.itineraries()).size(),
                            safeList(day.logs()).size(),
                            safeList(day.logs()).stream().mapToLong(log -> safeList(log.photos()).size()).sum(),
                            expenseByDate.getOrDefault(date, BigDecimal.ZERO));
                })
                .sorted(Comparator.comparing(DayFacts::date))
                .toList();
    }

    private Map<LocalDate, BigDecimal> dailyExpenses(String tripId, String userId) {
        return safeList(expenseMapper.dailyVisibleByTripIdAndUserId(tripId, userId)).stream()
                .filter(item -> item.spentDate() != null)
                .collect(Collectors.toMap(
                        ExpenseMapper.DailyAmountSummary::spentDate,
                        item -> zeroIfNull(item.amountCny()),
                        BigDecimal::add,
                        LinkedHashMap::new));
    }

    private TripStageOverviewResp.PlanningInsight buildPlanning(
            Trip trip,
            List<DayFacts> days,
            LocalDate today,
            String clientPlatform,
            ZoneId timeZone) {
        long totalDays = tripDays(trip);
        long nodeCount = days.stream().mapToLong(DayFacts::itineraryCount).sum();
        BigDecimal average = totalDays == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(nodeCount).divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP);
        return new TripStageOverviewResp.PlanningInsight(
                totalDays,
                days.stream().filter(day -> day.itineraryCount() > 0).count(),
                days.stream().map(day -> new TripStageOverviewResp.DailyNodeCount(
                        day.date().toString(), day.dayIndex(), day.itineraryCount())).toList(),
                new TripStageOverviewResp.Density(
                        densityLevel(average), nodeCount, totalDays, average.toPlainString(), DENSITY_RULE_VERSION),
                Math.max(ChronoUnit.DAYS.between(today, trip.getStartDate()), 0),
                weatherAvailability(trip, normalizePlatform(clientPlatform), timeZone),
                days.stream().filter(day -> day.itineraryCount() == 0).map(day -> day.date().toString()).toList());
    }

    private TripStageOverviewResp.WeatherAvailability weatherAvailability(
            Trip trip,
            String clientPlatform,
            ZoneId timeZone) {
        TripWeatherResponse weather = tripWeatherService.getTripWeather(trip.getId(), clientPlatform, timeZone.getId());
        return new TripStageOverviewResp.WeatherAvailability(weather.available(), weather.status(), weather.message());
    }

    private TripStageOverviewResp.OngoingInsight buildOngoing(
            Trip trip,
            List<DayFacts> days,
            LocalDate today,
            boolean canAddJournal,
            SummaryResp summary) {
        DayFacts current = days.stream().filter(day -> day.date().equals(today)).findFirst()
                .orElse(new DayFacts(today, 0, 0, 0, 0, BigDecimal.ZERO));

        List<SummaryResp.TimelineItineraryItem> todayNodes = summary.dailyTimeline().stream()
                .filter(day -> day.date().equals(today.toString()))
                .findFirst()
                .map(SummaryResp.DailyTimelineItem::itineraries)
                .orElse(List.of());

        return new TripStageOverviewResp.OngoingInsight(
                new TripStageOverviewResp.TodaySnapshot(
                        today.toString(), current.itineraryCount(), formatMoney(current.expenseAmount()), CURRENCY_CNY,
                        current.journalCount(), current.photoCount(), todayNodes),
                canAddJournal ? selectSuggestion(trip, days, today) : List.of());
    }

    private List<TripStageOverviewResp.Suggestion> selectSuggestion(Trip trip, List<DayFacts> days, LocalDate today) {
        LocalDate earliest = today.minusDays(SUGGESTION_LOOKBACK_DAYS);
        DayFacts candidate = days.stream()
                .filter(day -> !day.date().isAfter(today) && !day.date().isBefore(earliest))
                .filter(day -> day.itineraryCount() > 0 && (day.journalCount() == 0 || day.photoCount() == 0))
                .sorted(Comparator.comparing(DayFacts::date).reversed())
                .findFirst()
                .orElse(null);
        if (candidate == null) {
            return List.of();
        }
        Map<String, Long> facts = new LinkedHashMap<>();
        facts.put("itineraryCount", candidate.itineraryCount());
        facts.put("journalCount", candidate.journalCount());
        facts.put("photoCount", candidate.photoCount());
        return List.of(new TripStageOverviewResp.Suggestion(
                trip.getId() + ":" + candidate.date() + ":MEMORY_FRAGMENT",
                candidate.date().toString(),
                facts,
                "ADD_MEMORY",
                candidate.date().toString()));
    }

    private TripStageOverviewResp.MemoryMaterial buildMemory(SummaryResp summary, List<DayFacts> days) {
        List<String> sections = new ArrayList<>();
        sections.add("TRIP_OVERVIEW");
        if (summary.stats().itineraryCount() > 0) sections.add("DAILY_ITINERARY");
        if (summary.stats().journalCount() > 0) sections.add("JOURNALS");
        if (summary.stats().photoCount() > 0) sections.add("PHOTOS");
        if (summary.stats().expenseCount() > 0) sections.add("EXPENSES");
        if (!safeList(summary.userFeaturedPhotos()).isEmpty()) sections.add("USER_FEATURED_PHOTOS");
        if (!safeList(summary.recommendedPhotos()).isEmpty()) sections.add("SYSTEM_RECOMMENDATIONS");
        if (sections.size() == 1) sections.add("BRAND_PLACEHOLDER");

        List<TripStageOverviewResp.RecentFragment> fragments = safeList(summary.dailyTimeline()).stream()
                .sorted(Comparator.comparing(SummaryResp.DailyTimelineItem::date).reversed())
                .flatMap(day -> safeList(day.logs()).stream().map(log -> new TripStageOverviewResp.RecentFragment(
                        log.id(), day.date(), StringUtils.hasText(log.content()), safeList(log.photos()).size())))
                .limit(3)
                .toList();
        SummaryResp.CoverPhoto cover = summary.trip().coverPhoto();
        String coverSource = cover == null ? "NONE"
                : StringUtils.hasText(cover.fileId()) ? "USER_UPLOAD" : "SYSTEM_DESTINATION";
        return new TripStageOverviewResp.MemoryMaterial(
                summary.stats().journalCount(),
                summary.stats().photoCount(),
                summary.stats().highlightCount(),
                safeList(summary.userFeaturedPhotos()).size(),
                safeList(summary.recommendedPhotos()).size(),
                days.stream().map(day -> new TripStageOverviewResp.DailyMaterial(
                        day.date().toString(), day.dayIndex(), day.itineraryCount(), day.journalCount(), day.photoCount()))
                        .toList(),
                fragments,
                new TripStageOverviewResp.CoverState(cover != null, coverSource),
                new TripStageOverviewResp.DisplayableContent(
                        sections,
                        presentationOf(summary.stats().journalCount(), summary.stats().photoCount())));
    }

    private String presentationOf(long journalCount, long photoCount) {
        return photoCount >= 3 || (journalCount >= 2 && photoCount >= 1)
                ? "MATERIAL_RICH"
                : "LIGHTWEIGHT";
    }

    private TripStageOverviewResp.CompletedInsight buildCompleted(SummaryResp summary, List<DayFacts> days) {
        BigDecimal total = new BigDecimal(summary.stats().totalExpenseAmount());
        String average = summary.stats().dayCount() == 0 ? ZERO_MONEY
                : formatMoney(total.divide(BigDecimal.valueOf(summary.stats().dayCount()), 2, RoundingMode.HALF_UP));
        DayFacts busiest = days.stream().max(Comparator.comparingLong(DayFacts::itineraryCount)).orElse(null);
        double averageNodes = days.stream().mapToLong(DayFacts::itineraryCount).average().orElse(0);
        TripStageOverviewResp.Pace pace = new TripStageOverviewResp.Pace(
                paceLevel(Math.round(averageNodes)),
                busiest == null ? null : busiest.date().toString(),
                busiest == null ? 0 : busiest.itineraryCount(),
                PACE_RULE_VERSION);
        List<TripStageOverviewResp.DailyTrend> dailyTrend = days.stream().map(day ->
                new TripStageOverviewResp.DailyTrend(
                        day.date().toString(), day.dayIndex(), day.itineraryCount(), day.journalCount(),
                        day.photoCount(), formatMoney(day.expenseAmount()), paceLevel(day.itineraryCount()))
        ).toList();
        TripStageOverviewResp.Highlights highlights = buildHighlights(summary, days, pace);
        return new TripStageOverviewResp.CompletedInsight(
                formatMoney(total), average, summary.stats().currency(),
                safeList(summary.expenseSummary().categoryStats()).stream()
                        .map(item -> new TripStageOverviewResp.CategoryStat(
                                item.category(), item.categoryName(), item.amount(), item.percentage(), item.count()))
                        .toList(),
                dailyTrend, pace, highlights);
    }

    private TripStageOverviewResp.Highlights buildHighlights(
            SummaryResp summary, List<DayFacts> days, TripStageOverviewResp.Pace pace) {
        if (days.isEmpty()) {
            return null;
        }
        return new TripStageOverviewResp.Highlights(
                toHighlightDay(findMostRelaxedDay(days), "MOST_RELAXED"),
                toHighlightDay(findBusiestDay(days, pace), "BUSIEST"),
                toHighlightDay(findMostMemorableDay(days), "MOST_MEMORABLE"),
                toRepresentativeLog(selectRepresentativeLog(summary)),
                new TripStageOverviewResp.FeaturedPhotoSummary(
                        summary.userFeaturedPhotos().size(),
                        summary.recommendedPhotos().size(),
                        selectTopPhotos(summary)));
    }

    private DayFacts findMostRelaxedDay(List<DayFacts> days) {
        return days.stream()
                .filter(day -> "RELAXED".equals(paceLevel(day.itineraryCount())))
                .min(Comparator.comparingLong(DayFacts::itineraryCount))
                .orElseGet(() -> days.stream()
                        .min(Comparator.comparingLong(DayFacts::itineraryCount))
                        .orElse(null));
    }

    private DayFacts findBusiestDay(List<DayFacts> days, TripStageOverviewResp.Pace pace) {
        DayFacts busiest = pace != null && StringUtils.hasText(pace.busiestDate())
                ? days.stream().filter(day -> day.date().toString().equals(pace.busiestDate())).findFirst().orElse(null)
                : null;
        if (busiest == null) {
            busiest = days.stream().max(Comparator.comparingLong(DayFacts::itineraryCount)).orElse(null);
        }
        return busiest;
    }

    private DayFacts findMostMemorableDay(List<DayFacts> days) {
        return days.stream()
                .max(Comparator.comparingLong(day -> day.journalCount() + day.photoCount()))
                .orElse(null);
    }

    private SummaryResp.LogItem selectRepresentativeLog(SummaryResp summary) {
        return summary.highlightLogs().stream().findFirst()
                .orElseGet(() -> summary.dailyTimeline().stream()
                        .flatMap(day -> day.logs().stream())
                        .filter(log -> StringUtils.hasText(log.content()))
                        .findFirst()
                        .orElse(null));
    }

    private List<SummaryResp.PhotoItem> selectTopPhotos(SummaryResp summary) {
        List<SummaryResp.PhotoItem> topPhotos = new ArrayList<>(summary.userFeaturedPhotos());
        Set<String> existingFileIds = topPhotos.stream()
                .map(SummaryResp.PhotoItem::fileId)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        for (SummaryResp.PhotoItem photo : summary.recommendedPhotos()) {
            if (topPhotos.size() >= 6) break;
            if (StringUtils.hasText(photo.fileId()) && !existingFileIds.contains(photo.fileId())) {
                topPhotos.add(photo);
                existingFileIds.add(photo.fileId());
            }
        }
        return topPhotos;
    }

    private TripStageOverviewResp.HighlightDay toHighlightDay(DayFacts day, String label) {
        if (day == null) {
            return null;
        }
        return new TripStageOverviewResp.HighlightDay(
                day.date().toString(), day.dayIndex(), label,
                day.itineraryCount(), day.journalCount(), day.photoCount(), paceLevel(day.itineraryCount()));
    }

    private TripStageOverviewResp.RepresentativeLog toRepresentativeLog(SummaryResp.LogItem log) {
        if (log == null) {
            return null;
        }
        String content = StringUtils.hasText(log.content()) ? log.content() : "";
        String summary = content.length() > 80 ? content.substring(0, 80) + "…" : content;
        return new TripStageOverviewResp.RepresentativeLog(
                log.id(), log.logTime(), summary, log.locationName(),
                Boolean.TRUE.equals(log.isHighlight()), safeList(log.photos()).size());
    }
    private List<TripStageOverviewResp.ActionItem> buildActions(
            String phase,
            String tripId,
            TripStageOverviewResp.PlanningInsight planning,
            TripStageOverviewResp.OngoingInsight ongoing,
            TripStageOverviewResp.Permissions permissions) {
        if ("PLANNING".equals(phase) && planning != null && !planning.unplannedDates().isEmpty()) {
            return List.of(new TripStageOverviewResp.ActionItem(
                    "OPEN_ITINERARY", "继续看看行程", tripId, planning.unplannedDates().getFirst()));
        }
        if ("ONGOING".equals(phase) && permissions.canAddJournal()
                && ongoing != null && !ongoing.suggestions().isEmpty()) {
            var suggestion = ongoing.suggestions().getFirst();
            return List.of(new TripStageOverviewResp.ActionItem(
                    "ADD_MEMORY", "记录此刻", tripId, suggestion.targetDate()));
        }
        if ("COMPLETED".equals(phase)) {
            return List.of(new TripStageOverviewResp.ActionItem(
                    "VIEW_STORY", "查看故事回顾", tripId, null));
        }
        return List.of();
    }

    private TripStageOverviewResp.Permissions resolvePermissions(Trip trip, String userId) {
        TripAccessService.TripAccess access =
                tripAccessService.requirePermission(trip.getId(), userId, TripPermission.READ);
        boolean canWrite = access.allows(TripPermission.EDIT_CONTENT);
        return new TripStageOverviewResp.Permissions(canWrite, canWrite);
    }

    private TimeZoneResolution resolveTimeZone(String requested) {
        ZoneId fallback = clock.getZone() == null ? ZoneOffset.UTC : clock.getZone();
        if (!StringUtils.hasText(requested)) {
            return new TimeZoneResolution(fallback, false);
        }
        try {
            return new TimeZoneResolution(ZoneId.of(requested.trim()), false);
        } catch (Exception ignored) {
            return new TimeZoneResolution(fallback, true);
        }
    }

    private String normalizePlatform(String clientPlatform) {
        return StringUtils.hasText(clientPlatform) ? clientPlatform.trim().toUpperCase() : null;
    }

    private TripStageOverviewResp.FocusTrip toFocusTrip(Trip trip) {
        return new TripStageOverviewResp.FocusTrip(
                trip.getId(), trip.getTitle(),
                StringUtils.hasText(trip.getDestinationName()) ? trip.getDestinationName() : trip.getTitle(),
                formatDate(trip.getStartDate()), formatDate(trip.getEndDate()), tripDays(trip));
    }

    private String phaseOf(Trip trip, LocalDate today) {
        if (trip.getStartDate() != null && today.isBefore(trip.getStartDate())) return "PLANNING";
        if (trip.getEndDate() != null && today.isAfter(trip.getEndDate())) return "COMPLETED";
        return "ONGOING";
    }

    private boolean isOngoing(Trip trip, LocalDate today) {
        return trip.getStartDate() != null && trip.getEndDate() != null
                && !today.isBefore(trip.getStartDate()) && !today.isAfter(trip.getEndDate());
    }

    private long tripDays(Trip trip) {
        if (trip.getStartDate() == null || trip.getEndDate() == null || trip.getEndDate().isBefore(trip.getStartDate())) {
            return 0;
        }
        return ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }

    private String densityLevel(BigDecimal average) {
        if (average.compareTo(BigDecimal.ZERO) == 0) return "EMPTY";
        if (average.compareTo(BigDecimal.valueOf(2)) < 0) return "LIGHT";
        if (average.compareTo(BigDecimal.valueOf(4)) <= 0) return "MODERATE";
        return "DENSE";
    }

    private String paceLevel(long nodeCount) {
        if (nodeCount == 0) return "EMPTY";
        if (nodeCount <= 2) return "RELAXED";
        if (nodeCount <= 4) return "BALANCED";
        return "BUSY";
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

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private record FocusSelection(Trip trip, String mode, String reason, int candidateCount,
                                  List<TripStageOverviewResp.CandidateTrip> candidates) {
    }

    private record TimeZoneResolution(ZoneId zoneId, boolean fallbackApplied) {
    }

    private record DayFacts(
            LocalDate date,
            int dayIndex,
            long itineraryCount,
            long journalCount,
            long photoCount,
            BigDecimal expenseAmount) {
    }
}
