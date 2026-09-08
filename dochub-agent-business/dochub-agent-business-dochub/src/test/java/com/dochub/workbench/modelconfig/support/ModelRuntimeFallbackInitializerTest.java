package com.dochub.workbench.modelconfig.support;

import com.dochub.workbench.manage.config.QdrantProperties;
import com.dochub.workbench.modelconfig.mapper.DochubAiModelConfigMapper;
import com.dochub.workbench.modelconfig.runtime.ModelRuntimeRegistry;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ModelRuntimeFallbackInitializerTest {

    @Test
    void doesNotCallRemoteFallbackWhenStaticApiKeyIsBlank() {
        DochubAiModelConfigMapper mapper = mock(DochubAiModelConfigMapper.class);
        OpenAiCompatibleModelFactory factory = mock(OpenAiCompatibleModelFactory.class);
        ModelRuntimeFallbackInitializer initializer = new ModelRuntimeFallbackInitializer(mapper,
            new ModelRuntimeRegistry(), factory, new QdrantProperties());
        ReflectionTestUtils.setField(initializer, "apiKey", "");

        initializer.seedFallbacks();

        verifyNoInteractions(factory);
    }
}
