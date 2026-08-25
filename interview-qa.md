# DocHub 项目面试深挖问答（代码核对版）

> 本文基于当前仓库代码、配置和模块结构整理，目标是帮助你在面试中“按真实实现回答”，而不是只背技术名词。每道题都包含实现方式、设计优点、局限和可改进方向。
>
> 说明：文中不复制配置文件中的外部 API Key、JWT 密钥、默认账号或 MinIO 凭据。面试前应将这些配置全部改为环境变量/密钥管理，并轮换已经暴露过的凭据。

## 一、项目总览与架构

### 1. 请你先整体介绍一下 DocHub 项目，你负责的核心价值是什么？

**标准回答：**

这是一个基于 Spring Boot、Spring AI 和 Vue 的企业文档智能平台，主要解决三个问题：第一，把 PDF、Word、Markdown、HTML 等文档解析、分块并建立向量和关键词索引；第二，提供普通对话、指定文档问答、自动选择知识库、ReAct Agent、Plan-and-Execute 和文档结构导航等能力；第三，提供文档生成、参考文档仿写、导出和重新入库等工作流。

后端以 `BusinessChatController` 和 `DocumentManageController` 为入口，聊天请求由 `BusinessChatService` 统一编排，先做会话记忆、查询改写、技能路由和文档路由，再根据 `ConversationExecutionMode` 分发到不同执行器。文档侧使用 MinIO 保存原文件，MySQL 保存业务状态，Kafka 解耦解析/建索引任务，Qdrant 做向量检索，Elasticsearch 做关键词和路由检索，Neo4j 或 MySQL 做文档结构图谱。

**优点：** 业务编排、检索、持久化和基础设施被拆成了相对独立的模块；检索与 Agent 不是强耦合，后续可以单独替换检索策略或模型。

**不足与改进：** 当前能力较多，但模块边界仍有一些编排逻辑集中在聊天服务中。进一步可以拆出独立的会话编排服务、检索服务和异步任务服务，并用统一的领域事件连接它们；同时补充离线评测集，用准确率、召回率、引用覆盖率和首 token 延迟衡量实际效果。

**代码锚点：** `dochub-agent-business/dochub-agent-business-dochub`、`BusinessChatService`、`RagRetrievalEngine`、`DocumentAsyncProcessServiceImpl`、`dochub-web/src/api/api.js`。

### 2. 为什么没有把所有问题都交给一个 Agent，而是设计多个执行模式？

**标准回答：**

因为不同问题需要不同的确定性。普通开放问题适合 `REACT_AGENT`，让模型根据工具自主决策；文档问答需要先检索证据，再用受控 Prompt 生成答案，使用 `RETRIEVAL`；需要多步拆解时使用 `PLAN_AND_EXECUTE`；文档目录、章节、条款定位等问题可以走 `GRAPH_ONLY` 或 `GRAPH_THEN_EVIDENCE`。此外，自动知识库选择不明确时会进入 `CLARIFICATION`，先让用户补充范围。

实际代码通过 `ConversationExecutor` 接口和 `ConversationExecutorRegistry` 按枚举注册执行器，编排层只关心执行模式，不需要写大量 `if/else`。

**优点：** 把“是否使用工具”“是否检索”“是否查图谱”从模型自由发挥变成了可解释的路由决策，稳定性和可观测性更好。

**不足与改进：** 模式数量继续增加后，路由规则可能变复杂。可以把模式抽象成能力组合，例如 `needRetrieval`、`needGraph`、`needPlanning`，再用策略表生成执行计划；同时给每种模式定义统一的超时、重试和降级协议。

### 3. 一次聊天请求从进入接口到返回结果，完整链路是什么？

**标准回答：**

请求进入 `/api/chat/stream` 后，Controller 返回 `text/event-stream`。`BusinessChatService` 先规范化问题、会话 ID、对话模式和文档选择，获取 `chat:running:<conversationId>` 的 Redis 租约，防止同一会话并发生成。随后创建交换记录、`TaskInfo`、本地运行时注册信息和 Reactor `Sinks.Many`，把事件 sink、引用、思考步骤、工具记录、traceId 等放入 `RunnableConfig` 上下文。

真正的执行在订阅后启动：先异步准备执行计划，包括历史记忆、查询改写、子问题拆分、知识库路由、文档结构路由和技能路由；再从执行器注册表选择 ReAct、RAG、图谱或计划执行器。模型输出被转成统一流事件，结束时补发引用和推荐问题，最后写入会话交换记录、更新摘要、释放租约并清理运行时。

**优点：** 准备阶段、执行阶段、收尾阶段职责清楚，且流式输出不会阻塞 HTTP 请求线程。

**不足与改进：** 这是一个跨 Redis、数据库、模型、向量库和消息系统的长链路，任一步失败都可能产生状态不一致。可以引入显式状态机、幂等键和统一补偿事件，给每个阶段记录开始/结束/失败原因。

### 4. 为什么聊天采用 SSE，而不是一次性返回 JSON？

**标准回答：**

模型生成和检索可能持续几秒到几十秒，一次性 JSON 会让用户长时间看到空白。项目使用 SSE 按事件返回 `text`、`thinking`、`status`、`skill`、`reference`、`recommend` 和 `error`，前端在 `api.js` 中读取 `ReadableStream`，用 `TextDecoder` 处理 UTF-8，按空行切分 SSE block，再根据事件类型更新消息、引用和推荐问题。

**优点：** 用户能尽早看到首 token 和执行阶段；统一事件结构使前端不必解析模型厂商的原始协议；引用和推荐问题可以在答案结束后单独到达。

**不足与改进：** SSE 本身不解决断线续传，当前运行时主要在单实例内存中，客户端刷新后不能自然接着看。可以为每个事件增加递增序号和幂等 ID，把关键事件写入 Redis Stream 或数据库，客户端带 `Last-Event-ID` 重连；生产环境还需要网关关闭缓冲并设置心跳。

### 5. 如果用户在生成中点击停止，项目如何实现取消？

**标准回答：**

停止操作会根据会话找到 `ChatRuntimeRegistry` 中的运行时，调用 `businessChatReactAgent.interrupt`，取消当前订阅，向 sink 写入停止状态，更新交换记录为 `STOPPED`，刷新摘要并释放 Redis 租约。模型、工具和后处理都共享当前任务的上下文，因此引用、思考步骤和 trace 信息可以一起持久化。

**优点：** 取消不只是关闭浏览器连接，还会更新业务状态，避免页面显示“仍在生成”。

**不足与改进：** 如果模型供应商请求已经发出，应用层取消不一定能真正取消远端推理；此外，停止和正常完成可能存在竞态。可以使用任务版本号或 CAS 状态更新，要求只有持有当前 generation token 的流程能写最终状态，并把远端调用包装成可取消的 HTTP 请求。

### 6. 如何保证同一会话不能同时生成两个答案？

**标准回答：**

项目做了两层控制：进程内通过 `ChatRuntimeRegistry` 保存 `conversationId -> runtime`，使用 `putIfAbsent` 防止同一 JVM 重入；跨实例通过 `RedisLeaseManager` 获取带 TTL 的租约，key 是 `chat:running:<conversationId>`，value 是随机 owner token。获取、续租和释放都用 Lua 做 compare-and-set，释放时只有 owner token 相同才删除。

**优点：** 本地快速失败，Redis 负责分布式互斥，且 TTL 可以避免实例宕机后永久死锁。

**不足与改进：** Redis 租约只能保证“同一时刻尽量只有一个执行者”，不能替代数据库最终状态；本地 runtime 仍然不是分布式的，用户可能连到没有 runtime 的实例。可以使用 Redis Stream/消息队列保存运行事件，并用数据库唯一约束或版本字段保证最终提交幂等。

### 7. Redis 租约为什么需要续租？TTL 设置不当有什么问题？

**标准回答：**

聊天可能超过一次 TTL，项目在获取租约后按固定周期续租，续租也校验 owner token。TTL 太短会在任务仍运行时自动过期，第二个请求可能进来形成双写；TTL 太长则实例宕机后需要等待更久才能恢复。合理做法是 TTL 大于续租周期数倍，并把续租失败视为当前任务失去主权，停止继续写最终结果。

**改进方向：** 增加 Redis 时间漂移和网络分区监控；对续租失败、租约被抢占、重复完成分别打指标；如果业务需要严格一致性，可引入数据库 generation/version 做最后一道栅栏。

### 8. 为什么既保存会话归档，又使用 Spring AI 的 checkpoint？

**标准回答：**

`ConversationArchiveStore` 负责业务层历史：用户问题、答案、状态、引用、推荐问题、思考步骤和工具轨迹，便于页面展示、审计和统计。ReAct Agent 的 `MysqlSaver` checkpoint 负责 Agent 执行状态，例如中断/恢复时需要的消息和图状态。两者关注点不同，不能简单用一张业务表替代 Agent checkpoint。

**优点：** 页面查询不依赖 Agent 内部状态格式；Agent 框架升级时，业务归档仍然稳定。

**不足与改进：** 两套持久化可能出现一个成功、另一个失败。可以给 exchange 和 checkpoint 绑定同一 trace/generation ID，增加异步对账任务；对大消息和工具输入做脱敏、压缩和保留周期控制。

## 二、Agent、路由与技能

### 9. ReAct Agent 配置了哪些安全阀和稳定性措施？

**标准回答：**

`ChatAgentConfiguration` 创建带 MySQL checkpoint 的 ReAct Agent，挂载 Tavily 搜索工具，启用并行工具执行，最大并行工具数为 4。`ModelCallLimitHook` 限制单次运行和单线程模型调用，`ToolCallLimitHook` 限制工具次数；`ToolRetryInterceptor` 对 Tavily 做最多 2 次重试，初始延迟约 200ms，最大延迟约 1200ms，并带抖动；`ToolErrorInterceptor` 统一处理工具错误。

**优点：** 避免模型无限循环调用模型或搜索，降低成本和雪崩风险；重试只针对可恢复的工具失败。

**不足与改进：** 当前限额主要是静态配置，不能根据问题复杂度动态调整；并行调用也可能触发供应商限流。可以按租户配置预算，增加 token、金额和总耗时限制，并对工具按幂等性分类，只对安全的读操作自动重试。

### 10. 技能系统到底做了什么？是否会动态执行脚本？

**标准回答：**

技能由 classpath 下的 `skills/*/SKILL.md` 和数据库启用技能组成。`SkillLoader` 启动时加载，数据库状态覆盖文件默认状态；`SkillFrontMatterParser` 解析 front matter 和正文，`SkillRegistry` 使用并发 Map 管理启用技能。`SkillSceneRouter` 先尝试让模型从技能名和描述中返回 JSON 技能名，失败时退回标签命中和二元组 Jaccard 相似度；`SkillQuestionComposer` 把技能说明注入本轮问题，再交给固定的 Agent 工具集合。

当前设计是“检索和注入指令”，不是运行时执行技能目录下的任意脚本。这样降低了代码执行和供应链风险。

**不足与改进：** 指令注入仍可能影响模型行为，且技能匹配阈值 `0.12` 偏经验化。可以给技能内容做版本、审核和灰度；对技能正文做敏感指令扫描；记录匹配置信度、命中技能与最终效果，用离线数据校准阈值。

### 11. 查询改写和子问题拆分是怎样做的？为什么不能无条件拆分？

**标准回答：**

`ChatQueryRewriteService` 根据历史和当前问题生成更适合检索的表达，支持生成多个子问题，最大 4 个。编排器对 LLM 返回做保守校验：只有原问题明确包含多个独立问题时才接受拆分，否则把结果折叠为一个检索问题，避免模型“过度拆题”导致检索次数和上下文膨胀。

**优点：** 兼顾多跳问题召回和单问题成本；后续每个子问题可以独立并行检索。

**不足与改进：** 当前拆分质量主要依赖模型和规则，缺少基于数据的评测。可以加入问题复杂度分类器、子问题去重和依赖关系；对互相依赖的子问题采用顺序执行，对独立问题才并行。

### 12. AUTO_DOCUMENT 如何自动决定查哪个文档？

**标准回答：**

`KnowledgeRouteServiceImpl` 采用 Scope → Topic → Document 的层级路由。路由文本由原问题和改写问题组成，先用向量和 Elasticsearch 关键词路由索引召回 scope/topic/document，再按分数和阈值逐层过滤。配置中 scope/topic floor 为 15，最终计算置信度；如果候选不明确，准备阶段返回 `CLARIFICATION`，如果置信度达到阈值则选择顶部文档，否则把候选文档范围传给后续执行器。路由结果和 top 候选会保存为 trace，DOCUMENT 模式还会记录 shadow route。

**优点：** 先缩小范围再查 chunk，降低全库误召回；层级和 trace 让结果可以解释。

**不足与改进：** 分数混合和阈值是手工调参，未必跨领域稳定；路由阶段还会额外调用 embedding。可以用标注问题集做置信度校准，按知识库/租户动态阈值，并缓存热门路由向量。

### 13. 文档结构图谱和普通向量检索如何协作？

**标准回答：**

`DocumentQuestionRouter` 通过规则和可选 LLM 意图识别判断问题是否是目录、章节邻接、条款定位、某项清单或结构分析。如果只是定位结构，走 `GRAPH_ONLY`；如果需要先找结构再回答，走 `GRAPH_THEN_EVIDENCE`；普通语义问题才走 RAG。图谱查询由 `StructureGraphQueryEngine` 获取章节、子节点、兄弟节点和条款，并递归遍历子树；必要时把结构锚点转成检索提示。

这里的图谱主要是“文档结构图谱”，不是通用实体知识图谱。节点包括 Document、Section、Item，边包括 HAS_SECTION、HAS_CHILD、HAS_ITEM、NEXT_SIBLING 等。

**优点：** 结构问题不必完全依赖 embedding，章节关系和目录邻接更准确。

**不足与改进：** 结构抽取依赖标题质量，复杂扫描 PDF 可能没有可靠层级；图谱目前表达的是结构关系，不能直接回答跨文档实体关系。后续可以增加版面/OCR 解析、实体抽取和版本关系，并给图谱查询增加权限过滤。

## 三、RAG 检索与生成

### 14. 这个项目的混合检索具体怎么实现？

**标准回答：**

每个子问题会并行调用 `VectorRetrievalChannel` 和 `KeywordRetrievalChannel`。向量通道使用 embedding 模型和 Qdrant，关键词通道使用 Elasticsearch 的 match phrase/multi match，并对 section path、canonical path 等字段加权。向量结果先过滤低于 `minVectorSimilarity` 的候选，当前配置约为 0.45；关键词结果使用相对最高分的 floor 过滤，避免绝对分数跨查询不可比。之后合并候选并用 RRF 融合，候选上限约 10。

**优点：** 向量检索负责语义相似，关键词检索保留专有名词、编号和精确短语，两者互补。

**不足与改进：** RRF 不使用原始分数，可能丢失高质量候选的分数信息；两个通道的过滤阈值也需要持续校准。可以采用加权 RRF 或学习排序，并按问题类型动态调整向量/关键词权重。

### 15. RRF 为什么适合这里？它有什么代价？

**标准回答：**

项目使用固定 `RRF_K=60`，常见形式是对候选在各通道中的排名累加 `1/(K+rank)`。它不要求向量 cosine 分数和 ES BM25 分数处于同一量纲，因此工程上比直接相加稳健。

代价是排名信息被保留、原始分数被弱化：一个排名靠后但分数极高的候选，可能不如两个通道都排中间的候选。改进上可以先做分数归一化，再使用可调权重；如果有点击或人工标注数据，可以训练轻量 LambdaMART/交叉编码器排序。

### 16. 为什么检索到 child chunk 后还要提升到 parent block？

**标准回答：**

建索引时采用 Parent-Child：父块保留章节/上下文，子块更细，适合精准命中。检索先命中 child，再通过 `elevateToParentBlocks` 找到父块并限制父证据长度，当前 parent evidence 最大约 2200 字符。这样既保留细粒度召回，又给模型完整的章节语境；最终引用仍记录文档、章节路径、chunk 和引用 ID。

**优点：** 比直接把大量小 chunk 丢进 Prompt 更有上下文完整性，降低回答断章取义。

**不足与改进：** 父块过大时会浪费 token，多个 child 命中同一父块也会重复。可以按命中 child 的相邻窗口拼接上下文，用 token 预算而不是字符预算；对同一父块做去重，并把命中位置高亮给模型。

### 17. RAG 如何控制上下文长度？为什么是字符预算而不是 token 预算？

**标准回答：**

`RagPromptAssemblyService` 设置总证据约 5200 字符、单子问题约 2200 字符；文档片段和网页片段还有各自上限，并且按引用 ID 去重。超过预算的证据会被记录为 omitted，Prompt 中保留当前日期、原问题、检索问题、历史和证据块，答案只能依据这些上下文。

字符预算实现简单、与中文场景直观，但不同模型的 tokenizer 对中文、英文、代码的 token 消耗不同，所以它不是严格的上下文控制。改进是接入目标模型 tokenizer，按 token 预算做 evidence packing，并为系统提示、历史、答案预留独立预算。

### 18. 没有检索证据时，系统如何避免模型胡编？

**标准回答：**

`RagChatExecutor` 在检索为空时不继续调用答案模型，而是写入检索说明并返回配置的 no-evidence reply。只有存在证据时，才由 `RagPromptAssemblyService` 组装证据 Prompt，再调用 `ObservedChatModelService` 流式生成。

**优点：** 是一个明确的安全闸门，能降低“没有依据仍生成确定结论”的概率。

**不足与改进：** “有证据”不等于“证据支持问题”，当前主要依赖分数阈值和 Prompt。可以增加 entailment/answerability 检测，让模型先判断证据是否足以回答；对法规、医疗、财务等高风险知识库增加强制引用和人工复核。

### 19. 多个子问题的检索是串行还是并行？如何处理一个通道超时？

**标准回答：**

`RagRetrievalEngine` 为每个子问题创建异步任务，再为每个检索通道创建带超时的 `CompletableFuture`。单子问题和单通道都有超时配置；异常或超时会降级为空证据并保留 retrieval note，其他子问题继续执行。最后再统一融合、父块提升、去重和可选 rerank。

**优点：** 并行降低整体延迟，单个 ES/Qdrant 故障不会让整个回答直接失败。

**不足与改进：** 降级为空可能造成“部分召回却看起来正常”。应在最终事件和 trace 中明确标记 partial retrieval；同时给线程池、队列、超时、拒绝次数打指标，并根据负载做舱壁隔离。

### 20. Rerank 在项目中是否真的启用了？面试应该怎么回答？

**标准回答：**

代码提供了 `HttpDocumentRerankPostProcessor`，可以对融合候选进行外部 HTTP rerank；但当前配置中 external rerank 是关闭的，所以实际默认链路是向量/关键词召回、RRF、父块提升和最终截断，并没有每次调用外部 reranker。

这是面试中需要如实区分的地方：可以说“实现了可插拔 rerank 能力，但当前环境默认关闭”，不能说线上每次都经过 rerank。

**改进方向：** 对候选 top 10 做 rerank 通常比全量重排成本低；需要设置超时和失败回退，并用 Recall@K、MRR、nDCG、引用支持率验证收益。

### 21. 如何保证引用能够追溯到证据？

**标准回答：**

Qdrant payload 和 ES 文档元数据里保留 documentId、taskId、planId、parentId、chunkId、sectionPath、canonicalPath、item 信息和文本。检索结果转换为 `SearchReference`，在流结束时通过 `reference` 事件返回，并将引用列表序列化到会话交换记录。管理端还会展示每个子问题的通道、fused 候选数、parent 候选数、rerank 数和最终引用数。

**优点：** 不只返回一段裸文本，能定位文档和章节，便于审计和排障。

**不足与改进：** 引用存在并不代表答案中的每句话都被支持。可以让生成模型输出引用 ID，之后做引用覆盖率校验；对未被证据支撑的句子标记不确定或阻止输出。

## 四、文档解析、分块与索引

### 22. 文档上传和异步处理流程是什么？

**标准回答：**

上传接口先校验文件名、大小和类型，读取文件后使用 UID 生成器创建文档 ID，将原文件上传 MinIO，同时插入文档记录、解析任务和任务日志。Kafka 消息携带文档/任务信息，消费者收到后调用异步服务：从 MinIO 下载原文件，用 Tika 解析，上传清洗后的文本，替换结构节点，同步导航和路由索引，生成策略推荐，并创建等待确认的分块计划。用户确认策略后，再发送建索引任务，执行父子分块、向量化和 ES 索引。

**优点：** 原始文件、解析文本、任务状态和索引状态分离；解析和建索引可以独立重试，用户还能在建索引前确认策略。

**不足与改进：** 当前上传方法中 Kafka `send().get()` 是同步等待，吞吐受 Kafka 延迟影响；可改为事务 outbox，先可靠落库事件，再异步投递。

### 23. 为什么使用 Kafka，而不是在上传接口里直接解析？

**标准回答：**

解析 PDF/Word 和批量 embedding 都可能耗时且占用内存，直接在 HTTP 请求中做会造成超时和线程池阻塞。Kafka 将上传、解析、建索引解耦，消费者可以限流、重试和水平扩展，任务状态则保存在数据库供前端查询进度。

**不足与改进：** 消息系统引入了重复消费、顺序、失败重试和状态一致性问题。当前消费者捕获异常后主要记录日志，是否提交 offset 取决于容器配置，存在失败消息被确认的风险。建议采用显式重试 topic、死信队列、幂等消费键和可恢复任务状态。

### 24. 解析服务如何判断文档质量？

**标准回答：**

`TikaDocumentParserService` 对 PDF、DOC、DOCX、HTML 使用 Tika，对 TXT/MD 直接按 UTF-8 读取；之后清理换行、制表符和空字符，抽取标题/段落结构，并统计标题数、段落数、最大段落、字符数、估算 token 数和异常字符比例，最终给出结构等级和质量等级。文本型快速解析会跳过结构和 LLM 处理，适合参考文档仿写等场景。

**优点：** 解析结果不仅有纯文本，还有后续策略推荐需要的质量特征。

**不足与改进：** token 是估算值，扫描 PDF、表格、图片和多栏排版仍可能解析失真。可以加入 OCR、版面分析、表格结构识别和抽样质量评估，并将解析质量反馈给用户，而不是只在后台日志中体现。

### 25. 分块策略如何自适应选择？

**标准回答：**

`DocumentStrategyServiceImpl` 根据文件类型、结构等级、标题数、字符数、段落数、最大段落和质量等级推荐策略：有可靠标题结构时优先 `STRUCTURE`；段落很长时加入 `RECURSIVE`；文本足够长且质量达到要求时可用 `SEMANTIC`；低质量且配置允许时才推荐 `LLM`。父块和子块可以采用不同策略，最终形成父子分块计划，并让用户确认。

**优点：** 不把所有文档强行套用一个 chunk size；策略有推荐原因，便于业务人员调整。

**不足与改进：** 目前判断阈值是经验配置，且主要按字符而不是模型 token；LLM 分块默认关闭。可以用真实问答集做策略 A/B 测试，让召回和答案质量反向优化分块参数。

### 26. Recursive、Semantic 和 LLM 分块分别怎样工作？

**标准回答：**

Recursive 分块按段落、行、句子逐级切分，超长时使用固定窗口和 overlap；Semantic 分块把句子转成词集合，用 Jaccard 相似度判断相邻句子是否属于同一语义组；LLM 分块要求模型返回 JSON 数组，失败或格式不合法时回退到 semantic。代码还会清理空文本、重复路径和重复块。

**优点：** 有确定性的快速回退，LLM 不是单点依赖。

**不足与改进：** Jaccard 对中文采用单字/简单词切分，语义能力有限；LLM JSON 用首尾方括号截取，遇到模型附带解释可能不稳。可以使用模型 tokenizer 和 embedding 相似度做语义切分，LLM 输出使用结构化 response format，并记录每种策略的耗时和质量。

### 27. Parent-Child 分块中的 overlap 为什么重要？

**标准回答：**

当前父块和子块都有 overlap 配置，子块默认约 120 字符，父块也有独立窗口设置。overlap 解决句子或定义刚好被切在边界的问题，提高召回连续性；父块保留更大上下文，子块负责精确命中。

**缺点：** overlap 过大带来重复向量、索引膨胀和 Prompt 重复；过小又容易丢上下文。改进是按句子边界和 token 预算动态 overlap，并在入库前检测相邻 chunk 的重复率。

### 28. Qdrant 向量入库有哪些一致性保护？

**标准回答：**

`DefaultDocumentVectorGateway` 过滤无效 chunk，按 batch size 约 10 生成 embedding，检查 embedding 数量与输入一致，再根据首个向量维度确保 collection 存在，payload 保存文档和结构元数据，最后批量 upsert。`QdrantVectorStore` 使用 cosine 距离，并为 chunk_text、section_path、canonical_path 建 payload 索引。

**优点：** 批处理降低远端调用次数，embedding 数量校验可以避免错位入库。

**不足与改进：** collection 初始化和搜索代码对异常有较宽泛的 catch，某些真实连接错误可能被当成“集合已存在”或“无结果”。生产上应区分 404、超时、鉴权、维度冲突和服务不可用，并对文档版本使用 namespace 或 index version，避免重建期间读到混合数据。

### 29. Elasticsearch 关键词检索具体解决了什么问题？

**标准回答：**

关键词索引保存 chunk 文本、章节路径、规范化路径、结构和条款信息。查询会按文档/任务过滤，使用 match phrase 和 multi match，对 section path、canonical path 等字段加 boost，并要求至少一个 should 子句命中。它特别适合编号、专有名词、章节标题和精确术语，这些场景向量相似度可能不稳定。

**不足与改进：** 建索引时使用 `Refresh.WaitFor` 能提高刚入库数据的可见性，但会影响批量吞吐；wildcard 或复杂路径过滤也可能放大成本。可以采用批量 refresh、别名切换、按版本建索引和异步刷新，并针对中文分词器做线上评测。

## 五、图谱、路由、记忆与生成

### 30. Neo4j 不可用时为什么还能查文档结构？

**标准回答：**

`CompositeDocumentStructureGraphService` 根据 Neo4j Bean 是否存在、图谱是否可用选择 Neo4j 实现，否则回退到 MySQL 结构表。Neo4j 投影时会删除指定文档旧节点，再事务性重建 Document、Section、Item 及层级/兄弟关系；MySQL 回退则通过结构节点表完成基础树查询。

**优点：** 图数据库不是单点故障，开发环境也可以不启动 Neo4j；结构型功能有可用的降级路径。

**不足与改进：** 两套实现的查询能力和性能可能不同，结果还可能存在语义差异。可以定义统一的结构查询契约和一致性测试，给图谱投影使用版本号与原子切换，避免重建期间查询半成品。

### 31. 知识路由为什么要保存 shadow route？

**标准回答：**

在用户明确选择文档的 DOCUMENT 模式下，系统仍然可以执行一次自动路由，但不改变实际选择，把结果作为 shadow route 保存。这样可以观察自动路由与用户选择的差异，评估未来自动化是否可靠；AUTO_DOCUMENT 则会把候选 scope/topic/document、置信度和选中结果保存到路由 trace。

**优点：** 线上真实流量可以用于无侵入评估，而不是直接把未验证的路由策略切给用户。

**改进方向：** 增加离线回放、用户纠正率、路由置信度校准和分桶实验；对于低置信度不要强行选文档，而是明确提示用户选择范围。

### 32. 会话记忆和 RAG 证据是如何区分的？

**标准回答：**

会话记忆由 `ConversationMemoryService` 和持久化摘要服务负责：保留最近若干轮，超过长度后按批次压缩为摘要，并保存用户偏好、约束和主题。RAG 证据是当前问题检索到的文档/网页片段，作为本轮回答依据。准备阶段同时构造 planning history 和 answer history，避免把无限历史直接塞入模型。

**优点：** 长对话的连续性和当前问题的事实证据分开管理，减少历史污染。

**不足与改进：** 摘要一旦丢失细节，后续轮次可能无法恢复；摘要和向量记忆还可能过期。可以保留可追溯的原始消息 ID，摘要按版本更新，回答时同时召回结构化记忆和近期原文，并允许用户删除或修正记忆。

### 33. 为什么还要把会话摘要存到 Qdrant？

**标准回答：**

`ConversationVectorMemoryService` 将摘要文本向量化写入 `dochub_conversation_memory`，payload 带 conversationId，召回时按会话过滤并取 top 3。这样当对话很长时，可以按语义找回早期相关记忆，而不是只依赖最近几轮。

**不足与改进：** 摘要更新如果不断生成新 UID，可能产生旧摘要重复或过期记忆；向量相似不代表事实仍有效。应按 conversationId + summaryVersion 做 upsert 或定期删除旧版本，同时加时间衰减、相似度阈值和记忆类型过滤。

### 34. 文档生成模块和普通 RAG 问答有什么不同？

**标准回答：**

文档生成接口先选择模板，再由 `DocumentOutlinePlanner` 调用模型生成大纲；`DocumentGenerationService` 根据大纲生成 Markdown 正文，保存 `DocGenerationRecordEntity`，之后可以通过 `DocumentExporter` 策略导出 Markdown 或 DOCX。参考文档仿写流程使用 Tika 的 `parseTextOnly` 获取参考内容，抽取参考大纲，将需求、参考标题/内容注入 Prompt，并提供 SSE 流式正文输出。

这不是普通的“给答案加引用”，而是一个有记录、导出和再次入库的内容生产工作流。

**不足与改进：** 参考内容有最大字符截断，长文风格和细节可能丢失；全篇生成也会占用较多 token。可以先生成章节级草稿，再逐章审阅/重写，并为每段保留参考来源和编辑版本。

### 35. 生成的文档如何回到知识库？

**标准回答：**

生成记录保存后，`/manage/workbench/docgen/ingest` 可以将生成的 Markdown 作为新的文档走普通入库流程，关联 `sourceDocumentId`。这样生成内容不会停留在文件下载层，而可以重新解析、分块、向量化、建立关键词和结构索引。

**优点：** 形成“生成—审核—入库—问答”的闭环。

**不足与改进：** 如果未经审核直接入库，模型错误会污染知识库；重新索引还可能与旧版本混在一起。应增加审核状态、发布版本、回滚、来源标记和增量索引，只有发布版本参与默认检索。

## 六、鉴权、基础设施、性能与改进

### 36. 管理端鉴权是怎样实现的？

**标准回答：**

`AdminAuthServiceImpl` 从数据库读取管理员，密码使用随机 salt + SHA-256 保存为 `salt$hash`；登录成功后签发 HS256 JWT，Interceptor 从 `Authorization: Bearer` 解析 token，并把用户名放入请求上下文。`AdminWebMvcConfiguration` 只对文档、知识库、用户和当前用户接口配置 `AdminAuthInterceptor`，聊天和部分工作台接口按当前设计未全部拦截；`PreviewModeInterceptor` 负责只读预览模式。

**优点：** 无状态 JWT 适合前后端分离，用户信息和业务接口解耦。

**不足与改进：** SHA-256 是快速哈希，不适合密码存储；JWT 缺少刷新/撤销机制，权限判断粒度也不够细，部分敏感工作台接口需要重新审查是否应鉴权。生产上应使用 Argon2id/BCrypt、短期 access token + refresh token、密钥轮换、权限注解、登录限流和审计日志。

### 37. 看到配置文件里有密钥和默认账号，你会如何处理？

**标准回答：**

这属于明显的生产安全风险，不能把配置文件中的真实值提交到仓库或写进简历。处理顺序是立即轮换已暴露的模型、搜索、数据库、MinIO 和 JWT 凭据；从 YAML 删除明文，改用环境变量、Docker Secret、KMS 或配置中心；在 CI 中增加 secret scanning；对历史 Git 提交做密钥清理，并限制 actuator、管理接口和数据库网络暴露。

面试时可以把它作为“上线前整改项”主动说清楚，而不是回避。

### 38. 项目自定义 UID 生成器解决了什么问题？

**标准回答：**

`dochub-agent-id-generator-framework` 提供时间、worker 和 sequence 的自定义 ID。`DefaultUidGenerator` 使用自定义 epoch 和位分配，worker 通过 Redis/MySQL 协调，序列在同一时间片内递增，并检测时钟回拨；`CachedUidGenerator` 预先把 ID 放入幂等 RingBuffer，后台填充，业务线程直接取号，降低高并发下的生成延迟。它用于文档、任务和生成记录等实体 ID。

**优点：** 趋势递增、可分布式生成、避免数据库自增热点；缓存模式可以提高吞吐。

**不足与改进：** 依赖时钟和 worker 分配；RingBuffer 为空时需要定义阻塞或失败策略。可以增加时钟回拨告警、worker 租约续期、ID 生成耗时和空槽率指标，必要时采用成熟 Snowflake/号段模式。

### 39. 这个项目的主要性能瓶颈会在哪里？

**标准回答：**

第一是模型调用和 embedding，尤其是查询改写、路由、RAG 回答、推荐问题可能叠加；第二是文档批量 embedding 和 ES/Qdrant 网络请求；第三是大文件 Tika 解析和 parent-child 分块；第四是同步 Kafka `send().get()`、ES `Refresh.WaitFor` 和外部 rerank；第五是流式后处理、摘要和推荐共用线程池时的排队。

当前配置已经有独立的 RAG、内存摘要和后处理线程池，也设置了队列和 `CallerRunsPolicy`。进一步应建立端到端阶段耗时指标，按租户限流，缓存 query rewrite/embedding/路由结果，批量 upsert，采用 ES alias + 异步 refresh，并把非关键推荐和摘要移到异步事件。

### 40. 如果线上出现“答案慢”，你如何定位？

**标准回答：**

先用 conversationId、exchangeId、traceId 串起一次请求，查看是首 token 慢还是总时长慢。再拆分准备阶段、技能/路由、每个子问题、向量通道、关键词通道、rerank、Prompt 生成、模型首 token、模型总 token、摘要和推荐的耗时。项目已有 thinking steps、retrieval notes、tool traces、channel traces 和管理端观测视图，可以先判断是排队、下游超时还是模型本身慢。

如果是检索慢，查看线程池队列、Qdrant/ES P95 和超时率；如果是模型慢，查看调用次数、Prompt token 和供应商限流；如果是尾部慢，检查摘要、推荐和引用后处理。优化前先保留基线，避免只凭感觉调参。

### 41. 线上遇到重复索引或脏数据，你会从哪些点排查？

**标准回答：**

检查文档/任务状态是否重复进入 `RUNNING`，Kafka 是否重复投递，恢复任务是否和消费者同时重试，向量 upsert 的 ID 是否稳定，ES 是否使用了同一 document/task/version 过滤。当前恢复任务定时扫描旧的 NEW 任务并重新发送，缺少跨实例 claim 时可能发生并发重发；消费者异常处理也需要确认 offset 提交策略。

改进是为任务增加版本和唯一幂等键，用数据库 CAS 抢占任务；向量和关键词索引使用 documentId + indexVersion 的稳定 ID；新索引完成后用 alias 原子切换，旧版本延迟清理。

### 42. 你会怎样测试这个项目，而不是只测 Controller？

**标准回答：**

测试分四层：第一，纯单元测试，覆盖 chunk strategy、Jaccard semantic split、查询拆分校验、RRF、Prompt budget、路由置信度和状态机；第二，集成测试，用 Testcontainers 或替代服务验证 MySQL、Kafka、Qdrant、ES、Neo4j 的索引和降级；第三，契约测试，固定 SSE event schema、管理接口和文档任务状态；第四，离线 RAG 评测，用问题—标准答案—证据集测 Recall@K、MRR、引用支持率、无证据拒答率、首 token 延迟和成本。

特别要测试：同会话并发、停止与完成竞态、Redis 租约过期、Kafka 重复消费、Qdrant/ES 单点超时、长文上下文预算和低质量 PDF。

### 43. 如果让你继续迭代，你优先改哪三项？

**标准回答：**

第一，安全和权限：移除明文密钥，改 Argon2id/BCrypt，补齐管理接口鉴权、RBAC、限流和审计。第二，可靠性：用 outbox + 重试/DLQ、任务 claim/CAS、索引版本和 alias，解决重复消费及状态不一致。第三，RAG 质量和评测：建立真实问题集，校准路由/召回阈值，引入 token-aware budget、可配置 rerank 和引用覆盖率校验。

这三项分别解决“能否安全上线”“故障能否恢复”“答案是否真的好”，比继续堆新的 Agent 工具更有收益。

## 七、面试时可直接使用的总结回答

### 44. 这个项目最能体现你的技术能力是什么？

**标准回答：**

我认为不是单独接入一个大模型，而是把模型能力落成了可控的业务系统：我能解释从文档上传、异步解析、策略化分块、向量/关键词索引，到查询改写、知识路由、混合召回、父块提升、Prompt 预算、流式输出、引用追踪和会话持久化的完整链路；同时也能讲清楚 Redis 租约、Kafka 重试、模型调用上限、RAG 无证据降级和权限安全等工程约束。

我不会把当前实现说成已经解决所有问题。当前明确的改进项包括：密钥外置、密码哈希升级、跨实例流事件恢复、任务幂等、token 级预算、阈值评测和引用支持校验。

### 45. 如果面试官质疑“这是不是只是把几个 API 拼起来”，如何回答？

**标准回答：**

单纯调用模型 API 只能得到文本，本项目还处理了文档生命周期、异步任务、结构化解析、父子分块、双通道检索、图谱路由、会话记忆、流式协议、并发租约、失败降级、引用审计、生成导入和管理员权限。真正有工程难度的是让这些组件在失败、超时、取消、重复消费和上下文受限时仍然表现可预期。

我会举一个具体例子：文档问答不是直接把全文发给模型，而是先根据问题选择执行模式，必要时拆分子问题，并行查 Qdrant 和 ES，做阈值过滤、RRF、父块提升和上下文预算；没有证据时直接拒答，回答结束还把引用和检索轨迹落库。这些都是业务闭环，不是单个 API 调用。

## 附录：面试前应熟记的代码事实

| 主题 | 当前代码事实 |
|---|---|
| 聊天入口 | `/api/chat/stream`，返回 SSE |
| 事件类型 | `text`、`thinking`、`status`、`skill`、`reference`、`recommend`、`error` |
| 执行模式 | ReAct、Retrieval、Plan-and-Execute、Clarification、Graph Only、Graph Then Evidence |
| 检索 | Qdrant 向量 + Elasticsearch 关键词 + RRF |
| RAG 参数 | 向量 topK 8、关键词 topK 8、候选 topK 10、最终 topK 5；总证据预算约 5200 字符 |
| 文档解析 | Tika；支持 PDF、DOC/DOCX、HTML、TXT、Markdown |
| 异步任务 | Kafka；数据库保存文档、任务、步骤和日志 |
| 文件存储 | MinIO |
| 结构查询 | Neo4j 优先，MySQL 回退；主要是文档结构图谱 |
| 记忆 | MySQL/业务摘要 + Qdrant 会话摘要向量 |
| 并发控制 | 本地 `ChatRuntimeRegistry` + Redis owner-token 租约 |
| Agent 防护 | 模型调用上限、工具调用上限、工具重试、工具错误拦截 |
| 生成能力 | 模板大纲、Markdown/DOCX 导出、参考文档仿写、生成文档重新入库 |
| 关键风险 | 明文敏感配置、快速 SHA-256 密码哈希、部分接口鉴权范围、任务幂等与跨实例流恢复 |

> 面试表达建议：先讲“当前实现”，再讲“为什么这样设计”，最后主动补一句“生产上我会怎样改”。这样既能体现项目真实深度，也能体现工程判断力。
