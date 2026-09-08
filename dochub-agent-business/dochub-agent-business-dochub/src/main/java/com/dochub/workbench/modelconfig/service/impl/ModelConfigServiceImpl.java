package com.dochub.workbench.modelconfig.service.impl;

import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import com.dochub.workbench.modelconfig.dto.ModelConfigSaveDto;
import com.dochub.workbench.modelconfig.dto.ModelConfigTestDto;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.provider.ChatModelProvider;
import com.dochub.workbench.modelconfig.provider.ChatModelProviderRouter;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import com.dochub.workbench.modelconfig.service.ModelConfigService;
import com.dochub.workbench.modelconfig.support.ChatModelPolicyValidator;
import com.dochub.workbench.modelconfig.support.ModelConfigVersionPublisher;
import com.dochub.workbench.modelconfig.support.ModelConfigFailureAuditRecorder;
import com.dochub.workbench.modelconfig.support.AdminGuard;
import com.dochub.workbench.modelconfig.vo.ModelConfigVo;
import com.dochub.workbench.modelconfig.vo.ModelConnectionTestVo;
import org.javaup.exception.DochubFrameException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.util.Date;
import java.util.Locale;

/** Validates and probes an immutable candidate before publishing it as the active chat runtime. */
@Service
public class ModelConfigServiceImpl implements ModelConfigService {

    private static final String DEFAULT_CHAT_PATH = "/v1/chat/completions";
    private static final String DEFAULT_EMBEDDING_PATH = "/v1/embeddings";

    private final DochubAiModelConfigMapper configMapper;
    private final DochubAiModelConfigAuditMapper auditMapper;
    private final UidGenerator uidGenerator;
    private final ModelRuntimeRegistry registry;
    private final ChatModelProviderRouter providerRouter;
    private final ModelCredentialCipher cipher;
    private final ChatModelPolicyValidator policyValidator;
    private final AdminGuard adminGuard;
    private final ModelConfigVersionPublisher versionPublisher;
    private final ModelConfigFailureAuditRecorder failureAuditRecorder;

    public ModelConfigServiceImpl(DochubAiModelConfigMapper configMapper,
                                  DochubAiModelConfigAuditMapper auditMapper,
                                  UidGenerator uidGenerator,
                                  ModelRuntimeRegistry registry,
                                  ChatModelProviderRouter providerRouter,
                                  ModelCredentialCipher cipher,
                                  ChatModelPolicyValidator policyValidator,
                                  AdminGuard adminGuard,
                                  ModelConfigVersionPublisher versionPublisher,
                                  ModelConfigFailureAuditRecorder failureAuditRecorder) {
        this.configMapper = configMapper;
        this.auditMapper = auditMapper;
        this.uidGenerator = uidGenerator;
        this.registry = registry;
        this.providerRouter = providerRouter;
        this.cipher = cipher;
        this.policyValidator = policyValidator;
        this.adminGuard = adminGuard;
        this.versionPublisher = versionPublisher;
        this.failureAuditRecorder = failureAuditRecorder;
    }

    @Override
    public ModelConfigVo queryChat(String username) {
        AdminUserEntity operator = adminGuard.require(username);
        DochubAiModelConfig current = activeChat();
        audit(current, operator, "QUERY", 1, null);
        return current == null ? null : toVo(current);
    }

    @Override
    public ModelConnectionTestVo testChat(String username, ModelConfigTestDto dto) {
        AdminUserEntity operator = adminGuard.require(username);
        Candidate candidate = candidate(dto, activeChat(), false);
        requireCipherForRemote(candidate.deploymentType());
        try {
            ChatModelProvider provider = providerRouter.requireProvider(candidate.spec());
            ChatModel model = provider.create(candidate.spec());
            provider.probe(model, candidate.toolCallingSupported());
            audit(null, operator, "TEST", 1, null, candidate.baseUrl());
            return new ModelConnectionTestVo(true, "连接测试成功", policyValidator.rejectedReasoningPatterns());
        }
        catch (RuntimeException exception) {
            failureAuditRecorder.record(operator.getId(), maskEndpoint(candidate.baseUrl()), "连接测试失败");
            return new ModelConnectionTestVo(false, "连接测试失败", policyValidator.rejectedReasoningPatterns());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ModelConfigVo saveChat(String username, ModelConfigSaveDto dto) {
        AdminUserEntity operator = adminGuard.require(username);
        Candidate candidate = candidate(dto, activeChat(), dto != null && Boolean.TRUE.equals(dto.getClearApiKey()));
        requireCipherForRemote(candidate.deploymentType());
        ChatModelProvider provider = providerRouter.requireProvider(candidate.spec());
        ChatModel model = provider.create(candidate.spec());
        try {
            provider.probe(model, candidate.toolCallingSupported());
        }
        catch (RuntimeException exception) {
            failureAuditRecorder.record(operator.getId(), maskEndpoint(candidate.baseUrl()), "连接测试失败");
            throw new DochubFrameException(400, "连接测试失败");
        }

        long version = nextVersion();
        DochubAiModelConfig saved = new DochubAiModelConfig();
        saved.setId(uidGenerator.getUid());
        saved.setModelType(ModelType.CHAT.name());
        saved.setDeploymentType(candidate.deploymentType());
        saved.setCompatibilityPreset(candidate.spec().compatibilityPreset().name());
        saved.setBaseUrl(candidate.spec().baseUrl());
        saved.setRequestPath(candidate.spec().completionsPath());
        saved.setModelName(candidate.spec().modelName());
        saved.setEncryptedApiKey(cipher.encrypt(candidate.spec().apiKey()));
        saved.setTemperature(candidate.spec().temperature());
        saved.setMaxTokens(candidate.spec().maxTokens());
        saved.setTimeoutMillis(candidate.spec().timeoutMillis());
        saved.setToolCallingSupported(candidate.toolCallingSupported() ? 1 : 0);
        saved.setConfigVersion(version);
        saved.setActive(1);
        saved.setUpdatedBy(operator.getId());
        saved.setCreateTime(new Date());
        saved.setEditTime(new Date());
        saved.setStatus(1);

        configMapper.update(null, new LambdaUpdateWrapper<DochubAiModelConfig>()
            .eq(DochubAiModelConfig::getModelType, ModelType.CHAT.name())
            .eq(DochubAiModelConfig::getActive, 1)
            .set(DochubAiModelConfig::getActive, 0));
        configMapper.insert(saved);
        audit(saved, operator, "ACTIVATE", 1, null);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                registry.activateChat(version, model, candidate.spec());
                try {
                    versionPublisher.publish(version);
                }
                catch (RuntimeException ignored) {
                    // The scheduled database version poll is the recovery path if Redis is temporarily unavailable.
                }
            }
        });
        return toVo(saved);
    }

    private void requireCipherForRemote(String deploymentType) {
        if ("REMOTE".equals(deploymentType) && !cipher.isAvailable()) {
            throw new DochubFrameException(400, "模型配置加密密钥未配置");
        }
    }

    private Candidate candidate(ModelConfigTestDto dto, DochubAiModelConfig current, boolean clearApiKey) {
        if (dto == null) {
            throw new DochubFrameException(400, "请求不能为空");
        }
        String deploymentType = requiredEnum(dto.getDeploymentType(), "deploymentType");
        if (!"REMOTE".equals(deploymentType) && !"LOCAL".equals(deploymentType)) {
            throw new DochubFrameException(400, "deploymentType 仅支持 REMOTE 或 LOCAL");
        }
        if (clearApiKey && !"LOCAL".equals(deploymentType)) {
            throw new DochubFrameException(400, "仅本地部署允许清空 API Key");
        }
        CompatibilityPreset preset;
        try {
            preset = CompatibilityPreset.valueOf(requiredEnum(dto.getCompatibilityPreset(), "compatibilityPreset"));
        }
        catch (IllegalArgumentException exception) {
            throw new DochubFrameException(400, "compatibilityPreset 不支持");
        }
        String baseUrl = required(dto.getBaseUrl(), "baseUrl");
        try {
            URI.create(baseUrl);
        }
        catch (IllegalArgumentException exception) {
            throw new DochubFrameException(400, "baseUrl 格式不正确");
        }
        String modelName = required(dto.getModelName(), "modelName");
        policyValidator.validate(modelName);
        String apiKey = resolveApiKey(dto.getApiKey(), current, deploymentType, clearApiKey);
        Integer timeout = dto.getTimeoutMillis() == null ? 30_000 : dto.getTimeoutMillis();
        if (timeout <= 0) {
            throw new DochubFrameException(400, "timeoutMillis 必须大于 0");
        }
        String completionsPath = blankToDefault(dto.getRequestPath(), DEFAULT_CHAT_PATH);
        DeploymentType deployment = DeploymentType.valueOf(deploymentType);
        ModelRuntimeSpec spec = new ModelRuntimeSpec(ModelType.CHAT, deployment, preset, baseUrl, completionsPath,
            DEFAULT_EMBEDDING_PATH, apiKey, modelName, dto.getTemperature(), dto.getMaxTokens(), timeout);
        return new Candidate(spec, deploymentType, Boolean.TRUE.equals(dto.getToolCallingSupported()), baseUrl);
    }

    private String resolveApiKey(String proposed, DochubAiModelConfig current, String deploymentType, boolean clearApiKey) {
        if ("LOCAL".equals(deploymentType) || clearApiKey) {
            return "";
        }
        if (proposed != null && !proposed.isBlank()) {
            return proposed.trim();
        }
        if (current != null && current.getEncryptedApiKey() != null && !current.getEncryptedApiKey().isBlank()) {
            return cipher.decrypt(current.getEncryptedApiKey());
        }
        if ("REMOTE".equals(deploymentType)) {
            throw new DochubFrameException(400, "远程模型必须提供 API Key");
        }
        return "";
    }

    private DochubAiModelConfig activeChat() {
        return configMapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
            .eq(DochubAiModelConfig::getModelType, ModelType.CHAT.name())
            .eq(DochubAiModelConfig::getActive, 1)
            .eq(DochubAiModelConfig::getStatus, 1)
            .last("LIMIT 1"));
    }

    private long nextVersion() {
        DochubAiModelConfig newest = configMapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
            .eq(DochubAiModelConfig::getModelType, ModelType.CHAT.name())
            .orderByDesc(DochubAiModelConfig::getConfigVersion)
            .last("LIMIT 1"));
        return newest == null || newest.getConfigVersion() == null ? 1L : newest.getConfigVersion() + 1;
    }

    private ModelConfigVo toVo(DochubAiModelConfig config) {
        return new ModelConfigVo(config.getId(), config.getConfigVersion(), config.getDeploymentType(),
            config.getCompatibilityPreset(), config.getBaseUrl(), config.getRequestPath(), config.getModelName(),
            config.getTemperature(), config.getMaxTokens(), config.getTimeoutMillis(),
            config.getToolCallingSupported() != null && config.getToolCallingSupported() == 1,
            config.getEncryptedApiKey() != null && !config.getEncryptedApiKey().isBlank(),
            config.getActive() != null && config.getActive() == 1,
            config.getUpdatedBy(), config.getEditTime());
    }

    private void audit(DochubAiModelConfig config, AdminUserEntity operator, String action, int success, String error) {
        audit(config, operator, action, success, error, config == null ? null : config.getBaseUrl());
    }

    private void audit(DochubAiModelConfig config, AdminUserEntity operator, String action, int success, String error, String endpoint) {
        auditMapper.insert(new DochubAiModelConfigAudit(uidGenerator.getUid(), config == null ? null : config.getId(),
            ModelType.CHAT.name(), config == null ? null : config.getConfigVersion(), action, success,
            maskEndpoint(endpoint), operator.getId(), error, new Date()));
    }

    private String maskEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(endpoint);
            return uri.getScheme() + "://" + uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort());
        }
        catch (IllegalArgumentException exception) {
            return "<invalid-endpoint>";
        }
    }

    private String requiredEnum(String value, String field) {
        return required(value, field).toUpperCase(Locale.ROOT);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new DochubFrameException(400, field + " 不能为空");
        }
        return value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private record Candidate(ModelRuntimeSpec spec, String deploymentType, boolean toolCallingSupported, String baseUrl) {
    }
}
