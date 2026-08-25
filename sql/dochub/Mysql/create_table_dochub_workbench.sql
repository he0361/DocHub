-- =============================================================
-- 文枢 DocHub 工作台新增表：文档模板 + 文档生成历史
-- 说明：执行 create_table_dochub.sql 之后执行本脚本
-- =============================================================

CREATE TABLE IF NOT EXISTS dochub_doc_template (
    id BIGINT NOT NULL COMMENT '主键id',
    template_code VARCHAR(64) NOT NULL COMMENT '模板编码',
    template_name VARCHAR(128) NOT NULL COMMENT '模板名称',
    template_type VARCHAR(32) NOT NULL COMMENT 'regulation/report/weekly/scheme/other',
    knowledge_scope_code VARCHAR(64) DEFAULT NULL COMMENT '关联知识域编码',
    description VARCHAR(1024) DEFAULT NULL COMMENT '模板说明',
    outline_prompt TEXT DEFAULT NULL COMMENT '大纲规划提示词',
    content_template_text LONGTEXT NOT NULL COMMENT '正文Markdown模板(含{{变量}})',
    variable_schema JSON DEFAULT NULL COMMENT '变量定义 JSON 数组',
    output_formats VARCHAR(32) DEFAULT 'md,docx' COMMENT '支持导出格式',
    version INT NOT NULL DEFAULT 1 COMMENT '模板版本号',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_template_code (template_code),
    KEY idx_doc_template_type (template_type),
    KEY idx_doc_template_scope (knowledge_scope_code),
    KEY idx_doc_template_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档模板表';

CREATE TABLE IF NOT EXISTS dochub_doc_generation_record (
    id BIGINT NOT NULL COMMENT '主键id',
    record_code VARCHAR(64) NOT NULL COMMENT '生成记录编号',
    template_id BIGINT DEFAULT NULL COMMENT '模板id',
    template_name VARCHAR(128) DEFAULT NULL COMMENT '模板名称快照',
    generation_mode VARCHAR(16) NOT NULL DEFAULT 'TEMPLATE_GUIDED' COMMENT 'TEMPLATE_GUIDED/FREE',
    user_requirement TEXT DEFAULT NULL COMMENT '一句话需求',
    variables_json JSON DEFAULT NULL COMMENT '用户填入变量',
    generated_markdown LONGTEXT DEFAULT NULL COMMENT '生成的markdown正文',
    output_format VARCHAR(16) DEFAULT NULL COMMENT '本次导出格式 md/docx',
    file_name VARCHAR(255) DEFAULT NULL COMMENT '下载文件名',
    storage_object_name VARCHAR(512) DEFAULT NULL COMMENT 'MinIO对象名(若留存)',
    source_document_id BIGINT DEFAULT NULL COMMENT '一键入库后的文档id',
    reference_document_id BIGINT DEFAULT NULL COMMENT '参考文档仿写模式下的参考文档id',
    generation_status TINYINT NOT NULL DEFAULT '1' COMMENT '1:生成中 2:成功 3:失败',
    error_msg VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
    model_provider VARCHAR(32) DEFAULT NULL COMMENT '模型供应商',
    model_name VARCHAR(64) DEFAULT NULL COMMENT '模型名称',
    prompt_tokens INT DEFAULT 0 COMMENT '提示token数',
    completion_tokens INT DEFAULT 0 COMMENT '生成token数',
    cost_millis BIGINT DEFAULT 0 COMMENT '耗时毫秒',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_gen_record_code (record_code),
    KEY idx_doc_gen_template (template_id),
    KEY idx_doc_gen_status (generation_status),
    KEY idx_doc_gen_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档生成历史记录表';

CREATE TABLE IF NOT EXISTS dochub_doc_skill (
    id BIGINT NOT NULL COMMENT '主键id',
    skill_name VARCHAR(128) NOT NULL COMMENT '技能目录名/唯一键',
    display_name VARCHAR(128) DEFAULT NULL COMMENT '展示名称',
    version VARCHAR(32) DEFAULT '1.0.0' COMMENT '版本号',
    description VARCHAR(2048) DEFAULT NULL COMMENT '技能描述',
    when_to_use VARCHAR(2048) DEFAULT NULL COMMENT '何时使用',
    instructions LONGTEXT DEFAULT NULL COMMENT 'SKILL.md 正文指令',
    skill_type VARCHAR(16) NOT NULL DEFAULT 'MARKET' COMMENT 'BUILT_IN/MARKET/UPLOAD',
    category VARCHAR(64) DEFAULT NULL COMMENT '分类',
    tags VARCHAR(512) DEFAULT NULL COMMENT '标签',
    author VARCHAR(128) DEFAULT NULL COMMENT '作者',
    source_type VARCHAR(16) DEFAULT 'classpath' COMMENT 'classpath/minio',
    object_prefix VARCHAR(512) DEFAULT NULL COMMENT '内容对象前缀 skills/<name>/',
    content_snapshot LONGTEXT DEFAULT NULL COMMENT 'SKILL.md 原文快照',
    script_exec_enabled TINYINT(1) DEFAULT '0' COMMENT '是否允许执行脚本(默认否)',
    run_state TINYINT(1) NOT NULL DEFAULT '1' COMMENT '1:已启用 2:已停用 3:待审核',
    install_count INT DEFAULT 0 COMMENT '安装次数',
    last_used_time DATETIME DEFAULT NULL COMMENT '最后使用时间',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_doc_skill_name (skill_name),
    KEY idx_doc_skill_type (skill_type),
    KEY idx_doc_skill_category (category),
    KEY idx_doc_skill_run_state (run_state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能注册表';

CREATE TABLE IF NOT EXISTS dochub_doc_skill_usage (
    id BIGINT NOT NULL COMMENT '主键id',
    skill_id BIGINT NOT NULL COMMENT '技能id',
    conversation_id VARCHAR(64) DEFAULT NULL COMMENT '会话id',
    exchange_id BIGINT DEFAULT NULL COMMENT '轮次id',
    scene VARCHAR(64) DEFAULT NULL COMMENT '命中场景',
    matched_score DECIMAL(8,4) DEFAULT 0 COMMENT '命中分数',
    matched_reason VARCHAR(1024) DEFAULT NULL COMMENT '命中原因',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    KEY idx_doc_skill_usage_skill (skill_id),
    KEY idx_doc_skill_usage_conversation (conversation_id, exchange_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技能调用记录表';

-- 初始化一条示例模板：差旅报销制度
INSERT INTO dochub_doc_template (id, template_code, template_name, template_type, knowledge_scope_code, description, outline_prompt, content_template_text, variable_schema, output_formats, version, create_time, edit_time, status)
VALUES (1, 'TRAVEL_REIMBURSEMENT', '差旅报销管理办法', 'regulation', 'operation_rule', '基于企业内部制度模板生成差旅报销管理办法', '围绕【{{companyName}}】的差旅报销场景，规划一份制度文档大纲，包含：总则、适用范围、差旅类型、申请与审批流程、费用标准、报销流程、违规处理、附则。', '# {{companyName}}差旅报销管理办法\n\n> 文档密级：内部使用\n> 生效日期：{{effectiveDate}}\n\n## 一、总则\n\n为规范公司差旅管理，提高费用使用透明度，特制定本办法。\n\n## 二、适用范围\n\n本办法适用于{{companyName}}全体员工，含试用期员工与实习生。\n\n## 三、差旅类型与审批\n\n{{travelTypes}}\n\n## 四、费用标准\n\n{{expenseStandard}}\n\n## 五、报销流程\n\n{{reimburseFlow}}\n\n## 六、附则\n\n本办法自{{effectiveDate}}起施行，由{{department}}负责解释。', '[{"name":"companyName","label":"公司名称","type":"text","required":true},{"name":"effectiveDate","label":"生效日期","type":"text","required":true},{"name":"department","label":"解释部门","type":"text","required":false,"default":"财务部"},{"name":"travelTypes","label":"差旅类型说明","type":"textarea","required":false,"default":"国内出差需提前2个工作日申请；国际出差需提前10个工作日申请。"},{"name":"expenseStandard","label":"费用标准","type":"textarea","required":false,"default":"国内住宿标准不超过 400 元/晚；市内交通实报实销。"},{"name":"reimburseFlow","label":"报销流程","type":"textarea","required":false,"default":"出差结束后 5 个工作日内通过系统提交报销单并上传票据。"}]', 'md,docx', 1, NOW(), NOW(), 1);
