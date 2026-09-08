package com.dochub.workbench.modelconfig.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class DochubAiModelConfigAuditMapperTest {

    @Test
    void exposesOnlyAnInsertPersistenceOperation() {
        assertThat(BaseMapper.class.isAssignableFrom(DochubAiModelConfigAuditMapper.class)).isFalse();
        assertThat(Arrays.stream(DochubAiModelConfigAuditMapper.class.getDeclaredMethods())
            .map(Method::getName))
            .containsExactly("insert");
    }
}
