package com.tracci.module.journal.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JournalEntryResp(
        String id,
        String tripId,
        String authorId,
        String authorName,
        String content,
        JournalEntryLocationResp location,
        List<PhotoResp> photos,
        String occurredAt,
        String mood,
        Boolean isHighlight,
        String visibility,
        String createdAt
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PhotoResp(
            String id,
            String fileId,
            String url,
            Integer sortOrder,
            String thumbnailUrl,
            String previewUrl,
            String exportUrl,
            String storyPortraitUrl,
            String storyCardUrl,
            String squareUrl,
            String watermarkedUrl,
            Integer width,
            Integer height,
            boolean userFeatured
    ) {
        public PhotoResp(String id, String fileId, String url, Integer sortOrder) {
            this(id, fileId, url, sortOrder, null, null, null, null, null, null, null, null, null, false);
        }
    }
}
