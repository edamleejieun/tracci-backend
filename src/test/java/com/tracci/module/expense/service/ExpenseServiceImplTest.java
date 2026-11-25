package com.tracci.module.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.module.trip.entity.ItineraryNode;
import com.tracci.integration.exchangerate.ExchangeRateClient;
import com.tracci.module.expense.config.ExpenseProperties;
import com.tracci.module.expense.dto.request.ExpenseCreateReq;
import com.tracci.module.expense.dto.request.ExpenseUpdateReq;
import com.tracci.module.expense.entity.Expense;
import com.tracci.module.expense.entity.ExpenseSplit;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.expense.repository.ExpenseSplitMapper;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.repository.TripMapper;
import com.tracci.module.trip.repository.TripMemberMapper;
import com.tracci.module.trip.service.TripAccessService;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static com.tracci.testsupport.TripAccessTestSupport.stubOwnerAccess;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceImplTest {

    @Mock
    private ExpenseMapper expenseMapper;
    @Mock
    private TripMapper tripMapper;
    @Mock
    private ExchangeRateClient exchangeRateClient;
    @Mock
    private ExpenseProperties expenseProperties;
    @Mock
    private ExpenseSplitMapper expenseSplitMapper;
    @Mock
    private TripMemberMapper tripMemberMapper;
    @Mock
    private TripAccessService tripAccessService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private com.tracci.module.trip.repository.ItineraryNodeMapper itineraryNodeMapper;

    @InjectMocks
    private ExpenseServiceImpl expenseService;

    @BeforeEach
    void setUpAccess() {
        stubOwnerAccess(tripAccessService, tripMapper);
    }

    @Test
    void createExpense_savesWithExchangeRateAndAmountCny() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(true);
        when(exchangeRateClient.getRate("JPY")).thenReturn(new BigDecimal("0.04830000"));
        doAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId("expense-1");
            expense.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            expense.setSpentAt(LocalDateTime.of(2026, 7, 1, 12, 30));
            return 1;
        }).when(expenseMapper).insert(any(Expense.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.createExpense("trip-1", new ExpenseCreateReq(
                    new BigDecimal("3000.00"),
                    "JPY",
                    "food",
                    " 一兰拉面 ",
                    LocalDateTime.of(2026, 7, 1, 12, 30)
            ));

            assertEquals("expense-1", resp.id());
            assertEquals("3000.00", resp.amountInput());
            assertEquals("JPY", resp.currency());
            assertEquals("144.90", resp.amountCny());
            assertEquals("0.04830000", resp.exchangeRate());
            assertEquals("food", resp.category());
            assertEquals("一兰拉面", resp.note());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseMapper).insert(captor.capture());
        Expense saved = captor.getValue();
        assertEquals("trip-1", saved.getTripId());
        assertEquals("u-1", saved.getUserId());
        assertEquals(new BigDecimal("3000.00"), saved.getAmountInput());
        assertEquals(new BigDecimal("0.04830000"), saved.getExchangeRate());
    }

    @Test
    void createExpense_allowsCnyWhenMultiCurrencyDisabledWithoutCallingExchangeRateClient() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(false);
        doAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId("expense-1");
            expense.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            return 1;
        }).when(expenseMapper).insert(any(Expense.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.createExpense("trip-1", new ExpenseCreateReq(
                    new BigDecimal("88.50"),
                    "CNY",
                    "food",
                    null,
                    LocalDateTime.of(2026, 7, 1, 12, 30)
            ));

            assertEquals("88.50", resp.amountInput());
            assertEquals("CNY", resp.currency());
            assertEquals("88.50", resp.amountCny());
            assertEquals("1.00000000", resp.exchangeRate());
        } finally {
            LoginUserContext.clear();
        }

        verify(exchangeRateClient, never()).getRate(any());
    }

    @Test
    void createGroupExpense_returnsParticipantMemberIds() {
        Trip trip = trip("owner-1");
        TripMember owner = member("member-a", "owner-1");
        TripMember editor = member("member-b", "user-2");
        doReturn(new TripAccessService.TripAccess(trip, owner))
                .when(tripAccessService)
                .requirePermission("trip-1", "owner-1", TripPermission.EDIT_CONTENT);
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(false);
        when(tripMemberMapper.listActiveByTripId("trip-1")).thenReturn(List.of(owner, editor));
        doAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId("expense-group-1");
            expense.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            return 1;
        }).when(expenseMapper).insert(any(Expense.class));
        when(expenseSplitMapper.listByExpenseId("expense-group-1")).thenReturn(List.of(
                split("member-a"), split("member-b")
        ));

        LoginUserContext.setUserId("owner-1");
        try {
            var response = expenseService.createExpense("trip-1", new ExpenseCreateReq(
                    new BigDecimal("120.00"), "CNY", "food", "午餐",
                    LocalDateTime.of(2026, 7, 1, 12, 30),
                    "group", "member-a", List.of("member-a", "member-b"), "equal"
            ));

            assertEquals(List.of("member-a", "member-b"), response.participantMemberIds());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getSettlement_keepsDeletedMemberWithFallbackDisplayName() {
        TripMember owner = member("member-a", "owner-1");
        TripMember deletedMember = member("member-b", "deleted-user");
        User ownerUser = new User();
        ownerUser.setId("owner-1");
        ownerUser.setNickname("Liz");
        doReturn(new TripAccessService.TripAccess(trip("owner-1"), owner))
                .when(tripAccessService)
                .requirePermission("trip-1", "owner-1", TripPermission.READ);
        when(tripMemberMapper.listActiveByTripId("trip-1")).thenReturn(List.of(owner, deletedMember));
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(ownerUser));
        when(expenseMapper.sumPaidByTripId("trip-1")).thenReturn(List.of());
        when(expenseSplitMapper.sumShareByTripId("trip-1")).thenReturn(List.of());

        LoginUserContext.setUserId("owner-1");
        try {
            var response = expenseService.getSettlement("trip-1");

            assertEquals(2, response.members().size());
            assertEquals("Liz", response.members().getFirst().displayName());
            assertEquals("已注销用户", response.members().get(1).displayName());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsNonCnyWhenMultiCurrencyDisabled() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(false);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(new BigDecimal("100"), "USD", "food", null,
                            LocalDateTime.of(2026, 7, 1, 12, 30))));
            verify(exchangeRateClient, never()).getRate(any());
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsBlankCurrency() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(new BigDecimal("100"), " ", "other", null,
                            LocalDateTime.of(2026, 7, 1, 12, 30))));
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsInvalidAmount() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(BigDecimal.ZERO, "CNY", "food", null,
                            LocalDateTime.of(2026, 7, 1, 12, 30))));
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsAmountWithTooManyFractionDigits() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(new BigDecimal("100.123"), "CNY", "food", null,
                            LocalDateTime.of(2026, 7, 1, 12, 30))));
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsInvalidCategory() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(new BigDecimal("100"), "CNY", "invalid_cat", null,
                            LocalDateTime.of(2026, 7, 1, 12, 30))));
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsMissingSpentAt() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(new BigDecimal("100"), "CNY", "food", null, null)));
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsSpentAtOutsideTripDateRange() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(new BigDecimal("100"), "CNY", "food", null,
                            LocalDateTime.of(2026, 6, 30, 12, 30))));
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsInvalidExchangeRate() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(true);
        when(exchangeRateClient.getRate("USD")).thenReturn(BigDecimal.ZERO);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(new BigDecimal("100"), "USD", "food", null,
                            LocalDateTime.of(2026, 7, 1, 12, 30))));
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(new BigDecimal("100"), "CNY", "food", null,
                            LocalDateTime.of(2026, 7, 1, 12, 30))));
            verify(expenseMapper, never()).insert(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void listExpenses_returnsPaginatedResults() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        Expense e1 = expense("expense-1", "food", new BigDecimal("3000.00"), new BigDecimal("144.90"));
        Expense e2 = expense("expense-2", "transport", new BigDecimal("5000.00"), new BigDecimal("241.50"));
        Page<Expense> page = new Page<>(1, 50);
        page.setRecords(List.of(e1, e2));
        page.setTotal(2);
        when(expenseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.listExpenses("trip-1", 1, 50, null);

            assertEquals(2, resp.total());
            assertEquals(2, resp.list().size());
            assertEquals("food", resp.list().getFirst().category());
            assertEquals("144.90", resp.list().getFirst().amountCny());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void listExpenses_filtersByCategory() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        Page<Expense> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(expenseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.listExpenses("trip-1", 1, 20, "food");
            assertEquals(0, resp.total());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void listExpenses_ordersBySpentAtDesc() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        Page<Expense> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(expenseMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(page);

        LoginUserContext.setUserId("u-1");
        try {
            expenseService.listExpenses("trip-1", 1, 20, null);
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<QueryWrapper> queryCaptor = ArgumentCaptor.forClass(QueryWrapper.class);
        verify(expenseMapper).selectPage(any(Page.class), queryCaptor.capture());
        String sql = queryCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("expense_scope"));
        assertTrue(sql.contains("user_id"));
        assertTrue(sql.contains("ORDER BY spent_at DESC"));
    }

    @Test
    void listExpenses_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class,
                    () -> expenseService.listExpenses("trip-1", 1, 20, null));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateExpense_updatesFieldsAndRecalculatesAmountCny() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        Expense existing = expense("expense-1", "food", new BigDecimal("3000.00"), new BigDecimal("144.90"));
        existing.setExchangeRate(new BigDecimal("0.04830000"));
        existing.setNote("一兰拉面");
        when(expenseMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(expenseMapper.updateById(any(Expense.class))).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.updateExpense("trip-1", "expense-1", new ExpenseUpdateReq(
                    new BigDecimal("3500.00"),
                    null,
                    "food",
                    " 追加了叉烧 ",
                    null
            ));

            assertEquals("3500.00", resp.amountInput());
            assertEquals("food", resp.category());
            assertEquals("追加了叉烧", resp.note());
            // 3500 * 0.0483 = 169.05
            assertEquals("169.05", resp.amountCny());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseMapper).updateById(captor.capture());
        assertEquals(new BigDecimal("3500.00"), captor.getValue().getAmountInput());
    }

    @Test
    void updateExpense_updatesCurrencyAndRecalculatesWithNewExchangeRate() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(true);

        Expense existing = expense("expense-1", "food", new BigDecimal("100.00"), new BigDecimal("724.00"));
        existing.setCurrency("USD");
        existing.setExchangeRate(new BigDecimal("7.24000000"));
        when(expenseMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);
        when(exchangeRateClient.getRate("EUR")).thenReturn(new BigDecimal("7.90000000"));
        when(expenseMapper.updateById(any(Expense.class))).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.updateExpense("trip-1", "expense-1", new ExpenseUpdateReq(
                    new BigDecimal("120.00"),
                    "EUR",
                    "food",
                    null,
                    null
            ));

            assertEquals("120.00", resp.amountInput());
            assertEquals("EUR", resp.currency());
            assertEquals("7.90000000", resp.exchangeRate());
            assertEquals("948.00", resp.amountCny());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateExpense_rejectsSpentAtOutsideTripDateRange() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));

        Expense existing = expense("expense-1", "food", new BigDecimal("100.00"), new BigDecimal("100.00"));
        when(expenseMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(BizException.class, () -> expenseService.updateExpense("trip-1", "expense-1",
                    new ExpenseUpdateReq(null, null, null, null,
                            LocalDateTime.of(2026, 6, 30, 12, 30))));
            verify(expenseMapper, never()).updateById(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateExpense_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> expenseService.updateExpense("trip-1", "expense-1",
                    new ExpenseUpdateReq(null, null, null, null, null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateExpense_throwsNotFoundForMissingExpense() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(NotFoundException.class, () -> expenseService.updateExpense("trip-1", "missing-id",
                    new ExpenseUpdateReq(null, null, null, null, null)));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void updateExpense_rejectsOtherUserExpenseIdInOwnedTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        Expense existing = expense("expense-from-u-2", "food", new BigDecimal("100.00"), new BigDecimal("100.00"));
        existing.setUserId("u-2");
        when(expenseMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> expenseService.updateExpense("trip-1", "expense-from-u-2",
                    new ExpenseUpdateReq(new BigDecimal("200"), null, null, null, null)));
            verify(expenseMapper, never()).updateById(any(Expense.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteExpense_success() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseMapper.selectOne(any(QueryWrapper.class)))
                .thenReturn(expense("expense-1", "food", new BigDecimal("100.00"), new BigDecimal("100.00")));
        when(expenseMapper.delete(any(UpdateWrapper.class))).thenReturn(1);

        LoginUserContext.setUserId("u-1");
        try {
            expenseService.deleteExpense("trip-1", "expense-1");
        } finally {
            LoginUserContext.clear();
        }

        verify(expenseMapper).delete(any(UpdateWrapper.class));
    }

    @Test
    void deleteExpense_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class,
                    () -> expenseService.deleteExpense("trip-1", "expense-1"));
            verify(expenseMapper, never()).delete(any(UpdateWrapper.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteExpense_throwsNotFoundForMissingExpense() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseMapper.selectOne(any(QueryWrapper.class))).thenReturn(null);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(NotFoundException.class,
                    () -> expenseService.deleteExpense("trip-1", "expense-1"));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void deleteExpense_rejectsOtherUserExpenseIdInOwnedTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        Expense existing = expense("expense-from-u-2", "food", new BigDecimal("100.00"), new BigDecimal("100.00"));
        existing.setUserId("u-2");
        when(expenseMapper.selectOne(any(QueryWrapper.class))).thenReturn(existing);

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class,
                    () -> expenseService.deleteExpense("trip-1", "expense-from-u-2"));
            verify(expenseMapper, never()).delete(any(UpdateWrapper.class));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void createExpense_linksAttractionNode() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(false);
        ItineraryNode node = attractionNode("node-1", "浅草寺");
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);
        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(node));
        doAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId("expense-1");
            expense.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            return 1;
        }).when(expenseMapper).insert(any(Expense.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.createExpense("trip-1", new ExpenseCreateReq(
                    new BigDecimal("88.00"),
                    "CNY",
                    "attraction",
                    "门票",
                    LocalDateTime.of(2026, 7, 1, 12, 30),
                    null, null, null, null,
                    "node-1",
                    null));
            assertEquals("node-1", resp.linkedNodeId());
            assertEquals("浅草寺", resp.linkedItinerary().title());
            assertEquals("attraction", resp.linkedItinerary().type());
        } finally {
            LoginUserContext.clear();
        }

        ArgumentCaptor<Expense> captor = ArgumentCaptor.forClass(Expense.class);
        verify(expenseMapper).insert(captor.capture());
        assertEquals("node-1", captor.getValue().getLinkedNodeId());
        assertEquals(null, captor.getValue().getLinkedStayGroupId());
    }

    @Test
    void createExpense_rejectsBothLinkFields() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(false);

        LoginUserContext.setUserId("u-1");
        try {
            BizException ex = assertThrows(BizException.class, () -> expenseService.createExpense("trip-1",
                    new ExpenseCreateReq(
                            new BigDecimal("88.00"),
                            "CNY",
                            "hotel",
                            null,
                            LocalDateTime.of(2026, 7, 1, 12, 30),
                            null, null, null, null,
                            "node-1",
                            "stay-1")));
            assertEquals(ErrorCode.EXPENSE_ITINERARY_LINK_INVALID, ex.getErrorCode());
        } finally {
            LoginUserContext.clear();
        }
        verify(expenseMapper, never()).insert(any(Expense.class));
    }

    @Test
    void createExpense_promotesHotelStayNodeToStayGroup() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-1"));
        when(expenseProperties.isMultiCurrencyEnabled()).thenReturn(false);
        ItineraryNode node = hotelCheckInNode("node-hotel", "g-1", "京王广场酒店");
        when(itineraryNodeMapper.selectOne(any(QueryWrapper.class))).thenReturn(node);
        when(itineraryNodeMapper.selectList(any(QueryWrapper.class))).thenReturn(List.of(node));
        doAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId("expense-1");
            expense.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
            return 1;
        }).when(expenseMapper).insert(any(Expense.class));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.createExpense("trip-1", new ExpenseCreateReq(
                    new BigDecimal("2400.00"),
                    "CNY",
                    "hotel",
                    null,
                    LocalDateTime.of(2026, 7, 1, 12, 30),
                    null, null, null, null,
                    "node-hotel",
                    null));
            assertEquals(null, resp.linkedNodeId());
            assertEquals("g-1", resp.linkedStayGroupId());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getSummary_calculatesCorrectBreakdown() {
        Trip trip = trip("u-1");
        trip.setStartDate(LocalDate.of(2026, 7, 1));
        trip.setEndDate(LocalDate.of(2026, 7, 5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);

        when(expenseMapper.sumVisibleAmountCnyByTripIdAndUserId("trip-1", "u-1"))
                .thenReturn(new BigDecimal("8560.00"));
        when(expenseMapper.categoryVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of(
                new ExpenseMapper.CategorySummary("food", new BigDecimal("2100.00"), 12),
                new ExpenseMapper.CategorySummary("transport", new BigDecimal("1800.00"), 8),
                new ExpenseMapper.CategorySummary("hotel", new BigDecimal("3000.00"), 5),
                new ExpenseMapper.CategorySummary("attraction", new BigDecimal("860.00"), 6),
                new ExpenseMapper.CategorySummary("shopping", new BigDecimal("800.00"), 3)
        ));

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.getSummary("trip-1");

            assertEquals("8560.00", resp.totalCny());
            assertEquals("1712.00", resp.perDayCny());
            assertEquals(34, resp.expenseCount());
            assertEquals(5, resp.breakdown().size());
            assertEquals("food", resp.breakdown().getFirst().category());
            assertEquals("2100.00", resp.breakdown().getFirst().amountCny());
            assertEquals(12, resp.breakdown().getFirst().count());
            assertEquals(0.245, resp.breakdown().getFirst().ratio());
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getSummary_rejectsOtherUserTrip() {
        when(tripMapper.selectById("trip-1")).thenReturn(trip("u-2"));

        LoginUserContext.setUserId("u-1");
        try {
            assertThrows(ForbiddenException.class, () -> expenseService.getSummary("trip-1"));
        } finally {
            LoginUserContext.clear();
        }
    }

    @Test
    void getSummary_returnsZeroForEmptyExpenses() {
        Trip trip = trip("u-1");
        trip.setStartDate(LocalDate.of(2026, 7, 1));
        trip.setEndDate(LocalDate.of(2026, 7, 5));
        when(tripMapper.selectById("trip-1")).thenReturn(trip);
        when(expenseMapper.sumVisibleAmountCnyByTripIdAndUserId("trip-1", "u-1")).thenReturn(BigDecimal.ZERO);
        when(expenseMapper.categoryVisibleByTripIdAndUserId("trip-1", "u-1")).thenReturn(List.of());

        LoginUserContext.setUserId("u-1");
        try {
            var resp = expenseService.getSummary("trip-1");

            assertEquals("0.00", resp.totalCny());
            assertEquals("0.00", resp.perDayCny());
            assertEquals(0, resp.expenseCount());
            assertEquals(0, resp.breakdown().size());
        } finally {
            LoginUserContext.clear();
        }
    }

    private Trip trip(String userId) {
        Trip trip = new Trip();
        trip.setId("trip-1");
        trip.setUserId(userId);
        trip.setTitle("东京 5 日");
        trip.setStartDate(LocalDate.of(2026, 7, 1));
        // Keep the fixture start date so spentAt=2026-07-01 stays valid, but do not
        // let "today" after that week mark the trip completed and lock writes.
        trip.setEndDate(DateTimeUtil.todayBusiness().plusDays(30));
        trip.setBaseCurrency("CNY");
        return trip;
    }

    private Expense expense(String id, String category, BigDecimal amountInput, BigDecimal amountCny) {
        Expense expense = new Expense();
        expense.setId(id);
        expense.setTripId("trip-1");
        expense.setUserId("u-1");
        expense.setAmountInput(amountInput);
        expense.setCurrency("JPY");
        expense.setAmountCny(amountCny);
        expense.setExchangeRate(new BigDecimal("0.04830000"));
        expense.setCategory(category);
        expense.setSpentAt(LocalDateTime.of(2026, 7, 1, 12, 30));
        expense.setCreatedAt(LocalDateTime.of(2026, 7, 1, 12, 31));
        return expense;
    }

    private TripMember member(String id, String userId) {
        TripMember member = new TripMember();
        member.setId(id);
        member.setTripId("trip-1");
        member.setUserId(userId);
        member.setRole("editor");
        member.setStatus("active");
        return member;
    }

    private ExpenseSplit split(String memberId) {
        ExpenseSplit split = new ExpenseSplit();
        split.setMemberId(memberId);
        return split;
    }

    private ItineraryNode attractionNode(String id, String title) {
        ItineraryNode node = new ItineraryNode();
        node.setId(id);
        node.setTripId("trip-1");
        node.setTitle(title);
        node.setLocationName(title);
        node.setType("attraction");
        node.setDetailsJson("{}");
        return node;
    }

    private ItineraryNode hotelCheckInNode(String id, String stayGroupId, String title) {
        ItineraryNode node = new ItineraryNode();
        node.setId(id);
        node.setTripId("trip-1");
        node.setTitle(title);
        node.setLocationName(title);
        node.setType("hotel");
        node.setDetailsJson("{\"kind\":\"hotel\",\"stayGroupId\":\"" + stayGroupId
                + "\",\"stayRole\":\"check_in\"}");
        return node;
    }
}
