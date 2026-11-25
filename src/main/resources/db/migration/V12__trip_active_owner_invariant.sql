-- 旅程权限不变量：
-- 1. 同一旅程最多一个 active owner；
-- 2. 存在有效成员的旅程必须存在有效 owner；
-- 3. trips.user_id 与 active owner 保持一致。
--
-- PostgreSQL Flyway migration。按应用内所有权操作相同的顺序加锁，
-- 避免迁移期间并发加入、注销或转让造成数据漂移。
LOCK TABLE trips, trip_members IN SHARE ROW EXCLUSIVE MODE;

-- 多个 active owner 无法安全自动裁决，直接阻止迁移并要求人工处理。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM trip_members
         WHERE role = 'owner'
           AND status = 'active'
           AND deleted_at IS NULL
         GROUP BY trip_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'trip owner invariant violation: duplicate active owners exist; audit trip_members before retrying V12';
    END IF;
END
$$;

-- 对没有 active owner 的历史旅程，若 trips.user_id 对应账号和成员关系仍有效，
-- 则该快照具有唯一、可确定的恢复语义，可安全恢复为 owner。
UPDATE trip_members tm
   SET role = 'owner',
       updated_at = NOW()
  FROM trips t
  JOIN users u
    ON u.id = t.user_id
   AND u.status = 'active'
   AND u.deleted_at IS NULL
 WHERE tm.trip_id = t.id
   AND tm.user_id = t.user_id
   AND tm.status = 'active'
   AND tm.deleted_at IS NULL
   AND t.deleted_at IS NULL
   AND NOT EXISTS (
       SELECT 1
         FROM trip_members existing_owner
        WHERE existing_owner.trip_id = t.id
          AND existing_owner.role = 'owner'
          AND existing_owner.status = 'active'
          AND existing_owner.deleted_at IS NULL
   );

-- 若自动修复后仍有有效成员但没有有效 owner，不能猜测由谁接管。
-- 让迁移失败，运维明确指定 owner 后再重新启动应用。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM trips t
         WHERE t.deleted_at IS NULL
           AND EXISTS (
               SELECT 1
                 FROM trip_members tm
                 JOIN users u
                   ON u.id = tm.user_id
                  AND u.status = 'active'
                  AND u.deleted_at IS NULL
                WHERE tm.trip_id = t.id
                  AND tm.status = 'active'
                  AND tm.deleted_at IS NULL
           )
           AND NOT EXISTS (
               SELECT 1
                 FROM trip_members tm
                 JOIN users u
                   ON u.id = tm.user_id
                  AND u.status = 'active'
                  AND u.deleted_at IS NULL
                WHERE tm.trip_id = t.id
                  AND tm.role = 'owner'
                  AND tm.status = 'active'
                  AND tm.deleted_at IS NULL
           )
    ) THEN
        RAISE EXCEPTION
            'trip owner invariant violation: trips with effective members but no effective owner exist; assign owners before retrying V12';
    END IF;
END
$$;

-- 部分唯一索引只覆盖有效 owner，避免已移除成员扩大索引或参与约束。
CREATE UNIQUE INDEX IF NOT EXISTS uniq_trip_members_active_owner
    ON trip_members (trip_id)
    WHERE role = 'owner'
      AND status = 'active'
      AND deleted_at IS NULL;

-- 以唯一 active owner 为事实源，修正 trips.user_id 查询快照。
UPDATE trips t
   SET user_id = owner_member.user_id,
       updated_at = NOW()
  FROM trip_members owner_member
 WHERE owner_member.trip_id = t.id
   AND owner_member.role = 'owner'
   AND owner_member.status = 'active'
   AND owner_member.deleted_at IS NULL
   AND t.deleted_at IS NULL
   AND t.user_id IS DISTINCT FROM owner_member.user_id;
