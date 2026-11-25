package com.tracci.module.expense.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.module.expense.dto.request.ExpenseCategoryCreateReq;
import com.tracci.module.expense.dto.response.ExpenseCategoryResp;
import com.tracci.module.expense.service.ExpenseCategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/expense-categories")
@RequiredArgsConstructor
public class ExpenseCategoryController {

    private final ExpenseCategoryService categoryService;

    @GetMapping
    public ApiResponse<List<ExpenseCategoryResp>> list(@PathVariable String tripId) {
        return ApiResponse.ok(categoryService.listCategories(tripId));
    }

    @PostMapping
    public ApiResponse<ExpenseCategoryResp> create(
            @PathVariable String tripId,
            @Valid @RequestBody ExpenseCategoryCreateReq req) {
        return ApiResponse.ok(categoryService.createCategory(tripId, req));
    }

    @DeleteMapping("/{categoryId}")
    public ApiResponse<Void> delete(
            @PathVariable String tripId,
            @PathVariable String categoryId) {
        categoryService.deleteCategory(tripId, categoryId);
        return ApiResponse.ok();
    }
}
