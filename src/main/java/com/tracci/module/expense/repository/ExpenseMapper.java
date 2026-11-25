package com.tracci.module.expense.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tracci.module.expense.entity.Expense;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ExpenseMapper extends BaseMapper<Expense> {

    String SCOPE_GROUP = "group";

    @Update("""
            UPDATE expenses
               SET linked_node_id = NULL,
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND linked_node_id = #{nodeId}
               AND deleted_at IS NULL
            """)
    int clearLinkedNodeId(@Param("tripId") String tripId, @Param("nodeId") String nodeId);

    @Update("""
            UPDATE expenses
               SET linked_stay_group_id = NULL,
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND linked_stay_group_id = #{stayGroupId}
               AND deleted_at IS NULL
            """)
    int clearLinkedStayGroupId(@Param("tripId") String tripId, @Param("stayGroupId") String stayGroupId);

    @Update("""
            UPDATE expenses
               SET linked_stay_group_id = NULL,
                   linked_node_id = #{keepNodeId},
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND linked_stay_group_id = #{stayGroupId}
               AND deleted_at IS NULL
            """)
    int retargetStayGroupToNode(
            @Param("tripId") String tripId,
            @Param("stayGroupId") String stayGroupId,
            @Param("keepNodeId") String keepNodeId);


    @Update("""
            UPDATE expenses
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    int softDeleteByTripId(@Param("tripId") String tripId, @Param("userId") String userId);

    @Update("""
            UPDATE expenses
               SET deleted_at = NOW(),
                   updated_at = NOW()
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    int softDeleteAllByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COALESCE(SUM(amount_cny), 0)
              FROM expenses
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
            """)
    java.math.BigDecimal sumAmountCnyByTripIdAndUserId(
            @Param("tripId") String tripId,
            @Param("userId") String userId);

    @Select("""
            SELECT category,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
             GROUP BY category
             ORDER BY amount_cny DESC
            """)
    List<CategorySummary> categorySummaryByTripIdAndUserId(
            @Param("tripId") String tripId,
            @Param("userId") String userId);

    @Select("""
            SELECT COALESCE(SUM(amount_cny), 0)
              FROM expenses
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
            """)
    java.math.BigDecimal sumAmountCnyByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT category,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
             GROUP BY category
             ORDER BY amount_cny DESC
            """)
    List<CategorySummary> categorySummaryByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COALESCE(SUM(amount_cny), 0)
              FROM expenses
             WHERE user_id = #{userId}
               AND deleted_at IS NULL
            """)
    java.math.BigDecimal sumAmountCnyByUserId(@Param("userId") String userId);

    @Select("""
            <script>
            SELECT COALESCE(SUM(amount_cny), 0)
              FROM expenses
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
            </script>
            """)
    java.math.BigDecimal sumAmountCnyByUserIdAndTripIds(
            @Param("userId") String userId,
            @Param("tripIds") List<String> tripIds);

    @Select("""
            SELECT category,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE user_id = #{userId}
               AND deleted_at IS NULL
             GROUP BY category
             ORDER BY amount_cny DESC
            """)
    List<CategorySummary> categorySummaryByUserId(@Param("userId") String userId);

    @Select("""
            <script>
            SELECT category,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
             GROUP BY category
             ORDER BY amount_cny DESC
            </script>
            """)
    List<CategorySummary> categorySummaryByUserIdAndTripIds(
            @Param("userId") String userId,
            @Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT trip_id,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny
              FROM expenses
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
             GROUP BY trip_id
            </script>
            """)
    List<TripAmountSummary> sumAmountCnyByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT COALESCE(SUM(amount_cny), 0)
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
            </script>
            """)
    java.math.BigDecimal sumAmountCnyByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT category,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
             GROUP BY category
             ORDER BY amount_cny DESC
            </script>
            """)
    List<CategorySummary> categorySummaryByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT trip_id,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
             GROUP BY trip_id
            </script>
            """)
    List<TripAmountSummary> sumAmountCnyGroupByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND deleted_at IS NULL
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countByTripIds(@Param("tripIds") List<String> tripIds);

    @Select("""
            SELECT DATE(spent_at) AS spent_date,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny
              FROM expenses
             WHERE trip_id = #{tripId}
               AND user_id = #{userId}
               AND deleted_at IS NULL
             GROUP BY DATE(spent_at)
             ORDER BY spent_date ASC
            """)
    List<DailyAmountSummary> dailyAmountCnyByTripIdAndUserId(
            @Param("tripId") String tripId,
            @Param("userId") String userId);

    @Select("""
            SELECT DATE(spent_at) AS spent_date,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny
              FROM expenses
             WHERE trip_id = #{tripId}
               AND deleted_at IS NULL
             GROUP BY DATE(spent_at)
             ORDER BY spent_date ASC
            """)
    List<DailyAmountSummary> dailyAmountCnyByTripId(@Param("tripId") String tripId);

    @Select("""
            <script>
            SELECT TO_CHAR(spent_at, 'YYYY-MM') AS expense_month,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE user_id = #{userId}
               AND trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND spent_at &gt;= #{startTime}
               AND spent_at &lt; #{endTime}
               AND deleted_at IS NULL
             GROUP BY TO_CHAR(spent_at, 'YYYY-MM')
             ORDER BY expense_month ASC
            </script>
            """)
    List<MonthlyAmountSummary> monthlyAmountCnyByUserIdAndTripIds(
            @Param("userId") String userId,
            @Param("tripIds") List<String> tripIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            <script>
            SELECT TO_CHAR(spent_at, 'YYYY-MM') AS expense_month,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND spent_at &gt;= #{startTime}
               AND spent_at &lt; #{endTime}
               AND deleted_at IS NULL
             GROUP BY TO_CHAR(spent_at, 'YYYY-MM')
             ORDER BY expense_month ASC
            </script>
            """)
    List<MonthlyAmountSummary> monthlyAmountCnyByTripIds(
            @Param("tripIds") List<String> tripIds,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Select("""
            SELECT payer_member_id AS member_id,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny
              FROM expenses
             WHERE trip_id = #{tripId}
               AND expense_scope = 'group'
               AND payer_member_id IS NOT NULL
               AND deleted_at IS NULL
             GROUP BY payer_member_id
            """)
    List<MemberAmountSummary> sumPaidByTripId(@Param("tripId") String tripId);

    @Select("""
            SELECT COUNT(*)
              FROM expenses
             WHERE trip_id = #{tripId}
               AND expense_scope = 'group'
               AND payer_member_id = #{memberId}
               AND deleted_at IS NULL
            """)
    long countGroupExpensesByPayerMemberId(
            @Param("tripId") String tripId,
            @Param("memberId") String memberId);

    @Select("""
            SELECT COALESCE(SUM(amount_cny), 0)
              FROM expenses
             WHERE trip_id = #{tripId}
               AND (expense_scope = 'group' OR user_id = #{userId})
               AND deleted_at IS NULL
            """)
    java.math.BigDecimal sumVisibleAmountCnyByTripIdAndUserId(
            @Param("tripId") String tripId,
            @Param("userId") String userId);

    @Select("""
            SELECT category,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id = #{tripId}
               AND (expense_scope = 'group' OR user_id = #{userId})
               AND deleted_at IS NULL
             GROUP BY category
             ORDER BY amount_cny DESC
            """)
    List<CategorySummary> categoryVisibleByTripIdAndUserId(
            @Param("tripId") String tripId,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT trip_id,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND (expense_scope = 'group' OR user_id = #{userId})
               AND deleted_at IS NULL
             GROUP BY trip_id
            </script>
            """)
    List<TripAmountSummary> sumVisibleAmountCnyByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT category,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND (expense_scope = 'group' OR user_id = #{userId})
               AND deleted_at IS NULL
             GROUP BY category
             ORDER BY amount_cny DESC
            </script>
            """)
    List<CategorySummary> categoryVisibleByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT trip_id,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND (expense_scope = 'group' OR user_id = #{userId})
               AND deleted_at IS NULL
             GROUP BY trip_id
            </script>
            """)
    List<TripCountSummary> countVisibleByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId);

    @Select("""
            SELECT DATE(spent_at) AS spent_date,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny
              FROM expenses
             WHERE trip_id = #{tripId}
               AND (expense_scope = 'group' OR user_id = #{userId})
               AND deleted_at IS NULL
             GROUP BY DATE(spent_at)
             ORDER BY spent_date ASC
            """)
    List<DailyAmountSummary> dailyVisibleByTripIdAndUserId(
            @Param("tripId") String tripId,
            @Param("userId") String userId);

    @Select("""
            <script>
            SELECT TO_CHAR(spent_at, 'YYYY-MM') AS expense_month,
                   COALESCE(SUM(amount_cny), 0) AS amount_cny,
                   COUNT(*) AS cnt
              FROM expenses
             WHERE trip_id IN
               <foreach collection="tripIds" item="tripId" open="(" separator="," close=")">
                   #{tripId}
               </foreach>
               AND (expense_scope = 'group' OR user_id = #{userId})
               AND spent_at &gt;= #{startTime}
               AND spent_at &lt; #{endTime}
               AND deleted_at IS NULL
             GROUP BY TO_CHAR(spent_at, 'YYYY-MM')
             ORDER BY expense_month ASC
            </script>
            """)
    List<MonthlyAmountSummary> monthlyVisibleByTripIdsAndUserId(
            @Param("tripIds") List<String> tripIds,
            @Param("userId") String userId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    record CategorySummary(String category, java.math.BigDecimal amountCny, long cnt) {
    }

    record TripAmountSummary(String tripId, java.math.BigDecimal amountCny) {
    }

    record TripCountSummary(String tripId, long cnt) {
    }

    record DailyAmountSummary(LocalDate spentDate, java.math.BigDecimal amountCny) {
    }

    record MonthlyAmountSummary(String expenseMonth, java.math.BigDecimal amountCny, long cnt) {
    }

    record MemberAmountSummary(String memberId, java.math.BigDecimal amountCny) {
    }
}
