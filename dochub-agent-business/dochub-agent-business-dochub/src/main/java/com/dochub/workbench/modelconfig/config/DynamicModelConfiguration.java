package com.dochub.workbench.modelconfig.config;

import com.dochub.workbench.modelconfig.runtime.DynamicChatModel;
import com.dochub.workbench.modelconfig.runtime.DynamicEmbeddingModel;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import com.dochub.workbench.modelconfig.security.ModelCredentialCipher;
import com.dochub.workbench.modelconfig.support.ChatModelConnectionTester;
import com.dochub.workbench.modelconfig.support.ModelConfigRuntimeReloader;
import com.dochub.workbench.modelconfig.support.ModelConfigVersionPublisher;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Registers the stable primary model delegates used by existing application consumers. */
@Configuration
public class DynamicModelConfiguration {

    @Bean
    public ApplicationRunner modelConfigPropertyValidator(ModelConfigProperties properties, Environment environment) {
        return arguments -> properties.validate(environment.acceptsProfiles(Profiles.of("prod", "production")));
    }

    @Bean
    public ModelRuntimeRegistry modelRuntimeRegistry() {
        return new ModelRuntimeRegistry();
    }

    @Bean
    public OpenAiCompatibleModelFactory openAiCompatibleModelFactory() {
        return new OpenAiCompatibleModelFactory();
    }

    @Bean
    public ModelCredentialCipher modelCredentialCipher(ModelConfigProperties properties) {
        return new ModelCredentialCipher(properties.getEncryptionKey());
    }

    @Bean
    public ChatModelConnectionTester chatModelConnectionTester() {
        return ChatModelConnectionTester.defaultTester();
    }

    @Bean
    public com.dochub.workbench.modelconfig.support.ChatModelPolicyValidator chatModelPolicyValidator(ModelConfigProperties properties) {
        return new com.dochub.workbench.modelconfig.support.ChatModelPolicyValidator(properties);
    }

    @Bean
    public RedisMessageListenerContainer modelConfigRedisListenerContainer(RedisConnectionFactory connectionFactory,
                                                                            ModelConfigRuntimeReloader reloader) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(reloader, new ChannelTopic(ModelConfigVersionPublisher.CHANNEL));
        return container;
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
