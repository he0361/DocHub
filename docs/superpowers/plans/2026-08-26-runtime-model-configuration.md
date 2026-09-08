# Runtime Model Configuration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build administrator-only, encrypted, database-backed OpenAI-compatible chat and embedding configuration with tested atomic runtime delegation.

**Architecture:** A model configuration store persists versioned settings while API keys are encrypted with AES-GCM. Primary `DynamicChatModel` and `DynamicEmbeddingModel` beans delegate each new call to immutable runtime snapshots, so existing consumers do not need to be rebuilt. Candidate configurations are tested before activation; database version polling and Redis notification keep multiple application instances aligned.

**Tech Stack:** Java 17, Spring Boot 3.5.6, Spring AI 1.1.0, MyBatis-Plus, MySQL 8, Redis, Vue 3, Vite 6, JUnit 5, Mockito.

## Global Constraints

- Only accounts with `is_admin=1` may query, test, save, or activate model configuration.
- Remote and local models use OpenAI-compatible HTTP APIs.
- API keys are never returned in plaintext and are never logged.
- A failed test, save, decrypt, or reload must leave the last valid runtime snapshot active.
- Local API keys may be empty; remote API keys are required.
- Chat reasoning is disabled by the compatibility preset where the provider exposes a supported switch, and per-stage options are sanitized so they cannot re-enable it.
- Database configuration is the runtime source of truth after the first saved version.
- Existing in-flight calls finish on their captured model; new calls use the newly activated snapshot.

---

### Task 1: Persist and encrypt model configuration

**Files:**
- Create: `sql/dochub/Mysql/20260826_add_ai_model_config.sql`
- Modify: `sql/dochub/Mysql/create_table_dochub.sql`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/data/DochubAiModelConfig.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/data/DochubAiModelConfigAudit.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/mapper/DochubAiModelConfigMapper.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/mapper/DochubAiModelConfigAuditMapper.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/config/ModelConfigProperties.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/security/ModelCredentialCipher.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/security/ModelCredentialCipherTest.java`

**Interfaces:**
- Produces: `ModelCredentialCipher.encrypt(String): String` and `decrypt(String): String`.
- Produces: one active row per `model_type` (`CHAT`, `EMBEDDING`) plus immutable audit rows.

- [ ] **Step 1: Write the failing AES-GCM tests**

```java
class ModelCredentialCipherTest {
    private final ModelCredentialCipher cipher = new ModelCredentialCipher(
        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test void roundTripsWithoutDeterministicCiphertext() {
        String first = cipher.encrypt("sk-secret");
        String second = cipher.encrypt("sk-secret");
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("sk-secret");
    }

    @Test void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("sk-secret");
        assertThatThrownBy(() -> cipher.decrypt(encrypted + "x"))
            .isInstanceOf(IllegalStateException.class);
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=ModelCredentialCipherTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because `ModelCredentialCipher` does not exist.

- [ ] **Step 3: Add schema and minimal encryption implementation**

Use `dochub_ai_model_config` columns `id`, `model_type`, `deployment_type`, `compatibility_preset`, `base_url`, `request_path`, `model_name`, `encrypted_api_key`, `temperature`, `max_tokens`, `timeout_millis`, `tool_calling_supported`, `options_json`, `config_version`, `active`, generated nullable `active_model_type`, `updated_by`, `create_time`, `edit_time`, `status`, with unique keys `(model_type, config_version)` and `(active_model_type)`; the generated value is `model_type` only when `active=1`, otherwise `NULL`, so many historical inactive versions but only one active version per type are legal. Store audit action, success, masked endpoint, operator and error in `dochub_ai_model_config_audit`.

```java
public final class ModelCredentialCipher {
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private final SecretKey key;

    public ModelCredentialCipher(String base64Key) {
        byte[] decoded = Base64.getDecoder().decode(base64Key);
        if (decoded.length != 32) throw new IllegalArgumentException("模型配置加密密钥必须为 32 字节");
        this.key = new SecretKeySpec(decoded, "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) return "";
        try {
            byte[] iv = new byte[12];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return "v1:" + Base64.getEncoder().encodeToString(iv) + ":"
                + Base64.getEncoder().encodeToString(cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("模型密钥加密失败", exception);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            String[] parts = value.split(":", 3);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, Base64.getDecoder().decode(parts[1])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[2])), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("模型密钥解密失败", exception);
        }
    }
}
```

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the command from Step 2.

Expected: `Tests run: 2, Failures: 0, Errors: 0`.

- [ ] **Step 5: Commit**

```powershell
git add -- sql/dochub/Mysql/20260826_add_ai_model_config.sql sql/dochub/Mysql/create_table_dochub.sql dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/security/ModelCredentialCipherTest.java
git commit -m "feat: persist encrypted model configuration"
```

### Task 2: Build OpenAI-compatible candidates and dynamic delegates

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/model/ModelType.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/model/CompatibilityPreset.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/model/ModelRuntimeSpec.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/ModelRuntimeSnapshot.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/ModelRuntimeRegistry.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/DynamicChatModel.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/DynamicEmbeddingModel.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/runtime/OpenAiCompatibleModelFactory.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/config/DynamicModelConfiguration.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/runtime/ModelRuntimeRegistryTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/runtime/OpenAiCompatibleModelFactoryTest.java`

**Interfaces:**
- Consumes: decrypted `ModelRuntimeSpec`.
- Produces: `ModelRuntimeRegistry.activateChat(long, ChatModel, ModelRuntimeSpec)` and `activateEmbedding(long, EmbeddingModel, ModelRuntimeSpec)`.
- Produces: primary Spring beans named `dynamicChatModel` and `dynamicEmbeddingModel`.

- [ ] **Step 1: Write failing atomic delegation and option tests**

```java
@Test void newCallsUseActivatedChatSnapshot() {
    registry.activateChat(1L, chatModelReturning("old"), spec("DASHSCOPE"));
    assertThat(new DynamicChatModel(registry).call("ping")).isEqualTo("old");
    registry.activateChat(2L, chatModelReturning("new"), spec("DASHSCOPE"));
    assertThat(new DynamicChatModel(registry).call("ping")).isEqualTo("new");
}

@Test void dashscopeDisablesThinking() {
    OpenAiChatOptions options = factory.chatOptions(spec("DASHSCOPE"));
    assertThat(options.getExtraBody()).containsEntry("enable_thinking", false);
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=ModelRuntimeRegistryTest,OpenAiCompatibleModelFactoryTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because runtime classes do not exist.

- [ ] **Step 3: Implement immutable snapshots and factory**

```java
public record ModelRuntimeSnapshot<T>(long version, T model, ModelRuntimeSpec spec) {}

public final class DynamicChatModel implements ChatModel {
    private final ModelRuntimeRegistry registry;
    @Override public ChatResponse call(Prompt prompt) { return registry.requireChat().model().call(prompt); }
    @Override public Flux<ChatResponse> stream(Prompt prompt) { return registry.requireChat().model().stream(prompt); }
    @Override public ChatOptions getDefaultOptions() { return registry.requireChat().model().getDefaultOptions(); }
}
```

Build `OpenAiApi` with the configured `baseUrl`, `apiKey`, `completionsPath`, and `embeddingsPath`. Build chat options with model, temperature, maxTokens, `parallelToolCalls(false)`, and preset extras: DashScope `enable_thinking=false`, Ollama `think=false`, and no provider-private reasoning field for the generic OpenAI-compatible preset. Sanitize call-level options in the dynamic delegate so they cannot override the active preset's disabled-thinking field. Never send one preset's private fields to another preset.

- [ ] **Step 4: Run tests and verify GREEN**

Run the command from Step 2.

Expected: all runtime and factory tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig
git commit -m "feat: add dynamic model runtime"
```

### Task 3: Add administrator-only test, save, and reload APIs

**Files:**
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/dto/ModelConfigSaveDto.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/dto/ModelConfigTestDto.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/vo/ModelConfigVo.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/vo/ModelConnectionTestVo.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/ModelConfigService.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/service/impl/ModelConfigServiceImpl.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/controller/AdminModelConfigController.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/support/AdminGuard.java`
- Create: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig/support/ChatModelPolicyValidator.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/auth/config/AdminWebMvcConfiguration.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/auth/service/AdminAuthService.java`
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/auth/service/impl/AdminAuthServiceImpl.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/service/ModelConfigServiceImplTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/controller/AdminModelConfigControllerTest.java`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/support/ChatModelPolicyValidatorTest.java`

**Interfaces:**
- Produces: `POST /admin/model-config/query`, `/chat/test`, `/chat/save`.
- Produces: `AdminAuthService.verifyCurrentPassword(String username, String password)`.
- Save semantics: blank API key retains the encrypted current key; `clearApiKey=true` clears it only for local deployment.

- [ ] **Step 1: Write failing service and authorization tests**

```java
@Test void failedCandidateTestDoesNotChangeActiveVersion() {
    when(factory.createChat(any())).thenReturn(failingChatModel());
    assertThatThrownBy(() -> service.saveChat("admin", dto()))
        .hasMessageContaining("连接测试失败");
    assertThat(registry.requireChat().version()).isEqualTo(7L);
    verify(configMapper, never()).insert(any());
}

@Test void nonAdminCannotReadModelConfig() throws Exception {
    mockMvc.perform(post("/admin/model-config/query")
            .requestAttr(AdminRequestContext.ADMIN_USERNAME_ATTRIBUTE, "operator"))
        .andExpect(status().isForbidden());
}

@ParameterizedTest
@ValueSource(strings = {"qwq-32b", "deepseek-r1", "example-thinking-only"})
void knownReasoningOnlyModelsAreRejectedForLowLatencyChat(String modelName) {
    assertThatThrownBy(() -> policyValidator.validate(modelName))
        .hasMessageContaining("请选择支持非推理模式的对话模型");
}
```

- [ ] **Step 2: Run tests and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=ModelConfigServiceImplTest,AdminModelConfigControllerTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL because service/controller APIs do not exist.

- [ ] **Step 3: Implement validation, test-before-save, activation, and audit**

```java
@Transactional
public ModelConfigVo saveChat(String username, ModelConfigSaveDto dto) {
    adminGuard.require(username);
    ModelRuntimeSpec candidate = validator.validateAndResolve(dto, currentChatConfig());
    ChatModel model = factory.createChat(candidate);
    connectionTester.testChat(model, candidate.toolCallingSupported());
    DochubAiModelConfig saved = store.insertNextVersion(candidate, username);
    registry.activateChat(saved.getConfigVersion(), model, candidate);
    publisher.publish(ModelType.CHAT, saved.getConfigVersion());
    audit.success(saved, username, "ACTIVATE");
    return presenter.toVo(saved);
}
```

Return only masked key metadata. Add `/admin/model-config/**` to the admin interceptor and enforce `is_admin=1` again inside every controller action. Add a 10-second database version poll and Redis version event listener; reload a new candidate before swapping and retain the old snapshot on error.

Reject known reasoning-only model families for the low-latency chat slot with an explicit validation message. Keep the pattern list configurable, show it in the connection-test result, and let provider-specific non-thinking switches handle dual-mode models.

- [ ] **Step 4: Run tests and verify GREEN**

Run the command from Step 2.

Expected: authorization, rollback, masking, and activation tests pass.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/modelconfig dochub-agent-business/dochub-agent-business-dochub/src/main/java/com/dochub/workbench/auth dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig
git commit -m "feat: expose secured model configuration APIs"
```

### Task 4: Add the administrator model configuration page

**Files:**
- Create: `dochub-web/src/views/admin/AdminModelConfigView.vue`
- Modify: `dochub-web/src/router/index.js`
- Modify: `dochub-web/src/views/admin/AdminLayoutView.vue`
- Modify: `dochub-web/src/api/api.js`
- Modify: `dochub-web/src/utils/adminAuth.js`
- Modify: `dochub-web/package.json`
- Modify: `dochub-web/vite.config.js`
- Create: `dochub-web/src/views/admin/AdminModelConfigView.test.js`

**Interfaces:**
- Consumes: query/test/save endpoints from Task 3.
- Produces: route `/admin/model-config`, visible only when `isAdminProfile()` is true.

- [ ] **Step 1: Add Vitest and write failing UI tests**

Run: `npm --prefix dochub-web install --save-dev vitest @testing-library/vue jsdom`

Add `"test": "vitest run"` to `package.json`, configure `test.environment = "jsdom"` in `vite.config.js`, and proxy `/admin/model-config` to the backend in development.

```javascript
it('shows the global-impact warning and final URL preview', async () => {
  render(AdminModelConfigView, { global: { stubs: apiStubs } })
  expect(screen.getByText(/影响所有用户和后台文档任务/)).toBeTruthy()
  await fireEvent.update(screen.getByLabelText('Base URL'), 'http://127.0.0.1:11434')
  await fireEvent.update(screen.getByLabelText('请求路径'), '/v1/chat/completions')
  expect(screen.getByText('http://127.0.0.1:11434/v1/chat/completions')).toBeTruthy()
})
```

- [ ] **Step 2: Run the focused UI test and verify RED**

Run: `npm --prefix dochub-web run test -- AdminModelConfigView.test.js`

Expected: FAIL because the view and test script do not exist.

- [ ] **Step 3: Implement page, API client, route, and admin-only navigation**

The page must include the exact warning and five-step configuration guide from the design spec, a complete chat configuration card, and a read-only embedding runtime summary that is extended into the protected change workflow by the embedding blue-green migration plan. Include deployment and compatibility selectors, URL preview, masked key behavior, test status, active version, updater, and update time. Do not render the nav item for permission-only operators.

```javascript
export const modelConfigApi = {
  query() { return requestApiEnvelope('/admin/model-config/query', { method: 'POST', body: {} }) },
  testChat(payload) { return requestApiEnvelope('/admin/model-config/chat/test', { method: 'POST', body: stringifyManageValue(payload) }) },
  saveChat(payload) { return requestApiEnvelope('/admin/model-config/chat/save', { method: 'POST', body: stringifyManageValue(payload) }) }
}
```

- [ ] **Step 4: Run UI tests and production build**

Run: `npm --prefix dochub-web run test -- AdminModelConfigView.test.js`

Expected: focused test passes.

Run: `npm --prefix dochub-web run build`

Expected: Vite exits 0 without unresolved imports.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-web/package.json dochub-web/package-lock.json dochub-web/src
git commit -m "feat: add administrator model settings page"
```

### Task 5: Remove committed plaintext credentials and verify the subsystem

**Files:**
- Modify: `dochub-agent-business/dochub-agent-business-dochub/src/main/resources/application.yaml`
- Modify: `README.md`
- Create: `scripts/verify-model-runtime.ps1`
- Test: `dochub-agent-business/dochub-agent-business-dochub/src/test/java/com/dochub/workbench/modelconfig/config/ModelConfigPropertiesTest.java`

**Interfaces:**
- Consumes: `DOCHUB_MODEL_CONFIG_ENCRYPTION_KEY`, `ALI_BAI_LIAN_API_KEY`, `TAVILY_API_KEY`.
- Produces: startup fallback from environment and database-first runtime after the first saved version.

- [ ] **Step 1: Add a configuration binding test that rejects an absent production encryption key**

```java
@Test void productionModeRequiresIndependentEncryptionKey() {
    assertThatThrownBy(() -> properties.validate(true))
        .hasMessageContaining("DOCHUB_MODEL_CONFIG_ENCRYPTION_KEY");
}
```

- [ ] **Step 2: Run the test and verify RED**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am -Dtest=ModelConfigPropertiesTest -Dsurefire.failIfNoSpecifiedTests=false test`

Expected: FAIL until validation is implemented.

- [ ] **Step 3: Replace plaintext YAML keys and document environment setup**

```yaml
spring:
  ai:
    openai:
      api-key: ${ALI_BAI_LIAN_API_KEY:}
      embedding:
        api-key: ${ALI_BAI_LIAN_EMBEDDING_API_KEY:${ALI_BAI_LIAN_API_KEY:}}
app:
  model-config:
    encryption-key: ${DOCHUB_MODEL_CONFIG_ENCRYPTION_KEY:}
  tavily:
    api-key: ${TAVILY_API_KEY:}
```

Document that exposed historical credentials must be rotated and local model URLs are resolved from the backend host/container.

Remove the current generic `thinking` extra-body entry from YAML; disabled-thinking options must come only from the selected compatibility preset so DashScope and Ollama receive their own supported field names.

- [ ] **Step 4: Run subsystem verification**

Run: `mvn -pl dochub-agent-business/dochub-agent-business-dochub -am test`

Expected: all backend tests pass.

Run: `npm --prefix dochub-web run test -- --run && npm --prefix dochub-web run build`

Expected: all frontend tests and build pass.

Run: `powershell -ExecutionPolicy Bypass -File scripts/verify-model-runtime.ps1 -BaseUrl http://127.0.0.1:8090`

Expected: with credentials supplied through environment variables, the script can test one DashScope-compatible chat configuration and one local `http://127.0.0.1:11434/v1` configuration, confirms masked-key responses, and never echoes a credential.

- [ ] **Step 5: Commit**

```powershell
git add -- dochub-agent-business/dochub-agent-business-dochub/src/main/resources/application.yaml README.md scripts/verify-model-runtime.ps1 dochub-agent-business/dochub-agent-business-dochub/src/test
git commit -m "security: remove plaintext model credentials"
```
