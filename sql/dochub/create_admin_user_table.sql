-- =============================================================
-- 文枢 DocHub 控制台账号表（统一存于主库 dochub_business_chat）
-- =============================================================
USE dochub_business_chat;

CREATE TABLE IF NOT EXISTS dochub_admin_user (
  id BIGINT PRIMARY KEY,
  username VARCHAR(64) NOT NULL COMMENT '登录账号',
  password_hash VARCHAR(160) NOT NULL COMMENT '密码哈希：salt$sha256(salt+password)',
  display_name VARCHAR(128) NOT NULL DEFAULT '' COMMENT '显示名',
  is_admin TINYINT NOT NULL DEFAULT 0 COMMENT '是否管理员：1=是（拥有全部权限），0=否',
  permissions VARCHAR(512) NOT NULL DEFAULT '' COMMENT '逗号分隔的权限码：dashboard/knowledge_route/document_manage/observability/route_trace/account_manage',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '账号状态：1=启用，0=停用',
  create_time DATETIME,
  edit_time DATETIME,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 初始账号（固定盐哈希，可重跑）
INSERT INTO dochub_admin_user (id, username, password_hash, display_name, is_admin, permissions, status, create_time, edit_time) VALUES
 (1, 'admin', 'dochub-admin-salt-1$d11f690a0c63c059c383015cdce3a882a63a0520b21086b9b1a213a954d5477f', '系统管理员', 1, 'dashboard,knowledge_route,document_manage,observability,route_trace,account_manage', 1, NOW(), NOW()),
 (2, 'zjh',    'dochub-zjh-salt-2$4c9a333c6f4a0e33478fce46decfd77e9093c9e2dead385f75cab3fe1de568bd', '张军豪', 1, 'dashboard,knowledge_route,document_manage,observability,route_trace,account_manage', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE password_hash=VALUES(password_hash), is_admin=VALUES(is_admin), permissions=VALUES(permissions), status=1;
