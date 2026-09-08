package com.dochub.workbench.modelconfig.provider;

import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.dochub.workbench.modelconfig.runtime.OpenAiHttpClientBuilderFactory;
import com.dochub.workbench.modelconfig.support.ChatModelConnectionTester;
import org.javaup.exception.DochubFrameException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractChatModelProvider implements ChatModelProvider {

    private final OpenAiCompatibleModelFactory modelFactory;
    private final ChatModelConnectionTester toolProbe;

    protected AbstractChatModelProvider(OpenAiCompatibleModelFactory modelFactory,
                                        ChatModelConnectionTester toolProbe) {
        this.modelFactory = Objects.requireNonNull(modelFactory);
        this.toolProbe = Objects.requireNonNull(toolProbe);
    }

    @Override
    public final ChatModel create(ModelRuntimeSpec source) {
        ModelRuntimeSpec spec = normalize(source);
        validateCommon(spec);
        validateProviderSpec(spec);
        Map<String, Object> providerBody = extraBody(spec);
        return modelFactory.chatModel(spec, providerBody, httpClientBuilders(spec));
    }

    @Override
    public final void probe(ChatModel model, boolean toolCallingSupported) {
        try {
            Duration timeout = Duration.ofMillis(Math.max(1_000, probeTimeoutMillis()));
            ChatResponse response = model.stream(new Prompt("只回复 OK"))
                .filter(Objects::nonNull)
                .next()
                .block(timeout);
            if (response == null) {
                throw new IllegalStateException("流式连接未返回数据");
            }
            if (toolCallingSupported) {
                toolProbe.test(model, true);
            }
        }
        catch (RuntimeException exception) {
            throw new DochubFrameException(400, "模型流式连接测试失败: " + safeMessage(exception));
        }
    }

    final ModelRuntimeSpec normalize(ModelRuntimeSpec source) {
        Objects.requireNonNull(source, "spec must not be null");
        String apiKey = source.deploymentType() == DeploymentType.LOCAL ? "" : source.apiKey();
        return new ModelRuntimeSpec(source.modelType(), source.deploymentType(), source.compatibilityPreset(),
            source.baseUrl(), source.completionsPath(), source.embeddingsPath(), apiKey, source.modelName(),
            source.temperature(), source.maxTokens(), source.timeoutMillis());
    }

    protected abstract void validateProviderSpec(ModelRuntimeSpec spec);

    abstract Map<String, Object> extraBody(ModelRuntimeSpec spec);

    protected abstract OpenAiHttpClientBuilderFactory httpClientBuilders(ModelRuntimeSpec spec);

    private void validateCommon(ModelRuntimeSpec spec) {
        if (!supports(spec.deploymentType(), spec.compatibilityPreset())) {
            throw new DochubFrameException(400, "模型配置与提供方不匹配");
        }
    }

    private int probeTimeoutMillis() {
        return 30_000;
    }

    private String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }
}
