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

import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingModelChangeServiceTest {

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

    private ModelRuntimeSpec spec(String name) {
        return new ModelRuntimeSpec(ModelType.EMBEDDING, CompatibilityPreset.OPENAI_COMPATIBLE,
            "http://localhost", "/v1/chat/completions", "/v1/embeddings", "", name,
            null, null, 30_000);
    }
}
