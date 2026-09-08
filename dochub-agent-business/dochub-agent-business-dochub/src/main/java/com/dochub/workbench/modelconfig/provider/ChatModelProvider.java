package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import org.springframework.ai.chat.model.ChatModel;

public interface ChatModelProvider {

    boolean supports(DeploymentType deploymentType, CompatibilityPreset preset);

    ChatModel create(ModelRuntimeSpec spec);

    void probe(ChatModel model, boolean toolCallingSupported);
}
