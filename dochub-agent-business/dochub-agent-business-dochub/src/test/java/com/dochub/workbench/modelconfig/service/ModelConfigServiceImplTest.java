package com.dochub.workbench.modelconfig.service;

import com.baidu.fsg.uid.UidGenerator;
import com.dochub.workbench.modelconfig.dto.ModelConfigSaveDto;
import com.dochub.workbench.modelconfig.dto.ModelConfigTestDto;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigAuditMapper;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.provider.ChatModelProvider;
import com.dochub.workbench.modelconfig.provider.ChatModelProviderRouter;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import com.dochub.workbench.modelconfig.service.impl.ModelConfigServiceImpl;
import com.dochub.workbench.modelconfig.support.ChatModelPolicyValidator;
import com.dochub.workbench.modelconfig.support.ModelConfigVersionPublisher;
import com.dochub.workbench.modelconfig.support.AdminGuard;
import com.dochub.workbench.modelconfig.support.ModelConfigFailureAuditRecorder;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Base64;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ModelConfigServiceImplTest {

    @Test
    void localChatCandidateWithoutApiKeyClearsPreviouslySavedRemoteCredential() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        ChatModelProvider provider = mock(ChatModelProvider.class);
        when(provider.supports(any(), any())).thenReturn(true);
        AtomicReference<ModelRuntimeSpec> captured = new AtomicReference<>();
        when(provider.create(any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(0));
            return mock(ChatModel.class);
        });
        AdminGuard adminGuard = mock(AdminGuard.class);
        com.dochub.workbench.auth.data.AdminUserEntity administrator = new com.dochub.workbench.auth.data.AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        ModelCredentialCipher cipher = new ModelCredentialCipher("");
        DochubAiModelConfig current = new DochubAiModelConfig();
        current.setEncryptedApiKey("previously-encrypted-remote-secret");
        when(configMapper.selectOne(any())).thenReturn(current);
        ModelConfigServiceImpl service = new ModelConfigServiceImpl(configMapper,
            mock(DochubAiModelConfigAuditMapper.class), mock(UidGenerator.class), new ModelRuntimeRegistry(),
            new ChatModelProviderRouter(java.util.List.of(provider)), cipher, new ChatModelPolicyValidator(), adminGuard,
            mock(ModelConfigVersionPublisher.class), mock(ModelConfigFailureAuditRecorder.class));
        ModelConfigTestDto local = new ModelConfigTestDto();
        local.setDeploymentType("LOCAL");
        local.setCompatibilityPreset("OPENAI_COMPATIBLE");
        local.setBaseUrl("http://127.0.0.1:11434");
        local.setModelName("local-chat");

        service.testChat("admin", local);

        assertThat(captured.get().apiKey()).isEmpty();
    }

    @Test
    void queryIncludesNonSecretUpdaterAndUpdateTimeMetadata() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        AdminGuard adminGuard = mock(AdminGuard.class);
        com.dochub.workbench.auth.data.AdminUserEntity administrator = new com.dochub.workbench.auth.data.AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        Date updateTime = new Date(1_700_000_000_000L);
        DochubAiModelConfig active = new DochubAiModelConfig();
        active.setUpdatedBy(42L);
        active.setEditTime(updateTime);
        active.setEncryptedApiKey("encrypted-value");
        when(configMapper.selectOne(any())).thenReturn(active);
        ModelConfigServiceImpl service = new ModelConfigServiceImpl(configMapper,
            mock(DochubAiModelConfigAuditMapper.class), mock(UidGenerator.class), new ModelRuntimeRegistry(),
            new ChatModelProviderRouter(java.util.List.of()), new ModelCredentialCipher(""), new ChatModelPolicyValidator(),
            adminGuard, mock(ModelConfigVersionPublisher.class),
            mock(ModelConfigFailureAuditRecorder.class));

        com.dochub.workbench.modelconfig.vo.ModelConfigVo result = service.queryChat("admin");

        assertThat(result.updatedBy()).isEqualTo(42L);
        assertThat(result.updateTime()).isEqualTo(updateTime);
        assertThat(result.hasApiKey()).isTrue();
    }

    @Test
    void failedCandidateTestDoesNotChangeActiveVersion() {
        DochubAiModelConfigMapper configMapper = mock(DochubAiModelConfigMapper.class);
        ChatModelProvider provider = mock(ChatModelProvider.class);
        when(provider.supports(any(), any())).thenReturn(true);
        ModelRuntimeRegistry registry = new ModelRuntimeRegistry();
        registry.activateChat(7L, mock(ChatModel.class), spec());
        when(provider.create(any())).thenReturn(mock(ChatModel.class));
        org.mockito.Mockito.doThrow(new IllegalStateException("unreachable provider"))
            .when(provider).probe(any(), org.mockito.ArgumentMatchers.anyBoolean());
        AdminGuard adminGuard = mock(AdminGuard.class);
        com.dochub.workbench.auth.data.AdminUserEntity administrator = new com.dochub.workbench.auth.data.AdminUserEntity();
        administrator.setId(1L);
        when(adminGuard.require("admin")).thenReturn(administrator);
        ModelConfigServiceImpl service = new ModelConfigServiceImpl(configMapper,
            mock(DochubAiModelConfigAuditMapper.class), mock(UidGenerator.class), registry,
            new ChatModelProviderRouter(java.util.List.of(provider)),
            new ModelCredentialCipher(Base64.getEncoder().encodeToString(new byte[32])),
            new ChatModelPolicyValidator(), adminGuard, mock(ModelConfigVersionPublisher.class),
            mock(ModelConfigFailureAuditRecorder.class));

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
