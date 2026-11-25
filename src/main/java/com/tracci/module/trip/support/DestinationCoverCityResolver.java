package com.tracci.module.trip.support;

import com.tracci.module.trip.entity.Trip;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 将旅程目的地解析为国内城市封面 key（COS 对象名用）。
 * 仅支持国内城市；未命中时由调用方走默认封面。
 */
@Component
public class DestinationCoverCityResolver {

    /**
     * 中文地名 / 常见别名 → 稳定 ascii cityKey。
     * COS 路径：system/destination-covers/v1/cities/{cityKey}.jpg
     */
    private static final Map<String, String> CITY_KEYS = Map.ofEntries(
            // 直辖市 & 一线
            entry("北京", "beijing"),
            entry("北京市", "beijing"),
            entry("上海", "shanghai"),
            entry("上海市", "shanghai"),
            entry("天津", "tianjin"),
            entry("天津市", "tianjin"),
            entry("重庆", "chongqing"),
            entry("重庆市", "chongqing"),
            entry("广州", "guangzhou"),
            entry("广州市", "guangzhou"),
            entry("深圳", "shenzhen"),
            entry("深圳市", "shenzhen"),
            // 热门旅游 / 新一线
            entry("杭州", "hangzhou"),
            entry("杭州市", "hangzhou"),
            entry("成都", "chengdu"),
            entry("成都市", "chengdu"),
            entry("西安", "xian"),
            entry("西安市", "xian"),
            entry("南京", "nanjing"),
            entry("南京市", "nanjing"),
            entry("苏州", "suzhou"),
            entry("苏州市", "suzhou"),
            entry("厦门", "xiamen"),
            entry("厦门市", "xiamen"),
            entry("青岛", "qingdao"),
            entry("青岛市", "qingdao"),
            entry("大连", "dalian"),
            entry("大连市", "dalian"),
            entry("武汉", "wuhan"),
            entry("武汉市", "wuhan"),
            entry("长沙", "changsha"),
            entry("长沙市", "changsha"),
            entry("昆明", "kunming"),
            entry("昆明市", "kunming"),
            entry("三亚", "sanya"),
            entry("三亚市", "sanya"),
            entry("桂林", "guilin"),
            entry("桂林市", "guilin"),
            entry("丽江", "lijiang"),
            entry("丽江市", "lijiang"),
            entry("大理", "dali"),
            entry("大理市", "dali"),
            entry("大理白族自治州", "dali"),
            entry("拉萨", "lhasa"),
            entry("拉萨市", "lhasa"),
            entry("哈尔滨", "harbin"),
            entry("哈尔滨市", "harbin"),
            entry("郑州", "zhengzhou"),
            entry("郑州市", "zhengzhou"),
            entry("合肥", "hefei"),
            entry("合肥市", "hefei"),
            entry("福州", "fuzhou"),
            entry("福州市", "fuzhou"),
            entry("济南", "jinan"),
            entry("济南市", "jinan"),
            entry("贵阳", "guiyang"),
            entry("贵阳市", "guiyang"),
            entry("南宁", "nanning"),
            entry("南宁市", "nanning"),
            entry("海口", "haikou"),
            entry("海口市", "haikou"),
            entry("宁波", "ningbo"),
            entry("宁波市", "ningbo"),
            entry("无锡", "wuxi"),
            entry("无锡市", "wuxi"),
            // 更多省会 / 常见目的地
            entry("沈阳", "shenyang"),
            entry("沈阳市", "shenyang"),
            entry("长春", "changchun"),
            entry("长春市", "changchun"),
            entry("南昌", "nanchang"),
            entry("南昌市", "nanchang"),
            entry("太原", "taiyuan"),
            entry("太原市", "taiyuan"),
            entry("石家庄", "shijiazhuang"),
            entry("石家庄市", "shijiazhuang"),
            entry("兰州", "lanzhou"),
            entry("兰州市", "lanzhou"),
            entry("西宁", "xining"),
            entry("西宁市", "xining"),
            entry("银川", "yinchuan"),
            entry("银川市", "yinchuan"),
            entry("呼和浩特", "hohhot"),
            entry("呼和浩特市", "hohhot"),
            entry("乌鲁木齐", "urumqi"),
            entry("乌鲁木齐市", "urumqi"),
            // 港澳（国内游常见）
            entry("香港", "hongkong"),
            entry("香港特别行政区", "hongkong"),
            entry("澳门", "macau"),
            entry("澳门特别行政区", "macau")
    );

    /** 已规范化的 ascii cityKey 集合，便于英文输入直通。 */
    private static final Map<String, String> ASCII_KEYS = Map.ofEntries(
            entry("beijing", "beijing"),
            entry("shanghai", "shanghai"),
            entry("tianjin", "tianjin"),
            entry("chongqing", "chongqing"),
            entry("guangzhou", "guangzhou"),
            entry("shenzhen", "shenzhen"),
            entry("hangzhou", "hangzhou"),
            entry("chengdu", "chengdu"),
            entry("xian", "xian"),
            entry("xi'an", "xian"),
            entry("nanjing", "nanjing"),
            entry("suzhou", "suzhou"),
            entry("xiamen", "xiamen"),
            entry("qingdao", "qingdao"),
            entry("dalian", "dalian"),
            entry("wuhan", "wuhan"),
            entry("changsha", "changsha"),
            entry("kunming", "kunming"),
            entry("sanya", "sanya"),
            entry("guilin", "guilin"),
            entry("lijiang", "lijiang"),
            entry("dali", "dali"),
            entry("lhasa", "lhasa"),
            entry("harbin", "harbin"),
            entry("zhengzhou", "zhengzhou"),
            entry("hefei", "hefei"),
            entry("fuzhou", "fuzhou"),
            entry("jinan", "jinan"),
            entry("guiyang", "guiyang"),
            entry("nanning", "nanning"),
            entry("haikou", "haikou"),
            entry("ningbo", "ningbo"),
            entry("wuxi", "wuxi"),
            entry("shenyang", "shenyang"),
            entry("changchun", "changchun"),
            entry("nanchang", "nanchang"),
            entry("taiyuan", "taiyuan"),
            entry("shijiazhuang", "shijiazhuang"),
            entry("lanzhou", "lanzhou"),
            entry("xining", "xining"),
            entry("yinchuan", "yinchuan"),
            entry("hohhot", "hohhot"),
            entry("urumqi", "urumqi"),
            entry("hongkong", "hongkong"),
            entry("hong kong", "hongkong"),
            entry("macau", "macau"),
            entry("macao", "macau")
    );

    public Optional<String> resolveCityKey(Trip trip) {
        if (trip == null) {
            return Optional.empty();
        }
        Optional<String> fromCity = resolveLabel(trip.getDestinationCityName());
        if (fromCity.isPresent()) {
            return fromCity;
        }
        return resolveLabel(trip.getDestinationName());
    }

    Optional<String> resolveLabel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Optional.empty();
        }
        String label = raw.trim();
        String direct = CITY_KEYS.get(label);
        if (direct != null) {
            return Optional.of(direct);
        }

        String ascii = ASCII_KEYS.get(label.toLowerCase(Locale.ROOT));
        if (ascii != null) {
            return Optional.of(ascii);
        }

        // 去常见行政区后缀后再查
        String stripped = stripAdminSuffix(label);
        if (!stripped.equals(label)) {
            direct = CITY_KEYS.get(stripped);
            if (direct != null) {
                return Optional.of(direct);
            }
            ascii = ASCII_KEYS.get(stripped.toLowerCase(Locale.ROOT));
            if (ascii != null) {
                return Optional.of(ascii);
            }
        }
        return Optional.empty();
    }

    private static String stripAdminSuffix(String place) {
        String value = place;
        String[] suffixes = {"特别行政区", "白族自治州", "自治州", "地区", "市区", "市", "盟", "县"};
        for (String suffix : suffixes) {
            if (value.endsWith(suffix) && value.length() > suffix.length()) {
                value = value.substring(0, value.length() - suffix.length());
                break;
            }
        }
        return value;
    }

    private static Map.Entry<String, String> entry(String key, String value) {
        return Map.entry(key, value);
    }
}
