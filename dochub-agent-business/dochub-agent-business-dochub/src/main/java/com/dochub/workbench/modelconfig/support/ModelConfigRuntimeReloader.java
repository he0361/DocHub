package com.dochub.workbench.modelconfig.support;

import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/** Reloads database-backed active settings on Redis invalidation and a ten-second polling fallback. */
@Component
public class ModelConfigRuntimeReloader implements MessageListener {

    private final DochubAiModelConfigMapper configMapper;
    private final DochubAiModelConfigAuditMapper auditMapper;
    private final UidGenerator uidGenerator;
    private final ModelRuntimeRegistry registry;
    private final OpenAiCompatibleModelFactory factory;
    private final ModelCredentialCipher cipher;

    public ModelConfigRuntimeReloader(DochubAiModelConfigMapper configMapper,
                                      DochubAiModelConfigAuditMapper auditMapper,
                                      UidGenerator uidGenerator,
                                      ModelRuntimeRegistry registry,
                                      OpenAiCompatibleModelFactory factory,
                                      ModelCredentialCipher cipher) {
        this.configMapper = configMapper;
        this.auditMapper = auditMapper;
        this.uidGenerator = uidGenerator;
        this.registry = registry;
        this.factory = factory;
        this.cipher = cipher;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        reloadIfNewer();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadActiveConfiguration() {
        reloadIfNewer();
    }

    @Scheduled(fixedDelay = 10_000L, initialDelay = 10_000L)
    public void pollDatabaseVersion() {
        reloadIfNewer();
    }

    /** Public for deterministic scheduler and Redis listener tests. A failed reload deliberately retains the old snapshot. */
    public void reloadIfNewer() {
        DochubAiModelConfig active = null;
        try {
            active = configMapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
                .eq(DochubAiModelConfig::getModelType, ModelType.CHAT.name())
                .eq(DochubAiModelConfig::getActive, 1).eq(DochubAiModelConfig::getStatus, 1).last("LIMIT 1"));
            if (active == null || active.getConfigVersion() == null || active.getConfigVersion() <= activeVersion()) return;
            ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.CHAT,
                CompatibilityPreset.valueOf(active.getCompatibilityPreset()), active.getBaseUrl(),
                blankToDefault(active.getRequestPath(), "/v1/chat/completions"), "/v1/embeddings",
                cipher.decrypt(active.getEncryptedApiKey()), active.getModelName(), active.getTemperature(),
                active.getMaxTokens(), active.getTimeoutMillis());
            ChatModel model = factory.chatModel(spec);
            registry.activateChat(active.getConfigVersion(), model, spec);
            auditSafely(active, 1, null);
        }
        catch (RuntimeException exception) {
            if (active != null) auditSafely(active, 0, "运行时配置加载失败");
        }
    }

    private long activeVersion() {
        try {
            return registry.requireChat().version();
        }
        catch (IllegalStateException exception) {
            return -1L;
        }
    }

    private void audit(DochubAiModelConfig config, int success, String error) {
        auditMapper.insert(new DochubAiModelConfigAudit(uidGenerator.getUid(), config.getId(), ModelType.CHAT.name(),
            config.getConfigVersion(), "RELOAD", success, maskedEndpoint(config.getBaseUrl()), null, error, new Date()));
    }

    private void auditSafely(DochubAiModelConfig config, int success, String error) {
        try { audit(config, success, error); } catch (RuntimeException ignored) { }
    }

    private String maskedEndpoint(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(baseUrl);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        }
        catch (IllegalArgumentException exception) {
            return "<invalid-endpoint>";
        }
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
