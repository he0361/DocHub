package com.dochub.workbench.modelconfig.config;

import com.dochub.workbench.modelconfig.runtime.DynamicChatModel;
import com.dochub.workbench.modelconfig.runtime.DynamicEmbeddingModel;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Registers the stable primary model delegates used by existing application consumers. */
@Configuration
public class DynamicModelConfiguration {

    @Bean
    public ModelRuntimeRegistry modelRuntimeRegistry() {
        return new ModelRuntimeRegistry();
    }

    @Bean
    public OpenAiCompatibleModelFactory openAiCompatibleModelFactory() {
        return new OpenAiCompatibleModelFactory();
    }

    @Bean("dynamicChatModel")
    @Primary
    public ChatModel dynamicChatModel(ModelRuntimeRegistry registry) {
        return new DynamicChatModel(registry);
    }

    @Bean("dynamicEmbeddingModel")
    @Primary
    public EmbeddingModel dynamicEmbeddingModel(ModelRuntimeRegistry registry) {
        return new DynamicEmbeddingModel(registry);
    }
}
