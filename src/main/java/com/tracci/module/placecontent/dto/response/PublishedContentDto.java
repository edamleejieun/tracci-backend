package com.tracci.module.placecontent.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 当前 PUBLISHED 内容的聚合视图，供 Place detail 组装使用。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublishedContentDto(
        EditorialContentDto editorialContent,
        ContentMetaDto contentMeta
) {
}
