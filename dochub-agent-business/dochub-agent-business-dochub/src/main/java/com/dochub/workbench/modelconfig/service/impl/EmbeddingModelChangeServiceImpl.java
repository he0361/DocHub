package com.dochub.workbench.modelconfig.service.impl;

import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.dto.EmbeddingMigrationRetryDto;
import com.dochub.workbench.modelconfig.dto.EmbeddingModelChangeDto;
import com.dochub.workbench.modelconfig.dto.EmbeddingRollbackDto;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.EmbeddingRuntimeCandidate;
import com.dochub.workbench.modelconfig.model.EmbeddingRuntimeMetadata;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.security.EmbeddingChangeConfirmationGuard;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import com.dochub.workbench.modelconfig.service.EmbeddingMigrationService;
import com.dochub.workbench.modelconfig.service.EmbeddingModelChangeService;
import com.dochub.workbench.modelconfig.service.EmbeddingModelChangeServiceImplSupport;
import com.dochub.workbench.modelconfig.service.EmbeddingRuntimeActivator;
import com.dochub.workbench.modelconfig.support.EmbeddingCandidateProbe;
import com.dochub.workbench.modelconfig.support.SuperAdminGuard;
import com.dochub.workbench.modelconfig.support.VersionedVectorCollectionNames;
import com.dochub.workbench.modelconfig.vo.EmbeddingConfigVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingMigrationVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingModelChangeVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingModelTestVo;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.util.Date;
import java.util.Locale;

@Service
public class EmbeddingModelChangeServiceImpl implements EmbeddingModelChangeService {
    private static final String CHAT_PATH = "/v1/chat/completions";
    private static final String EMBEDDING_PATH = "/v1/embeddings";
    private final DochubAiModelConfigMapper configMapper;
    private final DochubAiModelConfigAuditMapper auditMapper;
    private final DochubEmbeddingModelMigrationMapper migrationMapper;
    private final UidGenerator uidGenerator;
    private final ModelCredentialCipher cipher;
    private final ModelRuntimeRegistry registry;
    private final EmbeddingCandidateProbe probe;
    private final EmbeddingMigrationService migrations;
    private final EmbeddingRuntimeActivator activator;
    private final EmbeddingChangeConfirmationGuard confirmationGuard;
    private final SuperAdminGuard superAdminGuard;
    private final QdrantVectorStore qdrant;
    private final ObjectMapper objectMapper;

    public EmbeddingModelChangeServiceImpl(DochubAiModelConfigMapper configMapper,
                                           DochubAiModelConfigAuditMapper auditMapper,
                                           DochubEmbeddingModelMigrationMapper migrationMapper,
                                           UidGenerator uidGenerator, ModelCredentialCipher cipher,
                                           ModelRuntimeRegistry registry, EmbeddingCandidateProbe probe,
                                           EmbeddingMigrationService migrations, EmbeddingRuntimeActivator activator,
                                           EmbeddingChangeConfirmationGuard confirmationGuard,
                                           SuperAdminGuard superAdminGuard, QdrantVectorStore qdrant,
                                           ObjectMapper objectMapper) {
        this.configMapper = configMapper; this.auditMapper = auditMapper; this.migrationMapper = migrationMapper;
        this.uidGenerator = uidGenerator; this.cipher = cipher; this.registry = registry; this.probe = probe;
        this.migrations = migrations; this.activator = activator; this.confirmationGuard = confirmationGuard;
        this.superAdminGuard = superAdminGuard; this.qdrant = qdrant; this.objectMapper = objectMapper;
    }

    @Override
    public EmbeddingConfigVo query(String username) {
        AdminUserEntity operator = superAdminGuard.require(username);
        DochubAiModelConfig active = activeConfig();
        EmbeddingRuntimeSnapshot runtime = registry.captureEmbedding();
        audit(active, operator.getId(), "QUERY", 1, null);
        return new EmbeddingConfigVo(runtime.configVersion(), active == null ? null : active.getDeploymentType(),
            active == null ? runtime.spec().compatibilityPreset().name() : active.getCompatibilityPreset(),
            active == null ? runtime.spec().baseUrl() : active.getBaseUrl(),
            active == null ? runtime.spec().embeddingsPath() : active.getRequestPath(), runtime.spec().modelName(),
            runtime.spec().timeoutMillis(), active != null && active.getEncryptedApiKey() != null && !active.getEncryptedApiKey().isBlank(),
            runtime.dimension(), runtime.documentCollection(), runtime.memoryCollection(),
            active == null ? null : active.getUpdatedBy(), active == null ? null : active.getEditTime(),
            EmbeddingMigrationVo.from(migrationMapper.findLatest()));
    }

    @Override
    public EmbeddingModelTestVo test(String username, EmbeddingModelChangeDto dto) {
        AdminUserEntity operator = superAdminGuard.require(username);
        Candidate candidate = candidate(dto, activeConfig());
        try {
            EmbeddingCandidateProbe.Result result = probe.test(candidate.spec());
            audit(null, operator.getId(), "TEST", 1, null, candidate.spec().baseUrl());
            return new EmbeddingModelTestVo(true, "连接与维度测试成功", result.dimension(),
                finalUrl(candidate.spec()), EmbeddingModelChangeServiceImplSupport.changeMode(
                    registry.captureEmbedding().spec().modelName(), candidate.spec().modelName()));
        } catch (RuntimeException exception) {
            audit(null, operator.getId(), "TEST", 0, "连接或维度测试失败", candidate.spec().baseUrl());
            return new EmbeddingModelTestVo(false, "连接或维度测试失败", 0, finalUrl(candidate.spec()), null);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmbeddingModelChangeVo change(String username, EmbeddingModelChangeDto dto) {
        requireChangeDto(dto);
        confirmationGuard.verify(username, dto.getCurrentPassword(), dto.getConfirmationPhrase());
        AdminUserEntity operator = superAdminGuard.require(username);
        DochubAiModelConfig current = activeConfig();
        Candidate candidate = candidate(dto, current);
        EmbeddingCandidateProbe.Result probed = probe.test(candidate.spec());
        EmbeddingRuntimeSnapshot source = registry.captureEmbedding();
        String mode = EmbeddingModelChangeServiceImplSupport.changeMode(source.spec().modelName(), candidate.spec().modelName());
        long version = nextVersion();
        VersionedVectorCollectionNames names = "HOT_SWAP".equals(mode)
            ? new VersionedVectorCollectionNames(source.documentCollection(), source.memoryCollection())
            : VersionedVectorCollectionNames.from(baseCollection(source.documentCollection()), baseCollection(source.memoryCollection()), version);
        if ("HOT_SWAP".equals(mode) && probed.dimension() != source.dimension()) {
            throw new DochubFrameException(409, "同名向量模型返回的维度与当前集合不一致，拒绝热切换");
        }
        DochubAiModelConfig saved = persisted(candidate, version, operator.getId(), probed.dimension(), names, false);
        configMapper.insert(saved);
        EmbeddingRuntimeSnapshot target = new EmbeddingRuntimeSnapshot(version, probed.model(), candidate.spec(),
            probed.dimension(), names.document(), names.memory());
        if ("HOT_SWAP".equals(mode)) {
            activator.activate(null, saved, target, operator.getId(), "ACTIVATE_EMBEDDING");
            return new EmbeddingModelChangeVo("ACTIVATED", version, null, "同名模型凭证与地址已通过维度校验并热切换");
        }
        EmbeddingRuntimeCandidate migrationCandidate = new EmbeddingRuntimeCandidate(version, saved.getId(),
            probed.model(), candidate.spec(), probed.dimension(), names.document(), names.memory());
        DochubEmbeddingModelMigration job = migrations.start(migrationCandidate, operator.getId());
        afterCommit(() -> migrations.schedule(job.getId()));
        audit(saved, operator.getId(), "START_REBUILD", 1, null);
        return new EmbeddingModelChangeVo("MIGRATION_STARTED", version, job.getId(), "后台重建完成并校验通过后才会切换");
    }

    @Override
    public EmbeddingMigrationVo migrationStatus(String username, Long migrationId) {
        superAdminGuard.require(username);
        DochubEmbeddingModelMigration job = migrationId == null ? migrationMapper.findLatest() : migrations.find(migrationId);
        if (job == null) throw new DochubFrameException(404, "向量迁移任务不存在");
        return EmbeddingMigrationVo.from(job);
    }

    @Override
    public EmbeddingMigrationVo retry(String username, EmbeddingMigrationRetryDto dto) {
        if (dto == null || dto.getMigrationId() == null) throw new DochubFrameException(400, "migrationId 不能为空");
        confirmationGuard.verify(username, dto.getCurrentPassword(), dto.getConfirmationPhrase());
        migrations.retry(dto.getMigrationId());
        return EmbeddingMigrationVo.from(migrations.find(dto.getMigrationId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public EmbeddingModelChangeVo rollback(String username, EmbeddingRollbackDto dto) {
        if (dto == null || dto.getConfigVersion() == null) throw new DochubFrameException(400, "configVersion 不能为空");
        confirmationGuard.verify(username, dto.getCurrentPassword(), dto.getConfirmationPhrase());
        AdminUserEntity operator = superAdminGuard.require(username);
        DochubAiModelConfig config = configMapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
            .eq(DochubAiModelConfig::getModelType, ModelType.EMBEDDING.name())
            .eq(DochubAiModelConfig::getConfigVersion, dto.getConfigVersion())
            .eq(DochubAiModelConfig::getStatus, 1).last("LIMIT 1"));
        if (config == null) throw new DochubFrameException(404, "回滚目标向量配置不存在");
        EmbeddingRuntimeMetadata metadata = EmbeddingRuntimeMetadata.fromJson(objectMapper, config.getOptionsJson());
        ModelRuntimeSpec spec = spec(config, cipher.decrypt(config.getEncryptedApiKey()));
        EmbeddingCandidateProbe.Result result = probe.test(spec);
        if (result.dimension() != metadata.dimension()
            || qdrant.collectionDimension(metadata.documentCollection()) != metadata.dimension()
            || qdrant.collectionDimension(metadata.memoryCollection()) != metadata.dimension()) {
            throw new DochubFrameException(409, "回滚目标模型或保留集合维度校验失败");
        }
        EmbeddingRuntimeSnapshot snapshot = new EmbeddingRuntimeSnapshot(config.getConfigVersion(), result.model(), spec,
            metadata.dimension(), metadata.documentCollection(), metadata.memoryCollection());
        activator.activate(null, config, snapshot, operator.getId(), "ROLLBACK_EMBEDDING");
        return new EmbeddingModelChangeVo("ROLLED_BACK", config.getConfigVersion(), null, "已回滚到保留的完整向量运行时");
    }

    private Candidate candidate(EmbeddingModelChangeDto dto, DochubAiModelConfig current) {
        if (dto == null) throw new DochubFrameException(400, "请求不能为空");
        String deployment = required(dto.getDeploymentType(), "deploymentType").toUpperCase(Locale.ROOT);
        if (!"LOCAL".equals(deployment) && !"REMOTE".equals(deployment)) throw new DochubFrameException(400, "deploymentType 仅支持 REMOTE 或 LOCAL");
        CompatibilityPreset preset;
        try { preset = CompatibilityPreset.valueOf(required(dto.getCompatibilityPreset(), "compatibilityPreset").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new DochubFrameException(400, "compatibilityPreset 不支持"); }
        String baseUrl = required(dto.getBaseUrl(), "baseUrl");
        try { URI.create(baseUrl); } catch (IllegalArgumentException exception) { throw new DochubFrameException(400, "baseUrl 格式不正确"); }
        String modelName = required(dto.getModelName(), "modelName");
        String apiKey = resolveApiKey(dto, current, deployment);
        int timeout = dto.getTimeoutMillis() == null ? 30_000 : dto.getTimeoutMillis();
        if (timeout <= 0) throw new DochubFrameException(400, "timeoutMillis 必须大于 0");
        String path = dto.getRequestPath() == null || dto.getRequestPath().isBlank() ? EMBEDDING_PATH : dto.getRequestPath().trim();
        return new Candidate(new ModelRuntimeSpec(ModelType.EMBEDDING, preset, baseUrl, CHAT_PATH, path,
            apiKey, modelName, null, null, timeout), deployment);
    }

    private String resolveApiKey(EmbeddingModelChangeDto dto, DochubAiModelConfig current, String deployment) {
        if (Boolean.TRUE.equals(dto.getClearApiKey())) {
            if (!"LOCAL".equals(deployment)) throw new DochubFrameException(400, "仅本地部署允许清空 API Key");
            return "";
        }
        if (dto.getApiKey() != null && !dto.getApiKey().isBlank()) return dto.getApiKey().trim();
        if (current != null && current.getEncryptedApiKey() != null && !current.getEncryptedApiKey().isBlank()) return cipher.decrypt(current.getEncryptedApiKey());
        if ("REMOTE".equals(deployment)) throw new DochubFrameException(400, "远程向量模型必须提供 API Key");
        return "";
    }

    private DochubAiModelConfig persisted(Candidate candidate, long version, Long operator, int dimension,
                                           VersionedVectorCollectionNames names, boolean active) {
        DochubAiModelConfig config = new DochubAiModelConfig();
        config.setId(uidGenerator.getUid()); config.setModelType(ModelType.EMBEDDING.name());
        config.setDeploymentType(candidate.deployment()); config.setCompatibilityPreset(candidate.spec().compatibilityPreset().name());
        config.setBaseUrl(candidate.spec().baseUrl()); config.setRequestPath(candidate.spec().embeddingsPath());
        config.setModelName(candidate.spec().modelName()); config.setEncryptedApiKey(cipher.encrypt(candidate.spec().apiKey()));
        config.setTimeoutMillis(candidate.spec().timeoutMillis()); config.setToolCallingSupported(0);
        config.setOptionsJson(new EmbeddingRuntimeMetadata(dimension, names.document(), names.memory()).toJson(objectMapper));
        config.setConfigVersion(version); config.setActive(active ? 1 : 0); config.setUpdatedBy(operator);
        config.setCreateTime(new Date()); config.setEditTime(new Date()); config.setStatus(1);
        return config;
    }

    private DochubAiModelConfig activeConfig() {
        return configMapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
            .eq(DochubAiModelConfig::getModelType, ModelType.EMBEDDING.name())
            .eq(DochubAiModelConfig::getActive, 1).eq(DochubAiModelConfig::getStatus, 1).last("LIMIT 1"));
    }

    private long nextVersion() {
        DochubAiModelConfig newest = configMapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
            .eq(DochubAiModelConfig::getModelType, ModelType.EMBEDDING.name())
            .orderByDesc(DochubAiModelConfig::getConfigVersion).last("LIMIT 1"));
        return newest == null || newest.getConfigVersion() == null ? 1L : newest.getConfigVersion() + 1;
    }

    private ModelRuntimeSpec spec(DochubAiModelConfig config, String apiKey) {
        return new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.valueOf(config.getCompatibilityPreset()),
            config.getBaseUrl(), CHAT_PATH, config.getRequestPath(), apiKey, config.getModelName(), null, null,
            config.getTimeoutMillis());
    }

    private void audit(DochubAiModelConfig config, Long operator, String action, int success, String error) {
        audit(config, operator, action, success, error, config == null ? null : config.getBaseUrl());
    }

    private void audit(DochubAiModelConfig config, Long operator, String action, int success, String error, String endpoint) {
        auditMapper.insert(new DochubAiModelConfigAudit(uidGenerator.getUid(), config == null ? null : config.getId(),
            ModelType.EMBEDDING.name(), config == null ? null : config.getConfigVersion(), action, success,
            maskEndpoint(endpoint), operator, error, new Date()));
    }

    private String maskEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) return null;
        try { URI uri = URI.create(endpoint); return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort()); }
        catch (RuntimeException ignored) { return "<invalid-endpoint>"; }
    }

    private String finalUrl(ModelRuntimeSpec spec) { return spec.baseUrl().replaceAll("/+$", "") + "/" + spec.embeddingsPath().replaceAll("^/+", ""); }
    private String required(String value, String field) { if (value == null || value.isBlank()) throw new DochubFrameException(400, field + " 不能为空"); return value.trim(); }
    private String baseCollection(String collection) { return collection == null ? "dochub" : collection.replaceFirst("_v\\d+$", ""); }
    private void requireChangeDto(EmbeddingModelChangeDto dto) { if (dto == null) throw new DochubFrameException(400, "请求不能为空"); }
    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { action.run(); } });
        else action.run();
    }
    private record Candidate(ModelRuntimeSpec spec, String deployment) { }
}
