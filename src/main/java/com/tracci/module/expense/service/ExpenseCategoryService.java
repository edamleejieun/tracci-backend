package com.tracci.module.expense.service;

import com.tracci.module.expense.dto.request.ExpenseCategoryCreateReq;
import com.tracci.module.expense.dto.response.ExpenseCategoryResp;

import java.util.List;

public interface ExpenseCategoryService {

    List<ExpenseCategoryResp> listCategories(String tripId);

    ExpenseCategoryResp createCategory(String tripId, ExpenseCategoryCreateReq req);

    void deleteCategory(String tripId, String categoryId);
}
