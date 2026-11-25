package com.tracci.module.placecontent.enums;

/**
 * 内容版本状态。
 * 状态机：DRAFT → PENDING_REVIEW → PUBLISHED → ARCHIVED / OUTDATED
 */
public enum ContentStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    OUTDATED,
    ARCHIVED
}
