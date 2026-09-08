package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import org.javaup.exception.DochubFrameException;

import java.util.List;
import java.util.Objects;

public final class ChatModelProviderRouter {

    private final List<ChatModelProvider> providers;

    public ChatModelProviderRouter(List<ChatModelProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    public ChatModelProvider requireProvider(ModelRuntimeSpec spec) {
        Objects.requireNonNull(spec, "spec must not be null");
        return providers.stream()
            .filter(provider -> provider.supports(spec.deploymentType(), spec.compatibilityPreset()))
            .findFirst()
            .orElseThrow(() -> new DochubFrameException(400, "不支持的模型部署与兼容预设组合"));
    }
}
