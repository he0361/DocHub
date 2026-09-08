package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.dochub.workbench.modelconfig.runtime.OpenAiHttpClientBuilderFactory;
import com.dochub.workbench.modelconfig.support.ChatModelConnectionTester;

import java.util.Locale;
import java.util.Map;

public final class VllmChatModelProvider extends AbstractChatModelProvider {

    public VllmChatModelProvider(OpenAiCompatibleModelFactory factory, ChatModelConnectionTester toolProbe) {
        super(factory, toolProbe);
    }

    @Override
    public boolean supports(DeploymentType deploymentType, CompatibilityPreset preset) {
        return deploymentType == DeploymentType.LOCAL && preset == CompatibilityPreset.OPENAI_COMPATIBLE;
    }

    @Override protected void validateProviderSpec(ModelRuntimeSpec spec) { }

    @Override Map<String, Object> extraBody(ModelRuntimeSpec spec) {
        return spec.modelName().toLowerCase(Locale.ROOT).startsWith("qwen")
            ? Map.of("chat_template_kwargs", Map.of("enable_thinking", false)) : Map.of();
    }

    @Override protected OpenAiHttpClientBuilderFactory httpClientBuilders(ModelRuntimeSpec spec) {
        return OpenAiHttpClientBuilderFactory.vllm();
    }
}
