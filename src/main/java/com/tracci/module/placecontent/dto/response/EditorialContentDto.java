package com.tracci.module.placecontent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 地点编辑内容块。嵌入 PlaceDetailResponse，仅 PUBLISHED 版本存在时返回。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EditorialContentDto(
        Integer version,
        String introduction,
        java.util.List<String> highlights,
        Integer recommendedDurationMinutes,
        java.util.List<String> visitTips,
        String bestVisitTime,
        String reservationTips,
        String notice
) {
}
