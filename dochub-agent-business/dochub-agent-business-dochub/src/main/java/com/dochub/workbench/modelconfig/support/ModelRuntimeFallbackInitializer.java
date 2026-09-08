package com.dochub.workbench.modelconfig.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.modelconfig.data.DochubAiModelConfig;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.model.CompatibilityPreset;
import com.dochub.workbench.modelconfig.model.DeploymentType;
import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.model.ModelType;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.dochub.workbench.modelconfig.runtime.EmbeddingRuntimeSnapshot;
import com.dochub.workbench.modelconfig.provider.ChatModelProviderRouter;
import com.dochub.workbench.manage.config.QdrantProperties;
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
    private final ChatModelProviderRouter chatProviderRouter;
    private final QdrantProperties qdrantProperties;
    @Value("${spring.ai.openai.base-url}") private String baseUrl;
    @Value("${spring.ai.openai.api-key}") private String apiKey;
    @Value("${spring.ai.openai.chat.options.model}") private String chatModel;
    @Value("${spring.ai.openai.embedding.options.model}") private String embeddingModel;

    public ModelRuntimeFallbackInitializer(DochubAiModelConfigMapper mapper, ModelRuntimeRegistry registry,
                                           OpenAiCompatibleModelFactory factory, ChatModelProviderRouter chatProviderRouter,
                                           QdrantProperties qdrantProperties) {
        this.mapper = mapper; this.registry = registry; this.factory = factory;
        this.chatProviderRouter = chatProviderRouter; this.qdrantProperties = qdrantProperties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedFallbacks() {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        seed(ModelType.CHAT); seed(ModelType.EMBEDDING);
    }
    private void seed(ModelType type) {
        try {
            DochubAiModelConfig active = mapper.selectOne(new LambdaQueryWrapper<DochubAiModelConfig>()
                .eq(DochubAiModelConfig::getModelType, type.name()).eq(DochubAiModelConfig::getActive, 1).last("LIMIT 1"));
            if (active != null) return;
            ModelRuntimeSpec spec = new ModelRuntimeSpec(type, DeploymentType.REMOTE,
                CompatibilityPreset.OPENAI_COMPATIBLE, baseUrl,
                "/v1/chat/completions", "/v1/embeddings", apiKey,
                type == ModelType.CHAT ? chatModel : embeddingModel, null, null, 30_000);
            if (type == ModelType.CHAT) {
                try { registry.requireChat(); } catch (IllegalStateException ignored) {
                    registry.activateChat(0, chatProviderRouter.requireProvider(spec).create(spec), spec);
                }
            } else {
                try { registry.requireEmbedding(); } catch (IllegalStateException ignored) {
                    var model = factory.embeddingModel(spec);
                    registry.activateEmbedding(new EmbeddingRuntimeSnapshot(0, model, spec,
                        safeDimension(model), qdrantProperties.getDocumentCollection(), qdrantProperties.getMemoryCollection()));
                }
            }
        } catch (RuntimeException ignored) { /* keep startup availability; an existing DB reload can still win */ }
    }

    private int safeDimension(org.springframework.ai.embedding.EmbeddingModel model) {
        try { return Math.max(0, model.dimensions()); } catch (RuntimeException ignored) { return 0; }
    }
}
