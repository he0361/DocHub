package com.dochub.workbench.modelconfig.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelConfigPropertiesTest {

    @Test
    void productionModeRequiresIndependentEncryptionKey() {
        ModelConfigProperties properties = new ModelConfigProperties();

        assertThatThrownBy(() -> properties.validate(true))
            .hasMessageContaining("DOCHUB_MODEL_CONFIG_ENCRYPTION_KEY");
    }

    @Test
    void developmentModeAllowsYamlFallbackWithoutEncryptionKey() {
        ModelConfigProperties properties = new ModelConfigProperties();

        assertThatCode(() -> properties.validate(false)).doesNotThrowAnyException();
    }
}
