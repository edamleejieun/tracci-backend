package com.tracci.module.journal.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record JournalEntryUpdateReq(
        @Size(max = 5000, message = "日志内容长度不能超过 5000")
        String content,

        @Valid
        JournalEntryLocationReq location,

        @Size(max = 9, message = "图片最多 9 张")
        List<@Size(max = 36, message = "fileId 长度不能超过 36") String> fileIds,

        LocalDateTime occurredAt,

        @Size(max = 20, message = "mood 长度不能超过 20")
        String mood,

        Boolean isHighlight,

        @Size(max = 20, message = "visibility 长度不能超过 20")
        String visibility
) {
}
