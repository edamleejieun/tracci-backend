package com.tracci.module.trip.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tracci.common.exception.BizException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.trip.enums.ItineraryNodeType;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * itinerary_nodes.details_json 规范化与校验（Phase 1 + Phase 2 hotel stay）。
 *
 * <p>Update 语义：请求 details=null 表示不修改；非 null（含 {}）表示整体替换。
 * <p>住宿跨天字段：
 * <ul>
 *   <li>客户端可写：checkOutDate、checkInDate（可选校验）、confirmationCode、checkInTime、checkOutTime</li>
 *   <li>服务端托管：stayGroupId、stayRole、nightIndex、totalNights（客户端传入会被剥离后由服务回填）</li>
 * </ul>
 */
public final class ItineraryNodeDetailsSupport {

    public static final String EMPTY_JSON = "{}";
    public static final String KIND_TRANSPORT = "transport";
    public static final String KIND_HOTEL = "hotel";
    public static final String STAY_ROLE_CHECK_IN = "check_in";
    public static final String STAY_ROLE_STAY = "stay";

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final Set<String> TRANSPORT_MODES = Set.of(
            "flight", "train", "hsr", "bus", "driving", "other");

    private static final int MAX_SHORT = 50;
    private static final int MAX_MED = 100;
    private static final int MAX_JSON_CHARS = 4000;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private ItineraryNodeDetailsSupport() {
    }

    /**
     * 规范化 create/update 写入值（用户可控字段）。返回可直接入库的 JSON 字符串（永不为 null）。
     * 不会写入 stayGroupId/stayRole 等服务端字段——由 {@link #applyStayServerFields} 回填。
     */
    public static String normalizeForWrite(String nodeType, Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return EMPTY_JSON;
        }
        Map<String, Object> normalized = normalizeMap(nodeType, details);
        return toJson(normalized);
    }

    /**
     * 解析库内 JSON；空或非法时返回 null（响应层可省略）。
     */
    public static Map<String, Object> parseForResponse(String detailsJson) {
        Map<String, Object> map = parseMap(detailsJson);
        return map == null || map.isEmpty() ? null : map;
    }

    public static Map<String, Object> parseMap(String detailsJson) {
        if (!StringUtils.hasText(detailsJson) || EMPTY_JSON.equals(detailsJson.trim())) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = MAPPER.readValue(detailsJson, MAP_TYPE);
            return map == null ? new LinkedHashMap<>() : new LinkedHashMap<>(map);
        } catch (JsonProcessingException ex) {
            return new LinkedHashMap<>();
        }
    }

    public static boolean isEmptyJson(String detailsJson) {
        return !StringUtils.hasText(detailsJson) || EMPTY_JSON.equals(detailsJson.trim());
    }

    public static String stringField(Map<String, Object> details, String key) {
        if (details == null) {
            return null;
        }
        Object value = details.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    public static LocalDate parseDateField(Map<String, Object> details, String key) {
        String text = stringField(details, key);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text, DATE_FMT);
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID,
                    "details." + key + " 格式必须为 yyyy-MM-dd");
        }
    }

    public static Integer intField(Map<String, Object> details, String key) {
        if (details == null || details.get(key) == null) {
            return null;
        }
        Object value = details.get(key);
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 在用户 details 基础上回填 stay 服务端字段，返回 JSON。
     */
    public static String applyStayServerFields(
            Map<String, Object> userHotelDetails,
            String stayGroupId,
            String stayRole,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int nightIndex,
            int totalNights) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (userHotelDetails != null) {
            out.putAll(userHotelDetails);
        }
        out.put("kind", KIND_HOTEL);
        out.put("stayGroupId", stayGroupId);
        out.put("stayRole", stayRole);
        out.put("checkInDate", checkInDate.toString());
        out.put("checkOutDate", checkOutDate.toString());
        out.put("nightIndex", nightIndex);
        out.put("totalNights", totalNights);
        return toJson(out);
    }

    /** 从用户 hotel details 中提取可同步的公共字段（不含服务端托管字段）。 */
    public static Map<String, Object> extractUserHotelFields(Map<String, Object> details) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", KIND_HOTEL);
        putIfPresent(out, details, "confirmationCode");
        putIfPresent(out, details, "checkInTime");
        putIfPresent(out, details, "checkOutTime");
        putIfPresent(out, details, "checkInDate");
        putIfPresent(out, details, "checkOutDate");
        return out;
    }

    private static void putIfPresent(Map<String, Object> out, Map<String, Object> src, String key) {
        if (src == null) {
            return;
        }
        Object value = src.get(key);
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            out.put(key, value instanceof String s ? s.trim() : value);
        }
    }

    private static Map<String, Object> normalizeMap(String nodeType, Map<String, Object> raw) {
        String type = nodeType == null ? "" : nodeType.trim().toLowerCase(Locale.ROOT);
        String kind = stringValue(raw.get("kind"));
        if (!StringUtils.hasText(kind)) {
            if (ItineraryNodeType.TRANSPORT.value().equals(type)) {
                kind = KIND_TRANSPORT;
            } else if (ItineraryNodeType.HOTEL.value().equals(type)) {
                kind = KIND_HOTEL;
            } else {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                        "details 仅支持 type=transport 或 type=hotel");
            }
        } else {
            kind = kind.trim().toLowerCase(Locale.ROOT);
        }

        return switch (kind) {
            case KIND_TRANSPORT -> normalizeTransport(type, raw);
            case KIND_HOTEL -> normalizeHotel(type, raw);
            default -> throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "details.kind 仅支持 transport / hotel");
        };
    }

    private static Map<String, Object> normalizeTransport(String nodeType, Map<String, Object> raw) {
        if (!ItineraryNodeType.TRANSPORT.value().equals(nodeType)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "details.kind=transport 时 type 必须为 transport");
        }
        String mode = stringValue(raw.get("mode"));
        if (!StringUtils.hasText(mode)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "交通节点 details.mode 不能为空");
        }
        mode = mode.trim().toLowerCase(Locale.ROOT);
        if (!TRANSPORT_MODES.contains(mode)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "details.mode 仅支持 flight / train / hsr / bus / driving / other");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", KIND_TRANSPORT);
        out.put("mode", mode);
        putOptional(out, "flightNo", raw.get("flightNo"), MAX_SHORT);
        putOptional(out, "airline", raw.get("airline"), MAX_MED);
        putOptional(out, "trainNo", raw.get("trainNo"), MAX_SHORT);
        putOptional(out, "busNo", raw.get("busNo"), MAX_SHORT);
        putOptional(out, "departureName", raw.get("departureName"), MAX_MED);
        putOptional(out, "arrivalName", raw.get("arrivalName"), MAX_MED);
        putOptional(out, "seatInfo", raw.get("seatInfo"), MAX_MED);
        return out;
    }

    private static Map<String, Object> normalizeHotel(String nodeType, Map<String, Object> raw) {
        if (!ItineraryNodeType.HOTEL.value().equals(nodeType)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "details.kind=hotel 时 type 必须为 hotel");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("kind", KIND_HOTEL);
        putOptional(out, "confirmationCode", raw.get("confirmationCode"), MAX_SHORT);
        putOptionalTime(out, "checkInTime", raw.get("checkInTime"));
        putOptionalTime(out, "checkOutTime", raw.get("checkOutTime"));
        putOptionalDate(out, "checkInDate", raw.get("checkInDate"));
        putOptionalDate(out, "checkOutDate", raw.get("checkOutDate"));
        // 服务端托管字段：客户端传入一律忽略，由 materializer 回填
        return out;
    }

    private static void putOptional(Map<String, Object> out, String key, Object value, int maxLen) {
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.length() > maxLen) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "details." + key + " 长度不能超过 " + maxLen);
        }
        out.put(key, trimmed);
    }

    private static void putOptionalTime(Map<String, Object> out, String key, Object value) {
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        try {
            LocalTime.parse(text.trim(), TIME_FMT);
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID,
                    "details." + key + " 格式必须为 HH:mm");
        }
        out.put(key, text.trim());
    }

    private static void putOptionalDate(Map<String, Object> out, String key, Object value) {
        String text = stringValue(value);
        if (!StringUtils.hasText(text)) {
            return;
        }
        try {
            LocalDate parsed = LocalDate.parse(text.trim(), DATE_FMT);
            out.put(key, parsed.toString());
        } catch (DateTimeParseException ex) {
            throw new BizException(ErrorCode.ITINERARY_STAY_DATE_INVALID,
                    "details." + key + " 格式必须为 yyyy-MM-dd");
        }
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "details 字段类型不合法");
    }

    public static String toJson(Map<String, Object> map) {
        try {
            String json = MAPPER.writeValueAsString(map);
            if (json.length() > MAX_JSON_CHARS) {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "details 内容过长");
            }
            return json;
        } catch (JsonProcessingException ex) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "details 无法序列化");
        }
    }

    /**
     * 列表/导出/摘要用短文案。无内容返回 null。
     */
    public static String summaryLine(String nodeType, String detailsJson) {
        Map<String, Object> d = parseMap(detailsJson);
        if (d.isEmpty()) {
            return null;
        }
        String type = nodeType == null ? "" : nodeType.trim().toLowerCase(Locale.ROOT);
        if (ItineraryNodeType.TRANSPORT.value().equals(type) || KIND_TRANSPORT.equals(stringField(d, "kind"))) {
            return transportSummary(d);
        }
        if (ItineraryNodeType.HOTEL.value().equals(type) || KIND_HOTEL.equals(stringField(d, "kind"))) {
            return hotelSummary(d);
        }
        return null;
    }

    private static String transportSummary(Map<String, Object> d) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        String mode = stringField(d, "mode");
        if ("flight".equals(mode)) {
            if (stringField(d, "flightNo") != null) {
                parts.add(stringField(d, "flightNo"));
            }
            if (stringField(d, "airline") != null) {
                parts.add(stringField(d, "airline"));
            }
        } else if ("train".equals(mode) || "hsr".equals(mode)) {
            if (stringField(d, "trainNo") != null) {
                parts.add(stringField(d, "trainNo"));
            }
        } else if ("bus".equals(mode) && stringField(d, "busNo") != null) {
            parts.add(stringField(d, "busNo"));
        }
        String dep = stringField(d, "departureName");
        String arr = stringField(d, "arrivalName");
        if (dep != null && arr != null) {
            parts.add(dep + " → " + arr);
        } else if (dep != null) {
            parts.add("出发：" + dep);
        } else if (arr != null) {
            parts.add("到达：" + arr);
        }
        if (stringField(d, "seatInfo") != null) {
            parts.add(stringField(d, "seatInfo"));
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static String hotelSummary(Map<String, Object> d) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        Integer total = intField(d, "totalNights");
        Integer night = intField(d, "nightIndex");
        if (total != null && total > 0) {
            if (night != null && total > 1) {
                parts.add("第 " + night + "/" + total + " 晚");
            } else if (total > 1) {
                parts.add("住 " + total + " 晚");
            } else {
                parts.add("住 1 晚");
            }
        }
        String outDate = stringField(d, "checkOutDate");
        if (outDate != null) {
            parts.add("离店 " + shortDate(outDate));
        }
        if (stringField(d, "confirmationCode") != null) {
            parts.add("确认号 " + stringField(d, "confirmationCode"));
        }
        if (stringField(d, "checkOutTime") != null) {
            parts.add("退房 " + stringField(d, "checkOutTime"));
        }
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    private static String shortDate(String yyyyMMdd) {
        String[] parts = yyyyMMdd.split("-");
        if (parts.length != 3) {
            return yyyyMMdd;
        }
        try {
            return Integer.parseInt(parts[1]) + "月" + Integer.parseInt(parts[2]) + "日";
        } catch (NumberFormatException ex) {
            return yyyyMMdd;
        }
    }

    /**
     * AI apply 用：剥离跨天物化字段，避免与「按天已展开」的 artifact 双重生成。
     * 保留 confirmation / 工具字段等轻量 details。
     */
    public static Map<String, Object> stripStayMaterializationFields(Map<String, Object> details) {
        if (details == null || details.isEmpty()) {
            return details;
        }
        Map<String, Object> copy = new LinkedHashMap<>(details);
        copy.remove("stayGroupId");
        copy.remove("stayRole");
        copy.remove("nightIndex");
        copy.remove("totalNights");
        copy.remove("checkOutDate");
        copy.remove("checkInDate");
        return copy.isEmpty() ? null : copy;
    }
}
