# Chat Model Provider Template Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Route local vLLM, local Ollama, remote OpenAI-compatible, and DashScope chat runtimes through provider templates whose activation probe exercises the same streaming path as production chat, while making an unconfigured embedding runtime a normal configuration-page state.

**Architecture:** `AbstractChatModelProvider` owns the invariant validate/build/probe workflow. `ChatModelProviderRouter` selects a provider using persisted deployment type and compatibility preset; local providers use a vLLM/Ollama-compatible Reactor Netty streaming transport, while remote providers retain the standard transport. Runtime publication remains atomic and global.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring AI 1.1.0, Spring WebFlux, Reactor Netty, JUnit 5, Mockito, Vue 3, Vitest.

## Global Constraints

- `LOCAL + OPENAI_COMPATIBLE` selects vLLM; `LOCAL + OLLAMA` selects Ollama.
- `REMOTE + OPENAI_COMPATIBLE` selects remote OpenAI-compatible; `REMOTE + DASHSCOPE` selects DashScope.
- Local providers clear credentials; remote providers require an API key protected by the existing cipher.
- A candidate is saved and activated only after its production streaming path succeeds.
- Failure preserves the previous database row and in-memory runtime.
- The active chat runtime remains global for all users.
- Missing embedding configuration returns `configured=false`, not a generic system error.

---

### Task 1: Explicit Deployment Type and Provider Router

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/model/DeploymentType.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/provider/ChatModelProvider.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/provider/ChatModelProviderRouter.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/model/ModelRuntimeSpec.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/provider/ChatModelProviderRouterTest.java`
- Modify constructor sites returned by `rg -l "new ModelRuntimeSpec\\(" dochub-agent-business/dochub-agent-business-dochub/src`

**Interfaces:**
- Produces: `DeploymentType { LOCAL, REMOTE }`.
- Produces: `ChatModelProvider.supports(DeploymentType, CompatibilityPreset)`, `create(ModelRuntimeSpec)`, and `probe(ChatModel, boolean)`.
- Produces: `ChatModelProviderRouter.requireProvider(ModelRuntimeSpec)`.

- [ ] **Step 1: Write failing routing tests**

```java
@Test
void routesSupportedPairs() {
    assertThat(router.requireProvider(spec(LOCAL, OPENAI_COMPATIBLE))).isSameAs(vllm);
    assertThat(router.requireProvider(spec(LOCAL, OLLAMA))).isSameAs(ollama);
    assertThat(router.requireProvider(spec(REMOTE, OPENAI_COMPATIBLE))).isSameAs(remoteOpenAi);
    assertThat(router.requireProvider(spec(REMOTE, DASHSCOPE))).isSameAs(dashScope);
}

@Test
void rejectsLocalDashScope() {
    assertThatThrownBy(() -> router.requireProvider(spec(LOCAL, DASHSCOPE)))
        .hasMessageContaining("不支持的模型部署与兼容预设组合");
}
```

- [ ] **Step 2: Run tests and verify RED**

```powershell
mvn -pl dochub-agent-business/dochub-agent-business-dochub -am '-Dtest=ChatModelProviderRouterTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Expected: compilation fails because provider routing and deployment types do not exist.

- [ ] **Step 3: Implement the routing API and explicit deployment field**

```java
public interface ChatModelProvider {
    boolean supports(DeploymentType deploymentType, CompatibilityPreset preset);
    ChatModel create(ModelRuntimeSpec spec);
    void probe(ChatModel model, boolean toolCallingSupported);
}

public ChatModelProvider requireProvider(ModelRuntimeSpec spec) {
    return providers.stream()
        .filter(provider -> provider.supports(spec.deploymentType(), spec.compatibilityPreset()))
        .findFirst()
        .orElseThrow(() -> new DochubFrameException(400, "不支持的模型部署与兼容预设组合"));
}
```

Add `DeploymentType deploymentType` after `ModelType modelType` in `ModelRuntimeSpec`, require it to be non-null, and update all constructor sites using their existing deployment context. Static fallbacks use `REMOTE`; local tests use `LOCAL`.

- [ ] **Step 4: Run router and runtime-spec tests and verify GREEN**

Expected: all selected tests pass.

- [ ] **Step 5: Commit**

```powershell
git add dochub-agent-business/dochub-agent-business-dochub/src
git commit -m "refactor: add explicit chat provider routing"
```

---

### Task 2: Template Workflow and Four Providers

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/provider/AbstractChatModelProvider.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/provider/VllmChatModelProvider.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/provider/OllamaChatModelProvider.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/provider/RemoteOpenAiChatModelProvider.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/provider/DashScopeChatModelProvider.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/OpenAiCompatibleModelFactory.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/config/DynamicModelConfiguration.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/provider/ChatModelProviderTemplateTest.java`

**Interfaces:**
- Consumes Task 1 routing types.
- Produces final template methods `create` and `probe`; subclasses implement `validateProviderSpec`, `extraBody`, and `httpClientBuilders`.

- [ ] **Step 1: Write failing provider tests**

```java
@Test
void vllmQwenUsesTemplateThinkingSwitch() {
    assertThat(vllm.options(localQwenSpec()).getExtraBody())
        .containsEntry("chat_template_kwargs", Map.of("enable_thinking", false));
}

@Test
void remoteRejectsBlankApiKey() {
    assertThatThrownBy(() -> remote.create(remoteSpecWithKey("")))
        .hasMessageContaining("远程模型必须提供 API Key");
}

@Test
void localNormalizationClearsApiKey() {
    assertThat(vllm.normalize(localSpecWithKey("secret")).apiKey()).isEmpty();
}
```

- [ ] **Step 2: Run tests and verify RED**

Expected: concrete providers and template hooks are missing.

- [ ] **Step 3: Implement the final template and subclasses**

```java
public abstract class AbstractChatModelProvider implements ChatModelProvider {
    @Override
    public final ChatModel create(ModelRuntimeSpec source) {
        ModelRuntimeSpec spec = normalize(source);
        validateCommon(spec);
        validateProviderSpec(spec);
        return modelFactory.chatModel(spec, options(spec), httpClientBuilders(spec));
    }

    protected abstract void validateProviderSpec(ModelRuntimeSpec spec);
    protected abstract Map<String, Object> extraBody(ModelRuntimeSpec spec);
    protected abstract OpenAiHttpClientBuilderFactory httpClientBuilders(ModelRuntimeSpec spec);
}
```

Provider options are exact: vLLM Qwen uses `chat_template_kwargs={enable_thinking:false}`, Ollama uses `think=false`, DashScope uses `enable_thinking=false`, and generic remote OpenAI sends no private reasoning fields.

- [ ] **Step 4: Register providers and verify GREEN**

Register four providers and one router in `DynamicModelConfiguration`; run provider, factory, policy, and registry tests. Expected: pass.

- [ ] **Step 5: Commit**

```powershell
git add dochub-agent-business/dochub-agent-business-dochub/src
git commit -m "refactor: implement chat provider template"
```

---

### Task 3: vLLM Streaming Transport and Activation Probe

**Files:**
- Modify: `dochub-agent-business/dochub-agent-business-dochub/pom.xml`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/OpenAiHttpClientBuilderFactory.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/provider/AbstractChatModelProvider.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/impl/ModelConfigServiceImpl.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/provider/VllmChatModelProviderIntegrationTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/service/ModelConfigServiceImplTest.java`

**Interfaces:**
- Produces: `OpenAiHttpClientBuilderFactory.vllm(Duration)` backed by `ReactorClientHttpConnector`.
- Produces: a bounded streaming activation probe that consumes the same path as production chat.

- [ ] **Step 1: Write a failing body-capture integration test**

Use JDK `HttpServer` on port `0`, record request bytes, and return valid OpenAI SSE:

```java
exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
byte[] bytes = ("data: {\"id\":\"probe\",\"object\":\"chat.completion.chunk\","
    + "\"created\":1,\"model\":\"Qwen3.8-27B\",\"choices\":[{\"index\":0,"
    + "\"delta\":{\"role\":\"assistant\",\"content\":\"OK\"},\"finish_reason\":null}]}\n\n"
    + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8);
```

Assert captured JSON is non-empty and contains `"model":"Qwen3.8-27B"`, `"messages"`, and `"stream":true`.

- [ ] **Step 2: Run the integration test and verify RED**

Expected: current transport reproduces the empty/unparseable request body or lacks vLLM transport selection.

- [ ] **Step 3: Add Reactor Netty and implement local streaming transport**

Add `io.projectreactor.netty:reactor-netty-http` under Spring Boot dependency management and build:

```java
HttpClient client = HttpClient.create()
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(timeout.toMillis()))
    .responseTimeout(timeout);
return WebClient.builder().clientConnector(new ReactorClientHttpConnector(client));
```

- [ ] **Step 4: Make test/save execute the streaming probe**

Call `model.stream(new Prompt("只回复 OK"))`, enforce the candidate timeout, and require at least one non-null response. If tool calling is enabled, run the existing tool capability probe after the stream probe. `ModelConfigServiceImpl` must select the provider, call `create`, then call `probe` for both test and save.

- [ ] **Step 5: Run integration and service tests and verify GREEN**

Expected: fixture receives a non-empty body and a failed stream prevents insert and activation.

- [ ] **Step 6: Run live vLLM smoke test**

Probe `http://192.168.10.228:8000/v1/chat/completions`, model `Qwen3.8-27B`, without API key, bounded to 30 seconds. Expected: assistant content arrives and completes without HTTP 400.

- [ ] **Step 7: Commit**

```powershell
git add dochub-agent-business/dochub-agent-business-dochub
git commit -m "fix: use vllm compatible streaming transport"
```

---

### Task 4: Provider-Aware Reload

**Files:**
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/support/ModelConfigRuntimeReloader.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/support/ModelRuntimeFallbackInitializer.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/DynamicChatModel.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/support/ModelConfigRuntimeReloaderTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/runtime/DynamicChatModelTest.java`

**Interfaces:**
- Consumes provider router.
- Produces deterministic provider reconstruction after restart and preservation of provider-owned prompt options.

- [ ] **Step 1: Write failing reload tests**

```java
@Test
void reloadsLocalOpenAiConfigThroughVllmProvider() {
    when(mapper.selectOne(any())).thenReturn(active("LOCAL", "OPENAI_COMPATIBLE"));
    reloader.reloadIfNewer();
    verify(vllm).create(argThat(spec -> spec.deploymentType() == LOCAL));
}
```

Add a test proving `chat_template_kwargs` survives `DynamicChatModel` sanitization for local vLLM.

- [ ] **Step 2: Run tests and verify RED**

Expected: reloader still constructs chat models directly or deployment is lost.

- [ ] **Step 3: Route reloader and static chat fallback through the router**

Construct specs from persisted `deployment_type` and `compatibility_preset`; publish only a complete provider-built model/spec snapshot. Keep startup available when no chat model exists.

- [ ] **Step 4: Run tests and verify GREEN**

Run reloader, fallback, registry, and dynamic-model tests. Expected: pass.

- [ ] **Step 5: Commit**

```powershell
git add dochub-agent-business/dochub-agent-business-dochub/src
git commit -m "fix: reload chat runtime through provider router"
```

---

### Task 5: Absence-Safe Embedding Configuration Page

**Files:**
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/ModelRuntimeRegistry.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/impl/EmbeddingModelChangeServiceImpl.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/vo/EmbeddingConfigVo.java`
- Modify: `dochub-web/src/views/admin/AdminModelConfigView.vue`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/service/EmbeddingModelChangeServiceTest.java`
- Test: `dochub-web/src/views/admin/AdminModelConfigView.test.js`

**Interfaces:**
- Produces: `ModelRuntimeRegistry.findEmbedding(): Optional<EmbeddingRuntimeSnapshot>`.
- Produces: `EmbeddingConfigVo.configured()`.

- [ ] **Step 1: Write failing backend absence test**

```java
@Test
void queryReturnsUnconfiguredStateWithoutEmbeddingRuntime() {
    EmbeddingConfigVo result = service.query("admin");
    assertThat(result.configured()).isFalse();
    assertThat(result.modelName()).isNull();
}
```

- [ ] **Step 2: Run test and verify RED**

Expected: `No active embedding model runtime snapshot` is thrown.

- [ ] **Step 3: Implement optional lookup and empty successful response**

Add `findEmbedding()` and retain `requireEmbedding()` for operations needing a model. Query returns `configured=false`, nullable provider fields, no credential flag, zero dimension, configured Qdrant collection names, and latest migration metadata when absent.

- [ ] **Step 4: Write failing frontend isolation test**

Mock chat query success and embedding `configured=false`. Assert chat fields remain populated, an unconfigured embedding hint appears, and no “系统错误，请稍后重试” banner is shown.

- [ ] **Step 5: Implement independent load states and verify GREEN**

Do not let embedding load state overwrite chat notices. Display “尚未配置向量模型，当前向量功能不可用” for the empty state.

- [ ] **Step 6: Commit**

```powershell
git add dochub-agent-business/dochub-agent-business-dochub/src dochub-web/src
git commit -m "fix: handle missing embedding model configuration"
```

---

### Task 6: Full Verification

**Files:**
- Verify all changed files; modify only when a failing regression identifies a defect within this feature.

**Interfaces:**
- Consumes Tasks 1–5.
- Produces a verified runnable backend and frontend.

- [ ] **Step 1: Run backend suite**

```powershell
mvn -pl dochub-agent-business/dochub-agent-business-dochub -am test
```

Expected: reactor succeeds with zero failures and errors.

- [ ] **Step 2: Run frontend suite and build**

```powershell
npm --prefix dochub-web test -- --run
npm --prefix dochub-web run build
```

Expected: Vitest and Vite build succeed.

- [ ] **Step 3: Package and start backend**

```powershell
mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -DskipTests package
```

Start the packaged JAR after checking port 9086 ownership. Expected: `/actuator/health` is `UP` without `Application run failed`.

- [ ] **Step 4: Verify original failures end to end**

Open `/admin/model-config` as an existing administrator and verify the empty embedding state has no generic error. Test/save local vLLM, send an open-chat question, and verify assistant SSE content arrives without HTTP 400.

- [ ] **Step 5: Confirm repository state**

```powershell
git status --short
git log --oneline -8
```

Expected: no untracked diagnostics and no uncommitted implementation changes.
