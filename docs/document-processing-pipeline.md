# 文档上传、解析、分块与父子块关联策略 — 完整学习指南

> 基于 `dochub-agent` 项目源码分析，涵盖从上传到向量化入库的全链路。

---

## 目录

1. [整体架构概览](#1-整体架构概览)
2. [阶段一：文档上传](#2-阶段一文档上传)
3. [阶段二：异步解析 (Parse Route)](#3-阶段二异步解析-parse-route)
4. [阶段三：策略推荐 (Strategy Recommendation)](#4-阶段三策略推荐-strategy-recommendation)
5. [阶段四：策略确认 (Strategy Confirmation)](#5-阶段四策略确认-strategy-confirmation)
6. [阶段五：索引构建 — 切块流水线 (Index Build)](#6-阶段五索引构建--切块流水线-index-build)
7. [父子块的关联机制](#7-父子块的关联机制)
8. [父子块的划分逻辑详解](#8-父子块的划分逻辑详解)
9. [完整示例：一份文档的“一生”](#9-完整示例一份文档的一生)
10. [关键设计决策总结](#10-关键设计决策总结)

---

## 1. 整体架构概览

```
┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐
│  Upload  │───▶│  Kafka   │───▶│ Parse Route  │───▶│   Strategy   │───▶│  Kafka   │
│ (同步)    │    │ (异步)    │    │  (异步处理)   │    │  Recommend   │    │ (异步)    │
└──────────┘    └──────────┘    └──────────────┘    └──────────────┘    └──────────┘
                                                                             │
                                                                             ▼
┌──────────┐    ┌──────────┐    ┌──────────────┐    ┌──────────────┐
│ PGVector │◀───│ Vectorize│◀───│  Persist     │◀───│ Index Build  │
│ 入库      │    │ 向量化    │    │  Parent+Chunk│    │ 切块流水线    │
└──────────┘    └──────────┘    └──────────────┘    └──────────────┘
```

**核心双流水线设计**：每个文档执行两套切块策略：
- **PARENT 流水线**：生成"回答单元"——较大的文本块，用于 LLM 回答时提供充足上下文
- **CHILD 流水线**：生成"检索单元"——较小的文本块，用于向量相似度检索，精确定位相关内容

---

## 2. 阶段一：文档上传

### 入口

`POST /manage/document/upload` → `DocumentManageServiceImpl.upload()`

### 处理步骤

| 步骤 | 操作 | 产出 |
|------|------|------|
| 1 | 校验文件非空、有可识别扩展名 | 文件类型枚举 (`DocumentFileTypeEnum`) |
| 2 | 生成分布式 ID (`UidGenerator`) | `documentId` |
| 3 | 上传原始文件到 MinIO | `bucketName` + `objectName` |
| 4 | 插入 `dochub_document` 记录 | 状态: `PARSING` + `WAIT_RECOMMEND` + `WAIT_BUILD` |
| 5 | 创建 `dochub_document_task` | 类型=`PARSE_ROUTE`, 状态=`NEW` |
| 6 | 发送 Kafka 消息 | topic: `dochub-agent-document-parse-route` |

> **同步返回**：上传接口立即返回 `documentId`，后续解析是异步的。

---

## 3. 阶段二：异步解析 (Parse Route)

### 3.1 整体流程

```
Kafka Consumer → DocumentAsyncProcessServiceImpl.handleParseRoute()
  ├─ 下载原始文件 (MinIO)
  ├─ TikaDocumentParserService.parse()  ← 核心解析
  │    ├─ extractRawText()     → Apache Tika / UTF-8
  │    ├─ cleanupText()        → 规范化处理
  │    ├─ structureNodeExtractor.extract() → 4阶段结构抽取
  │    ├─ estimateTokenCount() → 估算 token 数
  │    ├─ evaluateStructureLevel()    → 结构等级
  │    └─ evaluateContentQuality()   → 内容质量等级
  ├─ 上传解析后纯文本到 MinIO
  ├─ 持久化结构节点到 DB
  ├─ 同步导航产物 (ES 索引 + Neo4j 图投影)
  ├─ 生成文档画像 (AI Profile: 摘要、主题、示例问题)
  ├─ 策略推荐
  └─ 标记 PARSE_SUCCESS + RECOMMENDED
```

### 3.2 文本清洗 (`cleanupText`)

原始文本 → 清洗后文本：

```
原始: "第一章  概述\r\n\r\n1.1  背景\r\n\r\n正文内容……\r\n\r\n"
      ↓
清洗: "第一章 概述\n\n1.1 背景\n\n正文内容……"
```

**清洗规则**：
- `\r\n` → `\n`，`\r` → `\n`
- 空字符 `\0` → 空格
- Tab/垂直制表符 → 空格
- 连续 3 个以上换行 → 压缩为 2 个换行（空段落）
- 连续 2 个以上空格 → 1 个空格

### 3.3 结构节点抽取 — 4 阶段流水线

这是整个系统最精妙的部分，决定了后续父子块的划分质量。

#### Stage 1: 信号提取 (`DocumentStructureSignalExtractor`)

**输入**：清洗后的纯文本  
**输出**：`DocumentStructureSignalBatch`（信号列表 + 上下文行）

逐行扫描文本，用正则表达式将每一行分类为：

| 信号类型 | 示例 | 置信度 |
|----------|------|--------|
| `HEADING` | `## 第二章 系统设计`、`1.2.3 子章节`、`第一章 概述` | 高 (0.85~1.0) |
| `HEADING_CANDIDATE` | `一、项目背景`（短且无句末标点） | 中 (0.58~0.62) |
| `LIST_ITEM` | `- 功能点一`、`* 需求项` | 高 |
| `STEP_ITEM` | `第1步：登录系统`、`步骤一` | 高 |
| `TABLE_ROW` | `\| 列1 \| 列2 \|` | 高 |
| `BODY` | 普通段落文本 | — |
| `BLANK` | 空行 | — |
| `NOISE` | 页码、水印残留 | — |

**识别规则示例**：
- Markdown 标题：`^#{1,6}\s+`
- 数字编号标题：`^\d+(\.\d+)*\s+`
- 中文章节：`^第[一二三四五六七八九十百千]+章`
- 中文纲要：`^[一二三四五六七八九十]、`
- 步骤标记：`^第\d+步[：:]`
- 列表标记：`^[-*•]\s+`

#### Stage 2: 歧义消解 (`DocumentStructureAmbiguityResolver`)

**输入**：所有信号 + 上下文行  
**输出**：消歧后的信号列表

仅处理 `HEADING_CANDIDATE`（置信度 0.45~0.80 之间）。如果不启用 LLM 消歧（默认），则跳过此阶段。

启用 LLM 时：将候选行及其上下文窗口（前后各 N 行）发送给 LLM，让 LLM 判定该行是 `HEADING`、`LIST_ITEM` 还是 `BODY`。

#### Stage 3: 层级构建 (`DocumentStructureHierarchyResolver`)

**输入**：消歧后的信号列表  
**输出**：`List<DocumentStructureNodeDraft>`（带父子关系的草稿树）

这是父子关系建立的**第一个关键步骤**：

```
信号序列:  [HEADING:第一章] [BODY:...] [HEADING:1.1] [BODY:...] [LIST_ITEM] [BODY:...]
               │                │            │            │           │
               ▼                ▼            ▼            ▼           ▼
树结构:
  DOCUMENT (根)
    ├─ SECTION "第一章"  (depth=1)
    │    ├─ SECTION "1.1"  (depth=2)
    │    │    ├─ LIST_ITEM "需求1"
    │    │    └─ LIST_ITEM "需求2"
    │    └─ BODY content...
    └─ BODY content...
```

**核心算法**：用**深度栈**维护标题层级关系。
- Markdown 标题级别 → 深度
- 数字路径深度（如 `1.2.3` → depth=3）
- 遇到新标题时弹栈到合适层级，再入栈

LIST_ITEM / STEP_ITEM 嵌套通过**缩进级别栈**管理。

#### Stage 4: 树校验与构建 (`DocumentStructureTreeValidator`)

**输入**：草稿节点列表  
**输出**：`List<DocumentStructureNodeCandidate>`（最终候选节点）

校验与修复操作：

1. **合并合成标题**：如果一个 SECTION 节点的 `contentText` 与标题完全相同（如 `"1.1 概述"` 的内容也只有 `"1.1 概述"`），则该节点是"伪节点"——没有实质内容，将其与父节点合并
2. **修复无效父节点**：如果一个 SECTION 的父节点是 LIST_ITEM，重新挂载到最近的 SECTION 祖先
3. **重算深度**：根据修复后的树重新计算每个节点的 `depth`
4. **重建路径**：
   - `canonicalPath`：`/document/概述/背景`（URL 风格）
   - `sectionPath`：`概述 > 背景`（面包屑风格）
5. **重建兄弟链接**：设置 `prevSiblingNodeId` / `nextSiblingNodeId`

### 3.4 结构节点实体

持久化到 `dochub_document_structure_node` 表：

```
┌──────────────────────────────────────────────────────────┐
│              DochubDocumentStructureNode             │
├──────────────┬───────────────────────────────────────────┤
│ id           │ 节点唯一ID                                │
│ documentId   │ 所属文档ID                                │
│ nodeNo       │ 节点序号                                  │
│ nodeType     │ DOCUMENT(1) / SECTION(2) / STEP(3) /     │
│              │ LIST_ITEM(4)                              │
│ parentNodeId │ 父节点ID (树形结构的关联键)               │
│ prevSibling  │ 前一个兄弟节点ID                          │
│ nextSibling  │ 后一个兄弟节点ID                          │
│ depth        │ 在树中的深度 (根节点=0)                   │
│ nodeCode     │ 节点编码                                  │
│ title        │ 节点标题                                  │
│ anchorText   │ 锚文本 (用于导航定位)                      │
│ canonicalPath│ /document/章节1/子章节1                    │
│ sectionPath  │ 章节1 > 子章节1                           │
│ contentText  │ 该节点下的完整文本内容                     │
│ itemIndex    │ 列表项序号                                │
└──────────────┴───────────────────────────────────────────┘
```

---

## 4. 阶段三：策略推荐 (Strategy Recommendation)

### 4.1 推荐逻辑

`DocumentStrategyServiceImpl.recommendStrategy()` 根据解析结果自动推荐。

#### 父块 (PARENT) 策略推荐

```
structureLevel >= MEDIUM 或 headingCount >= 2
  AND 文件类型是 PDF/DOC/DOCX/MD/HTML ?
    ├─ YES → PARENT = [STRUCTURE]  ← 优先保留章节边界
    └─ NO  → PARENT = [RECURSIVE]  ← 大粒度递归分块
```

#### 子块 (CHILD) 策略推荐

```
contentQuality == LOW AND 文本够长 AND llmRecommendWhenLowQuality=true ?
  ├─ YES → CHILD = [LLM, RECURSIVE]       ← LLM 智能切块 + 长度兜底
  └─ NO  → 继续判断

paragraphCount >= 3 AND contentQuality >= MEDIUM ?
  ├─ YES → CHILD = [SEMANTIC, RECURSIVE]  ← 语义分块 + 长度兜底
  └─ NO  → CHILD = [RECURSIVE]            ← 纯递归分块
```

### 4.2 策略快照

推荐结果存为快照字符串，例如：

```
PARENT:1,2;CHILD:3,2
```

解读：
- **PARENT**: 策略类型 `1`(STRUCTURE) → `2`(RECURSIVE)
- **CHILD**: 策略类型 `3`(SEMANTIC) → `2`(RECURSIVE)

---

## 5. 阶段四：策略确认 (Strategy Confirmation)

`POST /manage/document/strategy/confirm` 允许用户调整策略后确认。

- 用户可传入 `parentStrategyTypes` 和 `childStrategyTypes` 覆盖推荐
- 未修改 → 标记 `CONFIRMED`
- 已修改 → 创建新版本方案，标记 `USER_ADJUST`，旧方案废弃

---

## 6. 阶段五：索引构建 — 切块流水线 (Index Build)

### 6.1 入口

`POST /manage/document/index/build` → 发送 Kafka 消息 → `handleIndexBuild()`

### 6.2 核心方法：`buildParentBlocks()`

这是整个父子块关系建立的**核心**。

```java
// DocumentStrategyServiceImpl.buildParentBlocks()
public List<ParentBlockCandidate> buildParentBlocks(document, plan, steps, parsedText) {

    // 1. 拆分 PARENT 和 CHILD 步骤
    List<Step> parentSteps = sortPipelineSteps(steps, PARENT);
    List<Step> childSteps  = sortPipelineSteps(steps, CHILD);

    // 2. 构建父块种子列表
    List<ChunkCandidate> parentSeedList = buildParentSeedList(parsedText, parentSteps, structureNodes);

    // 3. 遍历每个父块种子，为其生成子块
    for (ChunkCandidate parentSeed : cleanupChunkList(parentSeedList)) {
        // 3a. 在父块文本范围内生成子块
        List<ChunkCandidate> childSeedList = buildChildSeedList(parentSeed, childSteps, structureNodes);

        // 3b. 打包为 ParentBlockCandidate
        parentBlockList.add(new ParentBlockCandidate(
            parentSeed.sectionPath, parentSeed.structureNodeId,
            parentSeed.text, parentSeed.sourceType,
            childSeedList   // ← 子块列表挂载到父块上
        ));
    }
    return cleanupParentBlockList(parentBlockList);
}
```

### 6.3 父块种子生成 (`buildParentSeedList`)

```
PARENT 步骤中包含 STRUCTURE 且有结构节点？
  ├─ YES → buildStructureParentSeeds(structureNodes)
  │         └─ 遍历所有 SECTION 节点，筛选"有内容的章节"
  │            作为父块种子，每个种子携带 sectionPath + structureNodeId
  │
  │         然后对结构种子执行 PARENT 流水线的剩余步骤
  │         (如 STRUCTURE→RECURSIVE，则对每个结构种子再做递归切分)
  │
  └─ NO  → 从全文开始，执行 PARENT 流水线全部步骤
```

**"有内容的章节"判定** (`isContentBearingSection`)：
- 有子章节 → 内容的长度必须 > 标题长度 + 16 个字符（或包含换行），否则认为是纯标题章节（无实质内容）
- 无子章节 → 只要 `contentText` 非空即可

### 6.4 子块种子生成 (`buildChildSeedList`)

```java
private List<ChunkCandidate> buildChildSeedList(ChunkCandidate parentSeed, childSteps, structureNodes) {
    // 如果 CHILD 步骤包含 STRUCTURE 且父块有关联的 structureNodeId
    if (containsStructureStep(childSteps) && parentSeed.structureNodeId != null) {
        // 找到该结构节点下的所有子节点（SECTION、STEP、LIST_ITEM）
        // 每个子节点成为一个子块种子
        List<ChunkCandidate> structureSeeds = buildStructureChildSeeds(parentSeed, structureNodes);
        // 对结构种子执行 CHILD 流水线的剩余步骤
        return executePipeline(structureSeeds, remainingSteps, CHILD);
    }
    // 否则，以父块完整文本为种子，执行 CHILD 流水线全部步骤
    return executePipeline([parentSeed.text], childSteps, CHILD);
}
```

> **关键**：子块永远在父块文本的**边界内**生成——子块不会跨越父块边界。

### 6.5 四种切块策略详解

#### 策略 1：STRUCTURE（基于文档结构切块）

```
输入文本 → 逐行扫描
  ├─ 遇到标题行 → flush 当前块 + 更新 sectionPath + 开始新块
  └─ 遇到正文行 → 追加到当前块

参数: 无额外参数，完全由文档结构决定
```

#### 策略 2：RECURSIVE（递归分块）

```
输入文本
  ├─ 长度 ≤ maxChars ? → 直接返回
  ├─ 能按段落切分 (双换行) ? → mergeAndSplit(段落列表)
  ├─ 能按行切分 (单换行) ?   → mergeAndSplit(行列表)
  ├─ 能按句子切分 ?           → mergeAndSplit(句子列表)
  └─ 都不行 → 固定窗口切分 (step = maxChars - overlapChars)

参数:
  父块: maxChars=2200, overlapChars=180
  子块: maxChars=800,  overlapChars=120 (可配置)
```

#### 策略 3：SEMANTIC（语义分块）

```
输入文本 → 按句子切分 ([。！？!?;\.] 断句)
  → 逐句累积
    ├─ 当前块 + 新句子 > semanticMaxChars ? → flush
    ├─ jaccard(当前块词集, 句子词集) < threshold 且当前块 >= minChars ? → flush
    └─ 否则 → 继续累积

Jaccard 相似度 = |A ∩ B| / |A ∪ B|
  中文: 逐字 token
  英文: 逐词 token (按空格切分 + [A-Za-z0-9]{2,})

参数:
  父块: maxChars=1600, minChars=480
  子块: maxChars=700,  minChars=240
  threshold: 0.18 (可配置)
```

#### 策略 4：LLM（大模型智能切块）

```
输入文本
  ├─ 超过 llmMaxChars (3500) ? → 先递归切分
  └─ 发送 LLM prompt: "请将以下文本切分为语义完整的段落，返回 JSON 数组"
      ├─ 成功 → 使用 LLM 返回的切分
      └─ 失败 → 回退到 SEMANTIC
```

### 6.6 流水线执行模式

策略步骤是**链式串联**执行的：

```
输入文本
  → STRATEGY_1 → [chunk1, chunk2, chunk3]
    → STRATEGY_2 → 对每个 chunk 分别执行，结果展开
      → [chunk1_sub1, chunk1_sub2, chunk2_sub1, chunk3_sub1, chunk3_sub2]
```

例如 `PARENT:1,2`（STRUCTURE → RECURSIVE）：
1. STRUCTURE 先把全文按章节标题切为 3 个父块
2. RECURSIVE 对每个父块检查是否超长（>2200 字符），超长则递归切分

### 6.7 持久化

`buildParentChildEntities()` 将 `ParentBlockCandidate` 列表转为 DB 实体：

```
ParentBlockCandidate 列表
  ├─ ParentBlock #1  (parentNo=1, parentText="第一章...")
  │    ├─ Chunk #1   (chunkNo=1, parentBlockId=PB#1, chunkText="...")
  │    ├─ Chunk #2   (chunkNo=2, parentBlockId=PB#1, chunkText="...")
  │    └─ Chunk #3   (chunkNo=3, parentBlockId=PB#1, chunkText="...")
  ├─ ParentBlock #2  (parentNo=2, startChunkNo=4, endChunkNo=6, childCount=3)
  │    ├─ Chunk #4   (chunkNo=4, parentBlockId=PB#2)
  │    ├─ Chunk #5   (chunkNo=5, parentBlockId=PB#2)
  │    └─ Chunk #6   (chunkNo=6, parentBlockId=PB#2)
  └─ ...
```

### 6.8 向量化

持久化完成后，对所有 Chunk 批量向量化（每批 10 个）：

```
EmbeddingModel.embed([chunk1, chunk2, ..., chunk10])
  → 写入 PGVector 表 dochub_document_embedding
    ├─ embedding: vector(768)  或 vector(1536)
    ├─ metadata_json: {sectionPath, canonicalPath, structureNodeId, ...}
    └─ chunk_text: 原文
```

---

## 7. 父子块的关联机制

### 7.1 关联方式一：DB 外键

```
┌──────────────────────────────┐
│ DochubDocumentParentBlock│
│  id: 1001                    │
│  documentId: 5001            │
│  parentNo: 1                 │
│  sectionPath: "概述 > 背景"   │
│  parentText: "第一章..."      │
│  childCount: 3               │
│  startChunkNo: 1             │──┐
│  endChunkNo: 3               │  │
└──────────────────────────────┘  │
                                  │ parentBlockId 指向父块 ID
┌──────────────────────────────┐  │
│ DochubDocumentChunk      │◀─┘
│  id: 2001                    │
│  parentBlockId: 1001  ←──────┤ 外键关联
│  chunkNo: 1                  │
│  chunkText: "1.1 背景..."    │
├──────────────────────────────┤
│  id: 2002                    │
│  parentBlockId: 1001  ←──────┤
│  chunkNo: 2                  │
├──────────────────────────────┤
│  id: 2003                    │
│  parentBlockId: 1001  ←──────┘
│  chunkNo: 3
└──────────────────────────────┘
```

### 7.2 关联方式二：PGVector metadata

向量库中也冗余存储父子关系：

```json
// dochub_document_embedding 表的 metadata_json 字段
{
  "documentId": 5001,
  "parentBlockId": 1001,
  "parentBlockNo": 1,
  "sectionPath": "概述 > 背景",
  "canonicalPath": "/document/概述/背景",
  "structureNodeId": 20001,
  "chunkNo": 1
}
```

### 7.3 关联方式三：canonicalPath / sectionPath 语义关联

即使不查 DB 外键，父子块通过 `canonicalPath` 和 `sectionPath` 也有语义上的层级包含关系：

```
父块 canonicalPath:  /document/概述
子块 canonicalPath:  /document/概述/背景    ← 路径包含
```

### 7.4 检索时的父子块协同

检索流程（向量搜索 → 定位到 chunk → 回溯 parent block）：

```
用户查询: "系统的背景是什么"
  → 向量相似度检索 → 命中 Chunk #2002 (score=0.92)
  → 通过 parentBlockId 回溯 → ParentBlock #1001
  → 将 ParentBlock #1001 的完整文本 + 相邻 Chunk 上下文 → 喂给 LLM
```

**核心思想**：小粒度 chunk 做检索（精确命中），大粒度 parent block 做回答（充足上下文）。

---

## 8. 父子块的划分逻辑详解

### 8.1 划分规则总结

| 划分维度 | 父块 (Parent) | 子块 (Chunk) |
|----------|---------------|--------------|
| **粒度** | 大 (1600~2200 字符) | 小 (700~800 字符) |
| **目的** | 回答单元 (answer unit) | 检索单元 (retrieval unit) |
| **策略** | STRUCTURE 优先 / RECURSIVE 兜底 | LLM/SEMANTIC 优先 / RECURSIVE 兜底 |
| **边界** | 尊重文档章节边界 | 在父块边界内划分 |
| **重叠** | overlap=180 | overlap=120 |

### 8.2 划分时的 STRUCTURE 特殊逻辑

当 STRUCTURE 策略存在时，父子块划分使用文档结构树：

```
文档结构树:                    父子块划分:
DOCUMENT                       ┌──────────────────────┐
├─ SECTION "第一章" (有内容)    │ ParentBlock #1        │
│   ├─ SECTION "1.1" (有内容)  │  text: "第一章概述\n  │
│   │   ├─ LIST_ITEM "需求1"   │         1.1背景……"    │
│   │   └─ LIST_ITEM "需求2"   │  ├─ Chunk #1: "1.1..│
│   └─ SECTION "1.2" (有内容)  │  ├─ Chunk #2: "需求1│
│       └─ STEP "步骤1"        │  └─ Chunk #3: "需求2│
└─ SECTION "第二章" (无子内容)  ├──────────────────────┤
                                │ ParentBlock #2        │
                                │  text: "1.2 方案……"  │
                                │  ├─ Chunk #4: "步骤1│
                                │  └─ Chunk #5: "……"  │
                                ├──────────────────────┤
                                │ ParentBlock #3        │
                                │  text: "第二章……"    │
                                │  └─ Chunk #6: "……"  │
                                └──────────────────────┘
```

**关键规则**：
1. **有内容的 SECTION → 父块**：`isContentBearingSection()` 过滤掉纯标题节点
2. **父块包含其整个子树的文本**：`contentText` 是递归拼接的
3. **子块在父块内生成**：`buildChildSeedList()` 从结构树中找该父块节点的直接子节点（SECTION、STEP、LIST_ITEM）作为子块种子

### 8.3 划分时的非 STRUCTURE 逻辑

如果没有结构树（或 STRUCTURE 策略未启用），则：

```
全文
  → PARENT 流水线: RECURSIVE(maxChars=2200, overlap=180)
    → [父块A, 父块B, 父块C]
      → 对每个父块执行 CHILD 流水线: SEMANTIC(maxChars=700) → RECURSIVE(maxChars=800)
        → 父块A 内: [子块A1, 子块A2, 子块A3]
        → 父块B 内: [子块B1, 子块B2]
        → 父块C 内: [子块C1, 子块C2, 子块C3, 子块C4]
```

---

## 9. 完整示例：一份文档的“一生”

假设上传一份 `系统设计文档.pdf`，内容如下：

```markdown
# 智能客服系统设计文档

## 第一章 项目概述

本文档描述智能客服系统的整体设计方案。该系统旨在通过 AI 技术提升客服效率，
降低人工成本，并提供 7×24 小时不间断服务。

### 1.1 项目背景

随着企业业务规模扩大，传统人工客服面临人力成本高、响应速度慢、服务质量
不稳定等挑战。近年来大语言模型技术的突破为解决这些问题提供了新的可能性。

### 1.2 核心目标

本项目的核心目标包括以下几个方面：
- 实现常见问题的自动应答，覆盖率达到 80% 以上
- 复杂问题无缝转接人工坐席
- 支持多渠道接入（网页、APP、微信、电话）
- 提供知识库管理和持续学习能力

## 第二章 技术架构

系统采用微服务架构，主要包含以下模块：接入网关、对话引擎、知识管理、
数据分析以及运维监控。

### 2.1 接入网关

接入网关负责统一接收来自不同渠道的用户请求，进行身份认证、流量控制、
协议转换后转发到对话引擎。网关层采用 Spring Cloud Gateway 实现。

### 2.2 对话引擎

对话引擎是系统的核心模块，负责意图识别、对话管理、回复生成。
采用 RAG (Retrieval-Augmented Generation) 架构，结合向量检索和大模型生成
能力，确保回复的准确性和可控性。

#### 2.2.1 意图识别

意图识别模块基于微调后的 BERT 模型，支持 50+ 种业务意图的分类。
当置信度低于阈值时，系统会主动向用户确认意图。

#### 2.2.2 回复生成

回复生成结合检索增强和模板生成两种方式。对于知识库覆盖的问题优先使用
检索结果进行生成；对于流程性问题使用预定义模板确保合规性。
```

### 9.1 阶段一：上传

```
文件: 系统设计文档.pdf
大小: ~3KB
类型: PDF
→ MinIO 存储路径: documents/2025/06/25/abc123.pdf
→ documentId: 5001
→ Kafka 消息已发送
```

### 9.2 阶段二：解析

#### Step 1: Tika 提取原始文本

Tika 从 PDF 提取出的原始文本（含格式噪声）：

```
智能客服系统设计文档\n\n第一章  项目概述\n\n本文档描述智能客服系统的整体设计方案。该系统旨在通过 AI 技术提升客服效率，\n降低人工成本，并提供 7×24 小时不间断服务。\n\n1.1  项目背景\n\n随……
```

#### Step 2: cleanupText 清洗后

```
智能客服系统设计文档

第一章 项目概述

本文档描述智能客服系统的整体设计方案。该系统旨在通过 AI 技术提升客服效率，
降低人工成本，并提供 7×24 小时不间断服务。

1.1 项目背景

随着企业业务规模扩大，传统人工客服面临人力成本高、响应速度慢、服务质量
不稳定等挑战。近年来大语言模型技术的突破为解决这些问题提供了新的可能性。

1.2 核心目标

本项目的核心目标包括以下几个方面：
- 实现常见问题的自动应答，覆盖率达到 80% 以上
- 复杂问题无缝转接人工坐席
- 支持多渠道接入（网页、APP、微信、电话）
- 提供知识库管理和持续学习能力

第二章 技术架构

系统采用微服务架构，主要包含以下模块：接入网关、对话引擎、知识管理、
数据分析以及运维监控。

2.1 接入网关

接入网关负责统一接收来自不同渠道的用户请求，进行身份认证、流量控制、
协议转换后转发到对话引擎。网关层采用 Spring Cloud Gateway 实现。

2.2 对话引擎

对话引擎是系统的核心模块，负责意图识别、对话管理、回复生成。
采用 RAG (Retrieval-Augmented Generation) 架构，结合向量检索和大模型生成
能力，确保回复的准确性和可控性。

2.2.1 意图识别

意图识别模块基于微调后的 BERT 模型，支持 50+ 种业务意图的分类。
当置信度低于阈值时，系统会主动向用户确认意图。

2.2.2 回复生成

回复生成结合检索增强和模板生成两种方式。对于知识库覆盖的问题优先使用
检索结果进行生成；对于流程性问题使用预定义模板确保合规性。
```

**解析统计**：
- charCount: ~980
- headingCount: 8 (一个 #, 两个 ##, 两个 ###, 三个 ####)
- paragraphCount: 12
- structureLevel: HIGH (headingCount ≥ 5)
- contentQualityLevel: HIGH (无乱码，长度 > 500)

#### Step 3: 结构节点抽取

**Stage 1 - 信号提取结果**：

| 行号 | 原始文本 | 信号类型 | 置信度 |
|------|----------|----------|--------|
| 1 | `智能客服系统设计文档` | HEADING (#级) | 0.95 |
| 3 | `第一章 项目概述` | HEADING (##级) | 0.90 |
| 5-6 | `本文档描述...` | BODY | 0.95 |
| 8 | `1.1 项目背景` | HEADING (###级) | 0.92 |
| 10-11 | `随着企业...` | BODY | 0.95 |
| 13 | `1.2 核心目标` | HEADING (###级) | 0.92 |
| 16 | `- 实现常见问题...` | LIST_ITEM | 0.85 |
| 17 | `- 复杂问题...` | LIST_ITEM | 0.85 |
| 18 | `- 支持多渠道...` | LIST_ITEM | 0.85 |
| 19 | `- 提供知识库...` | LIST_ITEM | 0.85 |
| 21 | `第二章 技术架构` | HEADING (##级) | 0.90 |
| ... | ... | ... | ... |

**Stage 3 - 层级构建结果**（草稿树）：

```
DOCUMENT (nodeNo=1, depth=0)
│
├─ SECTION: "智能客服系统设计文档" (nodeNo=2, depth=1)
│   │  parentNodeId=1
│   │  contentText="智能客服系统设计文档\n\n..."
│   │
│   ├─ SECTION: "第一章 项目概述" (nodeNo=3, depth=2)
│   │   │  parentNodeId=2
│   │   │  contentText="第一章 项目概述\n\n本文档描述..."
│   │   │
│   │   ├─ SECTION: "1.1 项目背景" (nodeNo=4, depth=3)
│   │   │   │  parentNodeId=3
│   │   │   │  contentText="1.1 项目背景\n\n随着企业..."
│   │   │
│   │   └─ SECTION: "1.2 核心目标" (nodeNo=5, depth=3)
│   │       │  parentNodeId=3
│   │       │  contentText="1.2 核心目标\n\n本项目..."
│   │       ├─ LIST_ITEM: "实现常见问题..." (nodeNo=6, depth=4, parentNodeId=5)
│   │       ├─ LIST_ITEM: "复杂问题无缝转接..." (nodeNo=7, depth=4, parentNodeId=5)
│   │       ├─ LIST_ITEM: "支持多渠道接入..." (nodeNo=8, depth=4, parentNodeId=5)
│   │       └─ LIST_ITEM: "提供知识库管理..." (nodeNo=9, depth=4, parentNodeId=5)
│   │
│   └─ SECTION: "第二章 技术架构" (nodeNo=10, depth=2)
│       │  parentNodeId=2
│       │
│       ├─ SECTION: "2.1 接入网关" (nodeNo=11, depth=3, parentNodeId=10)
│       │
│       └─ SECTION: "2.2 对话引擎" (nodeNo=12, depth=3, parentNodeId=10)
│           │
│           ├─ SECTION: "2.2.1 意图识别" (nodeNo=13, depth=4, parentNodeId=12)
│           │
│           └─ SECTION: "2.2.2 回复生成" (nodeNo=14, depth=4, parentNodeId=12)
```

**Stage 4 - 校验后的最终节点**（部分输出）：

根节点 `智能客服系统设计文档` 被识别为与文档标题重复的"合成标题"——它的 contentText 就是整个文档，但它又是文档标题。校验器判断：如果该节点的内容里包含子节点标题，则保留并让其包含所有子节点内容。

### 9.3 阶段三：策略推荐

```
分析结果:
  fileType=PDF (适合结构切块)
  structureLevel=HIGH (≥5 个标题)
  headingCount=8 (≥2)
  → PARENT 推荐: STRUCTURE

  charCount=980 (>240 minChars)
  paragraphCount=12 (≥3)
  contentQuality=HIGH (≥MEDIUM)
  → CHILD 推荐: SEMANTIC → RECURSIVE

最终策略快照:
  PARENT:1,2;CHILD:3,2
  → PARENT: STRUCTURE → RECURSIVE
  → CHILD:   SEMANTIC → RECURSIVE
```

### 9.4 阶段四：用户确认

用户查看推荐策略，认为合理，直接确认（无调整）。

### 9.5 阶段五：索引构建 — 切块

#### Step 1: 父块种子生成

STRUCTURE 策略从结构节点中筛选"有内容的 SECTION"：

```
筛选结果 (isContentBearingSection):
  ✓ SECTION "智能客服系统设计文档" → 有子节点，内容包含全部子章节文本 → 保留
  ✗ SECTION "第一章 项目概述" → 有子节点(SECTION)，内容 > 标题+16 → 保留
  ✓ SECTION "1.1 项目背景" → 无子 SECTION，有内容 → 保留
  ✓ SECTION "1.2 核心目标" → 有子节点(LIST_ITEM)，内容 > 标题+16 → 保留
  ✗ SECTION "第二章 技术架构" → 有子节点(SECTION)，内容... → 保留
  ✓ SECTION "2.1 接入网关" → 无子 SECTION，有内容 → 保留
  ✓ SECTION "2.2 对话引擎" → 有子节点(SECTION) → 保留
  ✓ SECTION "2.2.1 意图识别" → 无子 SECTION，有内容 → 保留
  ✓ SECTION "2.2.2 回复生成" → 无子 SECTION，有内容 → 保留

共 9 个父块种子 (但根节点和第一章和第二章这种"父节点"有重叠)
```

> **注意**：实际上 `isContentBearingSection` 的判定逻辑，对于有子 SECTION 的节点会更加严格。在该例中，`智能客服系统设计文档`、`第一章 项目概述`、`1.2 核心目标`、`第二章 技术架构`、`2.2 对话引擎` 都包含子节点。校验器会检查这些节点的"自有内容"是否只是子节点文本的简单拼接，如果 `contentText` 去掉子节点文本后剩下的自有内容太少（≤ 标题长度+16），则该节点被过滤掉。

简化起见，假设最终筛选出以下 **5 个父块种子**：

```
父块种子 #1: SECTION "1.1 项目背景"
  sectionPath: "智能客服系统设计文档 > 第一章 项目概述 > 1.1 项目背景"
  contentText: "1.1 项目背景\n\n随着企业业务规模扩大，传统人工客服面临人力成本高、
               响应速度慢、服务质量不稳定等挑战。近年来大语言模型技术的突破为
               解决这些问题提供了新的可能性。"

父块种子 #2: SECTION "1.2 核心目标"
  sectionPath: "智能客服系统设计文档 > 第一章 项目概述 > 1.2 核心目标"
  contentText: "1.2 核心目标\n\n本项目的核心目标包括以下几个方面：\n
               - 实现常见问题的自动应答，覆盖率达到 80% 以上\n
               - 复杂问题无缝转接人工坐席\n
               - 支持多渠道接入（网页、APP、微信、电话）\n
               - 提供知识库管理和持续学习能力"

父块种子 #3: SECTION "2.1 接入网关"
  sectionPath: "智能客服系统设计文档 > 第二章 技术架构 > 2.1 接入网关"
  contentText: "2.1 接入网关\n\n接入网关负责统一接收来自不同渠道的用户请求，
               进行身份认证、流量控制、协议转换后转发到对话引擎。
               网关层采用 Spring Cloud Gateway 实现。"

父块种子 #4: SECTION "2.2.1 意图识别"
  sectionPath: "智能客服系统设计文档 > 第二章 技术架构 > 2.2 对话引擎 > 2.2.1 意图识别"
  contentText: "2.2.1 意图识别\n\n意图识别模块基于微调后的 BERT 模型，
               支持 50+ 种业务意图的分类。当置信度低于阈值时，
               系统会主动向用户确认意图。"

父块种子 #5: SECTION "2.2.2 回复生成"
  sectionPath: "智能客服系统设计文档 > 第二章 技术架构 > 2.2 对话引擎 > 2.2.2 回复生成"
  contentText: "2.2.2 回复生成\n\n回复生成结合检索增强和模板生成两种方式。
               对于知识库覆盖的问题优先使用检索结果进行生成；
               对于流程性问题使用预定义模板确保合规性。"
```

#### Step 2: PARENT RECURSIVE 二次处理

对每个父块种子执行 RECURSIVE(maxChars=2200)。本例中所有父块都未超过 2200 字符，所以**不触发切分**。

#### Step 3: 子块生成

对于每个父块，在父块文本内执行 CHILD 流水线：

**父块 #1 ("1.1 项目背景") → 子块**

```
CHILD 策略: SEMANTIC → RECURSIVE

输入文本:
  "1.1 项目背景\n\n随着企业业务规模扩大，传统人工客服面临人力成本高、
   响应速度慢、服务质量不稳定等挑战。近年来大语言模型技术的突破为
   解决这些问题提供了新的可能性。"

SEMANTIC 分块:
  句子1: "1.1 项目背景"
  句子2: "随着企业业务规模扩大，传统人工客服面临人力成本高、响应速度慢、
          服务质量不稳定等挑战。"
  句子3: "近年来大语言模型技术的突破为解决这些问题提供了新的可能性。"

  Jaccard(句子2, 句子3): 共享 token 有 [企业, 这些, 问题, 可能] → 相似度 ~0.15
  threshold=0.18, 当前块 >=240 chars → flush!

  结果:
    Chunk 1: "1.1 项目背景\n\n随着企业业务规模扩大..."
    Chunk 2: "近年来大语言模型技术的突破为..."

RECURSIVE 二次处理: Chunk 1 和 Chunk 2 都未超过 800 字符 → 不切分

最终子块: [Chunk 1, Chunk 2]
```

**父块 #2 ("1.2 核心目标") → 子块**

```
CHILD 策略: SEMANTIC → RECURSIVE

SEMANTIC 分块:
  句子: ["1.2 核心目标", "本项目的核心目标包括以下几个方面：",
         "实现常见问题的自动应答，覆盖率达到 80% 以上",
         "复杂问题无缝转接人工坐席",
         "支持多渠道接入（网页、APP、微信、电话）",
         "提供知识库管理和持续学习能力"]

  累积过程:
    句子0+1+2: ~160chars, Jaccard(句子2, 句子3) = 0.0 (数字 80% vs 人工坐席 无交集)
              → threshold 0.18, 但当前块 < 240 minChars → 不切
    句子0+1+2+3: ~190chars, Jaccard(句子3, 句子4) = 0.08
              → 当前块 < 240 → 不切
    句子0+1+2+3+4+5: ~250chars, 完成

  结果:
    Chunk 1: "1.2 核心目标\n\n本项目的核心目标包括以下几个方面：\n
             实现常见问题的自动应答，覆盖率达到 80% 以上\n
             复杂问题无缝转接人工坐席\n
             支持多渠道接入（网页、APP、微信、电话）\n
             提供知识库管理和持续学习能力"

  只有 1 个子块，但若父块更长，SEMANTIC 会在语义边界切分。
```

**父块 #3 ("2.1 接入网关") → 子块**

```
文本约 100 字符，SEMANTIC 只有 2 个句子，直接作为一个 Chunk
```

**父块 #4 ("2.2.1 意图识别") → 子块**

```
文本约 80 字符，直接作为一个 Chunk
```

**父块 #5 ("2.2.2 回复生成") → 子块**

```
SEMANTIC 分块:
  句子1: "2.2.2 回复生成"
  句子2: "回复生成结合检索增强和模板生成两种方式。"
  句子3: "对于知识库覆盖的问题优先使用检索结果进行生成；"
  句子4: "对于流程性问题使用预定义模板确保合规性。"

  Jaccard(句子2, 句子3): 共享 [生成, 检索] → 约 0.12 < 0.18 → flush!
  → Chunk 1: 句子1+2
  → Chunk 2: 句子3+4
```

#### Step 4: 最终的父子块结构

```
┌─────────────────────────────────────────────────────────────────┐
│ ParentBlock #1  (parentNo=1, childCount=2)                      │
│ sectionPath: "... > 第一章 项目概述 > 1.1 项目背景"               │
│ parentText: "1.1 项目背景\n\n随着企业业务规模扩大..."             │
├─────────────────────────────────────────────────────────────────┤
│  Chunk #1 (chunkNo=1)  parentBlockId=PB#1                       │
│  "1.1 项目背景\n\n随着企业业务规模扩大，传统人工客服面临人力成本高、│
│   响应速度慢、服务质量不稳定等挑战。"                              │
├─────────────────────────────────────────────────────────────────┤
│  Chunk #2 (chunkNo=2)  parentBlockId=PB#1                       │
│  "近年来大语言模型技术的突破为解决这些问题提供了新的可能性。"       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ ParentBlock #2  (parentNo=2, childCount=1)                      │
│ sectionPath: "... > 第一章 项目概述 > 1.2 核心目标"               │
├─────────────────────────────────────────────────────────────────┤
│  Chunk #3 (chunkNo=3)  parentBlockId=PB#2                       │
│  "1.2 核心目标\n\n本项目的核心目标包括以下几个方面：\n            │
│   - 实现常见问题的自动应答，覆盖率达到 80% 以上\n                 │
│   - 复杂问题无缝转接人工坐席\n                                   │
│   - 支持多渠道接入（网页、APP、微信、电话）\n                      │
│   - 提供知识库管理和持续学习能力"                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ ParentBlock #3  (parentNo=3, childCount=1)                      │
│ sectionPath: "... > 第二章 技术架构 > 2.1 接入网关"               │
├─────────────────────────────────────────────────────────────────┤
│  Chunk #4 (chunkNo=4)  parentBlockId=PB#3                       │
│  "2.1 接入网关\n\n接入网关负责统一接收来自不同渠道的用户请求..."   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ ParentBlock #4  (parentNo=4, childCount=1)                      │
│ sectionPath: "... > 2.2 对话引擎 > 2.2.1 意图识别"               │
├─────────────────────────────────────────────────────────────────┤
│  Chunk #5 (chunkNo=5)  parentBlockId=PB#4                       │
│  "2.2.1 意图识别\n\n意图识别模块基于微调后的 BERT 模型..."        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ ParentBlock #5  (parentNo=5, childCount=2)                      │
│ sectionPath: "... > 2.2 对话引擎 > 2.2.2 回复生成"               │
├─────────────────────────────────────────────────────────────────┤
│  Chunk #6 (chunkNo=6)  parentBlockId=PB#5                       │
│  "2.2.2 回复生成\n\n回复生成结合检索增强和模板生成两种方式。"     │
├─────────────────────────────────────────────────────────────────┤
│  Chunk #7 (chunkNo=7)  parentBlockId=PB#5                       │
│  "对于知识库覆盖的问题优先使用检索结果进行生成；                   │
│   对于流程性问题使用预定义模板确保合规性。"                        │
└─────────────────────────────────────────────────────────────────┘
```

#### Step 5: 向量化 + PGVector 入库

```
批量向量化 (batch=10):
  EmbeddingModel.embed([Chunk#1, Chunk#2, ..., Chunk#7])
  → 7 个 768 维向量
  → UPSERT 到 dochub_document_embedding

每条 embedding 记录携带:
  - parent_block_id (回溯父块)
  - section_path (面包屑导航)
  - canonical_path (层级路径)
  - chunk_text (原文)
  - metadata_json (完整元数据 JSON)
```

### 9.6 检索示例

```
用户查询: "BERT 模型支持多少种意图分类？"

1. 向量检索: 查询向量化 → PGVector 余弦相似度
   Top-3 命中:
   - Chunk #5: "2.2.1 意图识别..." score=0.94
   - Chunk #7: "对于知识库覆盖..." score=0.72
   - Chunk #4: "2.1 接入网关..." score=0.65

2. 父子回溯:
   Chunk #5 → parentBlockId=PB#4
   → 获取 ParentBlock #4 完整文本:
     "2.2.1 意图识别\n\n意图识别模块基于微调后的 BERT 模型，
      支持 50+ 种业务意图的分类。当置信度低于阈值时，
      系统会主动向用户确认意图。"
   → 同时获取相邻 Chunk #4 和 #6 作为上下文

3. LLM 回答:
   将 ParentBlock #4 文本 + 相邻上下文 + 用户问题 → LLM
   → "该系统支持 50 多种业务意图的分类。当置信度低于阈值时，
      系统会主动向用户确认意图。"
```

---

## 10. 关键设计决策总结

### 10.1 为什么需要父块和子块？

| 单一块的问题 | 父子块如何解决 |
|-------------|---------------|
| 块太小 → LLM 上下文不足，回答不完整 | 父块提供充足上下文（~2000 字符） |
| 块太大 → 向量检索精度下降，语义模糊 | 子块做精确检索（~500 字符） |
| 固定大小切块 → 破坏章节完整性 | STRUCTURE 策略尊重文档自然章节 |

### 10.2 父子块的关联层级

```
Layer 1: 文档结构节点 (DochubDocumentStructureNode)
         └─ 解析阶段产物，树形结构，parentNodeId 关联

Layer 2: 父块 → 子块 (DochubDocumentParentBlock → DochubDocumentChunk)
         └─ 切块阶段产物，parentBlockId 关联

Layer 3: PGVector metadata
         └─ 向量库冗余存储 parentBlockId + canonicalPath
```

### 10.3 策略选择原则

```
父块: 大而全 → STRUCTURE(保留章节) → RECURSIVE(长度兜底)
子块: 小而精 → LLM(低质量文本) / SEMANTIC(清晰文本) → RECURSIVE(兜底)
```

### 10.4 配置文件参考

```yaml
app:
  manage:
    chunk:
      recursive-max-chars: 800        # 子块递归分块最大字符数
      recursive-overlap-chars: 120     # 子块递归分块重叠字符数
      semantic-max-chars: 700          # 语义分块最大字符数
      semantic-min-chars: 240          # 语义分块最小字符数(低于此不分块)
      semantic-similarity-threshold: 0.18  # Jaccard 相似度阈值
      llm-enabled: false               # LLM 切块开关(默认关闭)
      llm-max-chars: 3500              # LLM 切块单次最大字符数
      recommend-llm-when-low-quality: false  # 低质量文本是否推荐LLM切块
```

### 10.5 数据表关系

```
dochub_document (文档主表)
  │
  ├── dochub_document_structure_node (结构节点, 树形)
  │     └── parentNodeId 自关联
  │
  ├── dochub_document_strategy_plan (策略方案)
  │     └── dochub_document_strategy_step (策略步骤)
  │
  ├── dochub_document_parent_block (父块)
  │     └── dochub_document_chunk (子块) ← parentBlockId 关联
  │           └── PGVector: dochub_document_embedding ← 向量化存储
  │
  ├── dochub_document_task (任务)
  │     └── dochub_document_task_log (任务日志)
  │
  └── dochub_document_profile (文档画像)
```

---

> **源码位置导航**：
> - 控制器: `dochub-agent-business-chat/.../controller/DocumentManageController.java`
> - 异步处理: `dochub-agent-business-chat/.../service/impl/DocumentAsyncProcessServiceImpl.java`
> - 策略服务: `dochub-agent-business-chat/.../service/impl/DocumentStrategyServiceImpl.java`
> - 解析服务: `dochub-agent-business-chat/.../service/impl/TikaDocumentParserService.java`
> - 结构提取: `dochub-agent-business-chat/.../support/DocumentStructureNodeExtractor.java`
> - 实体模型: `dochub-agent-business-chat/.../data/DochubDocument*.java`
> - 枚举定义: `dochub-agent-common-frame/.../enums/Document*.java`
