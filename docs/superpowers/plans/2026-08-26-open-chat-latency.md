# Open Chat Latency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make ordinary open questions start streaming after one bounded model request while preserving ReAct and Plan-and-Execute only for questions that truly need tools or multi-step execution.

**Architecture:** Add a measured `DIRECT_CHAT` execution path and route rule-first. Simple open chat skips skill LLM routing, planning, ReAct, and tools; it sends one prompt built from a bounded summary and recent turns to the dynamic chat model. Search/time-sensitive questions may enter ReAct, explicit complex mode may enter Plan-and-Execute, and agent checkpoints use per-turn child thread IDs with bounded seed history so the conversation graph no longer grows without limit.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring AI 1.1.0, Alibaba Graph/ReactAgent, Project Reactor, MyBatis-Plus, MySQL 8, JUnit 5, Mockito.

## Global Constraints

- Reasoning/thinking is disabled centrally by the active compatibility preset from the runtime model configuration plan.
- An ordinary `OPEN_CHAT` request must not invoke the skill-routing LLM, query rewriting, ReAct, Tavily, or planner.
- Tool execution requires an explicit rule/intent result; the lack of a knowledge route is not sufficient reason to invoke ReAct.
- Direct chat performs exactly one provider request and streams model text as it arrives.
- Prompt context has hard character/token and turn-count limits and includes summary plus the most recent exchanges.
- ReAct/Plan retain tool capability, but each user turn has an isolated graph thread seeded with bounded business history.
- Every branch records configuration version, prompt size, model-call count, tool-call count, time to first token, and total duration.
- A failed optimization must not change the existing SSE event contract.

---

### Task 1: Make chat latency and call amplification measurable

**Files:**
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/data/DochubChatStageBenchmark.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/model/debug/ChatModelUsageTrace.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/ConversationTraceRecorder.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/ObservedChatModelService.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/BusinessChatService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/model/debug/ChatLatencyTrace.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/support/ChatLatencyTracker.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/support/ChatLatencyTrackerTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/service/ObservedChatModelServiceTest.java`

**Interfaces:**
- Produces: stage timings `REQUEST_ACCEPTED`, `PREPARATION`, `SKILL_ROUTE`, `MODEL_REQUEST`, `FIRST_TOKEN`, `TOOL`, `COMPLETE`.
- Reads: active chat model `configVersion` from `ModelRuntimeRegistry`.

- [ ] **Step 1: Write failing first-token and model-call-count tests**

```java
@Test void firstTokenIsRecordedOnce() {
    ChatLatencyTracker tracker = new ChatLatencyTracker(clockAt(1_000));
    tracker.accepted();
    advanceClockTo(1_240);
    tracker.onTextChunk("你");
    advanceClockTo(1_500);
    tracker.onTextChunk("好");
    assertThat(tracker.snapshot().timeToFirstTokenMs()).isEqualTo(240);
}

@Test void observedStreamingIncrementsOneModelCall() {
    service.streamText("direct_chat", "system", "question", recorder).collectList().block();
    assertThat(recorder.snapshot().modelCallCount()).isOne();
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=ChatLatencyTrackerTest,ObservedChatModelServiceTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the latency trace and counters do not exist.

- [ ] **Step 3: Implement trace capture without changing response flow**

```java
public void onTextChunk(String chunk) {
    if (StrUtil.isNotBlank(chunk)) {
        firstTokenAt.compareAndSet(0L, clock.millis());
    }
}

public ChatLatencyTrace snapshot() {
    long first = firstTokenAt.get();
    return new ChatLatencyTrace(
        first == 0 ? null : first - acceptedAt,
        completedAt == 0 ? null : completedAt - acceptedAt,
        modelCalls.get(), toolCalls.get(), promptCharacters.get(), modelConfigVersion.get());
}
```

Attach tracking using Reactor `doOnNext`, `doOnError`, and `doFinally`; never block the stream to measure it. Persist the trace alongside the existing exchange/stage benchmark. Log identifiers and counts only, never prompts, API keys, or model credentials.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: timing, empty-chunk, failure, cancellation, and single-count tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent
git commit -m "feat: measure open chat time to first token"
```

### Task 2: Add the one-call direct chat executor and bounded context

**Files:**
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/model/ExecutionMode.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/executor/DirectChatExecutor.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/service/DirectChatContextService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/model/DirectChatContext.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/executor/ConversationExecutorRegistry.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/config/ChatAgentProperties.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/resources/application.yaml`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/rag/service/DirectChatContextServiceTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/rag/executor/DirectChatExecutorTest.java`

**Interfaces:**
- Produces: `ExecutionMode.DIRECT_CHAT` and a matching `ConversationExecutor`.
- Consumes: conversation summary plus at most `directChatRecentTurns` completed exchanges.

- [ ] **Step 1: Write failing one-call and bounded-context tests**

```java
@Test void directChatStreamsWithOneModelCallAndNoTools() {
    StepVerifier.create(executor.execute(taskInfo(openQuestion())))
        .expectNext("你", "好")
        .verifyComplete();
    verify(observedChatModel).streamText(eq("direct_chat"), anyString(), anyString(), any());
    verifyNoInteractions(reactAgent, tavilyService);
}

@Test void contextKeepsSummaryAndOnlyNewestTurnsWithinBudget() {
    DirectChatContext context = contextService.build(CONVERSATION_ID, 3, 8_000);
    assertThat(context.recentExchanges()).hasSize(3);
    assertThat(context.promptCharacters()).isLessThanOrEqualTo(8_000);
    assertThat(context.summary()).isNotBlank();
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=DirectChatContextServiceTest,DirectChatExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because direct chat types do not exist.

- [ ] **Step 3: Implement direct streaming and deterministic context trimming**

```java
@Override
public Flux<String> execute(TaskInfo taskInfo) {
    DirectChatContext context = contextService.build(
        taskInfo.conversationId(), properties.getDirectChatRecentTurns(), properties.getDirectChatMaxCharacters());
    String userPrompt = promptRenderer.render(context, taskInfo.executionPlan().getOriginalQuestion());
    return observedChatModel.streamText(
        "direct_chat", DIRECT_SYSTEM_PROMPT, userPrompt, taskInfo.traceRecorder());
}
```

Trim oldest turns first, retain the current question, and truncate the persisted summary only after recent turns have reached the configured minimum. Initial defaults: 6 recent turns and 12,000 prompt characters. Do not summarize synchronously on this request; use the already-persisted summary and let the existing asynchronous summary lifecycle update later.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: one-call, stream, cancellation, missing-summary, and context-budget tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent dochub-agent-business/dochub-agent-business-dochub/src/main/resources/application.yaml dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent
git commit -m "feat: add direct open chat streaming path"
```

### Task 3: Route ordinary open chat directly and reserve agents for explicit intent

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/service/OpenChatExecutionRouter.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/model/OpenChatRouteDecision.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/service/ChatPreparationOrchestrator.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/BusinessChatService.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/dto/ChatRequestDto.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/rag/service/OpenChatExecutionRouterTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/rag/service/ChatPreparationOrchestratorOpenChatTest.java`

**Interfaces:**
- Produces: deterministic route result `DIRECT_CHAT`, `REACT_AGENT`, or `PLAN_AND_EXECUTE` with reason code.
- Accepts: explicit client mode when supplied; defaults to automatic direct routing.

- [ ] **Step 1: Write failing routing contract tests**

```java
@ParameterizedTest
@ValueSource(strings = {"你好", "解释一下依赖注入", "帮我润色这段话", "Java 的 record 是什么"})
void ordinaryQuestionsUseDirectChat(String question) {
    assertThat(router.route(question, null).mode()).isEqualTo(ExecutionMode.DIRECT_CHAT);
}

@ParameterizedTest
@ValueSource(strings = {"搜索今天的 AI 新闻", "查一下当前上海天气", "联网看看最新版本"})
void currentOrSearchQuestionsUseReact(String question) {
    assertThat(router.route(question, null).mode()).isEqualTo(ExecutionMode.REACT_AGENT);
}

@Test void planModeMustBeExplicit() {
    assertThat(router.route("设计一个迁移方案", "PLAN_AND_EXECUTE").mode())
        .isEqualTo(ExecutionMode.PLAN_AND_EXECUTE);
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=OpenChatExecutionRouterTest,ChatPreparationOrchestratorOpenChatTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because ordinary open chat still defaults to `REACT_AGENT`.

- [ ] **Step 3: Implement fast-path routing before expensive preparation**

```java
if (chatMode == ChatQueryMode.OPEN_CHAT) {
    OpenChatRouteDecision route = openChatExecutionRouter.route(
        taskInfo.question(), requestedOpenChatMode(taskInfo));
    return ConversationExecutionPlan.builder()
        .originalQuestion(taskInfo.question())
        .agentQuestion(taskInfo.question())
        .executionMode(route.mode())
        .routeReason(route.reasonCode())
        .build();
}
```

Place this branch before query rewriting, knowledge routing, document listing, and navigation routing. In `BusinessChatService`, honor an explicitly forced skill first; otherwise skip `SkillSceneRouter` entirely when the execution plan is `DIRECT_CHAT`. `REACT_AGENT` rules must require current/time-sensitive terms, explicit search/browse intent, or explicit tool intent. Ambiguous questions default to direct chat; users can request web search explicitly. Preserve the current explicit Plan-and-Execute option.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: direct, search, explicit plan, empty question, and false-positive cases pass, and mocks verify expensive preparation is untouched on direct requests.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent
git commit -m "perf: route ordinary open chat directly"
```

### Task 4: Make skill routing rule-first and LLM-on-ambiguity only

**Files:**
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/skill/router/SkillSceneRouter.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/skill/config/SkillProperties.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/skill/router/SkillSceneRouterTest.java`

**Interfaces:**
- Produces: rule match immediately when above `strongRuleThreshold`.
- Invokes: LLM only when at least two candidates fall inside the ambiguity band.

- [ ] **Step 1: Write failing no-extra-call tests**

```java
@Test void noCandidateReturnsNoMatchWithoutCallingModel() {
    assertThat(router.route("你好").matched()).isFalse();
    verifyNoInteractions(chatModel);
}

@Test void strongRuleMatchSkipsLlm() {
    assertThat(router.route("生成项目周报").skillCode()).isEqualTo("weekly-report");
    verifyNoInteractions(chatModel);
}

@Test void closeCandidatesMayUseLlmOnce() {
    router.route("比较这两个方案并给出报告");
    verify(chatModel, times(1)).call(any(Prompt.class));
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=SkillSceneRouterTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the current implementation calls the LLM before rule scoring.

- [ ] **Step 3: Reorder routing and define the ambiguity band**

```java
List<ScoredSkill> candidates = scoreByRule(question);
if (candidates.isEmpty() || candidates.getFirst().score() < properties.getMinimumRuleScore()) {
    return SkillMatchResult.noMatch();
}
if (isStrongAndSeparated(candidates)) {
    return candidates.getFirst().toMatch("RULE");
}
return llmEnabled && isAmbiguous(candidates)
    ? routeWithLlm(question, candidates.subList(0, Math.min(3, candidates.size())))
    : candidates.getFirst().toMatch("RULE_FALLBACK");
```

Initial defaults: strong rule score `0.82`, minimum score `0.45`, ambiguity gap `0.10`. LLM receives no more than three candidates and cannot select a code outside that set.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: zero-candidate, strong-match, ambiguity, malformed response, unknown skill code, and disabled-LLM tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/skill dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/skill
git commit -m "perf: avoid unnecessary skill routing model calls"
```

### Task 5: Bound ReAct checkpoint history with per-turn graph threads

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/service/AgentTurnContextFactory.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/model/AgentTurnContext.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/executor/ReactAgentExecutor.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/rag/executor/PlanAndExecuteExecutor.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/ChatCheckpointManager.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent/service/BusinessChatService.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/rag/service/AgentTurnContextFactoryTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/rag/executor/ReactAgentExecutorTest.java`

**Interfaces:**
- Produces: graph thread ID `${conversationId}:exchange:${exchangeId}`.
- Seeds: summary and at most four recent completed business turns, never raw accumulated graph messages/tool results.

- [ ] **Step 1: Write failing isolation and bounded-seed tests**

```java
@Test void everyExchangeUsesAnIndependentGraphThread() {
    assertThat(factory.create("conv-1", 11L).threadId()).isEqualTo("conv-1:exchange:11");
    assertThat(factory.create("conv-1", 12L).threadId()).isEqualTo("conv-1:exchange:12");
}

@Test void repeatedReactTurnsDoNotGrowSeedMessages() {
    IntStream.range(0, 20).forEach(index -> executor.execute(taskInfo(index)).collectList().block());
    assertThat(agentInputs).allSatisfy(input -> assertThat(input.seedMessages()).hasSizeLessThanOrEqualTo(9));
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=AgentTurnContextFactoryTest,ReactAgentExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because the conversation ID is currently reused as the graph thread.

- [ ] **Step 3: Implement child-thread lifecycle and cleanup**

Create a new `RunnableConfig` for each exchange while retaining the business conversation ID in context metadata. Seed only system instructions, summary, bounded user/assistant pairs, and the current question. Do not seed previous tool results. Update checkpoint cleanup to delete all `${conversationId}:exchange:%` graph threads when the conversation is cleared, and add retention cleanup for old completed child threads.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run the command from Step 2.

Expected: isolation, bounded history, tool result exclusion, cleanup, cancel, and Plan step child-thread tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/chatagent dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent
git commit -m "perf: bound agent checkpoint history per turn"
```

### Task 6: Verify thinking is off and enforce latency regression budgets

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/chatagent/integration/OpenChatFastPathTest.java`
- Create: `scripts/verify-open-chat-latency.ps1`
- Modify: `README.md`

**Interfaces:**
- Verifies: one model request, zero tool calls, bounded input, and first SSE text event for ordinary open chat.
- Reports: p50/p95 TTFT separately for `DIRECT_CHAT`, `REACT_AGENT`, and `PLAN_AND_EXECUTE`.

- [ ] **Step 1: Add the fast-path integration contract**

```java
@Test void simpleOpenQuestionUsesOneNonThinkingStreamingRequest() {
    client.openChat("Java 的 record 是什么").expectSseText();
    assertThat(provider.requests()).singleElement().satisfies(request -> {
        assertThat(request.tools()).isEmpty();
        assertThat(request.extraBody()).containsEntry("enable_thinking", false);
    });
    assertThat(traceRepository.latest().getModelCallCount()).isOne();
    assertThat(traceRepository.latest().getToolCallCount()).isZero();
}
```

For the Ollama preset assert `think=false`; for standard OpenAI-compatible providers assert only the supported no-reasoning field selected by the candidate connection test. Never assert that an unknown provider accepts a private DashScope/Ollama field.

- [ ] **Step 2: Run backend tests**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=OpenChatFastPathTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: simple open chat has `modelCallCount=1`, `toolCallCount=0`, `executionMode=DIRECT_CHAT`, and a non-null TTFT.

- [ ] **Step 3: Run a local 20-request latency sample**

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-open-chat-latency.ps1 -BaseUrl http://127.0.0.1:8090 -Samples 20`

Expected: script exits 0; direct-chat p95 TTFT is below the configurable 5-second local acceptance ceiling and no direct request invokes Tavily. Record provider/network conditions with the result rather than weakening the assertion silently.

- [ ] **Step 4: Run the complete module suite**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am test`

Expected: all tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/test scripts/verify-open-chat-latency.ps1 README.md
git commit -m "test: guard open chat fast path latency"
```
