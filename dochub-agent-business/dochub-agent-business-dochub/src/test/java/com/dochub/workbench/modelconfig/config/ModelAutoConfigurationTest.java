package com.dochub.workbench.modelconfig.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ModelAutoConfigurationTest {

    @Test
    void staticOpenAiAutoConfigurationsAreDisabledForDynamicModelRuntime() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));

        assertThat(yaml.getObject())
            .containsEntry("spring.ai.model.chat", "none")
            .containsEntry("spring.ai.model.embedding", "none")
            .containsEntry("spring.ai.model.image", "none")
            .containsEntry("spring.ai.model.moderation", "none")
            .containsEntry("spring.ai.model.audio.speech", "none")
            .containsEntry("spring.ai.model.audio.transcription", "none");
    }

    @Test
    void dynamicModelTablesAreInitializedBeforeRecoveryTasksRun() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));

        assertThat(yaml.getObject())
            .containsEntry("spring.sql.init.mode", "always")
            .containsEntry("spring.sql.init.schema-locations", "classpath:db/model-runtime-schema.sql");
        assertThat(new ClassPathResource("db/model-runtime-schema.sql").exists()).isTrue();
    }
}
