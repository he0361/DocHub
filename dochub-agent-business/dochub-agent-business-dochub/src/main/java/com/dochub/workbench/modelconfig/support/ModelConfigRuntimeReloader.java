package com.dochub.workbench.modelconfig.support;

import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.model.EmbeddingRuntimeMetadata;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.dochub.workbench.modelconfig.provider.ChatModelProviderRouter;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import org.springframework.ai.chat.model.ChatModel;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ChatModelProviderRouter chatProviderRouter;
    private final ModelCredentialCipher cipher;
    private final EmbeddingCandidateProbe embeddingProbe;
    private final QdrantVectorStore qdrant;
    private final ObjectMapper objectMapper;

    public ModelConfigRuntimeReloader(DochubAiModelConfigMapper configMapper,
                                      DochubAiModelConfigAuditMapper auditMapper,
                                      UidGenerator uidGenerator,
                                      ModelRuntimeRegistry registry,
                                      OpenAiCompatibleModelFactory factory,
                                      ChatModelProviderRouter chatProviderRouter,
                                      ModelCredentialCipher cipher,
                                      EmbeddingCandidateProbe embeddingProbe,
                                      QdrantVectorStore qdrant,
                                      ObjectMapper objectMapper) {
        this.configMapper = configMapper;
        this.auditMapper = auditMapper;
        this.uidGenerator = uidGenerator;
        this.registry = registry;
        this.factory = factory;
        this.chatProviderRouter = chatProviderRouter;
        this.cipher = cipher;
        this.embeddingProbe = embeddingProbe;
        this.qdrant = qdrant;
        this.objectMapper = objectMapper;
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
        reloadChatIfNewer();
        reloadEmbeddingIfNewer();
    }

    private void reloadChatIfNewer() {
        DochubAiModelConfig active = null;
        try {
            active = configMapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
                .eq(DochubAiModelConfig::getModelType, ModelType.CHAT.name())
                .eq(DochubAiModelConfig::getActive, 1).eq(DochubAiModelConfig::getStatus, 1).last("LIMIT 1"));
            if (active == null || active.getConfigVersion() == null || active.getConfigVersion() <= activeVersion()) return;
            DeploymentType deployment = DeploymentType.valueOf(active.getDeploymentType());
            ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.CHAT, deployment,
                CompatibilityPreset.valueOf(active.getCompatibilityPreset()), active.getBaseUrl(),
                blankToDefault(active.getRequestPath(), "/v1/chat/completions"), "/v1/embeddings",
                deployment == DeploymentType.LOCAL ? "" : cipher.decrypt(active.getEncryptedApiKey()),
                active.getModelName(), active.getTemperature(),
                active.getMaxTokens(), active.getTimeoutMillis());
            ChatModel model = chatProviderRouter.requireProvider(spec).create(spec);
            registry.activateChat(active.getConfigVersion(), model, spec);
            auditSafely(active, 1, null);
        }
        catch (RuntimeException exception) {
            if (active != null) auditSafely(active, 0, "运行时配置加载失败");
        }
    }

    private void reloadEmbeddingIfNewer() {
        DochubAiModelConfig active = null;
        try {
            active = configMapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
                .eq(DochubAiModelConfig::getModelType, ModelType.EMBEDDING.name())
                .eq(DochubAiModelConfig::getActive, 1).eq(DochubAiModelConfig::getStatus, 1).last("LIMIT 1"));
            if (active == null || active.getConfigVersion() == null) return;
            EmbeddingRuntimeMetadata metadata = EmbeddingRuntimeMetadata.fromJson(objectMapper, active.getOptionsJson());
            ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.EMBEDDING,
                DeploymentType.valueOf(active.getDeploymentType()),
                CompatibilityPreset.valueOf(active.getCompatibilityPreset()), active.getBaseUrl(),
                "/v1/chat/completions", blankToDefault(active.getRequestPath(), "/v1/embeddings"),
                cipher.decrypt(active.getEncryptedApiKey()), active.getModelName(), null, null, active.getTimeoutMillis());
            if (isCurrentEmbedding(active, spec, metadata)) return;
            EmbeddingCandidateProbe.Result candidate = embeddingProbe.test(spec);
            if (candidate.dimension() != metadata.dimension()
                || qdrant.collectionDimension(metadata.documentCollection()) != metadata.dimension()
                || qdrant.collectionDimension(metadata.memoryCollection()) != metadata.dimension()) {
                throw new IllegalStateException("向量运行时维度或集合校验失败");
            }
            registry.activateEmbedding(new EmbeddingRuntimeSnapshot(active.getConfigVersion(), candidate.model(), spec,
                metadata.dimension(), metadata.documentCollection(), metadata.memoryCollection()));
            auditSafely(active, ModelType.EMBEDDING, 1, null);
        } catch (RuntimeException exception) {
            if (active != null) auditSafely(active, ModelType.EMBEDDING, 0, "向量运行时配置加载失败");
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

    private long activeEmbeddingVersion() {
        try { return registry.captureEmbedding().configVersion(); }
        catch (IllegalStateException exception) { return -1L; }
    }

    /** Version numbers are monotonic for normal saves, but rollback deliberately re-activates an older version. */
    private boolean isCurrentEmbedding(DochubAiModelConfig active, ModelRuntimeSpec spec, EmbeddingRuntimeMetadata metadata) {
        try {
            EmbeddingRuntimeSnapshot snapshot = registry.captureEmbedding();
            return snapshot.configVersion() == active.getConfigVersion()
                && snapshot.spec().equals(spec)
                && snapshot.dimension() == metadata.dimension()
                && snapshot.documentCollection().equals(metadata.documentCollection())
                && snapshot.memoryCollection().equals(metadata.memoryCollection());
        } catch (IllegalStateException ignored) {
            return false;
        }
    }

    private void audit(DochubAiModelConfig config, int success, String error) {
        audit(config, ModelType.CHAT, success, error);
    }

    private void audit(DochubAiModelConfig config, ModelType type, int success, String error) {
        auditMapper.insert(new DochubAiModelConfigAudit(uidGenerator.getUid(), config.getId(), type.name(),
            config.getConfigVersion(), "RELOAD", success, maskedEndpoint(config.getBaseUrl()), null, error, new Date()));
    }

    private void auditSafely(DochubAiModelConfig config, int success, String error) {
        try { audit(config, success, error); } catch (RuntimeException ignored) { }
    }

    private void auditSafely(DochubAiModelConfig config, ModelType type, int success, String error) {
        try { audit(config, type, success, error); } catch (RuntimeException ignored) { }
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
