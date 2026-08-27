-- 为已有数据库补齐系统预置科目；可重复执行，不会删除用户数据。
-- 使用 WHERE NOT EXISTS，兼容尚未建立 uk_user_name 唯一索引的旧开发库。
INSERT INTO t_category (user_id, name, is_system, is_deleted)
SELECT 0, preset.name, 1, 0
FROM (
    SELECT '饮食' AS name
    UNION ALL SELECT '交通'
    UNION ALL SELECT '租房'
    UNION ALL SELECT '通信'
    UNION ALL SELECT '其他'
) AS preset
WHERE NOT EXISTS (
    SELECT 1
    FROM t_category existing_category
    WHERE existing_category.user_id = 0
      AND existing_category.name = preset.name
      AND existing_category.is_deleted = 0
);
