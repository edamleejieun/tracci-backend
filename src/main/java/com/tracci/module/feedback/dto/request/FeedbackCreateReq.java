package com.tracci.module.feedback.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

public record FeedbackCreateReq(
        @NotBlank(message = "反馈类型不能为空")
        @Size(max = 32, message = "反馈类型长度不能超过 32")
        String type,

        @Size(max = 100, message = "标题长度不能超过 100")
        String title,

        @NotBlank(message = "反馈内容不能为空")
        @Size(max = 1000, message = "反馈内容长度不能超过 1000")
        String content,

        @Size(max = 16, message = "联系方式类型长度不能超过 16")
        String contactType,

        @Size(max = 100, message = "联系方式长度不能超过 100")
        String contact,

        @Size(max = 3, message = "截图最多 3 张")
        List<@Size(max = 36, message = "fileId 长度不能超过 36") String> imageIds,

        @Size(max = 32, message = "platform 长度不能超过 32")
        String platform,

        @Size(max = 32, message = "appVersion 长度不能超过 32")
        String appVersion,

        Map<String, Object> systemInfo
) {
}
