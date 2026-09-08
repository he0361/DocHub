package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.*;
import com.dochub.workbench.modelconfig.runtime.*;
import com.dochub.workbench.modelconfig.support.ChatModelConnectionTester;
import org.javaup.exception.DochubFrameException;
import java.util.Map;

public final class RemoteOpenAiChatModelProvider extends AbstractChatModelProvider {
    public RemoteOpenAiChatModelProvider(OpenAiCompatibleModelFactory factory, ChatModelConnectionTester probe) { super(factory, probe); }
    @Override public boolean supports(DeploymentType type, CompatibilityPreset preset) { return type == DeploymentType.REMOTE && preset == CompatibilityPreset.OPENAI_COMPATIBLE; }
    @Override protected void validateProviderSpec(ModelRuntimeSpec spec) { if (spec.apiKey().isBlank()) throw new DochubFrameException(400, "远程模型必须提供 API Key"); }
    @Override Map<String, Object> extraBody(ModelRuntimeSpec spec) { return Map.of(); }
    @Override protected OpenAiHttpClientBuilderFactory httpClientBuilders(ModelRuntimeSpec spec) { return OpenAiHttpClientBuilderFactory.defaults(); }
}
