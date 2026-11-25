package com.tracci.module.feedback.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record FeedbackOptionsResp(
        List<OptionItem> types,
        List<OptionItem> statuses,
        List<OptionItem> contactTypes,
        List<OptionItem> platforms
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OptionItem(
            String value,
            String label
    ) {
    }
}
