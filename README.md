# DocHub · 文枢

> **中国科学院软件研究所 · 内部智能体（AI Agent）平台**

DocHub（文枢）是面向**涉密 / 敏感办公环境**自研的企业级 AI 智能体平台，覆盖智能对话、文档知识问答（RAG）、知识路由与知识图谱、文档全生命周期管理、文档生成、技能管理等完整能力。

![78762843439](C:\Users\Lenovo\AppData\Local\Temp\1787628434394.png)

---

## 项目背景

研究所内部存在大量文档与知识处理需求，其中部分文档**涉及保密要求，无法将数据发送到外部的 AI 服务或云端 Agent 平台**。

为此，我们在单位内网自研并内部部署了 **DocHub（文枢）**：

- **数据不出内网**：全部中间件与模型调用均在内部完成，敏感文档不离开可控环境；
- **能力对标企业级 Agent**：不因安全限制而牺牲智能能力，对话、检索、生成、治理一应俱全；
- **可插拔模型接入**：基于 OpenAI 兼容协议，可灵活对接内网 / 合规的对话与向量模型服务。

> 一句话：**让涉密环境也能用上企业级 AI Agent，数据安全与智能能力两者兼得。**

---

## 核心能力

| 能力 | 说明 |
| --- | --- |
| 智能对话（Agent Chat） | 多轮对话、意图分析、会话记忆管理、全链路阶段可观测 |
| 文档知识问答（RAG） | 文档上传解析、智能分块、向量检索（Qdrant）+ ES 全文检索、证据驱动的答案生成 |
| 知识路由与知识图谱 | 基于知识范围 / 主题节点构建知识图谱（Neo4j），检索时按图路由，检索路径可追踪 |
| 文档全生命周期管理 | 文档画像、结构节点、处理任务与策略编排、任务日志 |
| 文档生成 | 基于文档模板与知识内容，自动生成结构化文档 |
| 技能管理（Skills） | 内置技能库与技能市场，支持能力扩展 |
| 管理后台与可观测 | 登录鉴权、账号管理、仪表盘、对话阶段与检索追踪 |

---

## 技术架构

### 中间件（Docker Compose 一键启动）

| 组件 | 用途 |
| --- | --- |
| MySQL 8 | 业务数据存储 |
| Qdrant | 向量数据库（语义检索） |
| Elasticsearch | 全文检索（关键词匹配） |
| Neo4j | 知识图谱存储与路由 |
| Redis | 缓存、分布式锁 |
| Kafka | 异步消息解耦 |
| MinIO | 对象存储（文档原件） |

### AI 模型

后端通过 **OpenAI 兼容协议**接入对话与向量模型（如阿里云百炼 DashScope 等合规服务），对话模型与向量模型可独立配置、随时替换，便于在内网环境下对接经过评估的模型网关。

### 前后端

- **前端** `dochub-web`：Vue 3 + Vite，开发端口 `5173`，通过 `/api` 代理到后端 `9086`；
- **后端** `dochub-agent-business/dochub-agent-business-dochub`：Spring Boot 应用，服务端口 `9086`。

---

## 模块说明

| 模块 | 说明 |
| --- | --- |
| `dochub-agent-origin` | 顶层聚合父工程 |
| `dochub-agent-business` | 业务聚合模块 |
| └ `dochub-agent-business-dochub` | 主业务应用（对话、文档、知识、技能、管理后台） |
| `dochub-agent-common` | 通用组件（统一返回、异常处理、MyBatis-Plus 等） |
| `dochub-agent-id-generator-framework` | 分布式 ID（百度 UidGenerator / 雪花算法） |
| `dochub-agent-redisson-framework` | Redisson 分布式基础设施（分布式锁、重复执行限制、服务租约、延迟队列） |
| `dochub-web` | 前端工程 |

---

## 技术栈

- **后端**：Java 17、Spring Boot 3.5、Spring AI / Spring AI Alibaba、MyBatis-Plus
- **前端**：Vue 3、Vite
- **中间件**：MySQL、Qdrant、Elasticsearch、Neo4j、Redis、Kafka、MinIO
- **部署**：Docker Compose

---

## 快速开始

### 1. 启动中间件

```bash
docker compose -f docker-compose-dochub.yml up -d
```

> `docker-compose-dochub.yml` 一键拉起 Qdrant、Redis、Kafka、MinIO、Elasticsearch、Neo4j 等中间件；MySQL 可复用已有实例（详见文件内注释）。

### 2. 初始化数据库

执行 `sql/dochub/` 目录下的建表脚本（`create_table_dochub*.sql`、`create_admin_user_table.sql` 等）。

### 3. 配置模型服务

编辑 `dochub-agent-business/dochub-agent-business-dochub/src/main/resources/application.yaml`，配置 OpenAI 兼容的模型地址与密钥。

> ⚠️ 生产环境请通过环境变量注入密钥，**不要**将真实密钥提交到代码仓库。

### 4. 启动后端

```bash
mvn -pl dochub-agent-business/dochub-agent-business-dochub -am spring-boot:run
```

### 5. 启动前端

```bash
cd dochub-web
npm install
npm run dev
```

浏览器访问 `http://localhost:5173`。

---

## 目录结构

```
DocHub-agent/
├── dochub-agent-business/                  # 业务聚合
│   └── dochub-agent-business-dochub/       # 主业务应用
├── dochub-agent-common/                    # 通用组件
│   ├── dochub-agent-common-frame/
│   └── dochub-agent-common-web/
├── dochub-agent-id-generator-framework/    # 分布式 ID
├── dochub-agent-redisson-framework/        # Redisson 基础设施
│   ├── dochub-agent-redisson-service-framework/
│   │   ├── dochub-agent-redisson-common-framework/
│   │   ├── dochub-agent-repeat-execute-limit-framework/
│   │   ├── dochub-agent-service-lease-framework/
│   │   └── dochub-agent-service-lock-framework/
│   └── dochub-agent-service-delay-queue-framework/
├── dochub-web/                             # 前端（Vue 3）
├── sql/dochub/                             # 建库建表脚本
├── docker-compose-dochub.yml               # 中间件一键启动
└── pom.xml                                 # 顶层聚合父工程
```

---

## 保密与安全

- 本项目面向**涉密 / 敏感环境**内部使用，请勿将涉密文档样本、真实账号与密钥提交到任何代码仓库；
- 生产配置中的密钥、连接串等敏感信息请统一通过环境变量或密钥管理服务注入；
- 模型调用保持在内网 / 合规通道内完成，数据不流向外部平台。
