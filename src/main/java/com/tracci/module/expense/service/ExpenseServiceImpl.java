package com.tracci.module.expense.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.tracci.common.context.LoginUserContext;
import com.tracci.common.exception.BizException;
import com.tracci.common.exception.ForbiddenException;
import com.tracci.common.exception.NotFoundException;
import com.tracci.common.response.ErrorCode;
import com.tracci.common.response.PageResponse;
import com.tracci.common.util.DateTimeUtil;
import com.tracci.integration.exchangerate.ExchangeRateClient;
import com.tracci.module.expense.config.ExpenseProperties;
import com.tracci.module.expense.dto.request.ExpenseCreateReq;
import com.tracci.module.expense.dto.request.ExpenseUpdateReq;
import com.tracci.module.expense.dto.response.ExpenseLinkedItineraryResp;
import com.tracci.module.expense.dto.response.ExpenseResp;
import com.tracci.module.expense.dto.response.ExpenseSettlementResp;
import com.tracci.module.expense.dto.response.ExpenseSummaryResp;
import com.tracci.module.expense.entity.Expense;
import com.tracci.module.expense.entity.ExpenseSplit;
import com.tracci.module.expense.enums.ExpenseCategory;
import com.tracci.module.expense.repository.ExpenseMapper;
import com.tracci.module.expense.repository.ExpenseSplitMapper;
import com.tracci.module.trip.dto.response.TripActivityLogResp;
import com.tracci.module.trip.entity.ItineraryNode;
import com.tracci.module.trip.entity.Trip;
import com.tracci.module.trip.entity.TripMember;
import com.tracci.module.trip.enums.ItineraryNodeType;
import com.tracci.module.trip.repository.ItineraryNodeMapper;
import com.tracci.module.trip.support.ItineraryNodeDetailsSupport;
import com.tracci.module.trip.enums.TripStatus;
import com.tracci.module.trip.repository.TripMemberMapper;
import com.tracci.module.trip.service.TripAccessService;
import com.tracci.module.trip.enums.TripPermission;
import com.tracci.module.trip.service.TripActivityLogService;
import com.tracci.module.user.entity.User;
import com.tracci.module.user.repository.UserMapper;
import com.tracci.module.user.support.UserDisplaySupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private static final int MAX_NOTE_LENGTH = 200;
    private static final String CURRENCY_CNY = "CNY";
    private static final String SCOPE_PERSONAL = "personal";
    private static final String SPLIT_EQUAL = "equal";
    private static final BigDecimal CNY_EXCHANGE_RATE = new BigDecimal("1.00000000");

    private final ExpenseMapper expenseMapper;
    private final ExpenseSplitMapper expenseSplitMapper;
    private final ExchangeRateClient exchangeRateClient;
    private final ExpenseProperties expenseProperties;
    private final TripAccessService tripAccessService;
    private final TripMemberMapper tripMemberMapper;
    private final TripActivityLogService activityLogService;
    private final UserMapper userMapper;
    private final ItineraryNodeMapper itineraryNodeMapper;

    @Override
    @Transactional
    public ExpenseResp createExpense(String tripId, ExpenseCreateReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        Trip trip = access.trip();
        requireTripContentMutable(trip);

        BigDecimal amountInput = requirePositiveAmount(req.amountInput());
        String currency = normalizeCurrency(req.currency());
        String category = normalizeCategory(req.category());
        String note = normalizeOptional(req.note());
        LocalDateTime spentAt = requireSpentAt(req.spentAt());
        validateSpentAtWithinTrip(spentAt, trip);

        BigDecimal exchangeRate = resolveExchangeRate(currency);
        BigDecimal amountCny = calculateAmountCny(amountInput, exchangeRate);

        Expense expense = new Expense();
        expense.setTripId(tripId);
        expense.setUserId(userId);
        expense.setAmountInput(amountInput);
        expense.setCurrency(currency);
        expense.setAmountCny(amountCny);
        expense.setExchangeRate(exchangeRate);
        expense.setCategory(category);
        expense.setNote(note);
        expense.setSpentAt(spentAt);
        applySplitFieldsForCreate(expense, req, access.member());
        applyItineraryLink(expense, req.linkedNodeId(), req.linkedStayGroupId());
        expenseMapper.insert(expense);
        if (ExpenseMapper.SCOPE_GROUP.equals(expense.getExpenseScope())) {
            createEqualSplits(expense, req.participantMemberIds());
        }
        recordActivity(tripId, userId, memberId(access), "EXPENSE_CREATED", "EXPENSE", expense.getId(),
                changes(change("amountInput", null, money(expense.getAmountInput())),
                        change("category", null, expense.getCategory()),
                        change("expenseScope", null, expense.getExpenseScope())),
                "创建了账单");
        return toResp(expense);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ExpenseResp> listExpenses(String tripId, int page, int pageSize, String category) {
        String userId = LoginUserContext.requireUserId();
        requireReadAccess(tripId, userId);

        int safePage = Math.max(page, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        QueryWrapper<Expense> query = new QueryWrapper<Expense>()
                .eq("trip_id", tripId)
                .and(visible -> visible.eq("expense_scope", ExpenseMapper.SCOPE_GROUP).or().eq("user_id", userId))
                .orderByDesc("spent_at");
        if (StringUtils.hasText(category)) {
            query.eq("category", normalizeCategory(category));
        }
        Page<Expense> result = expenseMapper.selectPage(new Page<>(safePage, safePageSize), query);
        Map<String, ExpenseLinkedItineraryResp> snapshots = loadLinkSnapshots(tripId, result.getRecords());
        return PageResponse.of(
                result.getRecords().stream().map(item -> toResp(item, snapshots)).toList(),
                result.getTotal(),
                safePage,
                safePageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseSummaryResp getSummary(String tripId) {
        String userId = LoginUserContext.requireUserId();
        Trip trip = requireReadAccess(tripId, userId).trip();

        BigDecimal totalCny = zeroIfNull(expenseMapper.sumVisibleAmountCnyByTripIdAndUserId(tripId, userId));
        long totalDays = ChronoUnit.DAYS.between(trip.getStartDate(), trip.getEndDate()) + 1;
        BigDecimal perDayCny = totalDays > 0
                ? totalCny.divide(BigDecimal.valueOf(totalDays), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        List<ExpenseMapper.CategorySummary> summaries =
                safeList(expenseMapper.categoryVisibleByTripIdAndUserId(tripId, userId));
        long expenseCount = summaries.stream().mapToLong(ExpenseMapper.CategorySummary::cnt).sum();
        BigDecimal finalTotalCny = totalCny;
        List<ExpenseSummaryResp.CategoryBreakdown> breakdown = summaries.stream()
                .map(s -> new ExpenseSummaryResp.CategoryBreakdown(
                        s.category(),
                        money(s.amountCny()),
                        s.cnt(),
                        finalTotalCny.compareTo(BigDecimal.ZERO) > 0
                                ? s.amountCny().divide(finalTotalCny, 3, RoundingMode.HALF_UP).doubleValue()
                                : 0.0))
                .toList();
        return new ExpenseSummaryResp(money(totalCny), money(perDayCny), expenseCount, breakdown);
    }

    @Override
    @Transactional
    public ExpenseResp updateExpense(String tripId, String expenseId, ExpenseUpdateReq req) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        Trip trip = access.trip();
        requireTripContentMutable(trip);
        Expense expense = expenseMapper.selectOne(new QueryWrapper<Expense>()
                .eq("id", expenseId)
                .eq("trip_id", tripId));
        if (expense == null) {
            throw new NotFoundException(ErrorCode.EXPENSE_NOT_FOUND);
        }
        requireExpenseMutationAccess(expense, userId);
        String oldAmount = money(expense.getAmountInput());
        String oldCategory = expense.getCategory();
        String oldNote = expense.getNote();
        String oldScope = defaultScope(expense.getExpenseScope());

        BigDecimal newAmountInput = expense.getAmountInput();
        String newCurrency = expense.getCurrency();
        boolean amountChanged = false;
        boolean currencyChanged = false;
        if (req.amountInput() != null) {
            newAmountInput = requirePositiveAmount(req.amountInput());
            amountChanged = true;
        }
        if (req.currency() != null) {
            newCurrency = normalizeCurrency(req.currency());
            currencyChanged = !newCurrency.equals(expense.getCurrency());
        }
        if (req.category() != null) {
            expense.setCategory(normalizeCategory(req.category()));
        }
        if (req.note() != null) {
            expense.setNote(normalizeOptional(req.note()));
        }
        if (req.spentAt() != null) {
            validateSpentAtWithinTrip(req.spentAt(), trip);
            expense.setSpentAt(req.spentAt());
        }
        if (amountChanged || currencyChanged) {
            BigDecimal exchangeRate = currencyChanged ? resolveExchangeRate(newCurrency) : expense.getExchangeRate();
            expense.setAmountInput(newAmountInput);
            expense.setCurrency(newCurrency);
            expense.setExchangeRate(exchangeRate);
            expense.setAmountCny(calculateAmountCny(newAmountInput, exchangeRate));
        }
        applySplitFieldsForUpdate(expense, req, access.member());
        if (req.linkedNodeId() != null || req.linkedStayGroupId() != null) {
            applyItineraryLink(expense, req.linkedNodeId(), req.linkedStayGroupId());
        }
        List<String> splitParticipantIds = ExpenseMapper.SCOPE_GROUP.equals(expense.getExpenseScope())
                ? resolveParticipantIdsForUpdate(expense, req.participantMemberIds())
                : List.of();

        int updated = expenseMapper.updateById(expense);
        if (updated == 0) {
            throw new NotFoundException(ErrorCode.EXPENSE_NOT_FOUND);
        }
        if (expenseSplitMapper != null && (ExpenseMapper.SCOPE_GROUP.equals(oldScope) || ExpenseMapper.SCOPE_GROUP.equals(expense.getExpenseScope()))) {
            expenseSplitMapper.softDeleteByExpenseId(expense.getId());
        }
        if (ExpenseMapper.SCOPE_GROUP.equals(expense.getExpenseScope())) {
            createEqualSplits(expense, splitParticipantIds);
        }
        recordActivity(tripId, userId, memberId(access), "EXPENSE_UPDATED", "EXPENSE", expenseId,
                changes(change("amountInput", oldAmount, money(expense.getAmountInput())),
                        change("category", oldCategory, expense.getCategory()),
                        change("note", oldNote, expense.getNote()),
                        change("expenseScope", oldScope, expense.getExpenseScope())),
                "更新了账单");
        return toResp(expense);
    }

    @Override
    @Transactional
    public void deleteExpense(String tripId, String expenseId) {
        String userId = LoginUserContext.requireUserId();
        TripAccessService.TripAccess access = requireWriteAccess(tripId, userId);
        requireTripContentMutable(access.trip());
        Expense expense = expenseMapper.selectOne(new QueryWrapper<Expense>()
                .eq("id", expenseId)
                .eq("trip_id", tripId));
        if (expense == null) {
            throw new NotFoundException(ErrorCode.EXPENSE_NOT_FOUND);
        }
        requireExpenseMutationAccess(expense, userId);
        int deleted = expenseMapper.delete(new UpdateWrapper<Expense>()
                .eq("id", expenseId)
                .eq("trip_id", tripId));
        if (deleted == 0) {
            throw new NotFoundException(ErrorCode.EXPENSE_NOT_FOUND);
        }
        if (expenseSplitMapper != null) {
            expenseSplitMapper.softDeleteByExpenseId(expenseId);
        }
        recordActivity(tripId, userId, memberId(access), "EXPENSE_DELETED", "EXPENSE", expenseId, List.of(), "删除了账单");
    }

    @Override
    @Transactional(readOnly = true)
    public ExpenseSettlementResp getSettlement(String tripId) {
        String userId = LoginUserContext.requireUserId();
        requireReadAccess(tripId, userId);
        List<TripMember> members = tripMemberMapper.listActiveByTripId(tripId);
        List<String> memberUserIds = members.stream()
                .map(TripMember::getUserId)
                .distinct()
                .toList();
        Map<String, User> usersById = memberUserIds.isEmpty()
                ? Map.of()
                : userMapper.selectBatchIds(memberUserIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user, (left, right) -> left));
        Map<String, BigDecimal> paid = expenseMapper.sumPaidByTripId(tripId).stream()
                .collect(Collectors.toMap(ExpenseMapper.MemberAmountSummary::memberId,
                        item -> zeroIfNull(item.amountCny()), (left, right) -> left));
        Map<String, BigDecimal> share = expenseSplitMapper.sumShareByTripId(tripId).stream()
                .collect(Collectors.toMap(ExpenseSplitMapper.MemberAmountSummary::memberId,
                        item -> zeroIfNull(item.amountCny()), (left, right) -> left));
        BigDecimal totalGroupAmount = paid.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ExpenseSettlementResp.MemberSettlement> memberSettlements = members.stream()
                .map(member -> {
                    BigDecimal paidAmount = paid.getOrDefault(member.getId(), BigDecimal.ZERO);
                    BigDecimal shareAmount = share.getOrDefault(member.getId(), BigDecimal.ZERO);
                    User user = usersById.get(member.getUserId());
                    return new ExpenseSettlementResp.MemberSettlement(
                            member.getId(), member.getUserId(), UserDisplaySupport.nickname(user),
                            money(paidAmount), money(shareAmount), money(paidAmount.subtract(shareAmount)));
                })
                .toList();
        return new ExpenseSettlementResp(tripId, CURRENCY_CNY, money(totalGroupAmount),
                memberSettlements, suggestTransfers(memberSettlements));
    }

    private void applySplitFieldsForCreate(Expense expense, ExpenseCreateReq req, TripMember currentMember) {
        String scope = normalizeScope(req.expenseScope());
        expense.setExpenseScope(scope);
        expense.setCreatedByMemberId(currentMember == null ? null : currentMember.getId());
        if (SCOPE_PERSONAL.equals(scope)) {
            expense.setPayerMemberId(null);
            expense.setSplitMethod(null);
            return;
        }
        validateParticipantMembers(expense.getTripId(), req.payerMemberId(), req.participantMemberIds());
        expense.setPayerMemberId(req.payerMemberId());
        expense.setSplitMethod(normalizeSplitMethod(req.splitMethod()));
    }

    private void applySplitFieldsForUpdate(Expense expense, ExpenseUpdateReq req, TripMember currentMember) {
        String scope = req.expenseScope() == null ? defaultScope(expense.getExpenseScope()) : normalizeScope(req.expenseScope());
        expense.setExpenseScope(scope);
        if (!StringUtils.hasText(expense.getCreatedByMemberId())) {
            expense.setCreatedByMemberId(currentMember == null ? null : currentMember.getId());
        }
        if (SCOPE_PERSONAL.equals(scope)) {
            expense.setPayerMemberId(null);
            expense.setSplitMethod(null);
            return;
        }
        String payerMemberId = StringUtils.hasText(req.payerMemberId()) ? req.payerMemberId() : expense.getPayerMemberId();
        List<String> participantIds = req.participantMemberIds();
        if (participantIds == null || participantIds.isEmpty()) {
            participantIds = expenseSplitMapper.listByExpenseId(expense.getId()).stream().map(ExpenseSplit::getMemberId).toList();
        }
        validateParticipantMembers(expense.getTripId(), payerMemberId, participantIds);
        expense.setPayerMemberId(payerMemberId);
        expense.setSplitMethod(normalizeSplitMethod(req.splitMethod() == null ? expense.getSplitMethod() : req.splitMethod()));
    }

    private void createEqualSplits(Expense expense, List<String> rawParticipantIds) {
        List<TripMember> members = validateParticipantMembers(expense.getTripId(), expense.getPayerMemberId(), rawParticipantIds);
        BigDecimal count = BigDecimal.valueOf(members.size());
        BigDecimal baseInput = expense.getAmountInput().divide(count, 2, RoundingMode.DOWN);
        BigDecimal baseCny = expense.getAmountCny().divide(count, 2, RoundingMode.DOWN);
        BigDecimal assignedInput = BigDecimal.ZERO;
        BigDecimal assignedCny = BigDecimal.ZERO;
        for (int i = 0; i < members.size(); i++) {
            boolean last = i == members.size() - 1;
            BigDecimal shareInput = last ? expense.getAmountInput().subtract(assignedInput) : baseInput;
            BigDecimal shareCny = last ? expense.getAmountCny().subtract(assignedCny) : baseCny;
            assignedInput = assignedInput.add(shareInput);
            assignedCny = assignedCny.add(shareCny);
            TripMember member = members.get(i);
            ExpenseSplit split = new ExpenseSplit();
            split.setExpenseId(expense.getId());
            split.setTripId(expense.getTripId());
            split.setMemberId(member.getId());
            split.setUserId(member.getUserId());
            split.setShareAmountInput(shareInput);
            split.setShareAmountCny(shareCny);
            expenseSplitMapper.insert(split);
        }
    }

    private List<String> resolveParticipantIdsForUpdate(Expense expense, List<String> requestParticipantIds) {
        if (requestParticipantIds != null && !requestParticipantIds.isEmpty()) {
            return requestParticipantIds;
        }
        return expenseSplitMapper.listByExpenseId(expense.getId()).stream().map(ExpenseSplit::getMemberId).toList();
    }

    private List<TripMember> validateParticipantMembers(String tripId, String payerMemberId, List<String> participantMemberIds) {
        if (!StringUtils.hasText(payerMemberId)) {
            throw new BizException(ErrorCode.EXPENSE_SPLIT_INVALID, "payerMemberId 不能为空");
        }
        if (participantMemberIds == null || participantMemberIds.isEmpty()) {
            throw new BizException(ErrorCode.EXPENSE_SPLIT_INVALID, "participantMemberIds 不能为空");
        }
        Map<String, TripMember> activeMembers = tripMemberMapper.listActiveByTripId(tripId).stream()
                .collect(Collectors.toMap(TripMember::getId, member -> member, (left, right) -> left));
        if (!activeMembers.containsKey(payerMemberId)) {
            throw new BizException(ErrorCode.EXPENSE_SPLIT_INVALID, "payerMemberId 不属于当前旅行");
        }
        List<TripMember> members = new ArrayList<>();
        for (String memberId : participantMemberIds.stream().filter(StringUtils::hasText).map(String::trim).distinct().toList()) {
            TripMember member = activeMembers.get(memberId);
            if (member == null) {
                throw new BizException(ErrorCode.EXPENSE_SPLIT_INVALID, "participantMemberIds 包含非当前旅行成员");
            }
            members.add(member);
        }
        if (members.isEmpty()) {
            throw new BizException(ErrorCode.EXPENSE_SPLIT_INVALID, "participantMemberIds 不能为空");
        }
        return members;
    }

    private TripAccessService.TripAccess requireReadAccess(String tripId, String userId) {
        return tripAccessService.requirePermission(tripId, userId, TripPermission.READ);
    }

    private TripAccessService.TripAccess requireWriteAccess(String tripId, String userId) {
        return tripAccessService.requirePermission(tripId, userId, TripPermission.EDIT_CONTENT);
    }

    /** 已结束旅程内容锁定：禁止新增/修改/删除开支 */
    private void requireTripContentMutable(Trip trip) {
        if (trip != null && TripStatus.isCompleted(DateTimeUtil.todayBusiness(), trip.getStartDate(), trip.getEndDate())) {
            throw new BizException(ErrorCode.TRIP_COMPLETED_CONTENT_LOCKED);
        }
    }

    private String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "currency 不能为空");
        }
        String value = currency.trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "currency 必须是 3 位 ISO 4217 代码");
        }
        return value;
    }

    private String normalizeCategory(String category) {
        if (!StringUtils.hasText(category)) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "category 不能为空");
        }
        String value = category.trim();
        // 系统分类
        if (ExpenseCategory.isSystem(value)) {
            return value;
        }
        // 自定义分类：custom:<uuid>
        if (ExpenseCategory.isCustom(value) && isValidUuid(ExpenseCategory.customIdOf(value))) {
            return value;
        }
        throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "category 不合法");
    }

    private boolean isValidUuid(String value) {
        if (!StringUtils.hasText(value)) return false;
        return value.matches("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_NOTE_LENGTH) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "note 长度不能超过 200");
        }
        return StringUtils.hasText(trimmed) ? trimmed : null;
    }

    private BigDecimal requirePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "amountInput 必须大于 0");
        }
        if (amount.precision() - amount.scale() > 10 || amount.scale() > 2) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "amountInput 最多 10 位整数和 2 位小数");
        }
        return amount;
    }

    private LocalDateTime requireSpentAt(LocalDateTime spentAt) {
        if (spentAt == null) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "spentAt 不能为空");
        }
        return spentAt;
    }

    private void validateSpentAtWithinTrip(LocalDateTime spentAt, Trip trip) {
        if (spentAt == null || trip.getStartDate() == null || trip.getEndDate() == null) {
            return;
        }
        java.time.LocalDate spentDate = spentAt.toLocalDate();
        if (spentDate.isBefore(trip.getStartDate()) || spentDate.isAfter(trip.getEndDate())) {
            throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "spentAt 必须在旅行日期范围内");
        }
    }

    private BigDecimal resolveExchangeRate(String currency) {
        if (!expenseProperties.isMultiCurrencyEnabled()) {
            if (!CURRENCY_CNY.equals(currency)) {
                throw new BizException(ErrorCode.AUTH_PARAM_INVALID, "当前仅支持 CNY，其他币种需启用汇率换算");
            }
            return CNY_EXCHANGE_RATE;
        }
        BigDecimal exchangeRate = exchangeRateClient.getRate(currency);
        if (exchangeRate == null || exchangeRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.EXCHANGE_RATE_UNAVAILABLE, "汇率服务返回无效汇率");
        }
        return exchangeRate;
    }

    private BigDecimal calculateAmountCny(BigDecimal amountInput, BigDecimal exchangeRate) {
        return amountInput.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeScope(String scope) {
        if (!StringUtils.hasText(scope)) {
            return SCOPE_PERSONAL;
        }
        String value = scope.trim().toLowerCase(Locale.ROOT);
        if (!SCOPE_PERSONAL.equals(value) && !ExpenseMapper.SCOPE_GROUP.equals(value)) {
            throw new BizException(ErrorCode.EXPENSE_SPLIT_INVALID, "expenseScope 仅支持 personal / group");
        }
        return value;
    }

    private String defaultScope(String scope) {
        return StringUtils.hasText(scope) ? scope : SCOPE_PERSONAL;
    }

    private String normalizeSplitMethod(String splitMethod) {
        if (!StringUtils.hasText(splitMethod)) {
            return SPLIT_EQUAL;
        }
        String value = splitMethod.trim().toLowerCase(Locale.ROOT);
        if (!SPLIT_EQUAL.equals(value)) {
            throw new BizException(ErrorCode.EXPENSE_SPLIT_INVALID, "splitMethod 第一版仅支持 equal");
        }
        return value;
    }

    private List<ExpenseSettlementResp.SuggestedTransfer> suggestTransfers(List<ExpenseSettlementResp.MemberSettlement> settlements) {
        List<Balance> debtors = new ArrayList<>();
        List<Balance> creditors = new ArrayList<>();
        for (ExpenseSettlementResp.MemberSettlement settlement : settlements) {
            BigDecimal balance = new BigDecimal(settlement.balanceAmount());
            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new Balance(settlement.memberId(), balance.abs()));
            } else if (balance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new Balance(settlement.memberId(), balance));
            }
        }
        debtors.sort(Comparator.comparing(Balance::amount).reversed());
        creditors.sort(Comparator.comparing(Balance::amount).reversed());
        List<ExpenseSettlementResp.SuggestedTransfer> result = new ArrayList<>();
        int debtorIndex = 0;
        int creditorIndex = 0;
        while (debtorIndex < debtors.size() && creditorIndex < creditors.size()) {
            Balance debtor = debtors.get(debtorIndex);
            Balance creditor = creditors.get(creditorIndex);
            BigDecimal amount = debtor.amount().min(creditor.amount()).setScale(2, RoundingMode.HALF_UP);
            if (amount.compareTo(BigDecimal.ZERO) > 0) {
                result.add(new ExpenseSettlementResp.SuggestedTransfer(debtor.memberId(), creditor.memberId(), money(amount)));
            }
            debtors.set(debtorIndex, new Balance(debtor.memberId(), debtor.amount().subtract(amount)));
            creditors.set(creditorIndex, new Balance(creditor.memberId(), creditor.amount().subtract(amount)));
            if (debtors.get(debtorIndex).amount().compareTo(BigDecimal.ZERO) == 0) debtorIndex++;
            if (creditors.get(creditorIndex).amount().compareTo(BigDecimal.ZERO) == 0) creditorIndex++;
        }
        return result;
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String money(BigDecimal value) {
        return zeroIfNull(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String memberId(TripAccessService.TripAccess access) {
        return access != null && access.member() != null ? access.member().getId() : null;
    }

    private void requireExpenseMutationAccess(Expense expense, String userId) {
        if (!ExpenseMapper.SCOPE_GROUP.equals(defaultScope(expense.getExpenseScope()))
                && !userId.equals(expense.getUserId())) {
            throw new ForbiddenException();
        }
    }

    private void recordActivity(String tripId, String userId, String memberId, String action,
                                String targetType, String targetId,
                                List<TripActivityLogResp.FieldChange> changes, String summary) {
        if (activityLogService != null) {
            activityLogService.record(tripId, userId, memberId, action, targetType, targetId, changes, summary);
        }
    }

    private List<TripActivityLogResp.FieldChange> changes(TripActivityLogResp.FieldChange... changes) {
        return java.util.Arrays.stream(changes).filter(Objects::nonNull).toList();
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    private TripActivityLogResp.FieldChange change(String field, Object oldValue, Object newValue) {
        String oldText = oldValue == null ? null : oldValue.toString();
        String newText = newValue == null ? null : newValue.toString();
        return Objects.equals(oldText, newText) ? null : new TripActivityLogResp.FieldChange(field, oldText, newText);
    }

    private ExpenseResp toResp(Expense expense) {
        return toResp(expense, loadLinkSnapshots(expense.getTripId(), List.of(expense)));
    }

    private ExpenseResp toResp(Expense expense, Map<String, ExpenseLinkedItineraryResp> snapshots) {
        return new ExpenseResp(
                expense.getId(),
                expense.getTripId(),
                money(expense.getAmountInput()),
                expense.getCurrency(),
                money(expense.getAmountCny()),
                expense.getExchangeRate().setScale(8, RoundingMode.HALF_UP).toPlainString(),
                expense.getCategory(),
                expense.getNote(),
                DateTimeUtil.toInstantString(expense.getSpentAt()),
                defaultScope(expense.getExpenseScope()),
                expense.getPayerMemberId(),
                participantMemberIds(expense),
                expense.getCreatedByMemberId(),
                expense.getSplitMethod(),
                expense.getLinkedNodeId(),
                expense.getLinkedStayGroupId(),
                snapshotFor(expense, snapshots),
                DateTimeUtil.toInstantString(expense.getCreatedAt()));
    }

    private void applyItineraryLink(Expense expense, String rawNodeId, String rawStayGroupId) {
        String nodeId = blankToNull(rawNodeId);
        String stayGroupId = blankToNull(rawStayGroupId);
        if (nodeId != null && stayGroupId != null) {
            throw new BizException(ErrorCode.EXPENSE_ITINERARY_LINK_INVALID, "linkedNodeId 与 linkedStayGroupId 不能同时填写");
        }
        if (nodeId == null && stayGroupId == null) {
            expense.setLinkedNodeId(null);
            expense.setLinkedStayGroupId(null);
            return;
        }
        if (itineraryNodeMapper == null) {
            throw new BizException(ErrorCode.EXPENSE_ITINERARY_LINK_INVALID, "行程关联暂不可用");
        }
        if (stayGroupId != null) {
            requireStayGroupPrimary(expense.getTripId(), stayGroupId);
            expense.setLinkedNodeId(null);
            expense.setLinkedStayGroupId(stayGroupId);
            return;
        }
        ItineraryNode node = itineraryNodeMapper.selectOne(new QueryWrapper<ItineraryNode>()
                .eq("id", nodeId)
                .eq("trip_id", expense.getTripId()));
        if (node == null) {
            throw new NotFoundException(ErrorCode.ITINERARY_NOT_FOUND);
        }
        Map<String, Object> details = ItineraryNodeDetailsSupport.parseMap(node.getDetailsJson());
        String groupId = ItineraryNodeDetailsSupport.stringField(details, "stayGroupId");
        String stayRole = ItineraryNodeDetailsSupport.stringField(details, "stayRole");
        if (ItineraryNodeDetailsSupport.STAY_ROLE_STAY.equals(stayRole)) {
            throw new BizException(ErrorCode.EXPENSE_ITINERARY_LINK_INVALID, "不能关联住宿中间夜，请选择入住当天或整段住宿");
        }
        if (ItineraryNodeType.HOTEL.value().equals(node.getType()) && StringUtils.hasText(groupId)) {
            expense.setLinkedNodeId(null);
            expense.setLinkedStayGroupId(groupId);
            return;
        }
        expense.setLinkedNodeId(node.getId());
        expense.setLinkedStayGroupId(null);
    }

    private ItineraryNode requireStayGroupPrimary(String tripId, String stayGroupId) {
        List<ItineraryNode> members = itineraryNodeMapper.selectList(new QueryWrapper<ItineraryNode>()
                .eq("trip_id", tripId)
                .eq("type", ItineraryNodeType.HOTEL.value())
                .apply("details_json->>'stayGroupId' = {0}", stayGroupId));
        if (members.isEmpty()) {
            throw new BizException(ErrorCode.EXPENSE_ITINERARY_LINK_INVALID, "linkedStayGroupId 不属于当前旅行");
        }
        return members.stream()
                .filter(node -> ItineraryNodeDetailsSupport.STAY_ROLE_CHECK_IN.equals(
                        ItineraryNodeDetailsSupport.stringField(
                                ItineraryNodeDetailsSupport.parseMap(node.getDetailsJson()), "stayRole")))
                .findFirst()
                .orElse(members.getFirst());
    }

    private Map<String, ExpenseLinkedItineraryResp> loadLinkSnapshots(String tripId, List<Expense> expenses) {
        if (itineraryNodeMapper == null || expenses == null || expenses.isEmpty()) {
            return Map.of();
        }
        Set<String> nodeIds = new HashSet<>();
        Set<String> stayGroupIds = new HashSet<>();
        for (Expense expense : expenses) {
            if (StringUtils.hasText(expense.getLinkedNodeId())) {
                nodeIds.add(expense.getLinkedNodeId());
            }
            if (StringUtils.hasText(expense.getLinkedStayGroupId())) {
                stayGroupIds.add(expense.getLinkedStayGroupId());
            }
        }
        Map<String, ExpenseLinkedItineraryResp> snapshots = new HashMap<>();
        if (!nodeIds.isEmpty()) {
            itineraryNodeMapper.selectList(new QueryWrapper<ItineraryNode>()
                            .eq("trip_id", tripId)
                            .in("id", nodeIds))
                    .forEach(node -> snapshots.put(nodeKey(node.getId()), toLinkedResp(node, null)));
        }
        if (!stayGroupIds.isEmpty()) {
            for (String stayGroupId : stayGroupIds) {
                ItineraryNode primary = requireStayGroupPrimaryOrNull(tripId, stayGroupId);
                if (primary != null) {
                    snapshots.put(stayKey(stayGroupId), toLinkedResp(primary, stayGroupId));
                }
            }
        }
        return snapshots;
    }

    private ItineraryNode requireStayGroupPrimaryOrNull(String tripId, String stayGroupId) {
        try {
            return requireStayGroupPrimary(tripId, stayGroupId);
        } catch (BizException ex) {
            return null;
        }
    }

    private ExpenseLinkedItineraryResp snapshotFor(
            Expense expense, Map<String, ExpenseLinkedItineraryResp> snapshots) {
        if (snapshots == null || snapshots.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(expense.getLinkedStayGroupId())) {
            return snapshots.get(stayKey(expense.getLinkedStayGroupId()));
        }
        if (StringUtils.hasText(expense.getLinkedNodeId())) {
            return snapshots.get(nodeKey(expense.getLinkedNodeId()));
        }
        return null;
    }

    private ExpenseLinkedItineraryResp toLinkedResp(ItineraryNode node, String stayGroupId) {
        String title = StringUtils.hasText(node.getLocationName())
                ? node.getLocationName()
                : StringUtils.hasText(node.getTitle()) ? node.getTitle() : node.getAddress();
        return new ExpenseLinkedItineraryResp(
                stayGroupId == null ? node.getId() : null,
                stayGroupId,
                title,
                node.getType());
    }

    private static String nodeKey(String nodeId) {
        return "node:" + nodeId;
    }

    private static String stayKey(String stayGroupId) {
        return "stay:" + stayGroupId;
    }

    private static String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private List<String> participantMemberIds(Expense expense) {
        if (!ExpenseMapper.SCOPE_GROUP.equals(defaultScope(expense.getExpenseScope())) || expenseSplitMapper == null) {
            return null;
        }
        return expenseSplitMapper.listByExpenseId(expense.getId()).stream()
                .map(ExpenseSplit::getMemberId)
                .toList();
    }

    private record Balance(String memberId, BigDecimal amount) {
    }
}
