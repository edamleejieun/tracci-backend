package com.tracci.module.expense.controller;

import com.tracci.common.response.ApiResponse;
import com.tracci.common.response.PageResponse;
import com.tracci.module.expense.dto.request.ExpenseCreateReq;
import com.tracci.module.expense.dto.request.ExpenseUpdateReq;
import com.tracci.module.expense.dto.response.ExpenseResp;
import com.tracci.module.expense.dto.response.ExpenseSettlementResp;
import com.tracci.module.expense.dto.response.ExpenseSummaryResp;
import com.tracci.module.expense.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trips/{tripId}/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @PostMapping
    public ApiResponse<ExpenseResp> create(
            @PathVariable String tripId,
            @Valid @RequestBody ExpenseCreateReq req) {
        return ApiResponse.ok(expenseService.createExpense(tripId, req));
    }

    @GetMapping
    public ApiResponse<PageResponse<ExpenseResp>> list(
            @PathVariable String tripId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String category) {
        return ApiResponse.ok(expenseService.listExpenses(tripId, page, pageSize, category));
    }

    @GetMapping("/summary")
    public ApiResponse<ExpenseSummaryResp> summary(@PathVariable String tripId) {
        return ApiResponse.ok(expenseService.getSummary(tripId));
    }

    @GetMapping("/settlement")
    public ApiResponse<ExpenseSettlementResp> settlement(@PathVariable String tripId) {
        return ApiResponse.ok(expenseService.getSettlement(tripId));
    }

    @PutMapping("/{expenseId}")
    public ApiResponse<ExpenseResp> update(
            @PathVariable String tripId,
            @PathVariable String expenseId,
            @Valid @RequestBody ExpenseUpdateReq req) {
        return ApiResponse.ok(expenseService.updateExpense(tripId, expenseId, req));
    }

    @DeleteMapping("/{expenseId}")
    public ApiResponse<Void> delete(
            @PathVariable String tripId,
            @PathVariable String expenseId) {
        expenseService.deleteExpense(tripId, expenseId);
        return ApiResponse.ok();
    }
}
