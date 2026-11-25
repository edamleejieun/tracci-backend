package com.tracci.module.feedback.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.feedback.dto.request.FeedbackCreateReq;
import com.tracci.module.feedback.dto.response.FeedbackCreateResp;
import com.tracci.module.feedback.dto.response.FeedbackDetailResp;
import com.tracci.module.feedback.dto.response.FeedbackListItemResp;
import com.tracci.module.feedback.dto.response.FeedbackOptionsResp;
import com.tracci.module.feedback.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackService feedbackService;

    @PostMapping
    public ApiResponse<FeedbackCreateResp> create(@Valid @RequestBody FeedbackCreateReq req) {
        return ApiResponse.ok(feedbackService.create(req));
    }

    @GetMapping
    public ApiResponse<PageResponse<FeedbackListItemResp>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(feedbackService.listMine(page, pageSize));
    }

    @GetMapping("/options")
    public ApiResponse<FeedbackOptionsResp> options() {
        return ApiResponse.ok(feedbackService.getOptions());
    }

    @GetMapping("/{id}")
    public ApiResponse<FeedbackDetailResp> detail(@PathVariable String id) {
        return ApiResponse.ok(feedbackService.getDetail(id));
    }
}
