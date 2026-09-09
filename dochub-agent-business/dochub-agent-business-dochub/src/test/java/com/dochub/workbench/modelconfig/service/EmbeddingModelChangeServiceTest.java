package com.dochub.workbench.modelconfig.service;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.manage.support.QdrantVectorStore;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.dto.EmbeddingModelChangeDto;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.mapper.DochubEmbeddingModelMigrationMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.security.EmbeddingChangeConfirmationGuard;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import com.dochub.workbench.modelconfig.service.impl.EmbeddingModelChangeServiceImpl;
import com.dochub.workbench.modelconfig.support.EmbeddingCandidateProbe;
import com.dochub.workbench.modelconfig.support.AdminGuard;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

import com.dochub.workbench.modelconfig.data.DochubAiModelConfigAudit;
import com.dochub.workbench.modelconfig.data.DochubEmbeddingModelMigration;
import com.dochub.workbench.modelconfig.vo.EmbeddingModelChangeVo;
import org.javaup.exception.DochubFrameException;
import org.mockito.ArgumentCaptor;

import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmbeddingModelChangeServiceTest {

    @Test
    void queryReturnsUnconfiguredStateWhenNoEmbeddingRuntimeExists() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        AdminGuard adminGuard = mock(AdminGuard.class);
        AdminUserEntity administrator = new AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        EmbeddingModelChangeServiceImpl service = new EmbeddingModelChangeServiceImpl(configMapper,
            mock(DochubAiModelConfigAuditMapper.class), mock(DochubEmbeddingModelMigrationMapper.class),
            mock(UidGenerator.class), new ModelCredentialCipher(""), new ModelRuntimeRegistry(),
            mock(EmbeddingCandidateProbe.class), mock(EmbeddingMigrationService.class),
            mock(EmbeddingRuntimeActivator.class), mock(EmbeddingChangeConfirmationGuard.class), adminGuard,
            mock(QdrantVectorStore.class), new ObjectMapper());

        var result = service.query("admin");

        assertThat(result.configured()).isFalse();
        assertThat(result.modelName()).isNull();
    }

    @Test
    void localEmbeddingCandidateWithoutApiKeyClearsPreviouslySavedRemoteCredential() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        ModelCredentialCipher cipher = new ModelCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        DochubAiModelConfig current = new DochubAiModelConfig();
        current.setEncryptedApiKey(cipher.encrypt("old-remote-secret"));
        when(configMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(current);
        AdminGuard adminGuard = mock(AdminGuard.class);
        AdminUserEntity administrator = new AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyList())).thenReturn(List.of(new float[] {1F, 2F}), List.of(new float[] {1F, 2F}));
        AtomicReference<ModelRuntimeSpec> captured = new AtomicReference<>();
        EmbeddingCandidateProbe probe = new EmbeddingCandidateProbe(spec -> { captured.set(spec); return embeddingModel; });
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        registry.activateEmbedding(new EmbeddingRuntimeSnapshot(1L, embeddingModel, spec("old-model"), 2, "document", "memory"));
        EmbeddingModelChangeServiceImpl service = new EmbeddingModelChangeServiceImpl(configMapper,
            mock(DochubAiModelConfigAuditMapper.class), mock(DochubEmbeddingModelMigrationMapper.class), mock(UidGenerator.class),
            cipher, registry, probe, mock(EmbeddingMigrationService.class), mock(EmbeddingRuntimeActivator.class),
            mock(EmbeddingChangeConfirmationGuard.class), adminGuard, mock(QdrantVectorStore.class), new ObjectMapper());
        EmbeddingModelChangeDto local = new EmbeddingModelChangeDto();
        local.setDeploymentType("LOCAL");
        local.setCompatibilityPreset("OPENAI_COMPATIBLE");
        local.setBaseUrl("http://127.0.0.1:11434");
        local.setModelName("local-embedding");

        service.test("admin", local);

        assertThat(captured.get().apiKey()).isEmpty();
    }

    @Test
    void firstEmbeddingCandidateTestSucceedsWithoutAnActiveRuntime() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        DochubAiModelConfigAuditMapper auditMapper = mock(DochubAiModelConfigAuditMapper.class);
        AdminGuard adminGuard = mock(AdminGuard.class);
        AdminUserEntity administrator = new AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        EmbeddingCandidateProbe probe = mock(EmbeddingCandidateProbe.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(probe.test(org.mockito.ArgumentMatchers.any())).thenReturn(
            new EmbeddingCandidateProbe.Result(embeddingModel, 1024));
        EmbeddingModelChangeServiceImpl service = new EmbeddingModelChangeServiceImpl(configMapper, auditMapper,
            mock(DochubEmbeddingModelMigrationMapper.class), mock(UidGenerator.class),
            new ModelCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])),
            new ModelRuntimeRegistry(), probe, mock(EmbeddingMigrationService.class),
            mock(EmbeddingRuntimeActivator.class), mock(EmbeddingChangeConfirmationGuard.class), adminGuard,
            mock(QdrantVectorStore.class), new ObjectMapper());
        EmbeddingModelChangeDto remote = new EmbeddingModelChangeDto();
        remote.setDeploymentType("REMOTE");
        remote.setCompatibilityPreset("DASHSCOPE");
        remote.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode");
        remote.setRequestPath("/v1/embeddings");
        remote.setModelName("text-embedding-v4");
        remote.setApiKey("test-key");

        var result = service.test("admin", remote);

        assertThat(result.success()).isTrue();
        assertThat(result.dimension()).isEqualTo(1024);
        assertThat(result.changeMode()).isEqualTo("BLUE_GREEN_REBUILD");
        verify(auditMapper).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void remoteEmbeddingCandidateExplainsMissingCredentialEncryptionKey() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        AdminGuard adminGuard = mock(AdminGuard.class);
        AdminUserEntity administrator = new AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        EmbeddingModelChangeServiceImpl service = new EmbeddingModelChangeServiceImpl(configMapper,
            mock(DochubAiModelConfigAuditMapper.class), mock(DochubEmbeddingModelMigrationMapper.class),
            mock(UidGenerator.class), new ModelCredentialCipher(""), new ModelRuntimeRegistry(),
            mock(EmbeddingCandidateProbe.class), mock(EmbeddingMigrationService.class),
            mock(EmbeddingRuntimeActivator.class), mock(EmbeddingChangeConfirmationGuard.class), adminGuard,
            mock(QdrantVectorStore.class), new ObjectMapper());
        EmbeddingModelChangeDto remote = new EmbeddingModelChangeDto();
        remote.setDeploymentType("REMOTE");
        remote.setCompatibilityPreset("DASHSCOPE");
        remote.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode");
        remote.setRequestPath("/v1/embeddings");
        remote.setModelName("text-embedding-v4");
        remote.setApiKey("test-key");

        assertThatThrownBy(() -> service.test("admin", remote))
            .hasMessageContaining("DOCHUB_MODEL_CONFIG_ENCRYPTION_KEY");
    }

    @Test
    void queryShowsLatestPendingCandidateBeforeFirstRuntimeIsActivated() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        DochubAiModelConfig pending = new DochubAiModelConfig();
        pending.setDeploymentType("REMOTE");
        pending.setCompatibilityPreset("DASHSCOPE");
        pending.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode");
        pending.setRequestPath("/v1/embeddings");
        pending.setModelName("text-embedding-v4");
        pending.setTimeoutMillis(30_000);
        pending.setConfigVersion(1L);
        pending.setEncryptedApiKey("encrypted-key");
        when(configMapper.selectOne(org.mockito.ArgumentMatchers.any())).thenReturn(null, pending);
        AdminGuard adminGuard = mock(AdminGuard.class);
        AdminUserEntity administrator = new AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        EmbeddingModelChangeServiceImpl service = new EmbeddingModelChangeServiceImpl(configMapper,
            mock(DochubAiModelConfigAuditMapper.class), mock(DochubEmbeddingModelMigrationMapper.class),
            mock(UidGenerator.class), new ModelCredentialCipher(""), new ModelRuntimeRegistry(),
            mock(EmbeddingCandidateProbe.class), mock(EmbeddingMigrationService.class),
            mock(EmbeddingRuntimeActivator.class), mock(EmbeddingChangeConfirmationGuard.class), adminGuard,
            mock(QdrantVectorStore.class), new ObjectMapper());

        var result = service.query("admin");

        assertThat(result.configured()).isFalse();
        assertThat(result.configVersion()).isEqualTo(1L);
        assertThat(result.modelName()).isEqualTo("text-embedding-v4");
        assertThat(result.hasApiKey()).isTrue();
    }

    @Test
    void probeRequiresStableFiniteNonEmptyDimensions() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenReturn(List.of(new float[] {1F, 2F}), List.of(new float[] {1F}));
        EmbeddingCandidateProbe probe = new EmbeddingCandidateProbe(spec -> model);

        assertThatThrownBy(() -> probe.test(spec("model-a"))).hasMessageContaining("维度");
    }

    @Test
    void sameNormalizedModelNameUsesHotSwapMode() {
        assertThat(EmbeddingModelChangeServiceImplSupport.changeMode(" model-a ", "model-a"))
            .isEqualTo("HOT_SWAP");
        assertThat(EmbeddingModelChangeServiceImplSupport.changeMode("model-a", "model-b"))
            .isEqualTo("BLUE_GREEN_REBUILD");
    }

    @Test
    void remoteEmbeddingChangeWithoutEncryptionKeyIsRejectedBeforeAnyPersist() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        DochubAiModelConfigAuditMapper auditMapper = mock(DochubAiModelConfigAuditMapper.class);
        EmbeddingMigrationService migrations = mock(EmbeddingMigrationService.class);
        EmbeddingModelChangeServiceImpl service = changeService(configMapper, auditMapper,
            new ModelCredentialCipher(""), mock(EmbeddingCandidateProbe.class), migrations,
            mock(EmbeddingRuntimeActivator.class), new ModelRuntimeRegistry());

        assertThatThrownBy(() -> service.change("admin", remoteDto("text-embedding-v4", "test-key")))
            .isInstanceOf(DochubFrameException.class)
            .hasMessageContaining("DOCHUB_MODEL_CONFIG_ENCRYPTION_KEY");
        verify(configMapper, never()).insert(any(DochubAiModelConfig.class));
        verify(migrations, never()).start(any(), any());
    }

    @Test
    void localEmbeddingFirstChangeActivatesImmediatelyWithoutEncryptionKey() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        DochubAiModelConfigAuditMapper auditMapper = mock(DochubAiModelConfigAuditMapper.class);
        EmbeddingRuntimeActivator activator = mock(EmbeddingRuntimeActivator.class);
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyList())).thenReturn(
            List.of(new float[] {1F, 2F, 3F}), List.of(new float[] {1F, 2F, 3F}));
        EmbeddingCandidateProbe probe = new EmbeddingCandidateProbe(spec -> embeddingModel);
        EmbeddingMigrationService migrations = mock(EmbeddingMigrationService.class);
        EmbeddingModelChangeServiceImpl service = changeService(configMapper, auditMapper,
            new ModelCredentialCipher(""), probe, migrations, activator, new ModelRuntimeRegistry());

        EmbeddingModelChangeVo result = service.change("admin", localDto());

        assertThat(result.status()).isEqualTo("ACTIVATED");
        assertThat(result.migrationId()).isNull();
        ArgumentCaptor<DochubAiModelConfig> inserted = ArgumentCaptor.forClass(DochubAiModelConfig.class);
        verify(configMapper).insert(inserted.capture());
        DochubAiModelConfig row = inserted.getValue();
        assertThat(row.getModelType()).isEqualTo(ModelType.EMBEDDING.name());
        assertThat(row.getDeploymentType()).isEqualTo("LOCAL");
        assertThat(row.getEncryptedApiKey()).isEmpty();
        assertThat(row.getOptionsJson()).isNotBlank();
        verify(activator).activate(any(), any(), any(), any(), any());
        verify(migrations, never()).start(any(), any());
        verify(auditMapper, never()).insert(any(DochubAiModelConfigAudit.class));
    }

    @Test
    void keyedRemoteFirstChangeActivatesImmediatelyAndEncryptsApiKey() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        DochubAiModelConfigAuditMapper auditMapper = mock(DochubAiModelConfigAuditMapper.class);
        EmbeddingRuntimeActivator activator = mock(EmbeddingRuntimeActivator.class);
        ModelCredentialCipher cipher = new ModelCredentialCipher(Base64.getEncoder().encodeToString(new byte[32]));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyList())).thenReturn(
            List.of(new float[] {1F, 2F, 3F}), List.of(new float[] {1F, 2F, 3F}));
        EmbeddingCandidateProbe probe = new EmbeddingCandidateProbe(spec -> embeddingModel);
        EmbeddingMigrationService migrations = mock(EmbeddingMigrationService.class);
        EmbeddingModelChangeServiceImpl service = changeService(configMapper, auditMapper,
            cipher, probe, migrations, activator, new ModelRuntimeRegistry());

        EmbeddingModelChangeVo result = service.change("admin", remoteDto("text-embedding-v4", "sk-secret"));

        assertThat(result.status()).isEqualTo("ACTIVATED");
        ArgumentCaptor<DochubAiModelConfig> inserted = ArgumentCaptor.forClass(DochubAiModelConfig.class);
        verify(configMapper).insert(inserted.capture());
        DochubAiModelConfig row = inserted.getValue();
        assertThat(row.getDeploymentType()).isEqualTo("REMOTE");
        assertThat(row.getEncryptedApiKey()).startsWith("v1:");
        assertThat(cipher.decrypt(row.getEncryptedApiKey())).isEqualTo("sk-secret");
        verify(activator).activate(any(), any(), any(), any(), any());
        verify(migrations, never()).start(any(), any());
    }

    @Test
    void changeWithActiveRuntimeAndDifferentModelStillStartsBlueGreenMigration() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        DochubAiModelConfigAuditMapper auditMapper = mock(DochubAiModelConfigAuditMapper.class);
        EmbeddingRuntimeActivator activator = mock(EmbeddingRuntimeActivator.class);
        EmbeddingModel activeModel = mock(EmbeddingModel.class);
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        registry.activateEmbedding(new EmbeddingRuntimeSnapshot(1L, activeModel, spec("model-a"), 3,
            "dochub_document_v1", "dochub_memory_v1"));
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.embed(anyList())).thenReturn(
            List.of(new float[] {1F, 2F, 3F}), List.of(new float[] {1F, 2F, 3F}));
        EmbeddingCandidateProbe probe = new EmbeddingCandidateProbe(s -> embeddingModel);
        EmbeddingMigrationService migrations = mock(EmbeddingMigrationService.class);
        DochubEmbeddingModelMigration job = mock(DochubEmbeddingModelMigration.class);
        when(job.getId()).thenReturn(10L);
        when(migrations.start(any(), any())).thenReturn(job);
        EmbeddingModelChangeServiceImpl service = changeService(configMapper, auditMapper,
            new ModelCredentialCipher(""), probe, migrations, activator, registry);

        EmbeddingModelChangeVo result = service.change("admin", localDto());

        assertThat(result.status()).isEqualTo("MIGRATION_STARTED");
        verify(configMapper).insert(any(DochubAiModelConfig.class));
        verify(migrations).start(any(), any());
        verify(activator, never()).activate(any(), any(), any(), any(), any());
        verify(auditMapper).insert(any(DochubAiModelConfigAudit.class));
    }

    private EmbeddingModelChangeServiceImpl changeService(DochubAiModelConfigMapper configMapper,
                                                          DochubAiModelConfigAuditMapper auditMapper,
                                                          ModelCredentialCipher cipher,
                                                          EmbeddingCandidateProbe probe,
                                                          EmbeddingMigrationService migrations,
                                                          EmbeddingRuntimeActivator activator,
                                                          ModelRuntimeRegistry registry) {
        AdminGuard adminGuard = mock(AdminGuard.class);
        AdminUserEntity administrator = new AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        return new EmbeddingModelChangeServiceImpl(configMapper, auditMapper,
            mock(DochubEmbeddingModelMigrationMapper.class), mock(UidGenerator.class), cipher, registry, probe,
            migrations, activator, mock(EmbeddingChangeConfirmationGuard.class), adminGuard,
            mock(QdrantVectorStore.class), new ObjectMapper());
    }

    private EmbeddingModelChangeDto localDto() {
        EmbeddingModelChangeDto dto = new EmbeddingModelChangeDto();
        dto.setDeploymentType("LOCAL");
        dto.setCompatibilityPreset("OPENAI_COMPATIBLE");
        dto.setBaseUrl("http://127.0.0.1:11434");
        dto.setRequestPath("/v1/embeddings");
        dto.setModelName("local-embedding");
        return dto;
    }

    private EmbeddingModelChangeDto remoteDto(String model, String apiKey) {
        EmbeddingModelChangeDto dto = new EmbeddingModelChangeDto();
        dto.setDeploymentType("REMOTE");
        dto.setCompatibilityPreset("DASHSCOPE");
        dto.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode");
        dto.setRequestPath("/v1/embeddings");
        dto.setModelName(model);
        dto.setApiKey(apiKey);
        return dto;
    }

    private ModelRuntimeSpec spec(String name) {
        return new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
            "http://localhost", "/v1/chat/completions", "/v1/embeddings", "", name,
            null, null, 30_000);
    }
}
