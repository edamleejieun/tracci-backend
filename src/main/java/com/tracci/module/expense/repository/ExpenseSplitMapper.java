package com.tracci.module.expense.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.expense.entity.ExpenseSplit;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface ExpenseSplitMapper extends BaseMapper<ExpenseSplit> {

    @Update("""
            UPDATE expense_splits
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE expense_id = #{expenseId}
               AND deleted_at IS NULL
            """)
    int softDeleteByExpenseId(@Param("expenseId") String expenseId);

    @Update("""
            UPDATE expense_splits
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    int softDeleteByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT *
              FROM expense_splits
             WHERE expense_id = #{expenseId}
               AND deleted_at IS NULL
             ORDER BY created_at ASC
            """)
    List<ExpenseSplit> listByExpenseId(@Param("expenseId") String expenseId);

    @Select("""
            SELECT member_id,
                   COALESCE(SUM(share_amount_cny), 0) AS amount_cny
              FROM expense_splits
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
             GROUP BY member_id
            """)
    List<MemberAmountSummary> sumShareByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM expense_splits
             WHERE trip_id = #{tripId}
               AND member_id = #{memberId}
               AND deleted_at IS NULL
            """)
    long countActiveByTripIdAndMemberId(
            @Param("tripId") String tripId,
            @Param("memberId") String memberId);

    record MemberAmountSummary(String memberId, BigDecimal amountCny) {
    }
}
