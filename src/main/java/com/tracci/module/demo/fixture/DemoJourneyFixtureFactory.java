package com.tracci.module.demo.fixture;

import com.tracci.common.response.PageResponse;
import com.tracci.common.util.DateTimeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.integration.cos.CiUrlBuilder;
import com.tracci.integration.cos.CosClient;
import com.tracci.integration.cos.CosProperties;
import com.tracci.module.demo.config.DemoJourneyProperties;
import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;
import com.tracci.module.demo.dto.response.DemoJourneyCapabilitiesResp;
import com.tracci.module.demo.dto.response.DemoJourneySceneResp;
import com.tracci.module.demo.dto.response.DemoJourneyScenesResp;
import com.tracci.module.demo.dto.response.DemoJourneyStatsResp;
import com.tracci.module.demo.validation.DemoJourneyCoverageValidator;
import com.tracci.module.expense.dto.response.ExpenseResp;
import com.tracci.module.expense.dto.response.ExpenseSettlementResp;
import com.tracci.module.expense.dto.response.ExpenseSummaryResp;
import com.tracci.module.expense.enums.ExpenseCategory;
import com.tracci.module.export.dto.response.ExportAssetsResp;
import com.tracci.module.export.dto.response.ItineraryExportAssetsResp;
import com.tracci.module.journal.dto.response.JournalEntryResp;
import com.tracci.module.journal.dto.response.JournalEntryLocationResp;
import com.tracci.module.journal.enums.JournalMood;
import com.tracci.module.stats.dto.response.StatsFootprintAnalysisResp;
import com.tracci.module.stats.dto.response.StatsOverviewResp;
import com.tracci.module.stats.dto.response.StatsRecordsAnalysisResp;
import com.tracci.module.stats.dto.response.StatsSpendAnalysisResp;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.trip.dto.response.ItineraryDayResp;
import com.tracci.module.trip.dto.response.ItineraryNodeBriefResp;
import com.tracci.module.trip.dto.response.ItineraryNodeLocationResp;
import com.tracci.module.trip.dto.response.ItineraryResp;
import com.tracci.module.trip.dto.response.TripAccessResp;
import com.tracci.module.trip.dto.response.TripActivityLogResp;
import com.tracci.module.trip.dto.response.TripCoverPhotoResp;
import com.tracci.module.trip.dto.response.TripDestinationResp;
import com.tracci.module.trip.dto.response.TripDetailResp;
import com.tracci.module.trip.dto.response.TripMemberResp;
import com.tracci.module.trip.dto.response.TripWeatherDailyItem;
import com.tracci.module.trip.dto.response.TripWeatherLocationResp;
import com.tracci.module.trip.dto.response.TripWeatherResponse;
import com.tracci.module.trip.enums.ItineraryNodeType;
import com.tracci.module.trip.enums.TripMemberRole;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.enums.TripStatus;
import com.tracci.module.trip.support.ItineraryNodeDetailsSupport;
import com.tracci.module.tripoverview.dto.response.TripStageOverviewResp;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds deterministic demo journey fixtures with full coverage validation.
 *
 * <p>Shanghai five-day script: D1–D2 Puxi (arrive Hongqiao, stay Puxi), D3–D5 Pudong
 * (hotel switch, Lujiazui, full-day Disney, depart PVG).
 *
 * <p>Organized by domain: scene orchestration → trip/itinerary → journals →
 * expenses → activity logs → weather → summary → stage overview → stats → exports.
 */
@Component
public class DemoJourneyFixtureFactory implements DemoJourneyBundleProvider {

    private static final DemoJourneyCapabilitiesResp READ_ONLY_CAPABILITIES =
            DemoJourneyCapabilitiesResp.allReadOnly();

    private static final int TRIP_DAYS = 5;

    private final DemoJourneyProperties properties;
    private final DemoJourneyCoverageValidator validator;
    private final CosClient cosClient;
    private final CosProperties cosProperties;
    private final CiUrlBuilder ciUrlBuilder;
    private volatile long signatureRotation;

    public DemoJourneyFixtureFactory(
            DemoJourneyProperties properties,
            DemoJourneyCoverageValidator validator,
            CosClient cosClient,
            CosProperties cosProperties,
            CiUrlBuilder ciUrlBuilder) {
        this.properties = properties;
        this.validator = validator;
        this.cosClient = cosClient;
        this.cosProperties = cosProperties;
        this.ciUrlBuilder = ciUrlBuilder;
    }

    @PostConstruct
    void validateEnabledConfiguration() {
        if (properties.isEnabled()) {
            requireAssetConfiguration();
        }
    }

    @Override
    public DemoJourneyBundleResp build(LocalDate referenceDate, long signatureRotation) {
        this.signatureRotation = signatureRotation;
        if (referenceDate == null) {
            throw new IllegalArgumentException("referenceDate is required");
        }
        requireAssetConfiguration();

        DemoJourneyBundleResp bundle = new DemoJourneyBundleResp(
                properties.getSchemaVersion(),
                properties.getContentVersion(),
                referenceDate.toString(),
                true,
                DemoJourneyBundleResp.DEFAULT_SCENE,
                READ_ONLY_CAPABILITIES,
                new DemoJourneyScenesResp(
                        buildScene(DemoSceneSeed.planning(referenceDate)),
                        buildScene(DemoSceneSeed.ongoing(referenceDate)),
                        buildScene(DemoSceneSeed.completed(referenceDate))));

        validator.validate(
                bundle,
                referenceDate,
                cosProperties.getBucket(),
                cosProperties.getRegion(),
                properties.getCosObjectKeyPrefix());
        return bundle;
    }

    // ===== Scene orchestration =====

    private DemoJourneySceneResp buildScene(DemoSceneSeed seed) {
        String tripId = id(seed.key(), "trip");
        var members = buildMembers(seed, tripId);
        var itinerary = buildItinerary(seed, tripId);
        var journals = buildJournals(seed, tripId, members);
        var expenses = buildExpenses(seed, tripId, members);
        var expenseSummary = buildExpenseSummary(seed);
        var expenseSettlement = buildExpenseSettlement(seed, tripId, members);
        var activityLogs = buildActivityLogs(seed, tripId, members);
        var weather = buildWeather(seed);
        var trip = buildTrip(seed, tripId, itinerary, journals, expenses);
        var summary = buildSummary(seed, tripId, itinerary, journals, expenses, expenseSummary);
        var stageOverview = buildStageOverview(seed, trip, itinerary, journals, expenses, summary, weather);
        var stats = buildStats(seed, trip, itinerary, journals, expenses, summary);
        var exportAssets = buildExportAssets(trip, summary);
        var itineraryExportAssets = buildItineraryExportAssets(trip, itinerary);

        return new DemoJourneySceneResp(
                trip, itinerary, journals, expenses, expenseSummary, expenseSettlement,
                members, activityLogs, weather, stageOverview, summary, stats,
                exportAssets, itineraryExportAssets);
    }

    // ===== Trip & Itinerary =====

    private TripDetailResp buildTrip(
            DemoSceneSeed seed, String tripId, ItineraryResp itinerary,
            PageResponse<JournalEntryResp> journals, PageResponse<ExpenseResp> expenses) {
        long nodeCount = itinerary.days().stream()
                .flatMap(day -> day.nodes().stream()).count();
        return new TripDetailResp(
                tripId, TITLE, seed.startDate().toString(), seed.endDate().toString(),
                seed.status(), TRIP_DAYS, destination(), coverPhoto(), CURRENCY,
                expenseTotal(expenses), nodeCount, journals.total(),
                new TripAccessResp(TripMemberRole.VIEWER.value(),
                        List.of(TripPermission.READ.value())),
                instant(seed.startDate(), LocalTime.of(8, 0)),
                instant(seed.referenceDate(), LocalTime.of(9, 30)));
    }

    private ItineraryResp buildItinerary(DemoSceneSeed seed, String tripId) {
        List<ItineraryDayResp> days = new ArrayList<>();
        for (int dayIndex = 1; dayIndex <= TRIP_DAYS; dayIndex++) {
            LocalDate date = seed.startDate().plusDays(dayIndex - 1L);
            String dayId = id(seed.key(), "day-" + dayIndex);
            days.add(new ItineraryDayResp(dayId, dayIndex, date.toString(),
                    buildNodes(seed, dayId, dayIndex)));
        }
        return new ItineraryResp(tripId, List.copyOf(days));
    }

    private List<ItineraryNodeBriefResp> buildNodes(DemoSceneSeed seed, String dayId, int dayIndex) {
        LocalDate d1 = seed.startDate();
        LocalDate d3 = seed.startDate().plusDays(2);
        LocalDate d5 = seed.endDate();
        return switch (dayIndex) {
            case 1 -> List.of(
                    node(seed, dayId, "1-hongqiao", "抵达虹桥机场", ItineraryNodeType.TRANSPORT.value(),
                            "10:00", "10:40", "虹桥机场 T2",
                            location("hongqiao", "上海虹桥国际机场", "闵行区申贵路", "31.1979", "121.3363", "闵行区"),
                            "从虹桥进沪，地铁 2/10 号线直达市中心，行李少可直接进城",
                            Map.of("kind", "transport", "mode", "flight", "flightNo", "MU5101",
                                    "arrivalName", "上海虹桥国际机场"), 1),
                    node(seed, dayId, "1-hotel-puxi", "入住浦西酒店", ItineraryNodeType.HOTEL.value(),
                            "12:00", "12:30", "黄浦区南京东路",
                            location("hotel-puxi", "浦西南京东路酒店", "黄浦区南京东路", "31.2355", "121.4780", "黄浦区"),
                            "D1–D2 住浦西，方便步行外滩、南京路与豫园",
                            Map.of("kind", "hotel",
                                    "checkInDate", d1.toString(),
                                    "checkOutDate", d3.toString(),
                                    "stayGroupId", id(seed.key(), "stay-puxi"),
                                    "stayRole", "check_in",
                                    "nightIndex", 1, "totalNights", 2), 2),
                    node(seed, dayId, "1-nanjing", "南京路步行街", ItineraryNodeType.ATTRACTION.value(),
                            "13:30", "15:00", "南京东路",
                            location("nanjing-road", "南京路步行街", "黄浦区南京东路", "31.2360", "121.4750", "黄浦区"),
                            "从人民广场到外滩一段步行，感受浦西最经典的商业街氛围",
                            Map.of("ticketRequired", false), 3),
                    node(seed, dayId, "1-xiaolongbao", "本帮点心与小笼", ItineraryNodeType.RESTAURANT.value(),
                            "15:15", "16:15", "黄浦区南京东路附近",
                            location("xiaolongbao", "南翔馒头店（豫园店）", "黄浦区豫园路", "31.2278", "121.4920", "黄浦区"),
                            "示例餐饮节点，不代表具体商户推荐；可换成更近的本帮馆子",
                            Map.of("mealType", "snack"), 4),
                    node(seed, dayId, "1-bund", "外滩与浦东天际线", ItineraryNodeType.ATTRACTION.value(),
                            "16:30", "18:30", "中山东一路",
                            location("bund", "外滩", "黄浦区中山东一路", "31.2400", "121.4900", "黄浦区"),
                            "沿防汛墙看陆家嘴，黄昏到夜景灯光切换时拍照最稳",
                            Map.of("ticketRequired", false), 5),
                    node(seed, dayId, "1-yuyuan", "豫园与城隍庙", ItineraryNodeType.ATTRACTION.value(),
                            "19:00", "20:30", "福佑路",
                            location("yuyuan", "豫园", "黄浦区安仁街 218 号", "31.2272", "121.4922", "黄浦区"),
                            "园林与老城厢夜景，人流高峰可优先园内再逛商铺",
                            Map.of("ticketRequired", true), 6));
            case 2 -> List.of(
                    node(seed, dayId, "2-wukang", "武康路历史风貌", ItineraryNodeType.ATTRACTION.value(),
                            "09:30", "11:30", "武康路",
                            location("wukang", "武康路", "徐汇区武康路", "31.2091", "121.4387", "徐汇区"),
                            "梧桐与老公寓是衡复片区的招牌散步线",
                            Map.of("ticketRequired", false), 1),
                    node(seed, dayId, "2-anfu", "安福路早午餐", ItineraryNodeType.RESTAURANT.value(),
                            "11:45", "13:00", "安福路",
                            location("anfu", "安福路", "徐汇区安福路", "31.2140", "121.4440", "徐汇区"),
                            "咖啡馆与轻食集中，适合慢节奏补给",
                            Map.of("mealType", "brunch"), 2),
                    node(seed, dayId, "2-tianzifang", "田子坊弄堂", ItineraryNodeType.ATTRACTION.value(),
                            "14:00", "16:00", "泰康路",
                            location("tianzifang", "田子坊", "黄浦区泰康路 210 号", "31.2107", "121.4680", "黄浦区"),
                            "石库门弄堂与文创小店，午后光线适合街拍",
                            Map.of("ticketRequired", false), 3),
                    node(seed, dayId, "2-xintiandi", "新天地与石库门", ItineraryNodeType.ATTRACTION.value(),
                            "16:30", "18:00", "太仓路",
                            location("xintiandi", "新天地", "黄浦区太仓路 181 号", "31.2199", "121.4752", "黄浦区"),
                            "从里弄走到餐吧街区，衔接晚餐与夜生活",
                            Map.of("ticketRequired", false), 4),
                    node(seed, dayId, "2-dinner", "新天地晚餐", ItineraryNodeType.RESTAURANT.value(),
                            "18:30", "20:00", "新天地",
                            location("xintiandi-dinner", "新天地", "黄浦区太仓路", "31.2199", "121.4752", "黄浦区"),
                            "示例多人聚餐节点，对应 AA 分摊演示",
                            Map.of("mealType", "dinner"), 5));
            case 3 -> List.of(
                    node(seed, dayId, "3-checkout", "退房转战浦东", ItineraryNodeType.TRANSPORT.value(),
                            "09:00", "10:00", "黄浦区 → 陆家嘴",
                            location("cross-river", "打浦桥隧道 / 地铁 2 号线", "黄浦区", "31.2200", "121.4800", "黄浦区"),
                            "行李可先寄存在浦东酒店或带行李过江，地铁 2 号线最省心",
                            Map.of("kind", "transport", "mode", "other",
                                    "departureName", "浦西南京东路酒店",
                                    "arrivalName", "陆家嘴"), 1),
                    node(seed, dayId, "3-hotel-pudong", "入住浦东酒店", ItineraryNodeType.HOTEL.value(),
                            "10:30", "11:00", "陆家嘴环路",
                            location("hotel-pudong", "浦东陆家嘴酒店", "浦东新区陆家嘴环路", "31.2350", "121.5050", "浦东新区"),
                            "D3–D4 住浦东，贴近陆家嘴与迪士尼动线，返程去浦东机场更顺",
                            Map.of("kind", "hotel",
                                    "checkInDate", d3.toString(),
                                    "checkOutDate", d5.toString(),
                                    "stayGroupId", id(seed.key(), "stay-pudong"),
                                    "stayRole", "check_in",
                                    "nightIndex", 1, "totalNights", 2), 2),
                    node(seed, dayId, "3-lujiazui", "陆家嘴与东方明珠广场", ItineraryNodeType.ATTRACTION.value(),
                            "11:30", "13:00", "陆家嘴环路",
                            location("lujiazui", "陆家嘴", "浦东新区陆家嘴环路", "31.2397", "121.4998", "浦东新区"),
                            "从浦西外滩看过天际线后，换到浦东脚下仰拍更有冲击力",
                            Map.of("ticketRequired", false), 3),
                    node(seed, dayId, "3-lunch", "陆家嘴简餐", ItineraryNodeType.RESTAURANT.value(),
                            "13:00", "14:00", "正大广场 / 国金中心",
                            location("ifc-lunch", "上海国金中心", "浦东新区世纪大道 8 号", "31.2358", "121.5015", "浦东新区"),
                            "商场餐饮选择多，观景前补能",
                            Map.of("mealType", "lunch"), 4),
                    node(seed, dayId, "3-tower", "上海中心大厦观光", ItineraryNodeType.ATTRACTION.value(),
                            "14:30", "17:00", "银城中路 501 号",
                            location("shanghai-tower", "上海中心大厦", "浦东新区银城中路 501 号", "31.2355", "121.5010", "浦东新区"),
                            "建议提前购票预约时段；雾霾天可改滨江散步",
                            Map.of("ticketRequired", true), 5),
                    node(seed, dayId, "3-riverside", "陆家嘴滨江步道", ItineraryNodeType.ATTRACTION.value(),
                            "17:30", "19:00", "滨江大道",
                            location("pudong-riverside", "陆家嘴滨江步道", "浦东新区滨江大道", "31.2410", "121.4960", "浦东新区"),
                            "回看外滩万国建筑群，适合日落到亮灯",
                            Map.of("ticketRequired", false), 6));
            case 4 -> List.of(
                    node(seed, dayId, "4-disney", "上海迪士尼度假区", ItineraryNodeType.ATTRACTION.value(),
                            "08:30", "20:30", "川沙新镇",
                            location("disney", "上海迪士尼度假区", "浦东新区川沙新镇黄赵路 310 号", "31.1440", "121.6570", "浦东新区"),
                            "全日主锚点：早入园优先热门项目，傍晚烟花/巡游；不与城区景点混排",
                            Map.of("ticketRequired", true, "allDay", true), 1),
                    node(seed, dayId, "4-disney-dinner", "乐园内或迪士尼小镇晚餐", ItineraryNodeType.RESTAURANT.value(),
                            "18:00", "19:30", "迪士尼小镇",
                            location("disney-town", "迪士尼小镇", "浦东新区申迪西路", "31.1455", "121.6630", "浦东新区"),
                            "离园后可在小镇补给，再地铁/打车回陆家嘴酒店",
                            Map.of("mealType", "dinner"), 2));
            case 5 -> List.of(
                    node(seed, dayId, "5-souvenir", "伴手礼与文创", ItineraryNodeType.SHOPPING.value(),
                            "09:30", "11:00", "陆家嘴或酒店附近",
                            location("souvenir", "IFC 国金中心商场", "浦东新区世纪大道 8 号", "31.2358", "121.5015", "浦东新区"),
                            "买特产与伴手礼，控制行李体积以方便过安检",
                            Map.of("shoppingType", "souvenir"), 1),
                    node(seed, dayId, "5-free", "整理行李与自由活动", ItineraryNodeType.OTHER.value(),
                            null, null, null, null,
                            "按航班时间弹性安排，保留无坐标节点以覆盖地图降级展示",
                            Map.of("allDay", true), 2),
                    node(seed, dayId, "5-pvg", "浦东国际机场返程", ItineraryNodeType.TRANSPORT.value(),
                            "14:00", "15:30", "浦东国际机场",
                            location("pvg", "上海浦东国际机场", "浦东新区启航路", "31.1443", "121.8083", "浦东新区"),
                            "从浦东酒店出发更顺；预留磁浮/地铁/网约车与值机时间",
                            Map.of("kind", "transport", "mode", "flight", "flightNo", "MU5102",
                                    "departureName", "上海浦东国际机场"), 3));
            default -> throw new IllegalArgumentException("Unsupported demo day index: " + dayIndex);
        };
    }

    // ===== Members =====

    private List<TripMemberResp> buildMembers(DemoSceneSeed seed, String tripId) {
        return List.of(
                member(seed, tripId, "owner", "林屿", TripMemberRole.OWNER.value(), "2026-01-01T00:00:00Z"),
                member(seed, tripId, "editor", "阿澄", TripMemberRole.EDITOR.value(), "2026-01-02T00:00:00Z"),
                member(seed, tripId, "viewer", "小满", TripMemberRole.VIEWER.value(), "2026-01-03T00:00:00Z"));
    }

    // ===== Journals =====
    // Nine entries across D1–D4 (+ closing on last recorded day) for a five-day trip density.

    private PageResponse<JournalEntryResp> buildJournals(
            DemoSceneSeed seed, String tripId, List<TripMemberResp> members) {
        if (!hasRecordedContent(seed)) {
            return PageResponse.of(List.of(), 0, 1, 20);
        }
        String ownerUserId = members.getFirst().userId();
        String editorUserId = members.get(1).userId();
        String viewerUserId = members.get(2).userId();
        LocalDate d1 = seed.startDate();
        LocalDate d2 = seed.startDate().plusDays(1);
        LocalDate d3 = seed.startDate().plusDays(2);
        LocalDate d4 = seed.startDate().plusDays(3);

        JournalEntryResp j1Arrival = new JournalEntryResp(
                id(seed.key(), "journal-1"), tripId, ownerUserId, "林屿",
                "虹桥落地后地铁进城，把行李丢进浦西酒店就扎进南京路。人流、橱窗和电车铃，上海第一印象是热闹但不慌。",
                JournalEntryLocationResp.from(location("nanjing-road", "南京路步行街",
                        "黄浦区南京东路", "31.2360", "121.4750", "黄浦区")),
                List.of(), instant(d1, LocalTime.of(14, 20)),
                JournalMood.EXCITED.value(), false, "all",
                instant(d1, LocalTime.of(14, 25)));

        JournalEntryResp j2Bund = new JournalEntryResp(
                id(seed.key(), "journal-2"), tripId, editorUserId, "阿澄",
                "外滩夜景亮灯后，陆家嘴三件套倒映在江面上，果然是上海最有辨识度的画面。",
                JournalEntryLocationResp.from(location("bund", "外滩",
                        "黄浦区中山东一路", "31.2400", "121.4900", "黄浦区")),
                List.of(photo(seed, "journal-2-photo-1", "the-bund.png", 1, true)),
                instant(d1, LocalTime.of(20, 10)),
                JournalMood.HAPPY.value(), true, "all",
                instant(d1, LocalTime.of(20, 12)));

        JournalEntryResp j3Yuyuan = new JournalEntryResp(
                id(seed.key(), "journal-3"), tripId, viewerUserId, "小满",
                "豫园门票不贵，园子不大但细节够看。出园再逛城隍庙小吃，第一天就把浦西经典路线走实了。",
                JournalEntryLocationResp.from(location("yuyuan", "豫园",
                        "黄浦区安仁街 218 号", "31.2272", "121.4922", "黄浦区")),
                List.of(photo(seed, "journal-3-photo-1", "yuyuan-garden.png", 1, false)),
                instant(d1, LocalTime.of(21, 0)),
                JournalMood.PEACEFUL.value(), false, "all",
                instant(d1, LocalTime.of(21, 5)));

        JournalEntryResp j4Wukang = new JournalEntryResp(
                id(seed.key(), "journal-4"), tripId, viewerUserId, "小满",
                "武康路的梧桐把阳光切成细碎光斑，老公寓门牌和街角咖啡店特别适合慢慢拍。",
                JournalEntryLocationResp.from(location("wukang", "武康路",
                        "徐汇区武康路", "31.2091", "121.4387", "徐汇区")),
                List.of(photo(seed, "journal-4-photo-1", "wukang-road.png", 1, false)),
                instant(d2, LocalTime.of(11, 0)),
                JournalMood.RELAXED.value(), false, "all",
                instant(d2, LocalTime.of(11, 5)));

        JournalEntryResp j5Tianzifang = new JournalEntryResp(
                id(seed.key(), "journal-5"), tripId, editorUserId, "阿澄",
                "田子坊的弄堂比想象中更绕，买了点小文创。傍晚转到新天地吃饭，石库门夜里更有氛围。",
                JournalEntryLocationResp.from(location("tianzifang", "田子坊",
                        "黄浦区泰康路 210 号", "31.2107", "121.4680", "黄浦区")),
                List.of(), instant(d2, LocalTime.of(16, 40)),
                JournalMood.HAPPY.value(), false, "all",
                instant(d2, LocalTime.of(16, 45)));

        JournalEntryResp j6Tower = new JournalEntryResp(
                id(seed.key(), "journal-6"), tripId, ownerUserId, "林屿",
                "换住浦东后站在陆家嘴脚下，再上上海中心看浦西。观光厅票提前约了时段，排队还能接受。",
                JournalEntryLocationResp.from(location("shanghai-tower", "上海中心大厦",
                        "浦东新区银城中路 501 号", "31.2355", "121.5010", "浦东新区")),
                List.of(
                        photo(seed, "journal-6-photo-1", "lujiazui-skyline.png", 1, false),
                        photo(seed, "journal-6-photo-2", "shanghai-tower.png", 2, false)),
                instant(d3, LocalTime.of(17, 20)),
                JournalMood.THOUGHTFUL.value(), false, "all",
                instant(d3, LocalTime.of(17, 25)));

        JournalEntryResp j7Riverside = new JournalEntryResp(
                id(seed.key(), "journal-7"), tripId, editorUserId, "阿澄",
                "滨江步道回看外滩万国建筑，风有点大。酒店换到浦东后，明天去迪士尼的通勤心理压力小了很多。",
                JournalEntryLocationResp.from(location("pudong-riverside", "陆家嘴滨江步道",
                        "浦东新区滨江大道", "31.2410", "121.4960", "浦东新区")),
                List.of(), instant(d3, LocalTime.of(19, 10)),
                JournalMood.RELAXED.value(), false, "private",
                instant(d3, LocalTime.of(19, 15)));

        JournalEntryResp j8Disney = new JournalEntryResp(
                id(seed.key(), "journal-8"), tripId, editorUserId, "阿澄",
                "迪士尼一日票不便宜，但早入园把热门项目排完再看烟花，值回票价。乐园餐另算，别指望省。",
                JournalEntryLocationResp.from(location("disney", "上海迪士尼度假区",
                        "浦东新区川沙新镇黄赵路 310 号", "31.1440", "121.6570", "浦东新区")),
                List.of(
                        photo(seed, "journal-8-photo-1", "disney-castle.png", 1, false),
                        photo(seed, "journal-8-photo-2", "disney-fireworks.png", 2, false)),
                instant(d4, LocalTime.of(21, 0)),
                JournalMood.EXCITED.value(), false, "all",
                instant(d4, LocalTime.of(21, 10)));

        JournalEntryResp j9Closing = new JournalEntryResp(
                id(seed.key(), "journal-9"), tripId, viewerUserId, "小满",
                "五日虹桥进、浦东出：浦西两天慢走，浦东三天观景加乐园。门票和住宿占大头，但节奏对了就不觉得冤。",
                JournalEntryLocationResp.from(location("nanjing-road", "南京路步行街",
                        "黄浦区南京东路", "31.2360", "121.4750", "黄浦区")),
                List.of(
                        photo(seed, "journal-9-photo-1", "pudong-riverside.png", 1, false),
                        photo(seed, "journal-9-photo-2", "nanjing-road.png", 2, false)),
                instant(lastRecordedDate(seed), LocalTime.of(22, 0)),
                JournalMood.PEACEFUL.value(), false, "all",
                instant(lastRecordedDate(seed), LocalTime.of(22, 5)));

        List<JournalEntryResp> list = List.of(
                j1Arrival, j2Bund, j3Yuyuan, j4Wukang, j5Tianzifang,
                j6Tower, j7Riverside, j8Disney, j9Closing);
        return PageResponse.of(list, list.size(), 1, 20);
    }

    // ===== Expenses =====
    // Realistic ticket/hotel/food splits for 3 pax, 5 days (amounts divisible for EQUAL share).
    // Paid:  owner 5540 + editor 1260 + viewer 2937 = 9737
    // Share: owner 3729 + editor 3729 + viewer 2279 = 9737
    // Balance: owner +1811, editor -2469, viewer +658

    private PageResponse<ExpenseResp> buildExpenses(
            DemoSceneSeed seed, String tripId, List<TripMemberResp> members) {
        if (!hasRecordedContent(seed)) {
            return PageResponse.of(List.of(), 0, 1, 50);
        }
        String owner = members.getFirst().id();
        String editor = members.get(1).id();
        String viewer = members.get(2).id();
        List<String> all = List.of(owner, editor, viewer);
        List<String> roommates = List.of(owner, editor);
        LocalDate d1 = seed.startDate();
        LocalDate d2 = seed.startDate().plusDays(1);
        LocalDate d3 = seed.startDate().plusDays(2);
        LocalDate d4 = seed.startDate().plusDays(3);
        List<ExpenseResp> list = List.of(
                // D1 抵达浦西：打车 + 地铁储值 + 餐饮 + 豫园门票 + 浦西酒店
                expense(seed, tripId, "taxi-hq", ExpenseCategory.TRANSPORT.value(), "120.00",
                        "虹桥机场打车进城", d1, owner, all, owner),
                expense(seed, tripId, "metro", ExpenseCategory.TRANSPORT.value(), "300.00",
                        "五日地铁与市内公共交通", d1, editor, all, editor),
                expense(seed, tripId, "food-d1", ExpenseCategory.FOOD.value(), "360.00",
                        "午餐与豫园本帮小吃", d1, owner, all, owner),
                expense(seed, tripId, "yuyuan-ticket", ExpenseCategory.ATTRACTION.value(), "120.00",
                        "豫园门票三人（约 40 元/人）", d1, viewer, all, viewer),
                expense(seed, tripId, "hotel-puxi", ExpenseCategory.HOTEL.value(), "1760.00",
                        "浦西两晚住宿（约 880 元/晚·一间）", d1, owner, roommates, owner),
                // D2 浦西文艺线
                expense(seed, tripId, "food-d2-brunch", ExpenseCategory.FOOD.value(), "270.00",
                        "安福路早午餐", d2, editor, all, editor),
                expense(seed, tripId, "food-d2-dinner", ExpenseCategory.FOOD.value(), "540.00",
                        "新天地晚餐", d2, owner, all, owner),
                expense(seed, tripId, "drinks", ExpenseCategory.OTHER.value(), "150.00",
                        "咖啡与茶饮", d2, editor, all, editor),
                // D3 转浦东：酒店 + 上海中心观光 + 简餐
                expense(seed, tripId, "hotel-pudong", ExpenseCategory.HOTEL.value(), "1980.00",
                        "浦东两晚住宿（约 990 元/晚·一间）", d3, owner, roommates, owner),
                expense(seed, tripId, "tower-ticket", ExpenseCategory.ATTRACTION.value(), "540.00",
                        "上海中心观光厅三人（约 180 元/人）", d3, editor, all, editor),
                expense(seed, tripId, "food-d3", ExpenseCategory.FOOD.value(), "300.00",
                        "陆家嘴简餐", d3, owner, all, owner),
                // D4 迪士尼全日：门票 + 园内餐饮（门票为最大单项）
                expense(seed, tripId, "disney-ticket", ExpenseCategory.ATTRACTION.value(), "2397.00",
                        "迪士尼一日票三人（约 799 元/人·常规日）", d4, viewer, all, viewer),
                expense(seed, tripId, "disney-food", ExpenseCategory.FOOD.value(), "480.00",
                        "乐园内餐饮与饮料", d4, owner, all, owner),
                // 收尾伴手礼（ongoing 记在参考日；completed 记在末日）
                expense(seed, tripId, "souvenirs", ExpenseCategory.SHOPPING.value(), "420.00",
                        "伴手礼与文创", lastRecordedDate(seed), viewer, List.of(viewer), viewer));
        return PageResponse.of(list, list.size(), 1, 50);
    }

    private ExpenseSummaryResp buildExpenseSummary(DemoSceneSeed seed) {
        if (!hasRecordedContent(seed)) {
            return new ExpenseSummaryResp("0.00", "0.00", 0, List.of());
        }
        // food 1950, transport 420, hotel 3740, attraction 3057, shopping 420, other 150 → 9737
        return new ExpenseSummaryResp(TOTAL_AMOUNT, AVERAGE_DAILY_AMOUNT, 14, List.of(
                new ExpenseSummaryResp.CategoryBreakdown("food", "1950.00", 5, 0.2003),
                new ExpenseSummaryResp.CategoryBreakdown("transport", "420.00", 2, 0.0431),
                new ExpenseSummaryResp.CategoryBreakdown("hotel", "3740.00", 2, 0.3841),
                new ExpenseSummaryResp.CategoryBreakdown("attraction", "3057.00", 3, 0.3140),
                new ExpenseSummaryResp.CategoryBreakdown("shopping", "420.00", 1, 0.0431),
                new ExpenseSummaryResp.CategoryBreakdown("other", "150.00", 1, 0.0154)));
    }

    private ExpenseSettlementResp buildExpenseSettlement(
            DemoSceneSeed seed, String tripId, List<TripMemberResp> members) {
        TripMemberResp owner = members.getFirst();
        TripMemberResp editor = members.get(1);
        TripMemberResp viewer = members.get(2);
        if (!hasRecordedContent(seed)) {
            return new ExpenseSettlementResp(tripId, CURRENCY, "0.00",
                    members.stream().map(m -> new ExpenseSettlementResp.MemberSettlement(
                            m.id(), m.userId(), m.nickname(), "0.00", "0.00", "0.00")).toList(),
                    List.of());
        }
        return new ExpenseSettlementResp(tripId, CURRENCY, TOTAL_AMOUNT, List.of(
                new ExpenseSettlementResp.MemberSettlement(
                        owner.id(), owner.userId(), owner.nickname(), "5540.00", "3729.00", "1811.00"),
                new ExpenseSettlementResp.MemberSettlement(
                        editor.id(), editor.userId(), editor.nickname(), "1260.00", "3729.00", "-2469.00"),
                new ExpenseSettlementResp.MemberSettlement(
                        viewer.id(), viewer.userId(), viewer.nickname(), "2937.00", "2279.00", "658.00")),
                List.of(new ExpenseSettlementResp.SuggestedTransfer(editor.id(), owner.id(), "1811.00"),
                        new ExpenseSettlementResp.SuggestedTransfer(editor.id(), viewer.id(), "658.00")));
    }

    // ===== Activity Logs =====

    private PageResponse<TripActivityLogResp> buildActivityLogs(
            DemoSceneSeed seed, String tripId, List<TripMemberResp> members) {
        TripMemberResp owner = members.getFirst();
        TripMemberResp editor = members.get(1);
        TripMemberResp viewer = members.get(2);
        List<TripActivityLogResp> planningLogs = List.of(
                activity(seed, tripId, "1", owner, "TRIP_CREATED", "TRIP", tripId,
                        "创建了示例旅程", seed.startDate().minusDays(20)),
                activity(seed, tripId, "2", owner, "ITINERARY_NODE_CREATED", "ITINERARY_NODE",
                        id(seed.key(), "node-1-bund"), "增加了外滩夜景行程", seed.startDate().minusDays(18)),
                activity(seed, tripId, "3", editor, "ITINERARY_NODE_CREATED", "ITINERARY_NODE",
                        id(seed.key(), "node-4-disney"), "补充了迪士尼全日行程", seed.startDate().minusDays(16)));
        if (!hasRecordedContent(seed)) {
            return PageResponse.of(planningLogs, planningLogs.size(), 1, 50);
        }
        List<TripActivityLogResp> logs = List.of(
                planningLogs.getFirst(), planningLogs.get(1), planningLogs.get(2),
                activity(seed, tripId, "4", viewer, "MEMBER_JOINED", "TRIP",
                        tripId, "加入了示例旅程", seed.startDate().minusDays(10)),
                activity(seed, tripId, "5", editor, "JOURNAL_CREATED", "JOURNAL",
                        id(seed.key(), "journal-4"), "记录了武康路旅行日志", seed.startDate().plusDays(1)),
                activity(seed, tripId, "6", owner, "EXPENSE_CREATED", "EXPENSE",
                        id(seed.key(), "expense-hotel-pudong"), "创建了浦东酒店账单", seed.startDate().plusDays(2)),
                activity(seed, tripId, "7", viewer, "EXPENSE_CREATED", "EXPENSE",
                        id(seed.key(), "expense-disney-ticket"), "登记了迪士尼三人门票", seed.startDate().plusDays(3)),
                activity(seed, tripId, "8", editor, "JOURNAL_PHOTO_FEATURED", "JOURNAL_PHOTO",
                        id(seed.key(), "journal-2-photo-1"), "精选了外滩夜景照片", lastRecordedDate(seed)));
        return PageResponse.of(logs, logs.size(), 1, 50);
    }

    // ===== Weather =====

    private TripWeatherResponse buildWeather(DemoSceneSeed seed) {
        if (TripStatus.COMPLETED.value().equals(seed.status())) {
            return new TripWeatherResponse(false, "PAST_TRIP", "DEMO", "DAILY",
                    "示例已完成旅程不展示历史天气", seed.referenceDate(), DESTINATION,
                    List.of(), TRIP_DAYS, weatherLocation());
        }
        List<TripWeatherDailyItem> items = List.of(
                weatherItem(seed.startDate(), "多云", 24, 31, "cloudy", 20, "适合步行外滩与南京路"),
                weatherItem(seed.startDate().plusDays(1), "晴", 25, 33, "sunny", 10, "注意防晒，武康路宜上午"),
                weatherItem(seed.startDate().plusDays(2), "多云", 25, 32, "cloudy", 25, "观景台能见度较好"),
                weatherItem(seed.startDate().plusDays(3), "小雨", 23, 28, "light_rain", 60, "乐园记得雨具与备用袜"),
                weatherItem(seed.startDate().plusDays(4), "晴", 24, 30, "sunny", 15, "返程预留交通缓冲"));
        return new TripWeatherResponse(true, "AVAILABLE", "DEMO", "DAILY",
                "示例天气，仅用于界面展示", seed.referenceDate(), DESTINATION, items, TRIP_DAYS, weatherLocation());
    }

    // ===== Summary =====

    private SummaryResp buildSummary(
            DemoSceneSeed seed, String tripId, ItineraryResp itinerary,
            PageResponse<JournalEntryResp> journals, PageResponse<ExpenseResp> expenses,
            ExpenseSummaryResp expenseSummary) {
        var timeline = itinerary.days().stream()
                .map(day -> new SummaryResp.DailyTimelineItem(
                        day.date(), day.dayIndex(),
                        day.nodes().stream().map(this::timelineNode).toList(),
                        journals.list().stream()
                                .filter(j -> day.date().equals(
                                        businessDate(j.occurredAt()).toString()))
                                .map(this::summaryLog).toList()))
                .toList();
        var allPhotos = journals.list().stream()
                .flatMap(j -> j.photos().stream().map(photo -> summaryPhoto(j.id(), photo))).toList();
        var featured = allPhotos.stream().filter(SummaryResp.PhotoItem::userFeatured).toList();
        var recommended = allPhotos.stream().filter(p -> !p.userFeatured()).toList();
        var highlights = journals.list().stream()
                .filter(j -> Boolean.TRUE.equals(j.isHighlight()))
                .map(this::summaryLog).toList();
        java.math.BigDecimal total = expenses.list().stream()
                .map(e -> new java.math.BigDecimal(e.amountCny()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        String totalAmount = money(total);

        return new SummaryResp(
                new SummaryResp.TripInfo(tripId, TITLE, DESTINATION,
                        seed.startDate().toString(), seed.endDate().toString(), TRIP_DAYS, seed.status(), summaryCover()),
                new SummaryResp.Stats(TRIP_DAYS,
                        itinerary.days().stream().flatMap(d -> d.nodes().stream()).count(),
                        (int) journals.total(), allPhotos.size(), (int) expenses.total(),
                        totalAmount, CURRENCY, highlights.size()),
                new SummaryResp.ExpenseSummary(totalAmount, CURRENCY,
                        expenseSummary.breakdown().stream()
                                .map(item -> new SummaryResp.ExpenseCategoryStat(
                                        item.category(), categoryName(item.category()),
                                        item.amountCny(), percentage(item.ratio()), item.count()))
                                .toList()),
                highlights, featured, recommended, timeline);
    }

    // ===== Stage Overview =====

    private TripStageOverviewResp buildStageOverview(
            DemoSceneSeed seed, TripDetailResp trip, ItineraryResp itinerary,
            PageResponse<JournalEntryResp> journals, PageResponse<ExpenseResp> expenses,
            SummaryResp summary, TripWeatherResponse weather) {
        TripStageOverviewResp.PlanningInsight planning = null;
        TripStageOverviewResp.OngoingInsight ongoing = null;
        TripStageOverviewResp.CompletedInsight completed = null;

        long nodeCount = itinerary.days().stream().flatMap(d -> d.nodes().stream()).count();
        if (TripStatus.PLANNING.value().equals(seed.status())) {
            planning = new TripStageOverviewResp.PlanningInsight(
                    TRIP_DAYS, TRIP_DAYS,
                    itinerary.days().stream()
                            .map(d -> new TripStageOverviewResp.DailyNodeCount(
                                    d.date(), d.dayIndex(), d.nodes().size())).toList(),
                    new TripStageOverviewResp.Density(
                            "DENSE", nodeCount, TRIP_DAYS,
                            money(BigDecimal.valueOf(nodeCount)
                                    .divide(BigDecimal.valueOf(TRIP_DAYS), 2, RoundingMode.HALF_UP)),
                            "nodes_per_trip_day_v1"),
                    seed.referenceDate().until(seed.startDate()).getDays(),
                    new TripStageOverviewResp.WeatherAvailability(
                            weather.available(), weather.status(), weather.message()),
                    List.of());
        } else if (TripStatus.ONGOING.value().equals(seed.status())) {
            SummaryResp.DailyTimelineItem today = summary.dailyTimeline().stream()
                    .filter(d -> d.date().equals(seed.referenceDate().toString())).findFirst().orElseThrow();
            long photoCount = today.logs().stream().flatMap(l -> l.photos().stream()).count();
            ongoing = new TripStageOverviewResp.OngoingInsight(
                    new TripStageOverviewResp.TodaySnapshot(
                            today.date(), today.itineraries().size(),
                            dailyExpense(expenses, today.date()), CURRENCY,
                            today.logs().size(), photoCount, today.itineraries()),
                    List.of());
        } else {
            completed = completedInsight(seed, summary);
        }

        return new TripStageOverviewResp(
                seed.referenceDate().toString(), "Asia/Shanghai", false,
                TripStatus.fromValue(seed.status()).name(),
                new TripStageOverviewResp.FocusTrip(
                        trip.id(), trip.title(), DESTINATION, trip.startDate(), trip.endDate(), trip.totalDays()),
                new TripStageOverviewResp.Selection("REQUESTED", "REQUESTED_TRIP", 0, List.of()),
                new TripStageOverviewResp.Permissions(false, false),
                planning, ongoing, memoryMaterial(summary), completed, List.of());
    }

    // ===== Stats =====

    private DemoJourneyStatsResp buildStats(
            DemoSceneSeed seed, TripDetailResp trip, ItineraryResp itinerary,
            PageResponse<JournalEntryResp> journals, PageResponse<ExpenseResp> expenses,
            SummaryResp summary) {
        java.math.BigDecimal total = expenses.list().stream()
                .map(e -> new java.math.BigDecimal(e.amountCny()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        String totalAmount = money(total);
        long nodeCount = summary.stats().itineraryCount();
        long photoCount = summary.stats().photoCount();
        String averageDaily = averageDailyAmount(totalAmount);

        List<StatsOverviewResp.CategoryStat> categories = summary.expenseSummary().categoryStats().stream()
                .map(item -> new StatsOverviewResp.CategoryStat(
                        item.category(), item.categoryName(), item.amount(), item.percentage(), item.count()))
                .toList();

        StatsOverviewResp overview = new StatsOverviewResp(
                new StatsOverviewResp.Scope("TRIP", trip.id(), trip.title()),
                new StatsOverviewResp.Overview(
                        1, TRIP_DAYS, nodeCount, (int) journals.total(), photoCount, 1, (int) expenses.total(),
                        totalAmount, averageDaily, CURRENCY),
                new StatsOverviewResp.ExpenseStats(totalAmount, CURRENCY, categories),
                List.of(), List.of(),
                summary.dailyTimeline().stream()
                        .map(d -> new StatsOverviewResp.DailyTrendItem(
                                d.date(), d.dayIndex(),
                                d.logs().isEmpty() ? null : d.logs().getFirst().content(),
                                (long) d.itineraries().size(), (long) d.logs().size(),
                                dailyExpense(expenses, d.date()),
                                d.logs().stream().flatMap(l -> l.photos().stream()).count()))
                        .toList(),
                List.of(
                        new StatsOverviewResp.InsightItem(
                                "FOOTPRINT", "五日串联浦西经典、陆家嘴天际线与迪士尼", trip.id()),
                        new StatsOverviewResp.InsightItem(
                                "RECORDS", hasRecordedContent(seed)
                                        ? "用 9 条日志和 9 张照片覆盖抵达、街区、观景与乐园"
                                        : "行程覆盖热门地标、跨区换宿、主题乐园与本地餐饮", trip.id()),
                        new StatsOverviewResp.InsightItem(
                                "SPEND", hasRecordedContent(seed)
                                        ? "住宿与门票（豫园/上海中心/迪士尼）约占七成，乐园餐饮另计"
                                        : "出发后可在此查看分类开支与多人结算", trip.id())),
                List.of(
                        new StatsOverviewResp.AnalysisEntry(
                                "ROUTE", "浦西→浦东节奏", "虹桥进、浦西两日、浦东三日、迪士尼整日、浦东机场出"),
                        new StatsOverviewResp.AnalysisEntry(
                                "PACE", "五日节奏", "前半城市场景慢走，后半观景与乐园，返程不走回头路")),
                new StatsOverviewResp.MemoryFacts(
                        (int) journals.total(), photoCount, summary.stats().highlightCount(),
                        (long) summary.userFeaturedPhotos().size(),
                        (long) summary.recommendedPhotos().size(), true));

        StatsSpendAnalysisResp spend = new StatsSpendAnalysisResp(
                totalAmount, averageDaily, totalAmount, (int) expenses.total(), CURRENCY,
                expenses.list().isEmpty() ? null : ExpenseCategory.HOTEL.value(),
                expenses.list().isEmpty() ? null : "住宿",
                expenses.list().isEmpty() ? "尚无旅行开支" : "住宿约占 38%，门票（景点/乐园）约占 31%",
                categories,
                List.of(new StatsSpendAnalysisResp.TripSpendEfficiency(
                        trip.id(), trip.title(), DESTINATION, TRIP_DAYS, totalAmount, averageDaily)));

        StatsRecordsAnalysisResp records = new StatsRecordsAnalysisResp(
                (int) journals.total(), photoCount, nodeCount,
                List.of(new StatsRecordsAnalysisResp.TripRecords(
                        trip.id(), trip.title(), DESTINATION, nodeCount,
                        (int) journals.total(), photoCount, (int) expenses.total())));

        Map<String, Long> nodeTypeCounts = new LinkedHashMap<>();
        itinerary.days().stream().flatMap(d -> d.nodes().stream())
                .forEach(n -> nodeTypeCounts.merge(n.type(), 1L, Long::sum));
        StatsFootprintAnalysisResp footprint = new StatsFootprintAnalysisResp(
                1, nodeCount, TRIP_DAYS, "五日串联浦西经典街区、陆家嘴天际线、迪士尼与双机场进出",
                List.of(new StatsFootprintAnalysisResp.CityFootprint(
                        DESTINATION, "310000", "021", "上海市",
                        "31.2304000", "121.4737000", 1, TRIP_DAYS, nodeCount, trip.startDate())),
                nodeTypeCounts.entrySet().stream()
                        .map(e -> new StatsFootprintAnalysisResp.NodeTypeStat(
                                e.getKey(), nodeTypeName(e.getKey()), e.getValue()))
                        .toList());

        return new DemoJourneyStatsResp(overview, spend, records, footprint);
    }

    // ===== Exports =====

    private ExportAssetsResp buildExportAssets(TripDetailResp trip, SummaryResp summary) {
        List<SummaryResp.PhotoItem> photos = new ArrayList<>();
        photos.addAll(summary.userFeaturedPhotos());
        photos.addAll(summary.recommendedPhotos());
        java.math.BigDecimal total = new java.math.BigDecimal(summary.stats().totalExpenseAmount());
        return new ExportAssetsResp(
                new ExportAssetsResp.TripInfo(
                        trip.id(), trip.title(), DESTINATION, trip.startDate(), trip.endDate(), trip.totalDays(),
                        trip.coverPhoto().exportUrl()),
                new ExportAssetsResp.Stats(
                        summary.stats().itineraryCount(), summary.stats().journalCount(),
                        summary.stats().photoCount(), summary.stats().expenseCount(),
                        total, new java.math.BigDecimal(averageDailyAmount(summary.stats().totalExpenseAmount())), CURRENCY),
                photos.stream().map(p -> new ExportAssetsResp.PhotoItem(
                        p.fileId(), p.journalId(), p.url(), p.thumbnailUrl(), p.previewUrl(),
                        p.exportUrl(), p.width(), p.height(), p.sortOrder())).toList(),
                List.of("虹桥机场", "南京路", "外滩", "豫园", "武康路", "田子坊", "新天地",
                        "陆家嘴", "上海中心", "迪士尼", "浦东国际机场"),
                summary.expenseSummary().categoryStats().stream()
                        .map(item -> new ExportAssetsResp.ExpenseCategoryStat(
                                item.category(), item.categoryName(),
                                new java.math.BigDecimal(item.amount()),
                                new java.math.BigDecimal(item.percentage()), item.count()))
                        .toList(),
                new ExportAssetsResp.Watermark(true, "趣溪 · 示例旅程"),
                new ExportAssetsResp.ExportOptions("story", "png",
                        new ExportAssetsResp.ContentOptions(
                                true, true, true, true, true, true, true, true, "narrative")));
    }

    private ItineraryExportAssetsResp buildItineraryExportAssets(TripDetailResp trip, ItineraryResp itinerary) {
        List<ItineraryExportAssetsResp.DayItem> days = itinerary.days().stream()
                .map(d -> new ItineraryExportAssetsResp.DayItem(
                        d.id(), d.dayIndex(), d.date(), d.nodes().size(),
                        d.nodes().stream().map(n -> new ItineraryExportAssetsResp.NodeItem(
                                n.id(), n.title(), n.type(), nodeTypeName(n.type()),
                                n.startTime(), n.endTime(),
                                n.location() == null ? null : n.location().name(),
                                n.address(), n.note(), n.sortOrder(), detailSummary(n))).toList()))
                .toList();
        long nodeCount = itinerary.days().stream().flatMap(d -> d.nodes().stream()).count();
        return new ItineraryExportAssetsResp(
                new ItineraryExportAssetsResp.TripInfo(
                        trip.id(), trip.title(), DESTINATION, trip.startDate(), trip.endDate(), trip.totalDays()),
                days, new ItineraryExportAssetsResp.Meta(TRIP_DAYS, nodeCount, TRIP_DAYS, nodeCount),
                new ItineraryExportAssetsResp.Watermark(true, "趣溪 · 示例旅程"));
    }

    // ===== Private domain helpers =====

    private ItineraryNodeBriefResp node(
            DemoSceneSeed seed, String dayId, String suffix, String title, String type,
            String startTime, String endTime, String address,
            ItineraryNodeLocationResp location, String note,
            Map<String, Object> details, int sortOrder) {
        return new ItineraryNodeBriefResp(
                id(seed.key(), "node-" + suffix), title, type, startTime, endTime,
                address, location, note, details, sortOrder);
    }

    private TripMemberResp member(
            DemoSceneSeed seed, String tripId, String suffix, String nickname,
            String role, String joinedAt) {
        return new TripMemberResp(
                id(seed.key(), "member-" + suffix), tripId, id(seed.key(), "user-" + suffix),
                nickname, asset("avatars/" + suffix + ".png"), role, "active", "active", joinedAt);
    }

    private ExpenseResp expense(
            DemoSceneSeed seed, String tripId, String suffix, String category, String amount,
            String note, LocalDate date, String payer, List<String> participants, String createdBy) {
        return new ExpenseResp(
                id(seed.key(), "expense-" + suffix), tripId, amount, CURRENCY, amount,
                "1.00000000", category, note, instant(date, LocalTime.of(18, 30)),
                "GROUP", payer, participants, createdBy, "EQUAL",
                null, null, null,
                instant(date, LocalTime.of(18, 35)));
    }

    private TripActivityLogResp activity(
            DemoSceneSeed seed, String tripId, String suffix, TripMemberResp member,
            String action, String targetType, String targetId, String summary, LocalDate date) {
        return new TripActivityLogResp(
                id(seed.key(), "activity-" + suffix), tripId,
                new TripActivityLogResp.Actor(member.userId(), member.id(), member.nickname()),
                action, targetType, targetId, summary, List.of(),
                instant(date, LocalTime.of(10, 0)));
    }

    private TripWeatherDailyItem weatherItem(
            LocalDate date, String weather, int min, int max, String icon, int pop, String tip) {
        return new TripWeatherDailyItem(date, date.getDayOfWeek().name(), weather,
                min, max, "东南风 2 级", icon, pop, tip);
    }

    private JournalEntryResp.PhotoResp photo(
            DemoSceneSeed seed, String suffix, String assetName, int sortOrder, boolean featured) {
        String fileId = id(seed.key(), "asset-" + assetName.replace('.', '-'));
        CiUrlBuilder.CiUrls urls = imageAsset("photos/" + assetName);
        return new JournalEntryResp.PhotoResp(
                id(seed.key(), suffix), fileId, urls.url(), sortOrder,
                urls.thumbnailUrl(),
                urls.previewUrl(),
                urls.exportUrl(),
                urls.storyPortraitUrl(),
                urls.storyCardUrl(),
                urls.squareUrl(),
                fallbackWatermarkedUrl(urls),
                1440, 1080, featured);
    }

    private SummaryResp.LogItem summaryLog(JournalEntryResp journal) {
        return new SummaryResp.LogItem(
                journal.id(), journal.content(),
                journal.location() == null ? null : journal.location().name(),
                journal.occurredAt(), journal.isHighlight(),
                journal.photos().stream().map(photo -> summaryPhoto(journal.id(), photo)).toList());
    }

    private SummaryResp.PhotoItem summaryPhoto(JournalEntryResp journal, JournalEntryResp.PhotoResp photo) {
        return new SummaryResp.PhotoItem(
                photo.id(), photo.fileId(), journal.id(), photo.url(), photo.thumbnailUrl(),
                photo.previewUrl(), photo.exportUrl(), photo.storyPortraitUrl(), photo.storyCardUrl(),
                photo.squareUrl(), photo.watermarkedUrl(), photo.width(), photo.height(),
                photo.sortOrder(), photo.userFeatured());
    }

    private SummaryResp.PhotoItem summaryPhoto(String journalId, JournalEntryResp.PhotoResp photo) {
        return new SummaryResp.PhotoItem(
                photo.id(), photo.fileId(), journalId, photo.url(), photo.thumbnailUrl(),
                photo.previewUrl(), photo.exportUrl(), photo.storyPortraitUrl(), photo.storyCardUrl(),
                photo.squareUrl(), photo.watermarkedUrl(), photo.width(), photo.height(),
                photo.sortOrder(), photo.userFeatured());
    }

    private SummaryResp.TimelineItineraryItem timelineNode(ItineraryNodeBriefResp node) {
        return new SummaryResp.TimelineItineraryItem(
                node.id(), node.type(), node.title(),
                node.location() == null ? null : node.location().name(),
                node.startTime(), node.endTime(), detailSummary(node));
    }

    private TripStageOverviewResp.CompletedInsight completedInsight(DemoSceneSeed seed, SummaryResp summary) {
        SummaryResp.DailyTimelineItem busiest = summary.dailyTimeline().stream()
                .max((l, r) -> Integer.compare(l.itineraries().size(), r.itineraries().size()))
                .orElseThrow();
        // D5 is lighter departure day; D2 is citywalk; busiest is usually D1 or D3
        SummaryResp.DailyTimelineItem relaxed = summary.dailyTimeline().get(4);
        SummaryResp.DailyTimelineItem memorable = summary.dailyTimeline().get(3);
        SummaryResp.LogItem representative = summary.highlightLogs().getFirst();
        return new TripStageOverviewResp.CompletedInsight(
                summary.stats().totalExpenseAmount(), averageDailyAmount(summary.stats().totalExpenseAmount()),
                CURRENCY,
                summary.expenseSummary().categoryStats().stream()
                        .map(i -> new TripStageOverviewResp.CategoryStat(
                                i.category(), i.categoryName(), i.amount(), i.percentage(), i.count()))
                        .toList(),
                summary.dailyTimeline().stream()
                        .map(d -> new TripStageOverviewResp.DailyTrend(
                                d.date(), d.dayIndex(), d.itineraries().size(), d.logs().size(),
                                d.logs().stream().flatMap(l -> l.photos().stream()).count(),
                                completedDailyExpense(d.dayIndex()),
                                d.itineraries().size() >= 4 ? "FULL" : "RELAXED"))
                        .toList(),
                new TripStageOverviewResp.Pace(
                        "BALANCED", busiest.date(), busiest.itineraries().size(), "daily_node_count_v1"),
                new TripStageOverviewResp.Highlights(
                        highlightDay(relaxed, "最松弛的一天"),
                        highlightDay(busiest, "最充实的一天"),
                        highlightDay(memorable, "最难忘的一天"),
                        new TripStageOverviewResp.RepresentativeLog(
                                representative.id(), seed.startDate().toString(),
                                representative.content(), representative.locationName(),
                                Boolean.TRUE.equals(representative.isHighlight()),
                                representative.photos().size()),
                        new TripStageOverviewResp.FeaturedPhotoSummary(
                                summary.userFeaturedPhotos().size(), summary.recommendedPhotos().size(),
                                summary.userFeaturedPhotos())));
    }

    private TripStageOverviewResp.HighlightDay highlightDay(
            SummaryResp.DailyTimelineItem day, String label) {
        return new TripStageOverviewResp.HighlightDay(
                day.date(), day.dayIndex(), label, day.itineraries().size(),
                day.logs().size(), day.logs().stream().flatMap(l -> l.photos().stream()).count(),
                day.itineraries().size() >= 4 ? "FULL" : "RELAXED");
    }

    private TripStageOverviewResp.MemoryMaterial memoryMaterial(SummaryResp summary) {
        return new TripStageOverviewResp.MemoryMaterial(
                summary.stats().journalCount(), summary.stats().photoCount(),
                summary.stats().highlightCount(), summary.userFeaturedPhotos().size(),
                summary.recommendedPhotos().size(),
                summary.dailyTimeline().stream()
                        .map(d -> new TripStageOverviewResp.DailyMaterial(
                                d.date(), d.dayIndex(), d.itineraries().size(),
                                d.logs().size(),
                                d.logs().stream().flatMap(l -> l.photos().stream()).count()))
                        .toList(),
                summary.dailyTimeline().stream()
                        .flatMap(d -> d.logs().stream()
                                .map(l -> new TripStageOverviewResp.RecentFragment(
                                        l.id(), d.date(), StringUtils.hasText(l.content()), l.photos().size())))
                        .toList(),
                new TripStageOverviewResp.CoverState(true, "DEMO_ASSET"),
                new TripStageOverviewResp.DisplayableContent(
                        List.of("itinerary", "journals", "photos", "expenses"), "FULL"));
    }

    // ===== Configuration & Constants =====

    private static final String PREFIX = "demo-v1-";
    private static final String TITLE = "上海五日游";
    private static final String DESTINATION = "上海";
    private static final String CURRENCY = "CNY";
    private static final String TOTAL_AMOUNT = "9737.00";
    private static final String AVERAGE_DAILY_AMOUNT = "1947.40";
    private static final ObjectMapper JSON = new ObjectMapper();

    private TripDestinationResp destination() {
        return new TripDestinationResp(DESTINATION, "310000", "021", "上海市", "上海市", "黄浦区",
                new BigDecimal("31.2304000"), new BigDecimal("121.4737000"), "DEMO", "demo-shanghai", "中国", null);
    }

    private TripCoverPhotoResp coverPhoto() {
        CiUrlBuilder.CiUrls urls = imageAsset("cover/hero.png");
        return new TripCoverPhotoResp(
                PREFIX + "asset-cover",
                urls.url(), urls.thumbnailUrl(), urls.previewUrl(), urls.exportUrl(),
                urls.storyPortraitUrl(), urls.storyCardUrl(), urls.squareUrl(),
                fallbackWatermarkedUrl(urls),
                1920, 1080);
    }

    private SummaryResp.CoverPhoto summaryCover() {
        TripCoverPhotoResp cover = coverPhoto();
        return new SummaryResp.CoverPhoto(
                cover.fileId(), cover.url(), cover.thumbnailUrl(), cover.previewUrl(),
                cover.exportUrl(), cover.storyPortraitUrl(), cover.storyCardUrl(),
                cover.squareUrl(), cover.watermarkedUrl(), cover.width(), cover.height());
    }

    // ===== Shared record types =====

    record DemoSceneSeed(
            String key, LocalDate referenceDate, LocalDate startDate, LocalDate endDate, String status) {
        /** Planning: trip starts in 3 days, five-day window. */
        static DemoSceneSeed planning(LocalDate referenceDate) {
            return new DemoSceneSeed("planning", referenceDate,
                    referenceDate.plusDays(3), referenceDate.plusDays(7), TripStatus.PLANNING.value());
        }

        /**
         * Ongoing: reference date is Day 4 (Disney day) so default scene can show park content.
         * Window: ref-3 … ref+1.
         */
        static DemoSceneSeed ongoing(LocalDate referenceDate) {
            return new DemoSceneSeed("ongoing", referenceDate,
                    referenceDate.minusDays(3), referenceDate.plusDays(1), TripStatus.ONGOING.value());
        }

        /** Completed: ended a few weeks ago. */
        static DemoSceneSeed completed(LocalDate referenceDate) {
            return new DemoSceneSeed("completed", referenceDate,
                    referenceDate.minusDays(30), referenceDate.minusDays(26), TripStatus.COMPLETED.value());
        }
    }

    // ===== Utility methods =====

    private ItineraryNodeLocationResp location(
            String suffix, String name, String address, String latitude, String longitude, String district) {
        return new ItineraryNodeLocationResp(
                PREFIX + "poi-" + suffix, name, address,
                new BigDecimal(latitude), new BigDecimal(longitude),
                "310000", "021", "上海市", "上海市", district,
                "DEMO", PREFIX + "place-" + suffix, "中国", null);
    }

    private String asset(String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        String objectKey = properties.getCosObjectKeyPrefix().trim() + "/" + relative;
        return cosClient.generatePresignedAccessUrl(
                objectKey,
                cosProperties.getAccessUrlExpireSeconds());
    }

    private CiUrlBuilder.CiUrls imageAsset(String path) {
        String relative = path.startsWith("/") ? path.substring(1) : path;
        String objectKey = properties.getCosObjectKeyPrefix().trim() + "/" + relative;
        int expireSeconds = cosProperties.getAccessUrlExpireSeconds();
        long rotation = signatureRotation;
        String originalUrl = cosClient.generatePresignedAccessUrl(objectKey, expireSeconds);
        return ciUrlBuilder.buildSigned(
                originalUrl,
                imageContentType(relative),
                rule -> cosClient.generatePresignedAccessUrl(objectKey, expireSeconds, rule));
    }

    private String imageContentType(String path) {
        String lowercasePath = path.toLowerCase(Locale.ROOT);
        if (lowercasePath.endsWith(".png")) {
            return "image/png";
        }
        if (lowercasePath.endsWith(".jpg") || lowercasePath.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowercasePath.endsWith(".webp")) {
            return "image/webp";
        }
        if (lowercasePath.endsWith(".avif")) {
            return "image/avif";
        }
        throw new IllegalArgumentException("Unsupported Demo image extension: " + path);
    }

    private String fallbackWatermarkedUrl(CiUrlBuilder.CiUrls urls) {
        return StringUtils.hasText(urls.watermarkedUrl()) ? urls.watermarkedUrl() : urls.url();
    }

    private String id(String scene, String suffix) {
        return PREFIX + scene + "-" + suffix;
    }

    private String instant(LocalDate date, LocalTime time) {
        return LocalDateTime.of(date, time)
                .atZone(DateTimeUtil.BUSINESS_ZONE).toInstant().toString();
    }

    private LocalDate businessDate(String instant) {
        return java.time.Instant.parse(instant)
                .atZone(DateTimeUtil.BUSINESS_ZONE).toLocalDate();
    }

    private LocalDate lastRecordedDate(DemoSceneSeed seed) {
        return TripStatus.ONGOING.value().equals(seed.status())
                ? seed.referenceDate() : seed.endDate();
    }

    private boolean hasRecordedContent(DemoSceneSeed seed) {
        return !TripStatus.PLANNING.value().equals(seed.status());
    }

    private String expenseTotal(PageResponse<ExpenseResp> expenses) {
        return money(expenses.list().stream()
                .map(e -> new java.math.BigDecimal(e.amountCny()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
    }

    private String dailyExpense(PageResponse<ExpenseResp> expenses, String date) {
        java.math.BigDecimal total = expenses.list().stream()
                .filter(e -> date.equals(businessDate(e.spentAt()).toString()))
                .map(e -> new java.math.BigDecimal(e.amountCny()))
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        return money(total);
    }

    private String completedDailyExpense(int dayIndex) {
        // Rough daily totals aligned with fixture expense dates (completed scene).
        return switch (dayIndex) {
            case 1 -> "2660.00";  // taxi + metro + food + yuyuan + puxi hotel
            case 2 -> "960.00";   // brunch + dinner + drinks
            case 3 -> "2820.00";  // pudong hotel + tower + lunch
            case 4 -> "2877.00";  // disney ticket + park food
            default -> "420.00";  // souvenirs
        };
    }

    private String averageDailyAmount(String totalAmount) {
        return averageDailyAmount(new java.math.BigDecimal(totalAmount));
    }

    private String averageDailyAmount(java.math.BigDecimal total) {
        return money(total.divide(java.math.BigDecimal.valueOf(TRIP_DAYS), 2, RoundingMode.HALF_UP));
    }

    private String money(java.math.BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String percentage(double ratio) {
        return java.math.BigDecimal.valueOf(ratio)
                .multiply(java.math.BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String categoryName(String category) {
        return ExpenseCategory.displayNameOf(category);
    }

    private String nodeTypeName(String type) {
        return switch (type) {
            case "attraction" -> "景点"; case "restaurant" -> "餐饮";
            case "transport" -> "交通"; case "hotel" -> "住宿";
            case "shopping" -> "购物"; default -> "其他";
        };
    }

    private String detailSummary(ItineraryNodeBriefResp node) {
        try {
            return ItineraryNodeDetailsSupport.summaryLine(
                    node.type(), JSON.writeValueAsString(node.details()));
        } catch (Exception e) {
            return null;
        }
    }

    private TripWeatherLocationResp weatherLocation() {
        return new TripWeatherLocationResp(DESTINATION,
                new BigDecimal("31.2304000"), new BigDecimal("121.4737000"), "中国");
    }

    // ===== Configuration validation =====

    private void requireAssetConfiguration() {
        if (!cosProperties.isEnabled()) {
            throw new IllegalStateException("Tencent COS must be enabled for Demo journey assets");
        }
        if (!StringUtils.hasText(cosProperties.getBucket())
                || !StringUtils.hasText(cosProperties.getRegion())) {
            throw new IllegalStateException(
                    "Tencent COS bucket and region are required for Demo journey assets");
        }
        if (cosProperties.getAccessUrlExpireSeconds() < 120) {
            throw new IllegalStateException(
                    "Demo journey COS access URL expiry must be at least 120 seconds");
        }
        if (!StringUtils.hasText(properties.getCosObjectKeyPrefix())) {
            throw new IllegalStateException("Demo journey COS object-key prefix is required");
        }
        String prefix = properties.getCosObjectKeyPrefix().trim();
        if (prefix.startsWith("/") || prefix.endsWith("/") || prefix.contains("..")
                || prefix.contains("?") || prefix.contains("#") || prefix.contains("\\")) {
            throw new IllegalStateException("Demo journey COS object-key prefix is invalid");
        }
    }

}
