package com.dochub.workbench.modelconfig.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Seeds version-zero concrete OpenAI models from existing YAML until a DB configuration exists. */
@Component
public class ModelRuntimeFallbackInitializer {
    private final DochubAiModelConfigMapper mapper;
    private final ModelRuntimeRegistry registry;
    private final OpenAiCompatibleModelFactory factory;
    @Value("${spring.ai.openai.base-url}") private String baseUrl;
    @Value("${spring.ai.openai.api-key}") private String apiKey;
    @Value("${spring.ai.openai.chat.options.model}") private String chatModel;
    @Value("${spring.ai.openai.embedding.options.model}") private String embeddingModel;

    public ModelRuntimeFallbackInitializer(DochubAiModelConfigMapper mapper, ModelRuntimeRegistry registry,
                                           OpenAiCompatibleModelFactory factory) {
        this.mapper = mapper; this.registry = registry; this.factory = factory;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedFallbacks() {
        seed(ModelType.CHAT); seed(ModelType.EMBEDDING);
    }
    private void seed(ModelType type) {
        try {
            DochubAiModelConfig active = mapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
                .eq(DochubAiModelConfig::getModelType, type.name()).eq(DochubAiModelConfig::getActive, 1).last("LIMIT 1"));
            if (active != null) return;
            ModelRuntimeSpec spec = new ModelRuntimeSpec(type, CompatibilityPreset.OPENAI_COMPATIBLE, baseUrl,
                "/v1/chat/completions", "/v1/embeddings", apiKey,
                type == ModelType.CHAT ? chatModel : embeddingModel, null, null, 30_000);
            if (type == ModelType.CHAT) {
                try { registry.requireChat(); } catch (IllegalStateException ignored) { registry.activateChat(0, factory.chatModel(spec), spec); }
            } else {
                try { registry.requireEmbedding(); } catch (IllegalStateException ignored) { registry.activateEmbedding(0, factory.embeddingModel(spec), spec); }
            }
        } catch (RuntimeException ignored) { /* keep startup availability; an existing DB reload can still win */ }
    }
}
