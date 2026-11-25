package com.tracci.module.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.expense.dto.request.ExpenseCategoryCreateReq;
import com.tracci.module.expense.dto.response.ExpenseCategoryResp;
import com.tracci.module.expense.entity.ExpenseCategoryEntity;
import com.tracci.module.expense.repository.ExpenseCategoryMapper;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.service.TripAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseCategoryServiceImpl implements ExpenseCategoryService {

    private static final int MAX_CUSTOM_CATEGORIES = 20;

    private final ExpenseCategoryMapper categoryMapper;
    private final TripAccessService tripAccessService;

    @Override
    @Transactional(readOnly = true)
    public List<ExpenseCategoryResp> listCategories(String tripId) {
        String userId = LoginUserContext.requireUserId();
        tripAccessService.requirePermission(tripId, userId, TripPermission.READ);

        List<ExpenseCategoryEntity> entities = categoryMapper.selectList(
                new QueryWrapper<ExpenseCategoryEntity>()
                        .eq("trip_id", tripId)
                        .orderByAsc("sort_order", "created_at"));
        return entities.stream().map(this::toResp).toList();
    }

    @Override
    @Transactional
    public ExpenseCategoryResp createCategory(String tripId, ExpenseCategoryCreateReq req) {
        String userId = LoginUserContext.requireUserId();
        tripAccessService.requirePermission(tripId, userId, TripPermission.EDIT_CONTENT);

        String name = req.name().trim();

        // 同名校验
        Long sameName = categoryMapper.selectCount(new QueryWrapper<ExpenseCategoryEntity>()
                .eq("trip_id", tripId)
                .eq("name", name));
        if (sameName > 0) {
            throw new BizException(ErrorCode.EXPENSE_CATEGORY_DUPLICATE);
        }

        // 上限校验
        Long total = categoryMapper.selectCount(new QueryWrapper<ExpenseCategoryEntity>()
                .eq("trip_id", tripId));
        if (total >= MAX_CUSTOM_CATEGORIES) {
            throw new BizException(ErrorCode.EXPENSE_CATEGORY_LIMIT);
        }

        ExpenseCategoryEntity entity = new ExpenseCategoryEntity();
        entity.setTripId(tripId);
        entity.setName(name);
        entity.setSortOrder(total.intValue() + 1);
        categoryMapper.insert(entity);
        return toResp(entity);
    }

    @Override
    @Transactional
    public void deleteCategory(String tripId, String categoryId) {
        String userId = LoginUserContext.requireUserId();
        tripAccessService.requirePermission(tripId, userId, TripPermission.EDIT_CONTENT);

        ExpenseCategoryEntity entity = categoryMapper.selectById(categoryId);
        if (entity == null || !tripId.equals(entity.getTripId())) {
            throw new NotFoundException(ErrorCode.EXPENSE_CATEGORY_NOT_FOUND);
        }
        categoryMapper.deleteById(categoryId);
    }

    private ExpenseCategoryResp toResp(ExpenseCategoryEntity entity) {
        return new ExpenseCategoryResp(
                entity.getId(),
                entity.getTripId(),
                entity.getName(),
                entity.getSortOrder());
    }
}
