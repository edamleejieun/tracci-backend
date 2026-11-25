package com.tracci.module.expense.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExpenseCategoryCreateReq(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 32, message = "分类名称不能超过 32 个字符")
        String name
) {
}
