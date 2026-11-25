package com.tracci.module.expense.service;

import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.expense.dto.request.ExpenseCategoryCreateReq;
import com.tracci.module.expense.dto.response.ExpenseCategoryResp;
import com.tracci.module.expense.entity.ExpenseCategoryEntity;
import com.tracci.module.expense.repository.ExpenseCategoryMapper;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.service.TripAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpenseCategoryServiceImplTest {

    @Mock
    private ExpenseCategoryMapper categoryMapper;
    @Mock
    private TripAccessService tripAccessService;

    @InjectMocks
    private ExpenseCategoryServiceImpl categoryService;

    private final MockedStatic<LoginUserContext> loginUserContext = mockStatic(LoginUserContext.class);

    @AfterEach
    void tearDown() {
        loginUserContext.close();
    }

    @Test
    void listCategories_returnsSortedList() {
        loginUserContext.when(LoginUserContext::requireUserId).thenReturn("user-1");
        when(tripAccessService.requirePermission("trip-1", "user-1", TripPermission.READ))
                .thenReturn(access());

        ExpenseCategoryEntity entity = new ExpenseCategoryEntity();
        entity.setId("cat-1");
        entity.setTripId("trip-1");
        entity.setName("按摩");
        entity.setSortOrder(1);
        when(categoryMapper.selectList(any())).thenReturn(List.of(entity));

        List<ExpenseCategoryResp> result = categoryService.listCategories("trip-1");

        assertEquals(1, result.size());
        assertEquals("cat-1", result.get(0).id());
        assertEquals("按摩", result.get(0).name());
    }

    @Test
    void createCategory_succeeds() {
        loginUserContext.when(LoginUserContext::requireUserId).thenReturn("user-1");
        when(tripAccessService.requirePermission("trip-1", "user-1", TripPermission.EDIT_CONTENT))
                .thenReturn(access());
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        ExpenseCategoryResp resp = categoryService.createCategory("trip-1",
                new ExpenseCategoryCreateReq("按摩"));

        assertEquals("按摩", resp.name());
        verify(categoryMapper).insert(any(ExpenseCategoryEntity.class));
    }

    @Test
    void createCategory_rejectsDuplicateName() {
        loginUserContext.when(LoginUserContext::requireUserId).thenReturn("user-1");
        when(tripAccessService.requirePermission("trip-1", "user-1", TripPermission.EDIT_CONTENT))
                .thenReturn(access());
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThrows(BizException.class, () ->
                categoryService.createCategory("trip-1", new ExpenseCategoryCreateReq("按摩")));
    }

    @Test
    void createCategory_rejectsWhenLimitReached() {
        loginUserContext.when(LoginUserContext::requireUserId).thenReturn("user-1");
        when(tripAccessService.requirePermission("trip-1", "user-1", TripPermission.EDIT_CONTENT))
                .thenReturn(access());
        // 同名校验通过（0），但总数已达上限
        when(categoryMapper.selectCount(any()))
                .thenReturn(0L)   // 同名校验
                .thenReturn(20L); // 总数校验

        assertThrows(BizException.class, () ->
                categoryService.createCategory("trip-1", new ExpenseCategoryCreateReq("按摩")));
    }

    @Test
    void deleteCategory_succeeds() {
        loginUserContext.when(LoginUserContext::requireUserId).thenReturn("user-1");
        when(tripAccessService.requirePermission("trip-1", "user-1", TripPermission.EDIT_CONTENT))
                .thenReturn(access());

        ExpenseCategoryEntity entity = new ExpenseCategoryEntity();
        entity.setId("cat-1");
        entity.setTripId("trip-1");
        entity.setName("按摩");
        when(categoryMapper.selectById("cat-1")).thenReturn(entity);

        categoryService.deleteCategory("trip-1", "cat-1");

        verify(categoryMapper).deleteById("cat-1");
    }

    @Test
    void deleteCategory_throwsWhenNotFound() {
        loginUserContext.when(LoginUserContext::requireUserId).thenReturn("user-1");
        when(tripAccessService.requirePermission("trip-1", "user-1", TripPermission.EDIT_CONTENT))
                .thenReturn(access());
        when(categoryMapper.selectById("cat-999")).thenReturn(null);

        assertThrows(NotFoundException.class, () ->
                categoryService.deleteCategory("trip-1", "cat-999"));
    }

    private TripAccessService.TripAccess access() {
        Trip trip = new Trip();
        trip.setId("trip-1");
        TripMember member = new TripMember();
        member.setId("member-1");
        member.setUserId("user-1");
        return new TripAccessService.TripAccess(trip, member);
    }
}
