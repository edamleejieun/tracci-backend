package com.tracci.module.demo.validation;

import com.tracci.common.response.PageResponse;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.module.demo.dto.response.DemoJourneyBundleResp;
import com.tracci.module.demo.dto.response.DemoJourneyCapabilitiesResp;
import com.tracci.module.demo.dto.response.DemoJourneySceneResp;
import com.tracci.module.expense.dto.response.ExpenseResp;
import com.tracci.module.expense.dto.response.ExpenseSettlementResp;
import com.tracci.module.journal.dto.response.JournalEntryResp;
import com.tracci.module.summary.dto.response.SummaryResp;
import com.tracci.module.trip.dto.response.ItineraryDayResp;
import com.tracci.module.trip.dto.response.ItineraryNodeBriefResp;
import com.tracci.module.trip.dto.response.TripCoverPhotoResp;
import com.tracci.module.trip.dto.response.TripMemberResp;
import com.tracci.module.trip.enums.TripStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Component
public class DemoJourneyCoverageValidator {

    private static final String ID_PREFIX = "demo-v1-";
    private static final int TRIP_DAYS = 5;
    private static final int MIN_NODE_COUNT = 20;
    private static final Set<String> REQUIRED_NODE_TYPES =
            Set.of("attraction", "restaurant", "transport", "hotel", "other");
    /** Landmark tokens that must appear in node title or location name. */
    private static final Set<String> REQUIRED_SHANGHAI_LANDMARKS =
            Set.of("虹桥", "南京路", "外滩", "豫园", "武康路", "田子坊", "新天地",
                    "陆家嘴", "上海中心", "迪士尼", "浦东国际机场");
    private static final Set<String> REQUIRED_EXPENSE_CATEGORIES =
            Set.of("food", "transport", "hotel", "attraction", "shopping", "other");
    private static final Set<String> REQUIRED_MEMBER_ROLES =
            Set.of("owner", "editor", "viewer");
    private static final Set<String> ALLOWED_MOODS =
            Set.of("happy", "relaxed", "excited", "peaceful", "thoughtful");
    private static final Set<String> ALLOWED_WEATHER_ICONS =
            Set.of("sunny", "cloudy", "overcast", "light_rain", "moderate_rain",
                    "heavy_rain", "snow", "fog", "unknown");

    public void validate(
            DemoJourneyBundleResp bundle,
            LocalDate referenceDate,
            String cosBucket,
            String cosRegion,
            String cosObjectKeyPrefix) {
        require(bundle != null, "bundle is required");
        require(referenceDate != null, "referenceDate is required");
        require(bundle.enabled(), "bundle must be enabled");
        require(bundle.schemaVersion() > 0, "schemaVersion must be positive");
        require(StringUtils.hasText(bundle.contentVersion()), "contentVersion is required");
        require(referenceDate.toString().equals(bundle.referenceDate()), "bundle referenceDate mismatch");
        require(DemoJourneyBundleResp.DEFAULT_SCENE.equals(bundle.defaultScene()), "defaultScene must be ONGOING");
        validateCapabilities(bundle.capabilities());
        require(bundle.scenes() != null, "scenes are required");
        require(StringUtils.hasText(cosBucket), "COS bucket is required");
        require(StringUtils.hasText(cosRegion), "COS region is required");
        require(StringUtils.hasText(cosObjectKeyPrefix), "COS object-key prefix is required");

        validateScene("planning", bundle.scenes().planning(), TripStatus.PLANNING, referenceDate,
                cosBucket, cosRegion, cosObjectKeyPrefix);
        validateScene("ongoing", bundle.scenes().ongoing(), TripStatus.ONGOING, referenceDate,
                cosBucket, cosRegion, cosObjectKeyPrefix);
        validateScene("completed", bundle.scenes().completed(), TripStatus.COMPLETED, referenceDate,
                cosBucket, cosRegion, cosObjectKeyPrefix);
    }

    private void validateCapabilities(DemoJourneyCapabilitiesResp capabilities) {
        require(capabilities != null, "capabilities are required");
        require(capabilities.readOnly(), "Demo must be read-only");
        require(!capabilities.canEditTrip(), "Demo cannot edit trip");
        require(!capabilities.canAddNode(), "Demo cannot add node");
        require(!capabilities.canEditJournal(), "Demo cannot edit journal");
        require(!capabilities.canManageExpense(), "Demo cannot manage expense");
        require(!capabilities.canManageMembers(), "Demo cannot manage members");
        require(!capabilities.canInvite(), "Demo cannot invite");
        require(!capabilities.canDelete(), "Demo cannot delete");
        require(!capabilities.canShare(), "Demo cannot share");
        require(!capabilities.canExport(), "Demo cannot export");
        require(!capabilities.canApplyAiChanges(), "Demo cannot apply AI changes");
    }

    private void validateScene(
            String sceneName,
            DemoJourneySceneResp scene,
            TripStatus expectedStatus,
            LocalDate referenceDate,
            String cosBucket,
            String cosRegion,
            String cosObjectKeyPrefix) {
        String path = "scenes." + sceneName;
        require(scene != null, path + " is required");
        require(scene.trip() != null, path + ".trip is required");
        require(scene.itinerary() != null, path + ".itinerary is required");
        require(scene.journals() != null, path + ".journals is required");
        require(scene.expenses() != null, path + ".expenses is required");
        require(scene.expenseSummary() != null, path + ".expenseSummary is required");
        require(scene.expenseSettlement() != null, path + ".expenseSettlement is required");
        require(scene.members() != null, path + ".members is required");
        require(scene.activityLogs() != null, path + ".activityLogs is required");
        require(scene.weather() != null, path + ".weather is required");
        require(scene.stageOverview() != null, path + ".stageOverview is required");
        require(scene.summary() != null, path + ".summary is required");
        require(scene.stats() != null, path + ".stats is required");
        require(scene.exportAssets() != null, path + ".exportAssets is required");
        require(scene.itineraryExportAssets() != null, path + ".itineraryExportAssets is required");

        String tripId = scene.trip().id();
        validateDemoId(tripId, path + ".trip.id");
        LocalDate startDate = LocalDate.parse(scene.trip().startDate());
        LocalDate endDate = LocalDate.parse(scene.trip().endDate());
        require(!endDate.isBefore(startDate), path + " trip dates are invalid");
        require(scene.trip().totalDays() == TRIP_DAYS, path + " must cover five days");
        require(expectedStatus.value().equals(scene.trip().status()), path + " trip status mismatch");
        require(expectedStatus == TripStatus.calculate(referenceDate, startDate, endDate),
                path + " status is not coherent with referenceDate");
        require(scene.trip().access() != null, path + ".trip.access is required");
        require("viewer".equals(scene.trip().access().role()), path + " access role must be viewer");
        require(List.of("trip.read").equals(scene.trip().access().permissions()),
                path + " access permissions must be read-only");

        validateItinerary(path, scene, tripId, startDate);
        validateMembers(path, scene.members(), tripId, cosBucket, cosRegion, cosObjectKeyPrefix);
        validateJournals(path, scene, expectedStatus, referenceDate, tripId,
                cosBucket, cosRegion, cosObjectKeyPrefix);
        validateExpenses(path, scene, expectedStatus, referenceDate, tripId);
        validateActivityLogs(path, scene, expectedStatus, tripId);
        validateStageOverview(path, scene, expectedStatus, referenceDate, tripId);
        validateWeather(path, scene, expectedStatus);
        validateSummaryAndStats(path, scene, tripId);
        validateExports(path, scene, tripId);
        validateAssets(path, scene, cosBucket, cosRegion, cosObjectKeyPrefix);
    }

    private void validateItinerary(
            String path,
            DemoJourneySceneResp scene,
            String tripId,
            LocalDate tripStartDate) {
        require(tripId.equals(scene.itinerary().tripId()), path + ".itinerary.tripId mismatch");
        require(scene.itinerary().days() != null && scene.itinerary().days().size() == TRIP_DAYS,
                path + ".itinerary must contain five days");

        Set<String> dayIds = new HashSet<>();
        Set<String> nodeIds = new HashSet<>();
        Set<String> nodeTypes = new HashSet<>();
        Set<String> coveredLandmarks = new HashSet<>();
        long locatedNodeCount = 0;
        long unlocatedNodeCount = 0;
        long nodeCount = 0;

        for (int index = 0; index < scene.itinerary().days().size(); index++) {
            ItineraryDayResp day = scene.itinerary().days().get(index);
            validateDemoId(day.id(), path + ".itinerary.days[" + index + "].id");
            require(dayIds.add(day.id()), path + " contains duplicate day id");
            require(day.dayIndex() == index + 1, path + " dayIndex must be sequential");
            require(StringUtils.hasText(day.date()), path + " day date is required");
            require(tripStartDate.plusDays(index).equals(LocalDate.parse(day.date())),
                    path + " day date must align with trip dates");
            require(day.nodes() != null && !day.nodes().isEmpty(), path + " each day needs nodes");
            int previousSortOrder = 0;
            for (ItineraryNodeBriefResp node : day.nodes()) {
                validateDemoId(node.id(), path + ".itinerary.node.id");
                require(nodeIds.add(node.id()), path + " contains duplicate node id");
                require(StringUtils.hasText(node.title()), path + " node title is required");
                require(StringUtils.hasText(node.type()), path + " node type is required");
                require(node.sortOrder() != null && node.sortOrder() > previousSortOrder,
                        path + " node sort order must increase");
                previousSortOrder = node.sortOrder();
                nodeTypes.add(node.type());
                String searchableText = node.title()
                        + " "
                        + (node.location() == null ? "" : node.location().name());
                REQUIRED_SHANGHAI_LANDMARKS.stream()
                        .filter(searchableText::contains)
                        .forEach(coveredLandmarks::add);
                validateNodeDetails(path, node);
                if (node.location() == null) {
                    unlocatedNodeCount++;
                } else {
                    locatedNodeCount++;
                }
                nodeCount++;
            }
        }
        require(nodeCount == scene.trip().nodeCount(), path + " trip nodeCount mismatch");
        require(nodeCount >= MIN_NODE_COUNT, path + " needs a complete five-day landmark itinerary");
        require(nodeTypes.containsAll(REQUIRED_NODE_TYPES), path + " node type coverage is incomplete");
        require(coveredLandmarks.containsAll(REQUIRED_SHANGHAI_LANDMARKS),
                path + " Shanghai landmark coverage is incomplete");
        require(locatedNodeCount >= 2, path + " needs at least two located nodes");
        require(unlocatedNodeCount >= 1, path + " needs at least one unlocated node");
    }

    private void validateMembers(
            String path,
            List<TripMemberResp> members,
            String tripId,
            String cosBucket,
            String cosRegion,
            String cosObjectKeyPrefix) {
        require(members.size() == 3, path + " must contain three members");
        Set<String> memberIds = new HashSet<>();
        Set<String> userIds = new HashSet<>();
        Set<String> roles = new HashSet<>();
        for (TripMemberResp member : members) {
            validateDemoId(member.id(), path + ".members.id");
            validateDemoId(member.userId(), path + ".members.userId");
            require(memberIds.add(member.id()), path + " contains duplicate member id");
            require(userIds.add(member.userId()), path + " contains duplicate member userId");
            require(tripId.equals(member.tripId()), path + " member tripId mismatch");
            require("active".equals(member.status()), path + " member status must be active");
            require("active".equals(member.accountStatus()), path + " account status must be active");
            roles.add(member.role());
            validateCosUrl(
                    member.avatarUrl(),
                    cosBucket,
                    cosRegion,
                    cosObjectKeyPrefix,
                    path + ".members.avatarUrl");
        }
        require(roles.equals(REQUIRED_MEMBER_ROLES), path + " member role coverage is incomplete");
    }

    private void validateJournals(
            String path,
            DemoJourneySceneResp scene,
            TripStatus expectedStatus,
            LocalDate referenceDate,
            String tripId,
            String cosBucket,
            String cosRegion,
            String cosObjectKeyPrefix) {
        validatePage(scene.journals(), path + ".journals");
        if (expectedStatus == TripStatus.PLANNING) {
            require(scene.journals().list().isEmpty(), path + " planning journals must be empty");
            require(scene.trip().journalCount() == 0, path + " planning journalCount must be zero");
            require(scene.summary().stats().photoCount() == 0, path + " planning photoCount must be zero");
            return;
        }
        Set<String> memberUserIds = scene.members().stream().map(TripMemberResp::userId).collect(java.util.stream.Collectors.toSet());
        LocalDate tripStartDate = LocalDate.parse(scene.trip().startDate());
        LocalDate tripEndDate = LocalDate.parse(scene.trip().endDate());
        Set<Integer> photoCounts = new HashSet<>();
        long totalPhotoCount = 0;
        for (JournalEntryResp journal : scene.journals().list()) {
            validateDemoId(journal.id(), path + ".journals.id");
            require(tripId.equals(journal.tripId()), path + " journal tripId mismatch");
            require(memberUserIds.contains(journal.authorId()), path + " journal author does not reference a member");
            LocalDate occurredDate = businessDate(journal.occurredAt());
            require(!occurredDate.isBefore(tripStartDate) && !occurredDate.isAfter(tripEndDate),
                    path + " journal must occur within trip dates");
            require(!occurredDate.isAfter(referenceDate),
                    path + " journal cannot occur after referenceDate");
            require(ALLOWED_MOODS.contains(journal.mood()), path + " journal mood is invalid");
            require(Set.of("all", "private").contains(journal.visibility()), path + " journal visibility is invalid");
            require(journal.photos() != null, path + " journal photos cannot be null");
            photoCounts.add(journal.photos().size());
            totalPhotoCount += journal.photos().size();
            for (JournalEntryResp.PhotoResp photo : journal.photos()) {
                validateDemoId(photo.id(), path + ".journals.photo.id");
                validateDemoId(photo.fileId(), path + ".journals.photo.fileId");
                validatePhotoUrls(
                        photo,
                        cosBucket,
                        cosRegion,
                        cosObjectKeyPrefix,
                        path + ".journals.photo");
            }
        }
        require(photoCounts.containsAll(Set.of(0, 1, 2)), path + " must cover text-only, single-photo and multi-photo journals");
        require(scene.journals().total() >= 8, path + " needs at least eight representative journals for a five-day trip");
        require(totalPhotoCount >= 9, path + " needs at least nine representative photos");
        require(scene.trip().journalCount() == scene.journals().total(), path + " trip journalCount mismatch");
        require(scene.summary().stats().photoCount() == totalPhotoCount, path + " summary photoCount mismatch");
    }

    private void validateExpenses(
            String path,
            DemoJourneySceneResp scene,
            TripStatus expectedStatus,
            LocalDate referenceDate,
            String tripId) {
        validatePage(scene.expenses(), path + ".expenses");
        Set<String> memberIds = scene.members().stream().map(TripMemberResp::id).collect(java.util.stream.Collectors.toSet());
        if (expectedStatus == TripStatus.PLANNING) {
            require(scene.expenses().list().isEmpty(), path + " planning expenses must be empty");
            require(money(scene.expenseSummary().totalCny(), path + ".expenseSummary.total").signum() == 0,
                    path + " planning expense total must be zero");
            require(scene.expenseSummary().breakdown().isEmpty(), path + " planning expense breakdown must be empty");
            require(money(scene.expenseSettlement().totalGroupAmount(), path + ".settlement.total").signum() == 0,
                    path + " planning settlement total must be zero");
            require(scene.expenseSettlement().suggestedTransfers().isEmpty(),
                    path + " planning transfers must be empty");
            require(scene.expenseSettlement().members().size() == scene.members().size(),
                    path + " planning settlement member count mismatch");
            scene.expenseSettlement().members().forEach(member -> {
                require(memberIds.contains(member.memberId()), path + " planning settlement member is missing");
                require(money(member.paidAmount(), path + ".settlement.paid").signum() == 0,
                        path + " planning paid amount must be zero");
                require(money(member.shareAmount(), path + ".settlement.share").signum() == 0,
                        path + " planning share amount must be zero");
                require(money(member.balanceAmount(), path + ".settlement.balance").signum() == 0,
                        path + " planning balance must be zero");
            });
            return;
        }
        Set<String> categories = new HashSet<>();
        LocalDate tripStartDate = LocalDate.parse(scene.trip().startDate());
        LocalDate tripEndDate = LocalDate.parse(scene.trip().endDate());
        BigDecimal total = BigDecimal.ZERO;
        for (ExpenseResp expense : scene.expenses().list()) {
            validateDemoId(expense.id(), path + ".expenses.id");
            require(tripId.equals(expense.tripId()), path + " expense tripId mismatch");
            LocalDate spentDate = businessDate(expense.spentAt());
            require(!spentDate.isBefore(tripStartDate) && !spentDate.isAfter(tripEndDate),
                    path + " expense must occur within trip dates");
            require(!spentDate.isAfter(referenceDate),
                    path + " expense cannot occur after referenceDate");
            require(memberIds.contains(expense.payerMemberId()), path + " expense payer member is missing");
            require(memberIds.contains(expense.createdByMemberId()), path + " expense creator member is missing");
            require(expense.participantMemberIds() != null && !expense.participantMemberIds().isEmpty(),
                    path + " expense participants are required");
            require(memberIds.containsAll(expense.participantMemberIds()),
                    path + " expense participant member is missing");
            categories.add(expense.category());
            total = total.add(money(expense.amountCny(), path + ".expenses.amountCny"));
        }
        require(categories.containsAll(REQUIRED_EXPENSE_CATEGORIES), path + " expense category coverage is incomplete");
        require(scene.expenses().total() >= 12, path + " needs at least twelve representative expenses for a five-day trip");
        require(total.compareTo(money(scene.expenseSummary().totalCny(), path + ".expenseSummary.totalCny")) == 0,
                path + " expense summary total mismatch");
        require(scene.expenseSummary().expenseCount() == scene.expenses().total(),
                path + " expense summary count mismatch");
        BigDecimal breakdownTotal = scene.expenseSummary().breakdown().stream()
                .map(item -> money(item.amountCny(), path + ".expenseSummary.breakdown"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        require(total.compareTo(breakdownTotal) == 0, path + " expense breakdown total mismatch");

        ExpenseSettlementResp settlement = scene.expenseSettlement();
        require(tripId.equals(settlement.tripId()), path + " settlement tripId mismatch");
        require(total.compareTo(money(settlement.totalGroupAmount(), path + ".settlement.totalGroupAmount")) == 0,
                path + " settlement total mismatch");
        require(settlement.members().size() == scene.members().size(), path + " settlement member count mismatch");
        BigDecimal balanceTotal = BigDecimal.ZERO;
        for (ExpenseSettlementResp.MemberSettlement member : settlement.members()) {
            require(memberIds.contains(member.memberId()), path + " settlement member is missing");
            balanceTotal = balanceTotal.add(money(member.balanceAmount(), path + ".settlement.balanceAmount"));
        }
        require(balanceTotal.compareTo(BigDecimal.ZERO) == 0, path + " settlement balances must sum to zero");
        for (ExpenseSettlementResp.SuggestedTransfer transfer : settlement.suggestedTransfers()) {
            require(memberIds.contains(transfer.fromMemberId()), path + " transfer source member is missing");
            require(memberIds.contains(transfer.toMemberId()), path + " transfer target member is missing");
            require(money(transfer.amount(), path + ".settlement.transfer.amount").signum() > 0,
                    path + " transfer amount must be positive");
        }
    }

    private void validateActivityLogs(
            String path,
            DemoJourneySceneResp scene,
            TripStatus expectedStatus,
            String tripId) {
        validatePage(scene.activityLogs(), path + ".activityLogs");
        Set<String> memberIds = scene.members().stream().map(TripMemberResp::id).collect(java.util.stream.Collectors.toSet());
        Set<String> userIds = scene.members().stream().map(TripMemberResp::userId).collect(java.util.stream.Collectors.toSet());
        long minimumActivityCount = expectedStatus == TripStatus.PLANNING ? 3 : 7;
        require(scene.activityLogs().total() >= minimumActivityCount, path + " activity coverage is incomplete");
        for (var activity : scene.activityLogs().list()) {
            validateDemoId(activity.id(), path + ".activityLogs.id");
            require(tripId.equals(activity.tripId()), path + " activity tripId mismatch");
            require(activity.actor() != null, path + " activity actor is required");
            require(memberIds.contains(activity.actor().memberId()), path + " activity actor member is missing");
            require(userIds.contains(activity.actor().userId()), path + " activity actor user is missing");
        }
    }

    private void validateStageOverview(
            String path,
            DemoJourneySceneResp scene,
            TripStatus expectedStatus,
            LocalDate referenceDate,
            String tripId) {
        var overview = scene.stageOverview();
        require(scene.trip().updatedAt() != null, path + " trip updatedAt is required");
        require(referenceDate.toString().equals(overview.generatedDate()),
                path + " overview generatedDate mismatch");
        require(expectedStatus.name().equals(overview.phase()), path + " overview phase mismatch");
        require(overview.focusTrip() != null && tripId.equals(overview.focusTrip().id()),
                path + " overview focus trip mismatch");
        require(overview.permissions() != null, path + " overview permissions are required");
        require(!overview.permissions().canAddJournal(), path + " overview cannot add journal");
        require(!overview.permissions().canFeaturePhoto(), path + " overview cannot feature photo");
        require(overview.actions() != null && overview.actions().isEmpty(),
                path + " overview cannot expose actions");
        require(overview.memory() != null, path + " overview memory is required");
        require((expectedStatus == TripStatus.PLANNING) == (overview.planning() != null),
                path + " planning insight mismatch");
        require((expectedStatus == TripStatus.ONGOING) == (overview.ongoing() != null),
                path + " ongoing insight mismatch");
        require((expectedStatus == TripStatus.COMPLETED) == (overview.completed() != null),
                path + " completed insight mismatch");
    }

    private void validateWeather(
            String path,
            DemoJourneySceneResp scene,
            TripStatus expectedStatus) {
        var weather = scene.weather();
        require("DEMO".equals(weather.source()), path + " weather source must be DEMO");
        require(weather.location() != null, path + " weather location is required");
        require(weather.items() != null, path + " weather items cannot be null");
        if (expectedStatus == TripStatus.COMPLETED) {
            require(!Boolean.TRUE.equals(weather.available()), path + " completed weather must be unavailable");
            require(weather.items().isEmpty(), path + " completed weather items must be empty");
        } else {
            require(Boolean.TRUE.equals(weather.available()), path + " weather must be available");
            require(weather.items().size() == TRIP_DAYS, path + " weather must cover five days");
            LocalDate tripStartDate = LocalDate.parse(scene.trip().startDate());
            for (int index = 0; index < weather.items().size(); index++) {
                require(tripStartDate.plusDays(index).equals(weather.items().get(index).date()),
                        path + " weather dates must align with trip dates");
            }
            require(weather.items().stream().allMatch(item -> ALLOWED_WEATHER_ICONS.contains(item.icon())),
                    path + " weather icon is invalid");
        }
    }

    private void validateSummaryAndStats(String path, DemoJourneySceneResp scene, String tripId) {
        SummaryResp summary = scene.summary();
        require(summary.trip() != null && tripId.equals(summary.trip().id()), path + " summary trip mismatch");
        require(summary.stats().dayCount() == TRIP_DAYS, path + " summary day count mismatch");
        require(summary.stats().itineraryCount() == scene.trip().nodeCount(), path + " summary node count mismatch");
        require(summary.stats().journalCount() == scene.journals().total(), path + " summary journal count mismatch");
        require(summary.stats().expenseCount() == scene.expenses().total(), path + " summary expense count mismatch");
        require(money(scene.trip().totalCny(), path + ".trip.totalCny")
                        .compareTo(money(summary.stats().totalExpenseAmount(), path + ".summary.totalExpense")) == 0,
                path + " trip totalCny mismatch");
        require(money(summary.stats().totalExpenseAmount(), path + ".summary.totalExpense")
                        .compareTo(money(scene.expenseSummary().totalCny(), path + ".expenseSummary.total")) == 0,
                path + " summary expense amount mismatch");
        require(summary.dailyTimeline() != null && summary.dailyTimeline().size() == TRIP_DAYS,
                path + " summary timeline must contain five days");
        LocalDate tripStartDate = LocalDate.parse(scene.trip().startDate());
        for (int index = 0; index < summary.dailyTimeline().size(); index++) {
            require(tripStartDate.plusDays(index)
                            .equals(LocalDate.parse(summary.dailyTimeline().get(index).date())),
                    path + " summary timeline date mismatch");
        }

        require(scene.stats().overview() != null, path + " stats overview is required");
        require(scene.stats().spendAnalysis() != null, path + " spend analysis is required");
        require(scene.stats().recordsAnalysis() != null, path + " records analysis is required");
        require(scene.stats().footprintAnalysis() != null, path + " footprint analysis is required");
        require("TRIP".equals(scene.stats().overview().scope().type()), path + " stats scope must be TRIP");
        require(tripId.equals(scene.stats().overview().scope().tripId()), path + " stats scope trip mismatch");
        require(scene.stats().overview().overview().itineraryCount() == scene.trip().nodeCount(),
                path + " stats itinerary count mismatch");
        require(scene.stats().overview().overview().journalCount() == scene.journals().total(),
                path + " stats journal count mismatch");
        require(scene.stats().overview().overview().expenseCount() == scene.expenses().total(),
                path + " stats expense count mismatch");
        require(scene.stats().recordsAnalysis().itineraryCount() == scene.trip().nodeCount(),
                path + " records itinerary count mismatch");
        require(scene.stats().footprintAnalysis().itineraryCount() == scene.trip().nodeCount(),
                path + " footprint itinerary count mismatch");
        require(scene.stats().overview().insights() != null
                        && scene.stats().overview().insights().size() >= 3,
                path + " stats insights coverage is incomplete");
        require(scene.stats().overview().analysisEntries() != null
                        && scene.stats().overview().analysisEntries().size() >= 2,
                path + " stats analysis coverage is incomplete");
        require(money(scene.stats().spendAnalysis().totalAmount(), path + ".stats.spend.total")
                        .compareTo(money(scene.expenseSummary().totalCny(), path + ".expenseSummary.total")) == 0,
                path + " spend analysis total mismatch");
    }

    private void validateExports(String path, DemoJourneySceneResp scene, String tripId) {
        require(scene.exportAssets().trip() != null && tripId.equals(scene.exportAssets().trip().id()),
                path + " export trip mismatch");
        require(scene.itineraryExportAssets().trip() != null
                        && tripId.equals(scene.itineraryExportAssets().trip().id()),
                path + " itinerary export trip mismatch");
        require(scene.exportAssets().stats().itineraryCount() == scene.trip().nodeCount(),
                path + " export node count mismatch");
        require(scene.itineraryExportAssets().meta().totalNodeCount() == scene.trip().nodeCount(),
                path + " itinerary export node count mismatch");
        require(scene.itineraryExportAssets().days().size() == TRIP_DAYS,
                path + " itinerary export must contain five days");
    }

    private void validateAssets(
            String path,
            DemoJourneySceneResp scene,
            String cosBucket,
            String cosRegion,
            String cosObjectKeyPrefix) {
        TripCoverPhotoResp cover = scene.trip().coverPhoto();
        require(cover != null, path + " cover is required");
        validateDemoId(cover.fileId(), path + ".trip.cover.fileId");
        Stream.of(
                        cover.url(),
                        cover.thumbnailUrl(),
                        cover.previewUrl(),
                        cover.exportUrl(),
                        cover.storyPortraitUrl(),
                        cover.storyCardUrl(),
                        cover.squareUrl(),
                        cover.watermarkedUrl())
                .forEach(url -> validateCosUrl(
                        url, cosBucket, cosRegion, cosObjectKeyPrefix, path + ".trip.cover"));
        scene.exportAssets().photos().forEach(photo -> {
            validateDemoId(photo.fileId(), path + ".exportAssets.photo.fileId");
            Stream.of(photo.url(), photo.thumbnailUrl(), photo.previewUrl(), photo.exportUrl())
                    .forEach(url -> validateCosUrl(
                            url,
                            cosBucket,
                            cosRegion,
                            cosObjectKeyPrefix,
                            path + ".exportAssets.photo"));
        });
    }

    private void validatePhotoUrls(
            JournalEntryResp.PhotoResp photo,
            String cosBucket,
            String cosRegion,
            String cosObjectKeyPrefix,
            String path) {
        Stream.of(
                        photo.url(),
                        photo.thumbnailUrl(),
                        photo.previewUrl(),
                        photo.exportUrl(),
                        photo.storyPortraitUrl(),
                        photo.storyCardUrl(),
                        photo.squareUrl(),
                        photo.watermarkedUrl())
                .forEach(url -> validateCosUrl(
                        url, cosBucket, cosRegion, cosObjectKeyPrefix, path));
    }

    private void validatePage(PageResponse<?> page, String path) {
        require(page.list() != null, path + ".list is required");
        require(page.page() == 1, path + ".page must be 1");
        require(page.total() == page.list().size(), path + ".total mismatch");
        require(page.pageSize() >= page.list().size(), path + ".pageSize is too small");
    }

    private void validateDemoId(String id, String path) {
        require(StringUtils.hasText(id) && id.startsWith(ID_PREFIX), path + " must use a synthetic Demo id");
    }

    private void validateNodeDetails(String path, ItineraryNodeBriefResp node) {
        Map<String, Object> details = node.details();
        if ("transport".equals(node.type())) {
            require(details != null && "transport".equals(details.get("kind")),
                    path + " transport details.kind is invalid");
            require(Set.of("flight", "train", "hsr", "bus", "driving", "other").contains(details.get("mode")),
                    path + " transport details.mode is invalid");
        }
        if ("hotel".equals(node.type())) {
            require(details != null && "hotel".equals(details.get("kind")),
                    path + " hotel details.kind is invalid");
            require(StringUtils.hasText(String.valueOf(details.get("checkInDate"))),
                    path + " hotel checkInDate is required");
            require(StringUtils.hasText(String.valueOf(details.get("checkOutDate"))),
                    path + " hotel checkOutDate is required");
        }
    }

    private void validateCosUrl(
            String url,
            String cosBucket,
            String cosRegion,
            String cosObjectKeyPrefix,
            String path) {
        require(StringUtils.hasText(url), path + " URL is required");
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(path + " URL is invalid", exception);
        }
        require("https".equalsIgnoreCase(uri.getScheme()), path + " URL must use HTTPS");
        String expectedHost = cosBucket.trim() + ".cos." + cosRegion.trim() + ".myqcloud.com";
        require(expectedHost.equalsIgnoreCase(uri.getHost()), path + " URL must use the configured COS bucket");
        require(uri.getRawUserInfo() == null, path + " URL cannot contain user info");
        String expectedPathPrefix = "/" + cosObjectKeyPrefix.trim() + "/";
        require(uri.getPath() != null && uri.getPath().startsWith(expectedPathPrefix),
                path + " URL is outside the approved Demo COS object directory");
        require(uri.getPath().length() > expectedPathPrefix.length() && !uri.getPath().endsWith("/"),
                path + " URL must reference a concrete Demo COS object");
        require(StringUtils.hasText(uri.getRawQuery()),
                path + " URL must be a private COS presigned access URL");
        require(uri.getRawFragment() == null, path + " URL cannot contain a fragment");
    }

    private BigDecimal money(String value, String path) {
        require(StringUtils.hasText(value), path + " money value is required");
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(path + " money value is invalid", exception);
        }
    }

    private LocalDate businessDate(String instant) {
        require(StringUtils.hasText(instant), "instant is required");
        try {
            return Instant.parse(instant)
                    .atZone(DateTimeUtil.BUSINESS_ZONE)
                    .toLocalDate();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid Demo journey fixture: instant is invalid", exception);
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invalid Demo journey fixture: " + message);
        }
    }
}
