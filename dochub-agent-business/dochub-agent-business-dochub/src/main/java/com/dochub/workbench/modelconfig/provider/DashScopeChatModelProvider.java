package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.*;
import com.dochub.workbench.modelconfig.runtime.*;
import com.dochub.workbench.modelconfig.support.ChatModelConnectionTester;
import org.javaup.exception.DochubFrameException;
import java.util.Map;

public final class DashScopeChatModelProvider extends AbstractChatModelProvider {
    public DashScopeChatModelProvider(OpenAiCompatibleModelFactory factory, ChatModelConnectionTester probe) { super(factory, probe); }
    @Override public boolean supports(DeploymentType type, CompatibilityPreset preset) { return type == DeploymentType.REMOTE && preset == CompatibilityPreset.DASHSCOPE; }
    @Override protected void validateProviderSpec(ModelRuntimeSpec spec) { if (spec.apiKey().isBlank()) throw new DochubFrameException(400, "远程模型必须提供 API Key"); }
    @Override Map<String, Object> extraBody(ModelRuntimeSpec spec) { return Map.of("enable_thinking", false); }
    @Override protected OpenAiHttpClientBuilderFactory httpClientBuilders(ModelRuntimeSpec spec) { return OpenAiHttpClientBuilderFactory.defaults(); }
}
