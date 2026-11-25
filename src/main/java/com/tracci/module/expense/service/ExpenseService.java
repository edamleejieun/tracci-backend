package com.tracci.module.expense.service;

import com.tracci.common.response.PageResponse;
import com.tracci.module.expense.dto.request.ExpenseCreateReq;
import com.tracci.module.expense.dto.request.ExpenseUpdateReq;
import com.tracci.module.expense.dto.response.ExpenseResp;
import com.tracci.module.expense.dto.response.ExpenseSettlementResp;
import com.tracci.module.expense.dto.response.ExpenseSummaryResp;

public interface ExpenseService {

    ExpenseResp createExpense(String tripId, ExpenseCreateReq req);

    PageResponse<ExpenseResp> listExpenses(String tripId, int page, int pageSize, String category);

    ExpenseSummaryResp getSummary(String tripId);

    ExpenseSettlementResp getSettlement(String tripId);

    ExpenseResp updateExpense(String tripId, String expenseId, ExpenseUpdateReq req);

    void deleteExpense(String tripId, String expenseId);
}
