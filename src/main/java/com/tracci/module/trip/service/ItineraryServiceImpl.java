package com.tracci.module.trip.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.enums.LocationProvider;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.module.trip.dto.request.ItineraryNodeCreateReq;
import com.tracci.module.trip.dto.request.ItineraryNodeLocationReq;
import com.tracci.module.trip.dto.request.ItineraryNodeReorderReq;
import com.tracci.module.trip.dto.request.ItineraryNodeUpdateReq;
import com.tracci.module.trip.dto.response.ItineraryDayResp;
import com.tracci.module.trip.dto.response.ItineraryNodeBriefResp;
import com.tracci.module.trip.dto.response.TripActivityLogResp;
import com.tracci.module.trip.dto.response.ItineraryNodeLocationResp;
import com.tracci.module.trip.dto.response.ItineraryNodeResp;
import com.tracci.module.trip.dto.response.ItineraryResp;
import com.tracci.module.trip.entity.ItineraryNode;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripDay;
import com.tracci.module.trip.enums.ItineraryNodeType;
import com.tracci.module.trip.enums.TripStatus;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.repository.TripDayMapper;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.support.ItineraryNodeDetailsSupport;
import com.tracci.module.place.entity.Place;
import com.tracci.module.place.enums.PlaceCategory;
import com.tracci.module.place.enums.PlaceScope;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.place.service.PlaceService;
import com.tracci.module.savedplace.dto.request.LocationCandidate;
import com.tracci.module.savedplace.service.SavedPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItineraryServiceImpl implements ItineraryService {

    private static final int SORT_STEP = 1000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final TripMapper tripMapper;
    private final TripDayMapper tripDayMapper;
    private final ItineraryNodeMapper itineraryNodeMapper;
    private final TripAccessService tripAccessService;
    private final TripActivityLogService activityLogService;
    private final SavedPlaceService savedPlaceService;
    private final PlaceService placeService;
    private final ExpenseMapper expenseMapper;
    /** 行程内容变更的旁路通知（如助手会话同步真值）；无实现时为空。 */
    private final List<com.tracci.module.trip.support.TripContentChangeListener> contentChangeListeners;

    @Override
    @Transactional(readOnly = true)
    public ItineraryResp getItinerary(String tripId) {
        String userId = LoginUserContext.requireUserId();
        requireReadAccess(tripId, userId);

        List<TripDay> days = tripDayMapper.selectList(new QueryWrapper<TripDay>()
                .eq("trip_id", tripId)
                .orderByAsc("day_index"));
        List<ItineraryNode> nodes = itineraryNodeMapper.selectList(new QueryWrapper<ItineraryNode>()
                .eq("trip_id", tripId)
                .orderByAsc("sort_order")
                .orderByAsc("created_at"));
        Map<String, List<ItineraryNodeBriefResp>> nodesByDay = new HashMap<>();
        for (ItineraryNode node : nodes) {
            nodesByDay.computeIfAbsent(node.getTripDayId(), key -> new java.util.ArrayList<>())
                    .add(toNodeBriefResp(node));
        }
        List<ItineraryDayResp> dayResponses = days.stream()
                .map(day -> new ItineraryDayResp(
                        day.getId(),
                        day.getDayIndex(),
                        day.getDate().toString(),
                        nodesByDay.getOrDefault(day.getId(), List.of())))
                .toList();
        return new ItineraryResp(tripId, dayResponses);
    }

    @Override
    @Transactional
    public ItineraryNodeResp createNode(String tripId, String dayId, ItineraryNodeCreateReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripContentMutable(access.trip());
        TripDay targetDay = requireTripDay(tripId, dayId);

        LocalTime startTime = parseOptionalTime(req.startTime(), "startTime 格式必须为 HH:mm");
        LocalTime endTime = parseOptionalTime(req.endTime(), "endTime 格式必须为 HH:mm");
        validateTimeRange(startTime, endTime);

        String type = normalizeType(req.type());
        // SavedPlace 优先：校验归属 / scope / 目的地匹配后，以 Place 为 location 权威源
        String savedPlaceId = normalizeOptional(req.savedPlaceId());
        Place resolvedPlace = null;
        if (StringUtils.hasText(savedPlaceId)) {
            resolvedPlace = savedPlaceService.resolveForItineraryNode(userId, savedPlaceId, tripId);
        } else {
            validateRequiredLocation(req.location());
            resolvedPlace = placeService.resolveOrCreatePlace(
                    toLocationCandidate(req.location()),
                    PlaceScope.POI.name(),
                    categoryForType(type).name());
        }

        // hotel + checkOutDate → Stay Group 跨天物化；否则单节点
        if (ItineraryNodeType.HOTEL.value().equals(type)) {
            return createHotelNode(
                    tripId, targetDay, req, access, userId, startTime, endTime, resolvedPlace, savedPlaceId);
        }

        String detailsJson = ItineraryNodeDetailsSupport.normalizeForWrite(type, req.details());
        ItineraryNode node = buildBaseNode(tripId, dayId, userId, req, type, startTime, endTime, detailsJson);
        applyResolvedPlace(node, resolvedPlace, savedPlaceId);

        node.setSortOrder(nextSortOrder(tripId, dayId, userId));
        itineraryNodeMapper.insert(node);

        if (StringUtils.hasText(savedPlaceId)) {
            savedPlaceService.recordNodeUsage(userId, savedPlaceId, tripId, node.getId());
        }

        recordActivity(tripId, userId, memberId(access), "ITINERARY_NODE_CREATED",
                "ITINERARY_NODE", node.getId(),
                changes(change("title", null, node.getTitle()), change("type", null, node.getType()),
                        change("details", null, summarizeDetails(detailsJson))),
                "创建了行程节点");
        return toNodeResp(node);
    }

    /**
     * 创建住宿节点。若 details.checkOutDate 存在且晚于入住日，则物化 Stay Group：
     * 在 [checkInDate, checkOutDate) 每天各一条节点，离店当天不生成；均置底。
     * resolvedPlace 非空时用 Place 快照覆盖 location（含 hotel + savedPlaceId 路径）。
     */
    private ItineraryNodeResp createHotelNode(
            String tripId,
            TripDay checkInDay,
            ItineraryNodeCreateReq req,
            TripAccessService.TripAccess access,
            String userId,
            LocalTime startTime,
            LocalTime endTime,
            Place resolvedPlace,
            String savedPlaceId) {
        Map<String, Object> userDetails = ItineraryNodeDetailsSupport.parseMap(
                ItineraryNodeDetailsSupport.normalizeForWrite(
                        ItineraryNodeType.HOTEL.value(),
                        req.details() == null ? Map.of() : req.details()));
        LocalDate checkInDate = checkInDay.getDate();
        LocalDate clientCheckIn = ItineraryNodeDetailsSupport.parseDateField(userDetails, "checkInDate");
        if (clientCheckIn != null && !clientCheckIn.equals(checkInDate)) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID,
                    "details.checkInDate 必须与所选行程日一致");
        }
        LocalDate checkOutDate = ItineraryNodeDetailsSupport.parseDateField(userDetails, "checkOutDate");

        if (checkOutDate == null) {
            // Phase 1 兼容：单日住宿，无 stay group
            String detailsJson = ItineraryNodeDetailsSupport.toJson(userDetails);
            ItineraryNode node = buildBaseNode(
                    tripId, checkInDay.getId(), userId, req,
                    ItineraryNodeType.HOTEL.value(), startTime, endTime, detailsJson);
            applyResolvedPlace(node, resolvedPlace, savedPlaceId);
            node.setSortOrder(nextSortOrder(tripId, checkInDay.getId(), userId));
            itineraryNodeMapper.insert(node);
            if (StringUtils.hasText(savedPlaceId)) {
                savedPlaceService.recordNodeUsage(userId, savedPlaceId, tripId, node.getId());
            }
            recordActivity(tripId, userId, memberId(access), "ITINERARY_NODE_CREATED",
                    "ITINERARY_NODE", node.getId(),
                    changes(change("title", null, node.getTitle()), change("type", null, "hotel")),
                    "创建了住宿节点");
            return toNodeResp(node);
        }

        List<TripDay> stayDays = resolveStayDays(access.trip(), tripId, checkInDate, checkOutDate);
        String stayGroupId = UUID.randomUUID().toString();
        int totalNights = stayDays.size();
        ItineraryNode primary = null;

        for (int i = 0; i < stayDays.size(); i++) {
            TripDay day = stayDays.get(i);
            String stayRole = i == 0
                    ? ItineraryNodeDetailsSupport.STAY_ROLE_CHECK_IN
                    : ItineraryNodeDetailsSupport.STAY_ROLE_STAY;
            String detailsJson = ItineraryNodeDetailsSupport.applyStayServerFields(
                    userDetails, stayGroupId, stayRole, checkInDate, checkOutDate, i + 1, totalNights);
            // 入住日保留用户 start/end；中间夜时间清空（当晚住宿锚点，置底展示）
            LocalTime nodeStart = i == 0 ? startTime : null;
            LocalTime nodeEnd = i == 0 ? endTime : null;
            ItineraryNode node = buildBaseNode(
                    tripId, day.getId(), userId, req,
                    ItineraryNodeType.HOTEL.value(), nodeStart, nodeEnd, detailsJson);
            applyResolvedPlace(node, resolvedPlace, savedPlaceId);
            node.setSortOrder(nextSortOrder(tripId, day.getId(), userId));
            itineraryNodeMapper.insert(node);
            if (i == 0) {
                primary = node;
            }
        }

        if (primary != null && StringUtils.hasText(savedPlaceId)) {
            // Stay Group 仅对 primary（入住日）记一次 usage
            savedPlaceService.recordNodeUsage(userId, savedPlaceId, tripId, primary.getId());
        }

        recordActivity(tripId, userId, memberId(access), "ITINERARY_NODE_CREATED",
                "ITINERARY_NODE", primary.getId(),
                changes(
                        change("title", null, primary.getTitle()),
                        change("type", null, "hotel"),
                        change("stayGroupId", null, stayGroupId),
                        change("checkOutDate", null, checkOutDate.toString()),
                        change("totalNights", null, String.valueOf(totalNights))),
                "创建了住宿（" + totalNights + " 晚）");
        return toNodeResp(primary);
    }

    private ItineraryNode buildBaseNode(
            String tripId,
            String dayId,
            String userId,
            ItineraryNodeCreateReq req,
            String type,
            LocalTime startTime,
            LocalTime endTime,
            String detailsJson) {
        ItineraryNode node = new ItineraryNode();
        node.setTripId(tripId);
        node.setTripDayId(dayId);
        node.setUserId(userId);
        node.setTitle(normalizeTitle(req.title()));
        node.setType(type);
        node.setStartTime(startTime);
        node.setEndTime(endTime);
        node.setAddress(normalizeOptional(req.address()));
        applyLocationForCreate(node, req.location());
        node.setNote(normalizeOptional(req.note()));
        node.setDetailsJson(detailsJson);
        return node;
    }

    @Override
    @Transactional
    public ItineraryNodeResp updateNode(String tripId, String dayId, String nodeId, ItineraryNodeUpdateReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripDay(tripId, dayId);
        ItineraryNode node = requireNode(tripId, dayId, nodeId);

        // 已结束旅程节点仅允许修改备注：备注是非结构化轻量补充，不影响结构与状态；
        // 其余字段（title/type/startTime/endTime/address/location/details）在旅程已结束后必须保持不变。
        if (isTripCompleted(access.trip())
                && (req.title() != null || req.type() != null || req.startTime() != null
                || req.endTime() != null || req.address() != null || req.location() != null
                || req.details() != null)) {
            throw new BizException(ErrorCode.ITINERARY_COMPLETED_NODE_NOTE_ONLY);
        }

        String oldTitle = node.getTitle();
        String oldType = node.getType();
        String oldStartTime = node.getStartTime() == null ? null : node.getStartTime().toString();
        String oldEndTime = node.getEndTime() == null ? null : node.getEndTime().toString();
        String oldAddress = node.getAddress();
        String oldNote = node.getNote();
        String oldDetails = summarizeDetails(node.getDetailsJson());

        if (req.title() != null) {
            // title 可选：空字符串直接写入，展示层兜底。
            node.setTitle(normalizeTitle(req.title()));
        }
        if (req.type() != null) {
            node.setType(normalizeType(req.type()));
        }
        if (req.startTime() != null) {
            node.setStartTime(parseOptionalTime(req.startTime(), "startTime 格式必须为 HH:mm"));
        }
        if (req.endTime() != null) {
            node.setEndTime(parseOptionalTime(req.endTime(), "endTime 格式必须为 HH:mm"));
        }
        validateTimeRange(node.getStartTime(), node.getEndTime());
        if (req.address() != null) {
            node.setAddress(normalizeOptional(req.address()));
        }
        if (req.location() != null) {
            applyLocationForUpdate(node, req.location());
        }
        // note: null = 不修改；非 null（含 ""）= 更新，空串经 normalizeOptional 清空
        if (req.note() != null) {
            node.setNote(normalizeOptional(req.note()));
        }
        Map<String, Object> previousDetails = ItineraryNodeDetailsSupport.parseMap(node.getDetailsJson());
        String previousStayGroupId = ItineraryNodeDetailsSupport.stringField(previousDetails, "stayGroupId");

        // details: null = 不修改；非 null（含 {}）= 整体替换（相对最终 type 校验）
        if (req.details() != null) {
            node.setDetailsJson(ItineraryNodeDetailsSupport.normalizeForWrite(node.getType(), req.details()));
        } else if (req.type() != null) {
            // 改 type 且未传 details：kind 与 type 不兼容时清空，避免脏数据
            reconcileDetailsAfterTypeChange(node);
        }

        // 从 hotel stay group 改成其他类型：先拆组（删投影），当前节点清空 stay 字段
        if (req.type() != null
                && ItineraryNodeType.HOTEL.value().equals(oldType)
                && !ItineraryNodeType.HOTEL.value().equals(node.getType())
                && StringUtils.hasText(previousStayGroupId)) {
            dissolveStayGroupKeepNode(tripId, previousStayGroupId, node.getId());
            node.setDetailsJson(ItineraryNodeDetailsSupport.EMPTY_JSON);
        }

        int updated = itineraryNodeMapper.updateById(node);
        if (updated == 0) {
            throw new NotFoundException(ErrorCode.ITINERARY_NOT_FOUND);
        }

        // hotel stay 同步 / 物化
        if (ItineraryNodeType.HOTEL.value().equals(node.getType())) {
            node = syncHotelStayAfterUpdate(access.trip(), tripId, node, previousStayGroupId, userId);
        }

        recordActivity(tripId, userId, memberId(access), "ITINERARY_NODE_UPDATED",
                "ITINERARY_NODE", node.getId(),
                changes(
                        change("title", oldTitle, node.getTitle()),
                        change("type", oldType, node.getType()),
                        change("startTime", oldStartTime, node.getStartTime() == null ? null : node.getStartTime().toString()),
                        change("endTime", oldEndTime, node.getEndTime() == null ? null : node.getEndTime().toString()),
                        change("address", oldAddress, node.getAddress()),
                        change("note", oldNote, node.getNote()),
                        change("details", oldDetails, summarizeDetails(node.getDetailsJson()))),
                "更新了行程节点");
        return toNodeResp(node);
    }

    @Override
    @Transactional
    public void deleteNode(String tripId, String dayId, String nodeId) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripContentMutable(access.trip());
        requireTripDay(tripId, dayId);
        ItineraryNode node = requireNode(tripId, dayId, nodeId);

        Map<String, Object> details = ItineraryNodeDetailsSupport.parseMap(node.getDetailsJson());
        String stayGroupId = ItineraryNodeDetailsSupport.stringField(details, "stayGroupId");
        String stayRole = ItineraryNodeDetailsSupport.stringField(details, "stayRole");

        if (StringUtils.hasText(stayGroupId)
                && ItineraryNodeDetailsSupport.STAY_ROLE_STAY.equals(stayRole)) {
            throw new BizException(ErrorCode.ITINERARY_STAY_PROJECTION_DELETE_FORBIDDEN);
        }

        if (StringUtils.hasText(stayGroupId)
                && ItineraryNodeDetailsSupport.STAY_ROLE_CHECK_IN.equals(stayRole)) {
            int deleted = deleteStayGroup(tripId, stayGroupId);
            recordActivity(tripId, userId, memberId(access), "ITINERARY_NODE_DELETED",
                    "ITINERARY_NODE", nodeId,
                    List.of(new TripActivityLogResp.FieldChange("stayGroupId", stayGroupId, null),
                            new TripActivityLogResp.FieldChange("deletedCount", null, String.valueOf(deleted))),
                    "删除了整段住宿");
            return;
        }

        int deleted = itineraryNodeMapper.delete(new UpdateWrapper<ItineraryNode>()
                .eq("id", nodeId)
                .eq("trip_id", tripId)
                .eq("trip_day_id", dayId));
        if (deleted == 0) {
            throw new NotFoundException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        clearExpenseNodeLinks(tripId, nodeId);
        recordActivity(tripId, userId, memberId(access), "ITINERARY_NODE_DELETED",
                "ITINERARY_NODE", nodeId, List.of(), "删除了行程节点");
    }

    @Override
    @Transactional
    public void reorderNodes(String tripId, String dayId, ItineraryNodeReorderReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripContentMutable(access.trip());
        requireTripDay(tripId, dayId);
        List<String> requestedIds = req.nodeIds();
        if (requestedIds == null || requestedIds.isEmpty()) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "nodeIds 不能为空");
        }
        if (new HashSet<>(requestedIds).size() != requestedIds.size()) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "nodeIds 不能包含重复节点");
        }

        List<ItineraryNode> existingNodes = itineraryNodeMapper.selectList(new QueryWrapper<ItineraryNode>()
                .eq("trip_id", tripId)
                .eq("trip_day_id", dayId));
        List<String> existingIds = existingNodes.stream().map(ItineraryNode::getId).toList();
        if (existingIds.size() != requestedIds.size() || !new HashSet<>(existingIds).equals(new HashSet<>(requestedIds))) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "nodeIds 必须包含该天全部未删除节点，且不能包含其他节点");
        }

        for (int index = 0; index < requestedIds.size(); index++) {
            itineraryNodeMapper.update(null, new UpdateWrapper<ItineraryNode>()
                    .eq("id", requestedIds.get(index))
                    .eq("trip_id", tripId)
                    .eq("trip_day_id", dayId)
                    .set("sort_order", (index + 1) * SORT_STEP));
        }
        recordActivity(tripId, userId, memberId(access), "ITINERARY_NODES_REORDERED",
                "ITINERARY_NODE", dayId,
                List.of(new TripActivityLogResp.FieldChange("nodeIds", null, String.join(",", requestedIds))),
                "调整了行程排序");
    }

    private TripDay requireTripDay(String tripId, String dayId) {
        TripDay day = tripDayMapper.selectOne(new QueryWrapper<TripDay>()
                .eq("id", dayId)
                .eq("trip_id", tripId));
        if (day == null) {
            throw new NotFoundException(ErrorCode.TRIP_NOT_FOUND, "行程天不存在");
        }
        return day;
    }

    private ItineraryNode requireNode(String tripId, String dayId, String nodeId) {
        ItineraryNode node = itineraryNodeMapper.selectOne(new QueryWrapper<ItineraryNode>()
                .eq("id", nodeId)
                .eq("trip_id", tripId)
                .eq("trip_day_id", dayId));
        if (node == null) {
            throw new NotFoundException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        return node;
    }

    private int nextSortOrder(String tripId, String dayId, String userId) {
        Integer maxSortOrder = itineraryNodeMapper.selectMaxSortOrderByTripDay(tripId, dayId);
        if (maxSortOrder == null || maxSortOrder == 0) {
            Integer legacyMaxSortOrder = itineraryNodeMapper.selectMaxSortOrder(tripId, dayId, userId);
            if (legacyMaxSortOrder != null) {
                maxSortOrder = legacyMaxSortOrder;
            }
        }
        return (maxSortOrder == null ? 0 : maxSortOrder) + SORT_STEP;
    }

    private TripAccessService.TripAccess requireReadAccess(String tripId, String userId) {
        return tripAccessService.requirePermission(tripId, userId, TripPermission.READ);
    }

    private TripAccessService.TripAccess requireWriteAccess(String tripId, String userId) {
        return tripAccessService.requirePermission(tripId, userId, TripPermission.EDIT_CONTENT);
    }

    /** 已结束旅程禁止结构性变更（创建/删除/重排节点）。备注更新走 note-only 分支，不经此守卫。 */
    private void requireTripContentMutable(Trip trip) {
        if (isTripCompleted(trip)) {
            throw new BizException(ErrorCode.TRIP_COMPLETED_CONTENT_LOCKED);
        }
    }

    private static boolean isTripCompleted(Trip trip) {
        if (trip == null) {
            return false;
        }
        return TripStatus.isCompleted(DateTimeUtil.todayBusiness(), trip.getStartDate(), trip.getEndDate());
    }

    private String memberId(TripAccessService.TripAccess access) {
        return access != null && access.member() != null ? access.member().getId() : null;
    }

    private void recordActivity(String tripId, String userId, String memberId, String action,
                                String targetType, String targetId,
                                List<TripActivityLogResp.FieldChange> changes, String summary) {
        if (activityLogService != null) {
            activityLogService.record(tripId, userId, memberId, action, targetType, targetId, changes, summary);
        }
        notifyTripContentChanged(tripId);
    }

    /**
     * 行程内容已变。所有写节点 / 写住宿的路径都会经过 recordActivity，所以统一在这里通知，
     * 避免在四个方法里各挂一次。实现方负责异步化。
     */
    private void notifyTripContentChanged(String tripId) {
        if (tripId == null || contentChangeListeners == null || contentChangeListeners.isEmpty()) {
            return;
        }
        String source = com.tracci.module.trip.support.ItineraryWriteContext.currentSource();
        for (com.tracci.module.trip.support.TripContentChangeListener listener : contentChangeListeners) {
            listener.onItineraryChanged(tripId, source);
        }
    }

    private List<TripActivityLogResp.FieldChange> changes(TripActivityLogResp.FieldChange... changes) {
        return java.util.Arrays.stream(changes)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private TripActivityLogResp.FieldChange change(String field, Object oldValue, Object newValue) {
        String oldText = oldValue == null ? null : oldValue.toString();
        String newText = newValue == null ? null : newValue.toString();
        return java.util.Objects.equals(oldText, newText) ? null : new TripActivityLogResp.FieldChange(field, oldText, newText);
    }

    /**
     * title 可选。空字符串入库，展示层自行用地点名称兜底。仅做长度校验。
     */
    private String normalizeTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return "";
        }
        String value = title.trim();
        if (value.length() > 100) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "title 长度不能超过 100");
        }
        return value;
    }

    /**
     * 校验必填的地点：location 对象必须存在且携带有效 name。
     */
    private void validateRequiredLocation(ItineraryNodeLocationReq location) {
        if (location == null) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "地点不能为空");
        }
        if (!StringUtils.hasText(location.name())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "地点名称不能为空");
        }
    }

    private String normalizeType(String type) {
        if (!StringUtils.hasText(type)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "type 不能为空");
        }
        try {
            return ItineraryNodeType.fromValue(type.trim()).value();
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "type 仅支持 attraction / restaurant / transport / hotel / shopping / other");
        }
    }

    private LocalTime parseOptionalTime(String value, String message) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FMT);
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, message);
        }
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "endTime 必须大于等于 startTime");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private void applyLocationForCreate(ItineraryNode node, ItineraryNodeLocationReq location) {
        // validateRequiredLocation 已保证 location.name 非空，hasLocationContent 必然为 true。
        if (location != null && hasLocationContent(location)) {
            setLocationFields(node, location);
        }
    }

    private void applyLocationForUpdate(ItineraryNode node, ItineraryNodeLocationReq location) {
        if (!hasLocationContent(location)) {
            clearLocationFields(node);
            return;
        }
        setLocationFields(node, location);
        Place place = placeService.resolveOrCreatePlace(
                toLocationCandidate(location),
                PlaceScope.POI.name(),
                categoryForType(node.getType()).name());
        node.setLocationPlaceId(place.getId());
    }

    private boolean hasLocationContent(ItineraryNodeLocationReq location) {
        return StringUtils.hasText(location.name())
                || StringUtils.hasText(location.address())
                || StringUtils.hasText(location.poiId())
                || StringUtils.hasText(location.providerPlaceId())
                || StringUtils.hasText(location.provider())
                || StringUtils.hasText(location.country())
                || StringUtils.hasText(location.adcode())
                || StringUtils.hasText(location.cityCode())
                || StringUtils.hasText(location.provinceName())
                || StringUtils.hasText(location.cityName())
                || StringUtils.hasText(location.districtName())
                || location.latitude() != null
                || location.longitude() != null;
    }

    private void setLocationFields(ItineraryNode node, ItineraryNodeLocationReq location) {
        String name = normalizeOptional(location.name());
        if (!StringUtils.hasText(name)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "location.name 不能为空");
        }
        node.setLocationName(name);
        node.setLocationAddress(normalizeOptional(location.address()));
        node.setLocationLatitude(location.latitude());
        node.setLocationLongitude(location.longitude());
        node.setLocationPoiId(resolveProviderPlaceId(location));
        node.setLocationProvider(normalizeProvider(location.provider()));
        node.setLocationCountry(normalizeOptional(location.country()));
        node.setLocationAdcode(normalizeOptional(location.adcode()));
        node.setLocationCityCode(normalizeOptional(location.cityCode()));
        node.setLocationProvinceName(normalizeOptional(location.provinceName()));
        node.setLocationCityName(normalizeOptional(location.cityName()));
        node.setLocationDistrictName(normalizeOptional(location.districtName()));
    }

    private String resolveProviderPlaceId(ItineraryNodeLocationReq location) {
        String providerPlaceId = normalizeOptional(location.providerPlaceId());
        if (StringUtils.hasText(providerPlaceId)) {
            return providerPlaceId;
        }
        return normalizeOptional(location.poiId());
    }

    private String normalizeProvider(String provider) {
        if (!StringUtils.hasText(provider)) {
            return LocationProvider.MANUAL.name();
        }
        String normalized = provider.trim().toUpperCase();
        try {
            return LocationProvider.valueOf(normalized).name();
        } catch (IllegalArgumentException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "location.provider 不支持");
        }
    }

    private LocationCandidate toLocationCandidate(ItineraryNodeLocationReq location) {
        return new LocationCandidate(
                location.name(),
                location.address(),
                location.provider(),
                resolveProviderPlaceId(location),
                location.country(),
                location.cityName(),
                location.latitude(),
                location.longitude(),
                location.adcode(),
                location.cityCode()
        );
    }

    private PlaceCategory categoryForType(String type) {
        return switch (ItineraryNodeType.fromValue(type)) {
            case ATTRACTION -> PlaceCategory.ATTRACTION;
            case RESTAURANT -> PlaceCategory.RESTAURANT;
            case HOTEL -> PlaceCategory.HOTEL;
            case SHOPPING -> PlaceCategory.SHOPPING;
            case TRANSPORT -> PlaceCategory.TRANSPORT;
            case OTHER -> PlaceCategory.OTHER;
        };
    }

    private void clearLocationFields(ItineraryNode node) {
        node.setLocationName(null);
        node.setLocationAddress(null);
        node.setLocationLatitude(null);
        node.setLocationLongitude(null);
        node.setLocationPoiId(null);
        node.setLocationProvider(null);
        node.setLocationCountry(null);
        node.setLocationAdcode(null);
        node.setLocationCityCode(null);
        node.setLocationProvinceName(null);
        node.setLocationCityName(null);
        node.setLocationDistrictName(null);
        node.setLocationPlaceId(null);
    }

    /**
     * 从 Place 实体填充 ItineraryNode 的 location 字段，并设置 locationPlaceId。
     * 若 Place 带 destination_key（adcode:/cityCode:），回填到节点快照字段。
     */
    private void applyLocationFromPlace(ItineraryNode node, Place place) {
        node.setLocationName(place.getCanonicalName());
        node.setLocationAddress(place.getAddress());
        // Saved Place 创建不传顶层 address；用 Place 快照补齐，避免时间线只读 node.address 时缺地址。
        if (!StringUtils.hasText(node.getAddress())) {
            node.setAddress(place.getAddress());
        }
        node.setLocationLatitude(place.getLatitude());
        node.setLocationLongitude(place.getLongitude());
        node.setLocationPoiId(null);
        node.setLocationProvider(null);
        node.setLocationCountry(place.getCountry());
        node.setLocationAdcode(null);
        node.setLocationCityCode(null);
        String key = place.getDestinationKey();
        if (StringUtils.hasText(key)) {
            if (key.startsWith("adcode:")) {
                node.setLocationAdcode(key.substring("adcode:".length()));
            } else if (key.startsWith("cityCode:")) {
                node.setLocationCityCode(key.substring("cityCode:".length()));
            }
        }
        node.setLocationProvinceName(null);
        node.setLocationCityName(place.getCityName());
        node.setLocationDistrictName(null);
        node.setLocationPlaceId(place.getId());
    }

    private void applyResolvedPlace(ItineraryNode node, Place place, String savedPlaceId) {
        if (place == null) {
            return;
        }
        if (StringUtils.hasText(savedPlaceId)) {
            applyLocationFromPlace(node, place);
        } else {
            // 普通结构化地点保留客户端展示快照，只关联统一 Place 身份。
            node.setLocationPlaceId(place.getId());
        }
    }

    private ItineraryNodeLocationResp toLocationResp(ItineraryNode node) {
        if (!StringUtils.hasText(node.getLocationName())) {
            return null;
        }
        return new ItineraryNodeLocationResp(
                node.getLocationPoiId(),
                node.getLocationName(),
                node.getLocationAddress(),
                node.getLocationLatitude(),
                node.getLocationLongitude(),
                node.getLocationAdcode(),
                node.getLocationCityCode(),
                node.getLocationProvinceName(),
                node.getLocationCityName(),
                node.getLocationDistrictName(),
                node.getLocationProvider(),
                node.getLocationPoiId(),
                node.getLocationCountry(),
                node.getLocationPlaceId()
        );
    }

    private ItineraryNodeResp toNodeResp(ItineraryNode node) {
        return new ItineraryNodeResp(
                node.getId(),
                node.getTripDayId(),
                node.getTitle(),
                node.getType(),
                DateTimeUtil.toTimeString(node.getStartTime()),
                DateTimeUtil.toTimeString(node.getEndTime()),
                node.getAddress(),
                toLocationResp(node),
                node.getNote(),
                ItineraryNodeDetailsSupport.parseForResponse(node.getDetailsJson()),
                node.getSortOrder(),
                DateTimeUtil.toInstantString(node.getCreatedAt()));
    }

    private ItineraryNodeBriefResp toNodeBriefResp(ItineraryNode node) {
        return new ItineraryNodeBriefResp(
                node.getId(),
                node.getTitle(),
                node.getType(),
                DateTimeUtil.toTimeString(node.getStartTime()),
                DateTimeUtil.toTimeString(node.getEndTime()),
                node.getAddress(),
                toLocationResp(node),
                node.getNote(),
                ItineraryNodeDetailsSupport.parseForResponse(node.getDetailsJson()),
                node.getSortOrder());
    }

    /** 活动日志摘要：过长 JSON 截断，空对象记为 null。 */
    private String summarizeDetails(String detailsJson) {
        if (ItineraryNodeDetailsSupport.isEmptyJson(detailsJson)) {
            return null;
        }
        String text = detailsJson.trim();
        return text.length() <= 200 ? text : text.substring(0, 200) + "…";
    }

    /**
     * type 变更后若未显式提交 details：不兼容则清空。
     * transport↔hotel 切换时 kind 不匹配也清空（避免 silent corrupt）。
     */
    private void reconcileDetailsAfterTypeChange(ItineraryNode node) {
        if (ItineraryNodeDetailsSupport.isEmptyJson(node.getDetailsJson())) {
            return;
        }
        String type = node.getType();
        if (!ItineraryNodeType.TRANSPORT.value().equals(type)
                && !ItineraryNodeType.HOTEL.value().equals(type)) {
            node.setDetailsJson(ItineraryNodeDetailsSupport.EMPTY_JSON);
            return;
        }
        Map<String, Object> existing = ItineraryNodeDetailsSupport.parseForResponse(node.getDetailsJson());
        if (existing == null) {
            node.setDetailsJson(ItineraryNodeDetailsSupport.EMPTY_JSON);
            return;
        }
        Object kind = existing.get("kind");
        if (kind == null || !type.equals(String.valueOf(kind))) {
            node.setDetailsJson(ItineraryNodeDetailsSupport.EMPTY_JSON);
        }
    }

    // ---- Hotel Stay Group (Phase 2) ----

    /**
     * 更新后同步 stay group：
     * <ul>
     *   <li>有 checkOutDate：物化/调整 [checkIn, checkOut) 每天节点，共享字段同步</li>
     *   <li>无 checkOutDate：若原有 group 则拆成单节点</li>
     * </ul>
     */
    private ItineraryNode syncHotelStayAfterUpdate(
            Trip trip,
            String tripId,
            ItineraryNode node,
            String previousStayGroupId,
            String userId) {
        Map<String, Object> userDetails = ItineraryNodeDetailsSupport.extractUserHotelFields(
                ItineraryNodeDetailsSupport.parseMap(node.getDetailsJson()));
        LocalDate checkOutDate = ItineraryNodeDetailsSupport.parseDateField(userDetails, "checkOutDate");

        // 解析入住日：优先已有 group 的 check_in 天；否则当前节点所属天
        StayGroupContext ctx = resolveStayGroupContext(tripId, node, previousStayGroupId);
        LocalDate checkInDate = ctx.checkInDate();

        if (checkOutDate == null) {
            if (StringUtils.hasText(ctx.stayGroupId())) {
                // 去掉离店日：删除投影，当前节点保留为单日住宿
                dissolveStayGroupKeepNode(tripId, ctx.stayGroupId(), ctx.primaryNodeId());
                // 确保返回的是 primary 节点上的最新状态
                ItineraryNode primary = itineraryNodeMapper.selectById(ctx.primaryNodeId());
                if (primary != null) {
                    primary.setTitle(node.getTitle());
                    primary.setAddress(node.getAddress());
                    primary.setNote(node.getNote());
                    primary.setStartTime(node.getStartTime());
                    primary.setEndTime(node.getEndTime());
                    copyLocation(node, primary);
                    primary.setDetailsJson(ItineraryNodeDetailsSupport.toJson(userDetails));
                    itineraryNodeMapper.updateById(primary);
                    return primary;
                }
            }
            // 无 group 的单日更新：确保 details 无 stay 字段
            node.setDetailsJson(ItineraryNodeDetailsSupport.toJson(userDetails));
            itineraryNodeMapper.updateById(node);
            return node;
        }

        List<TripDay> stayDays = resolveStayDays(trip, tripId, checkInDate, checkOutDate);
        String stayGroupId = StringUtils.hasText(ctx.stayGroupId())
                ? ctx.stayGroupId()
                : UUID.randomUUID().toString();
        int totalNights = stayDays.size();

        Map<String, ItineraryNode> existingByDayId = findStayGroupNodes(tripId, stayGroupId).stream()
                .collect(Collectors.toMap(ItineraryNode::getTripDayId, n -> n, (a, b) -> a, LinkedHashMap::new));
        // 若此前无 group，把当前节点挂到入住日
        if (existingByDayId.isEmpty()) {
            existingByDayId.put(node.getTripDayId(), node);
        }

        ItineraryNode primary = null;
        HashSet<String> desiredDayIds = new HashSet<>();

        for (int i = 0; i < stayDays.size(); i++) {
            TripDay day = stayDays.get(i);
            desiredDayIds.add(day.getId());
            String stayRole = i == 0
                    ? ItineraryNodeDetailsSupport.STAY_ROLE_CHECK_IN
                    : ItineraryNodeDetailsSupport.STAY_ROLE_STAY;
            String detailsJson = ItineraryNodeDetailsSupport.applyStayServerFields(
                    userDetails, stayGroupId, stayRole, checkInDate, checkOutDate, i + 1, totalNights);

            ItineraryNode existing = existingByDayId.get(day.getId());
            if (existing != null) {
                applySharedHotelFields(node, existing);
                existing.setDetailsJson(detailsJson);
                if (i == 0) {
                    existing.setStartTime(node.getStartTime());
                    existing.setEndTime(node.getEndTime());
                } else {
                    existing.setStartTime(null);
                    existing.setEndTime(null);
                }
                itineraryNodeMapper.updateById(existing);
                if (i == 0) {
                    primary = existing;
                }
            } else {
                ItineraryNode created = new ItineraryNode();
                created.setTripId(tripId);
                created.setTripDayId(day.getId());
                created.setUserId(userId);
                created.setTitle(node.getTitle());
                created.setType(ItineraryNodeType.HOTEL.value());
                created.setStartTime(i == 0 ? node.getStartTime() : null);
                created.setEndTime(i == 0 ? node.getEndTime() : null);
                created.setAddress(node.getAddress());
                copyLocation(node, created);
                created.setNote(node.getNote());
                created.setDetailsJson(detailsJson);
                created.setSortOrder(nextSortOrder(tripId, day.getId(), userId));
                itineraryNodeMapper.insert(created);
                if (i == 0) {
                    primary = created;
                }
            }
        }

        // 删除不再需要的投影天
        for (ItineraryNode existing : existingByDayId.values()) {
            if (!desiredDayIds.contains(existing.getTripDayId())) {
                itineraryNodeMapper.deleteById(existing.getId());
            }
        }

        // 优先返回被编辑节点（若仍在 group 内）；否则返回入住日主节点
        ItineraryNode refreshed = itineraryNodeMapper.selectById(node.getId());
        if (refreshed != null) {
            return refreshed;
        }
        if (primary != null) {
            ItineraryNode p = itineraryNodeMapper.selectById(primary.getId());
            if (p != null) {
                return p;
            }
        }
        return node;
    }

    private record StayGroupContext(
            String stayGroupId,
            String primaryNodeId,
            LocalDate checkInDate
    ) {
    }

    private StayGroupContext resolveStayGroupContext(
            String tripId,
            ItineraryNode node,
            String previousStayGroupId) {
        String groupId = previousStayGroupId;
        Map<String, Object> details = ItineraryNodeDetailsSupport.parseMap(node.getDetailsJson());
        if (!StringUtils.hasText(groupId)) {
            groupId = ItineraryNodeDetailsSupport.stringField(details, "stayGroupId");
        }

        if (StringUtils.hasText(groupId)) {
            List<ItineraryNode> members = findStayGroupNodes(tripId, groupId);
            ItineraryNode checkInNode = members.stream()
                    .filter(n -> ItineraryNodeDetailsSupport.STAY_ROLE_CHECK_IN.equals(
                            ItineraryNodeDetailsSupport.stringField(
                                    ItineraryNodeDetailsSupport.parseMap(n.getDetailsJson()), "stayRole")))
                    .findFirst()
                    .orElse(members.isEmpty() ? node : members.getFirst());
            TripDay day = tripDayMapper.selectById(checkInNode.getTripDayId());
            LocalDate checkInDate = day != null ? day.getDate() : LocalDate.now();
            // details 内 checkInDate 优先
            LocalDate fromDetails = ItineraryNodeDetailsSupport.parseDateField(
                    ItineraryNodeDetailsSupport.parseMap(checkInNode.getDetailsJson()), "checkInDate");
            if (fromDetails != null) {
                checkInDate = fromDetails;
            }
            return new StayGroupContext(groupId, checkInNode.getId(), checkInDate);
        }

        TripDay day = tripDayMapper.selectById(node.getTripDayId());
        LocalDate checkInDate = day != null ? day.getDate() : LocalDate.now();
        return new StayGroupContext(null, node.getId(), checkInDate);
    }

    private List<TripDay> resolveStayDays(
            Trip trip,
            String tripId,
            LocalDate checkInDate,
            LocalDate checkOutDate) {
        if (checkOutDate == null || !checkOutDate.isAfter(checkInDate)) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID,
                    "离店日期必须晚于入住日期");
        }
        if (trip.getEndDate() != null && checkOutDate.isAfter(trip.getEndDate().plusDays(1))) {
            // 离店日可以等于 trip.endDate+1（最后一晚住到结束次日离店）
            // 更严格：离店日 <= trip.endDate + 1 且 最后一晚 checkOut-1 <= trip.endDate
        }
        if (trip.getEndDate() != null && checkInDate.isAfter(trip.getEndDate())) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID, "入住日期超出旅程范围");
        }
        // 住宿覆盖的夜晚：checkIn .. checkOut-1，这些日期必须在旅程天数内
        LocalDate lastNight = checkOutDate.minusDays(1);
        if (trip.getStartDate() != null && checkInDate.isBefore(trip.getStartDate())) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID, "入住日期早于旅程开始");
        }
        if (trip.getEndDate() != null && lastNight.isAfter(trip.getEndDate())) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID,
                    "离店日期过晚：最后一晚不能晚于旅程结束日");
        }

        List<TripDay> allDays = tripDayMapper.selectList(new QueryWrapper<TripDay>()
                .eq("trip_id", tripId)
                .orderByAsc("day_index"));
        List<TripDay> stayDays = allDays.stream()
                .filter(d -> d.getDate() != null
                        && !d.getDate().isBefore(checkInDate)
                        && d.getDate().isBefore(checkOutDate))
                .toList();
        if (stayDays.isEmpty()) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID, "所选住宿日期范围内没有行程天");
        }
        if (!stayDays.getFirst().getDate().equals(checkInDate)) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID, "入住日必须在旅程行程天中");
        }
        // 校验范围内每一天都有 trip_day
        LocalDate cursor = checkInDate;
        int expected = 0;
        while (cursor.isBefore(checkOutDate)) {
            expected++;
            cursor = cursor.plusDays(1);
        }
        if (stayDays.size() != expected) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID,
                    "住宿日期范围内存在缺失的行程天");
        }
        return stayDays;
    }

    private List<ItineraryNode> findStayGroupNodes(String tripId, String stayGroupId) {
        if (!StringUtils.hasText(stayGroupId)) {
            return List.of();
        }
        return itineraryNodeMapper.selectList(new QueryWrapper<ItineraryNode>()
                .eq("trip_id", tripId)
                .eq("type", ItineraryNodeType.HOTEL.value())
                .apply("details_json->>'stayGroupId' = {0}", stayGroupId)
                .orderByAsc("created_at"));
    }

    private int deleteStayGroup(String tripId, String stayGroupId) {
        List<ItineraryNode> members = findStayGroupNodes(tripId, stayGroupId);
        int count = 0;
        for (ItineraryNode member : members) {
            count += itineraryNodeMapper.deleteById(member.getId());
        }
        clearExpenseStayGroupLinks(tripId, stayGroupId);
        return count;
    }

    /** 删除 group 中除 keepNodeId 以外的节点（用于拆组）。 */
    private void dissolveStayGroupKeepNode(String tripId, String stayGroupId, String keepNodeId) {
        for (ItineraryNode member : findStayGroupNodes(tripId, stayGroupId)) {
            if (!Objects.equals(member.getId(), keepNodeId)) {
                itineraryNodeMapper.deleteById(member.getId());
            }
        }
        retargetExpenseStayGroupLinks(tripId, stayGroupId, keepNodeId);
    }

    private void clearExpenseNodeLinks(String tripId, String nodeId) {
        if (expenseMapper != null && StringUtils.hasText(nodeId)) {
            expenseMapper.clearLinkedNodeId(tripId, nodeId);
        }
    }

    private void clearExpenseStayGroupLinks(String tripId, String stayGroupId) {
        if (expenseMapper != null && StringUtils.hasText(stayGroupId)) {
            expenseMapper.clearLinkedStayGroupId(tripId, stayGroupId);
        }
    }

    private void retargetExpenseStayGroupLinks(String tripId, String stayGroupId, String keepNodeId) {
        if (expenseMapper != null && StringUtils.hasText(stayGroupId) && StringUtils.hasText(keepNodeId)) {
            expenseMapper.retargetStayGroupToNode(tripId, stayGroupId, keepNodeId);
        }
    }

    private void applySharedHotelFields(ItineraryNode source, ItineraryNode target) {
        target.setTitle(source.getTitle());
        target.setAddress(source.getAddress());
        target.setNote(source.getNote());
        copyLocation(source, target);
    }

    private void copyLocation(ItineraryNode source, ItineraryNode target) {
        target.setLocationName(source.getLocationName());
        target.setLocationAddress(source.getLocationAddress());
        target.setLocationLatitude(source.getLocationLatitude());
        target.setLocationLongitude(source.getLocationLongitude());
        target.setLocationPoiId(source.getLocationPoiId());
        target.setLocationProvider(source.getLocationProvider());
        target.setLocationCountry(source.getLocationCountry());
        target.setLocationAdcode(source.getLocationAdcode());
        target.setLocationCityCode(source.getLocationCityCode());
        target.setLocationProvinceName(source.getLocationProvinceName());
        target.setLocationCityName(source.getLocationCityName());
        target.setLocationDistrictName(source.getLocationDistrictName());
        target.setLocationPlaceId(source.getLocationPlaceId());
    }

    /**
     * Phase 3：旅程改期后裁剪 hotel Stay Group。
     * <ul>
     *   <li>入住日节点已被改期删除 → 删除整段 group</li>
     *   <li>checkOut 越过新旅程末尾 → 裁剪到 endDate+1</li>
     *   <li>重新物化投影并校正 checkInDate/checkOutDate/nightIndex</li>
     * </ul>
     */
    @Override
    @Transactional
    public void reconcileHotelStaysAfterTripDateChange(String tripId) {
        Trip trip = tripMapper.selectById(tripId);
        if (trip == null || trip.getStartDate() == null || trip.getEndDate() == null) {
            return;
        }
        List<ItineraryNode> hotelNodes = itineraryNodeMapper.selectList(new QueryWrapper<ItineraryNode>()
                .eq("trip_id", tripId)
                .eq("type", ItineraryNodeType.HOTEL.value())
                .apply("jsonb_exists(details_json, 'stayGroupId')"));
        if (hotelNodes.isEmpty()) {
            return;
        }

        Map<String, List<ItineraryNode>> byGroup = new LinkedHashMap<>();
        for (ItineraryNode node : hotelNodes) {
            String groupId = ItineraryNodeDetailsSupport.stringField(
                    ItineraryNodeDetailsSupport.parseMap(node.getDetailsJson()), "stayGroupId");
            if (!StringUtils.hasText(groupId)) {
                continue;
            }
            byGroup.computeIfAbsent(groupId, k -> new ArrayList<>()).add(node);
        }

        List<TripDay> allDays = tripDayMapper.selectList(new QueryWrapper<TripDay>()
                .eq("trip_id", tripId)
                .orderByAsc("day_index"));
        Map<String, TripDay> dayById = allDays.stream()
                .collect(Collectors.toMap(TripDay::getId, d -> d, (a, b) -> a));

        for (Map.Entry<String, List<ItineraryNode>> entry : byGroup.entrySet()) {
            String stayGroupId = entry.getKey();
            List<ItineraryNode> members = entry.getValue();
            ItineraryNode checkInNode = members.stream()
                    .filter(n -> ItineraryNodeDetailsSupport.STAY_ROLE_CHECK_IN.equals(
                            ItineraryNodeDetailsSupport.stringField(
                                    ItineraryNodeDetailsSupport.parseMap(n.getDetailsJson()), "stayRole")))
                    .findFirst()
                    .orElse(null);

            // 入住主节点已不在（随超范围天 soft-delete）→ 清掉残留投影
            if (checkInNode == null || !dayById.containsKey(checkInNode.getTripDayId())) {
                deleteStayGroup(tripId, stayGroupId);
                continue;
            }

            TripDay checkInDay = dayById.get(checkInNode.getTripDayId());
            LocalDate checkInDate = checkInDay.getDate();
            Map<String, Object> userDetails = ItineraryNodeDetailsSupport.extractUserHotelFields(
                    ItineraryNodeDetailsSupport.parseMap(checkInNode.getDetailsJson()));
            LocalDate desiredCheckOut = ItineraryNodeDetailsSupport.parseDateField(userDetails, "checkOutDate");
            if (desiredCheckOut == null) {
                desiredCheckOut = checkInDate.plusDays(1);
            }
            // 最后一晚 ≤ trip.endDate → checkOut ≤ endDate+1
            LocalDate maxCheckOut = trip.getEndDate().plusDays(1);
            if (desiredCheckOut.isAfter(maxCheckOut)) {
                desiredCheckOut = maxCheckOut;
            }
            if (!desiredCheckOut.isAfter(checkInDate)) {
                // 无法保留任何完整夜晚：整段删除
                deleteStayGroup(tripId, stayGroupId);
                continue;
            }

            userDetails.put("checkOutDate", desiredCheckOut.toString());
            userDetails.put("checkInDate", checkInDate.toString());
            checkInNode.setDetailsJson(ItineraryNodeDetailsSupport.toJson(userDetails));
            // 以主节点为模板重新物化（校正日期 + 删/补投影）
            syncHotelStayAfterUpdate(trip, tripId, checkInNode, stayGroupId, checkInNode.getUserId());
        }
    }

}
