package com.dochub.workbench.modelconfig.service;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.modelconfig.dto.ModelConfigSaveDto;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import com.dochub.workbench.modelconfig.service.impl.ModelConfigServiceImpl;
import com.dochub.workbench.modelconfig.support.ChatModelPolicyValidator;
import com.dochub.workbench.modelconfig.support.ModelConfigVersionPublisher;
import com.dochub.workbench.modelconfig.support.SuperAdminGuard;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConfigServiceImplTest {

    @Test
    void failedCandidateTestDoesNotChangeActiveVersion() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        OpenAiCompatibleModelFactory factory = mock(OpenAiCompatibleModelFactory.class);
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        registry.activateChat(7L, mock(ChatModel.class), spec());
        when(factory.chatModel(any())).thenReturn(mock(ChatModel.class));
        SuperAdminGuard superAdminGuard = mock(SuperAdminGuard.class);
        com.dochub.workbench.auth.data.AdminUserEntity administrator = new com.dochub.workbench.auth.data.AdminUserEntity();
        administrator.setId(1L);
        when(superAdminGuard.require("admin")).thenReturn(administrator);
        ModelConfigServiceImpl service = new ModelConfigServiceImpl(configMapper,
            mock(DochubAiModelConfigAuditMapper.class), mock(UidGenerator.class), registry, factory,
            new ModelCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])),
            new ChatModelPolicyValidator(), (model, toolCallingSupported) -> {
                throw new IllegalStateException("unreachable provider");
            }, superAdminGuard, mock(ModelConfigVersionPublisher.class));

        assertThatThrownBy(() -> service.saveChat("admin", dto()))
            .hasMessageContaining("连接测试失败");
        assertThat(registry.requireChat().version()).isEqualTo(7L);
        verify(configMapper, never()).insert(any(DochubAiModelConfig.class));
    }

    private ModelConfigSaveDto dto() {
        ModelConfigSaveDto dto = new ModelConfigSaveDto();
        dto.setDeploymentType("REMOTE");
        dto.setCompatibilityPreset("OPENAI_COMPATIBLE");
        dto.setBaseUrl("https://example.test/v1");
        dto.setModelName("gpt-test");
        dto.setApiKey("secret");
        dto.setTimeoutMillis(1000);
        return dto;
    }

    private ModelRuntimeSpec spec() {
        return new ModelRuntimeSpec(ModelType.CHAT, CompatibilityPreset.OPENAI_COMPATIBLE,
            "https://example.test/v1", "/v1/chat/completions", "/v1/embeddings", "old-secret", "old-model", null, null, 1000);
    }
}
