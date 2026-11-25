package com.tracci.module.feedback.controller;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.feedback.dto.response.FeedbackAdminDetailResp;
import com.tracci.module.feedback.dto.response.FeedbackAdminListItemResp;
import com.tracci.module.feedback.dto.response.FeedbackOptionsResp;
import com.tracci.module.feedback.service.FeedbackService;
import com.tracci.service.AdminService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only feedback read APIs under {@code /internal/v1}.
 */
@RestController
@RequestMapping("/internal/v1/feedbacks")
@RequiredArgsConstructor
public class InternalFeedbackController {

    private final FeedbackService feedbackService;
    private final AdminService adminService;

    @GetMapping
    public ApiResponse<PageResponse<FeedbackAdminListItemResp>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String createdFrom,
            @RequestParam(required = false) String createdTo,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        requireAdmin();
        return ApiResponse.ok(feedbackService.listAll(
                type, status, platform, userId, keyword, createdFrom, createdTo, page, pageSize));
    }

    @GetMapping("/options")
    public ApiResponse<FeedbackOptionsResp> options() {
        requireAdmin();
        return ApiResponse.ok(feedbackService.getOptions());
    }

    @GetMapping("/{id}")
    public ApiResponse<FeedbackAdminDetailResp> detail(@PathVariable String id) {
        requireAdmin();
        return ApiResponse.ok(feedbackService.getAdminDetail(id));
    }

    private void requireAdmin() {
        LoginUserContext.requireUserId();
        if (!adminService.isCurrentUserAdmin()) {
            throw new ForbiddenException("仅管理员可查看用户反馈");
        }
    }
}
