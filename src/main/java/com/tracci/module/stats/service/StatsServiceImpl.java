package com.tracci.module.stats.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.module.expense.enums.ExpenseCategory;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.journal.repository.JournalEntryMapper;
import com.tracci.module.journal.repository.JournalPhotoMapper;
import com.tracci.module.stats.dto.response.StatsFootprintAnalysisResp;
import com.tracci.module.stats.dto.response.StatsOverviewResp;
import com.tracci.module.stats.dto.response.StatsRecordsAnalysisResp;
import com.tracci.module.stats.dto.response.StatsSpendAnalysisResp;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.summary.service.SummaryService;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.enums.TripStatus;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final String CURRENCY_CNY = "CNY";
    private static final String ZERO_MONEY = "0.00";
    private static final int RECENT_TRIP_LIMIT = 5;
    private static final int MONTHLY_TREND_MONTHS = 5;
    private static final int DAILY_HIGHLIGHT_MAX_LENGTH = 4;
    private final TripMapper tripMapper;
    private final ItineraryNodeMapper itineraryNodeMapper;
    private final JournalEntryMapper journalEntryMapper;
    private final JournalPhotoMapper journalPhotoMapper;
    private final ExpenseMapper expenseMapper;
    private final SummaryService summaryService;

    @Override
    @Transactional(readOnly = true)
    public StatsOverviewResp getOverview(String tripId, String timeZone) {
        if (StringUtils.hasText(tripId)) {
            return buildSingleTripStats(tripId);
        }
        return buildGlobalStats(resolveToday(timeZone));
    }

    private StatsOverviewResp buildGlobalStats(LocalDate today) {
        String userId = LoginUserContext.requireUserId();

        List<Trip> trips = findStartedUserTrips(userId, today);

        long tripCount = trips.size();
        List<String> tripIds = trips.stream()
                .map(Trip::getId)
                .toList();
        long totalTravelDays = trips.stream()
                .mapToLong(this::tripDays)
                .sum();
        long destinationCount = trips.stream()
                .map(this::destinationName)
                .filter(StringUtils::hasText)
                .distinct()
                .count();

        long itineraryCount = tripIds.isEmpty() ? 0 : itineraryNodeMapper.countAllByTripIds(tripIds);
        if (itineraryCount == 0 && !tripIds.isEmpty()) {
            itineraryCount = itineraryNodeMapper.countByUserIdAndTripIds(userId, tripIds);
        }
        long journalCount = tripIds.isEmpty()
                ? 0
                : journalEntryMapper.countVisibleByTripIdsAndUserId(tripIds, userId);
        long photoCount = tripIds.isEmpty()
                ? 0
                : journalPhotoMapper.countVisibleUploadedPhotoByTripIdsAndUserId(tripIds, userId);

        BigDecimal rawTotal = tripIds.isEmpty()
                ? BigDecimal.ZERO
                : expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, userId).stream()
                        .map(ExpenseMapper.TripAmountSummary::amountCny)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpense = rawTotal != null ? rawTotal : BigDecimal.ZERO;
        List<ExpenseMapper.CategorySummary> categorySummaries = tripIds.isEmpty()
                ? List.of()
                : safeList(expenseMapper.categoryVisibleByTripIdsAndUserId(tripIds, userId));

        long expenseCount = categorySummaries.stream()
                .mapToLong(ExpenseMapper.CategorySummary::cnt)
                .sum();

        String totalExpenseStr = formatMoney(totalExpense);
        String averageDailyExpense = totalTravelDays > 0
                ? formatMoney(totalExpense.divide(BigDecimal.valueOf(totalTravelDays), 2, RoundingMode.HALF_UP))
                : ZERO_MONEY;

        StatsContext context = buildStatsContext(tripIds, userId);
        return new StatsOverviewResp(
                new StatsOverviewResp.Scope("ALL", null, null),
                new StatsOverviewResp.Overview(
                        tripCount,
                        totalTravelDays,
                        itineraryCount,
                        journalCount,
                        photoCount,
                        destinationCount,
                        expenseCount,
                        totalExpenseStr,
                        averageDailyExpense,
                        CURRENCY_CNY),
                buildExpenseStats(totalExpense, categorySummaries),
                buildRecentTrips(trips, userId, context, today),
                buildMonthlyTrend(tripIds, userId, today),
                List.of(),
                buildGlobalInsights(trips, totalExpense, categorySummaries, context),
                buildAnalysisEntries(trips, totalTravelDays, itineraryCount, journalCount, photoCount, totalExpense,
                        categorySummaries, context),
                buildGlobalMemoryFacts(context)
        );
    }

    private StatsOverviewResp buildSingleTripStats(String tripId) {
        SummaryResp summary = summaryService.getSummary(tripId);
        String userId = LoginUserContext.requireUserId();

        long totalTravelDays = summary.stats().dayCount();
        String totalExpenseStr = summary.stats().totalExpenseAmount();
        BigDecimal totalExpense = new BigDecimal(totalExpenseStr);
        String averageDailyExpense = totalTravelDays > 0
                ? formatMoney(totalExpense.divide(BigDecimal.valueOf(totalTravelDays), 2, RoundingMode.HALF_UP))
                : ZERO_MONEY;

        List<StatsOverviewResp.CategoryStat> categoryStats = summary.expenseSummary().categoryStats().stream()
                .map(s -> new StatsOverviewResp.CategoryStat(
                        s.category(),
                        s.categoryName(),
                        s.amount(),
                        s.percentage(),
                        s.count()))
                .toList();

        List<ExpenseMapper.DailyAmountSummary> dailySummaries =
                safeList(expenseMapper.dailyVisibleByTripIdAndUserId(tripId, userId));
        Map<String, BigDecimal> expenseByDate = dailySummaries.stream()
                .collect(Collectors.toMap(
                        summaryItem -> summaryItem.spentDate().toString(),
                        summaryItem -> summaryItem.amountCny() == null ? BigDecimal.ZERO : summaryItem.amountCny(),
                        (left, right) -> left));

        List<StatsOverviewResp.DailyTrendItem> dailyTrend = buildDailyTrend(summary, expenseByDate);
        StatsOverviewResp.MemoryFacts memory = buildTripMemoryFacts(summary);

        return new StatsOverviewResp(
                new StatsOverviewResp.Scope("TRIP", summary.trip().id(), summary.trip().title()),
                new StatsOverviewResp.Overview(
                        1,
                        totalTravelDays,
                        summary.stats().itineraryCount(),
                        summary.stats().journalCount(),
                        summary.stats().photoCount(),
                        1,
                        summary.stats().expenseCount(),
                        totalExpenseStr,
                        averageDailyExpense,
                        summary.stats().currency()),
                new StatsOverviewResp.ExpenseStats(
                        summary.expenseSummary().totalAmount(),
                        summary.expenseSummary().currency(),
                        categoryStats),
                List.of(),
                List.of(),
                dailyTrend,
                List.of(),
                List.of(),
                memory
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StatsSpendAnalysisResp getSpendAnalysis(String tripId, String timeZone) {
        if (StringUtils.hasText(tripId)) {
            SummaryResp summary = summaryService.getSummary(tripId);
            BigDecimal totalExpense = new BigDecimal(summary.expenseSummary().totalAmount());
            List<StatsOverviewResp.CategoryStat> categoryStats = summary.expenseSummary().categoryStats().stream()
                    .map(s -> new StatsOverviewResp.CategoryStat(
                            s.category(), s.categoryName(), s.amount(), s.percentage(), s.count()))
                    .toList();
            return new StatsSpendAnalysisResp(
                    formatMoney(totalExpense),
                    averageMoney(totalExpense, summary.stats().dayCount()),
                    formatMoney(totalExpense),
                    summary.stats().expenseCount(),
                    summary.stats().currency(),
                    categoryStats.isEmpty() ? null : categoryStats.getFirst().category(),
                    categoryStats.isEmpty() ? null : categoryStats.getFirst().categoryName(),
                    spendInsight(categoryStats),
                    categoryStats,
                    List.of(new StatsSpendAnalysisResp.TripSpendEfficiency(
                            summary.trip().id(),
                            summary.trip().title(),
                            summary.trip().destination(),
                            summary.stats().dayCount(),
                            formatMoney(totalExpense),
                            averageMoney(totalExpense, summary.stats().dayCount()))));
        }

        String userId = LoginUserContext.requireUserId();
        List<Trip> trips = findStartedUserTrips(userId, resolveToday(timeZone));
        List<String> tripIds = trips.stream().map(Trip::getId).toList();
        BigDecimal totalExpense = zeroIfNull(tripIds.isEmpty()
                ? BigDecimal.ZERO
                : expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, userId).stream()
                        .map(ExpenseMapper.TripAmountSummary::amountCny)
                        .filter(java.util.Objects::nonNull)
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
        List<ExpenseMapper.CategorySummary> categorySummaries = tripIds.isEmpty()
                ? List.of()
                : safeList(expenseMapper.categoryVisibleByTripIdsAndUserId(tripIds, userId));
        StatsContext context = buildStatsContext(tripIds, userId);
        List<StatsOverviewResp.CategoryStat> categoryStats = buildExpenseStats(totalExpense, categorySummaries)
                .categoryStats();
        long totalTravelDays = trips.stream().mapToLong(this::tripDays).sum();
        List<StatsSpendAnalysisResp.TripSpendEfficiency> efficiencies = trips.stream()
                .map(trip -> {
                    BigDecimal amount = context.expenseByTripId().getOrDefault(trip.getId(), BigDecimal.ZERO);
                    long days = tripDays(trip);
                    return new StatsSpendAnalysisResp.TripSpendEfficiency(
                            trip.getId(),
                            trip.getTitle(),
                            destinationName(trip),
                            days,
                            formatMoney(amount),
                            averageMoney(amount, days));
                })
                .sorted(Comparator.comparing(
                        (StatsSpendAnalysisResp.TripSpendEfficiency item) -> new BigDecimal(item.averageDailyExpense()))
                        .reversed())
                .toList();

        return new StatsSpendAnalysisResp(
                formatMoney(totalExpense),
                averageMoney(totalExpense, totalTravelDays),
                averageMoney(totalExpense, trips.size()),
                categorySummaries.stream().mapToLong(ExpenseMapper.CategorySummary::cnt).sum(),
                CURRENCY_CNY,
                categoryStats.isEmpty() ? null : categoryStats.getFirst().category(),
                categoryStats.isEmpty() ? null : categoryStats.getFirst().categoryName(),
                spendInsight(categoryStats),
                categoryStats,
                efficiencies);
    }

    @Override
    @Transactional(readOnly = true)
    public StatsRecordsAnalysisResp getRecordsAnalysis(String tripId, String timeZone) {
        if (StringUtils.hasText(tripId)) {
            SummaryResp summary = summaryService.getSummary(tripId);
            return new StatsRecordsAnalysisResp(
                    summary.stats().journalCount(),
                    summary.stats().photoCount(),
                    summary.stats().itineraryCount(),
                    List.of(new StatsRecordsAnalysisResp.TripRecords(
                            summary.trip().id(),
                            summary.trip().title(),
                            summary.trip().destination(),
                            summary.stats().itineraryCount(),
                            summary.stats().journalCount(),
                            summary.stats().photoCount(),
                            summary.stats().expenseCount())));
        }

        String userId = LoginUserContext.requireUserId();
        List<Trip> trips = findStartedUserTrips(userId, resolveToday(timeZone));
        List<String> tripIds = trips.stream().map(Trip::getId).toList();
        StatsContext context = buildStatsContext(tripIds, userId);
        List<StatsRecordsAnalysisResp.TripRecords> tripRecords = trips.stream()
                .map(trip -> {
                    TripMetrics metrics = metricsOf(trip, context);
                    return new StatsRecordsAnalysisResp.TripRecords(
                            trip.getId(),
                            trip.getTitle(),
                            destinationName(trip),
                            metrics.itineraryCount(),
                            metrics.journalCount(),
                            metrics.photoCount(),
                            metrics.expenseCount());
                })
                .toList();
        return new StatsRecordsAnalysisResp(
                context.journalByTripId().values().stream().mapToLong(Long::longValue).sum(),
                context.photoByTripId().values().stream().mapToLong(Long::longValue).sum(),
                context.itineraryByTripId().values().stream().mapToLong(Long::longValue).sum(),
                tripRecords);
    }

    @Override
    @Transactional(readOnly = true)
    public StatsFootprintAnalysisResp getFootprintAnalysis(String tripId, String timeZone) {
        if (StringUtils.hasText(tripId)) {
            summaryService.getSummary(tripId);
        }
        String userId = LoginUserContext.requireUserId();
        List<Trip> trips = findStartedUserTrips(userId, resolveToday(timeZone));
        if (StringUtils.hasText(tripId)) {
            trips = trips.stream().filter(trip -> tripId.equals(trip.getId())).toList();
        }
        List<String> tripIds = trips.stream().map(Trip::getId).toList();
        long itineraryCount = tripIds.isEmpty() ? 0 : itineraryNodeMapper.countAllByTripIds(tripIds);
        if (itineraryCount == 0 && !tripIds.isEmpty()) {
            itineraryCount = itineraryNodeMapper.countByUserIdAndTripIds(userId, tripIds);
        }
        StatsContext context = buildStatsContext(tripIds, userId);
        List<StatsFootprintAnalysisResp.CityFootprint> cities = buildCityFootprints(trips, context);
        List<ItineraryNodeMapper.NodeTypeSummary> nodeTypeSummaries = tripIds.isEmpty()
                ? List.of()
                : safeList(itineraryNodeMapper.nodeTypeSummaryByTripIds(tripIds));
        if (nodeTypeSummaries.isEmpty() && !tripIds.isEmpty()) {
            nodeTypeSummaries = safeList(itineraryNodeMapper.nodeTypeSummaryByUserIdAndTripIds(userId, tripIds));
        }
        List<StatsFootprintAnalysisResp.NodeTypeStat> nodeTypes = nodeTypeSummaries.stream()
                .map(item -> new StatsFootprintAnalysisResp.NodeTypeStat(
                        item.type(),
                        nodeTypeName(item.type()),
                        item.cnt()))
                .toList();
        long totalTravelDays = trips.stream().mapToLong(this::tripDays).sum();
        String insight = cities.isEmpty()
                ? "还没有足迹数据。添加旅程目的地和行程节点后，这里会展示去过的城市。"
                : "你去过 " + cities.size() + " 座城市，留下 " + itineraryCount + " 个行程节点。";
        return new StatsFootprintAnalysisResp(
                cities.size(),
                itineraryCount,
                totalTravelDays,
                insight,
                cities,
                nodeTypes);
    }

    private StatsOverviewResp.ExpenseStats buildExpenseStats(
            BigDecimal totalExpense,
            List<ExpenseMapper.CategorySummary> categorySummaries) {
        List<StatsOverviewResp.CategoryStat> categoryStats = categorySummaries.stream()
                .map(s -> new StatsOverviewResp.CategoryStat(
                        s.category(),
                        ExpenseCategory.displayNameOf(s.category()),
                        formatMoney(s.amountCny()),
                        formatPercentage(s.amountCny(), totalExpense),
                        s.cnt()))
                .toList();
        return new StatsOverviewResp.ExpenseStats(formatMoney(totalExpense), CURRENCY_CNY, categoryStats);
    }

    private List<StatsOverviewResp.RecentTrip> buildRecentTrips(
            List<Trip> trips,
            String userId,
            StatsContext context,
            LocalDate today) {
        List<Trip> recentTrips = trips.stream()
                .limit(RECENT_TRIP_LIMIT)
                .toList();
        List<String> tripIds = recentTrips.stream()
                .map(Trip::getId)
                .toList();
        Map<String, BigDecimal> expenseByTripId = tripIds.isEmpty()
                ? Map.of()
                : expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, userId).stream()
                .collect(Collectors.toMap(
                        ExpenseMapper.TripAmountSummary::tripId,
                        summary -> summary.amountCny() == null ? BigDecimal.ZERO : summary.amountCny(),
                        (left, right) -> left));
        if (expenseByTripId.isEmpty() && !tripIds.isEmpty()) {
            expenseByTripId = expenseMapper.sumAmountCnyByTripIdsAndUserId(tripIds, userId).stream()
                    .collect(Collectors.toMap(
                            ExpenseMapper.TripAmountSummary::tripId,
                            summary -> summary.amountCny() == null ? BigDecimal.ZERO : summary.amountCny(),
                            (left, right) -> left));
        }
        Map<String, BigDecimal> finalExpenseByTripId = expenseByTripId;

        return recentTrips.stream()
                .map(trip -> {
                    BigDecimal expense = finalExpenseByTripId.getOrDefault(trip.getId(), BigDecimal.ZERO);
                    TripMetrics metrics = metricsOf(trip, context);
                    return new StatsOverviewResp.RecentTrip(
                            trip.getId(),
                            trip.getTitle(),
                            destinationName(trip),
                            formatDate(trip.getStartDate()),
                            formatDate(trip.getEndDate()),
                            calculateStatus(trip, today),
                            tripDays(trip),
                            formatMoney(expense),
                            averageMoney(expense, tripDays(trip)));
                })
                .toList();
    }

    private List<StatsOverviewResp.DailyTrendItem> buildDailyTrend(
            SummaryResp summary,
            Map<String, BigDecimal> expenseByDate) {
        Map<String, StatsOverviewResp.DailyTrendItem> itemsByDate = new LinkedHashMap<>();
        for (SummaryResp.DailyTimelineItem day : summary.dailyTimeline()) {
            BigDecimal expense = expenseByDate.get(day.date());
            long itineraryCount = day.itineraries().size();
            long journalCount = day.logs().size();
            long photoCount = countPhotos(day.logs());
            if (itineraryCount == 0 && journalCount == 0 && photoCount == 0 && isZero(expense)) {
                continue;
            }
            itemsByDate.put(day.date(), new StatsOverviewResp.DailyTrendItem(
                    day.date(),
                    day.dayIndex(),
                    dailyHighlight(day),
                    positiveOrNull(itineraryCount),
                    positiveOrNull(journalCount),
                    isZero(expense) ? null : formatMoney(expense),
                    positiveOrNull(photoCount)));
        }

        LocalDate startDate = parseDate(summary.trip().startDate());
        int nextDayIndex = itemsByDate.values().stream()
                .mapToInt(StatsOverviewResp.DailyTrendItem::dayIndex)
                .max()
                .orElse(0) + 1;
        List<String> missingExpenseDates = expenseByDate.keySet().stream()
                .filter(date -> !itemsByDate.containsKey(date))
                .sorted()
                .toList();
        for (String date : missingExpenseDates) {
            int dayIndex = calculateDayIndex(startDate, date, nextDayIndex++);
            itemsByDate.put(date, new StatsOverviewResp.DailyTrendItem(
                    date,
                    dayIndex,
                    defaultDailyHighlight(dayIndex),
                    null,
                    null,
                    formatMoney(expenseByDate.get(date)),
                    null));
        }

        List<StatsOverviewResp.DailyTrendItem> result = new ArrayList<>(itemsByDate.values());
        result.sort((left, right) -> left.date().compareTo(right.date()));
        return result;
    }

    private List<StatsOverviewResp.MonthlyTrendItem> buildMonthlyTrend(
            List<String> tripIds,
            String userId,
            LocalDate today) {
        YearMonth currentMonth = YearMonth.from(today);
        YearMonth firstMonth = currentMonth.minusMonths(MONTHLY_TREND_MONTHS - 1L);
        LocalDateTime startTime = firstMonth.atDay(1).atStartOfDay();
        LocalDateTime endTime = currentMonth.plusMonths(1).atDay(1).atStartOfDay();
        List<ExpenseMapper.MonthlyAmountSummary> monthlySummaries = tripIds.isEmpty()
                ? List.of()
                : safeList(expenseMapper.monthlyVisibleByTripIdsAndUserId(tripIds, userId, startTime, endTime));
        Map<String, ExpenseMapper.MonthlyAmountSummary> summariesByMonth = monthlySummaries.stream()
                .collect(Collectors.toMap(
                        ExpenseMapper.MonthlyAmountSummary::expenseMonth,
                        summary -> summary,
                        (left, right) -> left));

        List<StatsOverviewResp.MonthlyTrendItem> trend = new ArrayList<>();
        for (int i = 0; i < MONTHLY_TREND_MONTHS; i++) {
            YearMonth month = firstMonth.plusMonths(i);
            String yearMonth = month.toString();
            ExpenseMapper.MonthlyAmountSummary summary = summariesByMonth.get(yearMonth);
            trend.add(new StatsOverviewResp.MonthlyTrendItem(
                    yearMonth,
                    formatMoney(summary == null ? BigDecimal.ZERO : summary.amountCny()),
                    summary == null ? 0 : summary.cnt()));
        }
        return trend;
    }

    private String dailyHighlight(SummaryResp.DailyTimelineItem day) {
        if (day.dayIndex() == 1) {
            return "到达日";
        }

        String itineraryTitle = day.itineraries().stream()
                .map(SummaryResp.TimelineItineraryItem::title)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (StringUtils.hasText(itineraryTitle)) {
            return compactHighlight(itineraryTitle);
        }

        String itineraryLocation = day.itineraries().stream()
                .map(SummaryResp.TimelineItineraryItem::locationName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (StringUtils.hasText(itineraryLocation)) {
            return compactHighlight(itineraryLocation);
        }

        String logLocation = day.logs().stream()
                .map(SummaryResp.LogItem::locationName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (StringUtils.hasText(logLocation)) {
            return compactHighlight(logLocation);
        }

        String logContent = day.logs().stream()
                .map(SummaryResp.LogItem::content)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (StringUtils.hasText(logContent)) {
            return compactHighlight(logContent);
        }

        return defaultDailyHighlight(day.dayIndex());
    }

    private String defaultDailyHighlight(int dayIndex) {
        if (dayIndex == 1) {
            return "到达日";
        }
        String[] highlights = {"慢游日", "寻味日", "漫步日", "好景日", "小憩日"};
        return highlights[Math.floorMod(dayIndex - 2, highlights.length)];
    }

    private String compactHighlight(String value) {
        String cleaned = value.replaceAll("[\\s，。,.、!！?？:：;；()（）\\[\\]【】《》<>\"'“”‘’_-]+", "");
        if (!StringUtils.hasText(cleaned)) {
            return "漫步日";
        }
        return cleaned.codePoints()
                .limit(DAILY_HIGHLIGHT_MAX_LENGTH)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return LocalDate.parse(value);
    }

    private int calculateDayIndex(LocalDate startDate, String date, int fallback) {
        if (startDate == null) {
            return fallback;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(startDate, LocalDate.parse(date)) + 1);
    }

    private long countPhotos(List<SummaryResp.LogItem> logs) {
        return logs.stream()
                .map(SummaryResp.LogItem::photos)
                .filter(list -> list != null)
                .mapToLong(List::size)
                .sum();
    }

    private List<Trip> findUserTrips(String userId) {
        List<Trip> memberTrips = tripMapper.listMemberTrips(userId);
        if (memberTrips != null && !memberTrips.isEmpty()) {
            return memberTrips;
        }
        return tripMapper.selectList(new QueryWrapper<Trip>()
                .eq("user_id", userId)
                .orderByDesc("created_at"));
    }

    private List<Trip> findStartedUserTrips(String userId, LocalDate today) {
        List<Trip> memberTrips = tripMapper.listStartedMemberTrips(userId, today);
        if (memberTrips != null && !memberTrips.isEmpty()) {
            return memberTrips;
        }
        return findUserTrips(userId).stream().filter(trip -> hasTripStarted(trip, today)).toList();
    }

    private StatsContext buildStatsContext(List<String> tripIds, String userId) {
        if (tripIds.isEmpty()) {
            return new StatsContext(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        Map<String, Long> itineraryByTripId = toLongMap(safeList(
                itineraryNodeMapper.countGroupByTripIds(tripIds)));
        if (itineraryByTripId.isEmpty()) {
            itineraryByTripId = toLongMap(safeList(itineraryNodeMapper.countByTripIdsAndUserId(tripIds, userId)));
        }
        Map<String, Long> journalByTripId = toLongMap(safeList(
                journalEntryMapper.countVisibleGroupByTripIdsAndUserId(tripIds, userId)));
        Map<String, Long> photoByTripId = safeList(
                journalPhotoMapper.countVisibleUploadedPhotoGroupByTripIdsAndUserId(tripIds, userId)).stream()
                .collect(Collectors.toMap(
                        JournalPhotoMapper.TripCountSummary::tripId,
                        JournalPhotoMapper.TripCountSummary::cnt,
                        (left, right) -> left));
        Map<String, Long> expenseCountByTripId = safeList(
                expenseMapper.countVisibleByTripIdsAndUserId(tripIds, userId)).stream()
                .collect(Collectors.toMap(
                        ExpenseMapper.TripCountSummary::tripId,
                        ExpenseMapper.TripCountSummary::cnt,
                        (left, right) -> left));
        Map<String, BigDecimal> expenseByTripId = safeList(
                expenseMapper.sumVisibleAmountCnyByTripIdsAndUserId(tripIds, userId)).stream()
                .collect(Collectors.toMap(
                        ExpenseMapper.TripAmountSummary::tripId,
                        summary -> zeroIfNull(summary.amountCny()),
                        (left, right) -> left));
        return new StatsContext(itineraryByTripId, journalByTripId, photoByTripId, expenseCountByTripId,
                expenseByTripId);
    }

    private Map<String, Long> toLongMap(List<? extends Object> summaries) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object summary : summaries) {
            if (summary instanceof ItineraryNodeMapper.TripCountSummary item) {
                result.put(item.tripId(), item.cnt());
            } else if (summary instanceof JournalEntryMapper.TripCountSummary item) {
                result.put(item.tripId(), item.cnt());
            }
        }
        return result;
    }

    private TripMetrics metricsOf(Trip trip, StatsContext context) {
        String tripId = trip.getId();
        return new TripMetrics(
                tripDays(trip),
                context.itineraryByTripId().getOrDefault(tripId, 0L),
                context.journalByTripId().getOrDefault(tripId, 0L),
                context.photoByTripId().getOrDefault(tripId, 0L),
                context.expenseCountByTripId().getOrDefault(tripId, 0L));
    }

    private StatsOverviewResp.MemoryFacts buildTripMemoryFacts(SummaryResp summary) {
        return new StatsOverviewResp.MemoryFacts(
                summary.stats().journalCount(),
                summary.stats().photoCount(),
                summary.stats().highlightCount(),
                (long) safeList(summary.userFeaturedPhotos()).size(),
                (long) safeList(summary.recommendedPhotos()).size(),
                summary.trip().coverPhoto() != null);
    }

    private StatsOverviewResp.MemoryFacts buildGlobalMemoryFacts(StatsContext context) {
        return new StatsOverviewResp.MemoryFacts(
                context.journalByTripId().values().stream().mapToLong(Long::longValue).sum(),
                context.photoByTripId().values().stream().mapToLong(Long::longValue).sum(),
                null, null, null, null);
    }

    private List<StatsOverviewResp.InsightItem> buildGlobalInsights(
            List<Trip> trips,
            BigDecimal totalExpense,
            List<ExpenseMapper.CategorySummary> categorySummaries,
            StatsContext context) {
        List<StatsOverviewResp.InsightItem> insights = new ArrayList<>();
        if (!categorySummaries.isEmpty() && totalExpense.compareTo(BigDecimal.ZERO) > 0) {
            ExpenseMapper.CategorySummary top = categorySummaries.getFirst();
            insights.add(new StatsOverviewResp.InsightItem(
                    "top_expense_category",
                    "最近旅行花费主要集中在" + ExpenseCategory.displayNameOf(top.category()),
                    null));
        }
        return insights.stream().limit(3).toList();
    }

    private List<StatsOverviewResp.AnalysisEntry> buildAnalysisEntries(
            List<Trip> trips,
            long totalTravelDays,
            long itineraryCount,
            long journalCount,
            long photoCount,
            BigDecimal totalExpense,
            List<ExpenseMapper.CategorySummary> categorySummaries,
            StatsContext context) {
        String topCategoryName = categorySummaries.isEmpty()
                ? "暂无分类"
                : ExpenseCategory.displayNameOf(categorySummaries.getFirst().category()) + "占比最高";
        long destinationCount = trips.stream().map(this::destinationName).filter(StringUtils::hasText).distinct().count();
        return List.of(
                new StatsOverviewResp.AnalysisEntry("spend", "花费分析",
                        "总花费 " + formatMoney(totalExpense) + " · 日均 "
                                + averageMoney(totalExpense, totalTravelDays) + " · " + topCategoryName),
                new StatsOverviewResp.AnalysisEntry("records", "记录分析",
                        "日志 " + journalCount + " 篇 · 照片 " + photoCount + " 张"),
                new StatsOverviewResp.AnalysisEntry("footprint", "足迹分析",
                        destinationCount + " 个目的地 · " + itineraryCount + " 个行程节点 · " + totalTravelDays + " 天足迹")
        );
    }

    private List<StatsFootprintAnalysisResp.CityFootprint> buildCityFootprints(List<Trip> trips, StatsContext context) {
        Map<String, List<Trip>> tripsByCity = trips.stream()
                .filter(trip -> StringUtils.hasText(destinationName(trip)))
                .collect(Collectors.groupingBy(this::destinationName, LinkedHashMap::new, Collectors.toList()));
        return tripsByCity.entrySet().stream()
                .map(entry -> {
                    List<Trip> cityTrips = entry.getValue();
                    Trip sample = cityTrips.getFirst();
                    long days = cityTrips.stream().mapToLong(this::tripDays).sum();
                    long itineraryCount = cityTrips.stream()
                            .map(Trip::getId)
                            .mapToLong(id -> context.itineraryByTripId().getOrDefault(id, 0L))
                            .sum();
                    return new StatsFootprintAnalysisResp.CityFootprint(
                            entry.getKey(),
                            sample.getDestinationAdcode(),
                            sample.getDestinationCityCode(),
                            sample.getDestinationProvinceName(),
                            formatDecimal(sample.getDestinationLatitude()),
                            formatDecimal(sample.getDestinationLongitude()),
                            cityTrips.size(),
                            days,
                            itineraryCount,
                            cityTrips.stream()
                                    .map(Trip::getStartDate)
                                    .filter(Objects::nonNull)
                                    .min(LocalDate::compareTo)
                                    .map(LocalDate::toString)
                                    .orElse(null));
                })
                .sorted(Comparator.comparing(StatsFootprintAnalysisResp.CityFootprint::totalDays).reversed())
                .toList();
    }

    private String spendInsight(List<StatsOverviewResp.CategoryStat> categoryStats) {
        if (categoryStats.isEmpty()) {
            return "还没有开支记录。添加开支后，这里会展示分类构成和日均花费。";
        }
        StatsOverviewResp.CategoryStat top = categoryStats.getFirst();
        return "你的花费里 " + top.categoryName() + " 占比最高（" + top.percentage() + "%）。";
    }

    private long tripDays(Trip trip) {
        if (trip.getStartDate() == null || trip.getEndDate() == null
                || trip.getEndDate().isBefore(trip.getStartDate())) {
            return 0;
        }
        return ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
    }

    private boolean hasTripStarted(Trip trip, LocalDate today) {
        return trip.getStartDate() == null || !trip.getStartDate().isAfter(today);
    }

    private boolean isEmptyCount(Long count) {
        return count == null || count <= 0;
    }

    private Long positiveOrNull(long value) {
        return value > 0 ? value : null;
    }

    private boolean isZero(BigDecimal amount) {
        return amount == null || amount.compareTo(BigDecimal.ZERO) == 0;
    }

    private String calculateStatus(Trip trip, LocalDate today) {
        return TripStatus.resolveValue(today, trip.getStartDate(), trip.getEndDate(), trip.getStatus());
    }

    private LocalDate resolveToday(String timeZone) {
        ZoneId zoneId = ZoneId.systemDefault();
        if (StringUtils.hasText(timeZone)) {
            try {
                zoneId = ZoneId.of(timeZone.trim());
            } catch (Exception ignored) {
                // Invalid IANA zone falls back to the server zone, matching stage-overview.
            }
        }
        return LocalDate.now(zoneId);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return ZERO_MONEY;
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String averageMoney(BigDecimal amount, long denominator) {
        if (denominator <= 0) {
            return ZERO_MONEY;
        }
        return formatMoney(zeroIfNull(amount).divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP));
    }

    private BigDecimal zeroIfNull(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String formatPercentage(BigDecimal amount, BigDecimal total) {
        if (amount == null || total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
            return "0.00";
        }
        return amount.multiply(BigDecimal.valueOf(100))
                .divide(total, 2, RoundingMode.HALF_UP)
                .toPlainString();
    }

    private String formatDate(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private String formatDecimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private String destinationName(Trip trip) {
        if (StringUtils.hasText(trip.getDestinationName())) {
            return trip.getDestinationName();
        }
        if (StringUtils.hasText(trip.getDestinationCityName())) {
            return trip.getDestinationCityName();
        }
        return trip.getTitle();
    }

    private String nodeTypeName(String type) {
        return switch (type == null ? "other" : type) {
            case "food" -> "餐饮";
            case "hotel" -> "住宿";
            case "transport" -> "交通";
            case "attraction" -> "景点";
            case "shopping" -> "购物";
            default -> "其他";
        };
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private record StatsContext(
            Map<String, Long> itineraryByTripId,
            Map<String, Long> journalByTripId,
            Map<String, Long> photoByTripId,
            Map<String, Long> expenseCountByTripId,
            Map<String, BigDecimal> expenseByTripId
    ) {
    }

    private record TripMetrics(
            long totalDays,
            long itineraryCount,
            long journalCount,
            long photoCount,
            long expenseCount
    ) {
    }
}
