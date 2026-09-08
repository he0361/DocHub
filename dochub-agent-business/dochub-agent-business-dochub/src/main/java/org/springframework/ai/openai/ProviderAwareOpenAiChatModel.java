package org.springframework.ai.openai;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Bridges provider-specific OpenAI-compatible request fields into Spring AI 1.1's request record.
 * The upstream model exposes extraBody on options but does not copy it in createRequest.
 */
public final class ProviderAwareOpenAiChatModel extends OpenAiChatModel {

    private final Map<String, Object> providerBody;

    public ProviderAwareOpenAiChatModel(OpenAiApi api, OpenAiChatOptions options,
                                        Map<String, Object> providerBody) {
        super(api, options, ToolCallingManager.builder().build(), RetryUtils.DEFAULT_RETRY_TEMPLATE,
            ObservationRegistry.NOOP);
        this.providerBody = Map.copyOf(providerBody);
    }

    @Override
    OpenAiApi.ChatCompletionRequest createRequest(Prompt prompt, boolean stream) {
        OpenAiApi.ChatCompletionRequest source = super.createRequest(prompt, stream);
        Map<String, Object> extraBody = source.extraBody() == null
            ? new HashMap<>() : new HashMap<>(source.extraBody());
        extraBody.putAll(providerBody);
        return new OpenAiApi.ChatCompletionRequest(source.messages(), source.model(), source.store(),
            source.metadata(), source.frequencyPenalty(), source.logitBias(), source.logprobs(),
            source.topLogprobs(), source.maxTokens(), source.maxCompletionTokens(), source.n(),
            source.outputModalities(), source.audioParameters(), source.presencePenalty(), source.responseFormat(),
            source.seed(), source.serviceTier(), source.stop(), source.stream(), source.streamOptions(),
            source.temperature(), source.topP(), source.tools(), source.toolChoice(), source.parallelToolCalls(),
            source.user(), source.reasoningEffort(), source.webSearchOptions(), source.verbosity(),
            source.promptCacheKey(), source.safetyIdentifier(), extraBody);
    }
}
