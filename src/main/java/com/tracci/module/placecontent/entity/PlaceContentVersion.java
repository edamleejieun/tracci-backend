package com.tracci.module.placecontent.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.tracci.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 地点内容版本。一个 Place 的一个可审核、可发布、可归档内容版本。
 */
@Getter
@Setter
@TableName("place_content_versions")
public class PlaceContentVersion extends BaseEntity {

    @TableField("place_id")
    private String placeId;

    @TableField("version_no")
    private Integer versionNo;

    @TableField("locale")
    private String locale;

    @TableField("introduction")
    private String introduction;

    /** JSONB: 有顺序的亮点数组（字符串或轻量对象） */
    @TableField("highlights_json")
    private String highlightsJson;

    @TableField("recommended_duration_minutes")
    private Integer recommendedDurationMinutes;

    /** JSONB: 有顺序的游览建议数组 */
    @TableField("visit_tips_json")
    private String visitTipsJson;

    @TableField("best_visit_time")
    private String bestVisitTime;

    @TableField("reservation_tips")
    private String reservationTips;

    @TableField("notice")
    private String notice;

    @TableField("generation_type")
    private String generationType;

    @TableField("ai_assisted")
    private Boolean aiAssisted;

    @TableField("model_provider")
    private String modelProvider;

    @TableField("model_name")
    private String modelName;

    @TableField("prompt_version")
    private String promptVersion;

    @TableField("status")
    private String status;

    @TableField("reviewed_by")
    private String reviewedBy;

    @TableField("reviewed_at")
    private java.time.LocalDateTime reviewedAt;

    @TableField("verified_at")
    private java.time.LocalDateTime verifiedAt;

    @TableField("next_review_at")
    private java.time.LocalDateTime nextReviewAt;

    @TableField("published_at")
    private java.time.LocalDateTime publishedAt;

    @TableField("archived_at")
    private java.time.LocalDateTime archivedAt;

    @TableField("content_hash")
    private String contentHash;

    @TableField("source_digest")
    private String sourceDigest;

    @TableField("generation_idempotency_key")
    private String generationIdempotencyKey;

    @TableField("source_workflow_id")
    private String sourceWorkflowId;

    @TableField("publish_idempotency_key")
    private String publishIdempotencyKey;

    @TableField("publish_request_id")
    private String publishRequestId;

    @TableField("previous_published_version_id")
    private String previousPublishedVersionId;

    @TableField("previous_published_content_hash")
    private String previousPublishedContentHash;

    @TableField("rollback_from_version_id")
    private String rollbackFromVersionId;
}
