package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.*;
import com.dochub.workbench.modelconfig.runtime.*;
import com.dochub.workbench.modelconfig.support.ChatModelConnectionTester;
import java.util.Map;

public final class OllamaChatModelProvider extends AbstractChatModelProvider {
    public OllamaChatModelProvider(OpenAiCompatibleModelFactory factory, ChatModelConnectionTester probe) { super(factory, probe); }
    @Override public boolean supports(DeploymentType type, CompatibilityPreset preset) { return type == DeploymentType.LOCAL && preset == CompatibilityPreset.OLLAMA; }
    @Override protected void validateProviderSpec(ModelRuntimeSpec spec) { }
    @Override Map<String, Object> extraBody(ModelRuntimeSpec spec) { return Map.of("think", false); }
    @Override protected OpenAiHttpClientBuilderFactory httpClientBuilders(ModelRuntimeSpec spec) { return OpenAiHttpClientBuilderFactory.defaults(); }
}
