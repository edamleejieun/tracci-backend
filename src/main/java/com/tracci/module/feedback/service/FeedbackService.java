package com.tracci.module.feedback.service;

import com.tracci.common.response.PageResponse;
import com.tracci.module.feedback.dto.request.FeedbackCreateReq;
import com.tracci.module.feedback.dto.response.FeedbackAdminDetailResp;
import com.tracci.module.feedback.dto.response.FeedbackAdminListItemResp;
import com.tracci.module.feedback.dto.response.FeedbackCreateResp;
import com.tracci.module.feedback.dto.response.FeedbackDetailResp;
import com.tracci.module.feedback.dto.response.FeedbackListItemResp;
import com.tracci.module.feedback.dto.response.FeedbackOptionsResp;

public interface FeedbackService {

    FeedbackCreateResp create(FeedbackCreateReq req);

    PageResponse<FeedbackListItemResp> listMine(int page, int pageSize);

    FeedbackDetailResp getDetail(String feedbackId);

    FeedbackOptionsResp getOptions();

    /** Admin: paginated list of all feedbacks with filters. */
    PageResponse<FeedbackAdminListItemResp> listAll(
            String type,
            String status,
            String platform,
            String userId,
            String keyword,
            String createdFrom,
            String createdTo,
            int page,
            int pageSize);

    /** Admin: detail without ownership check; includes contact and handler fields. */
    FeedbackAdminDetailResp getAdminDetail(String feedbackId);
}
