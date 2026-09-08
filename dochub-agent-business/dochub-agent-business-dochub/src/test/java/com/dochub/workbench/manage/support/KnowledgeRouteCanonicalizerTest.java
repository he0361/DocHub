package com.dochub.workbench.manage.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class KnowledgeRouteCanonicalizerTest {

    private final KnowledgeRouteCanonicalizer canonicalizer = new KnowledgeRouteCanonicalizer();

    @ParameterizedTest
    @CsvSource({
        "DocHub Agent,dochubagent",
        "DocHub-Agent,dochubagent",
        "ＤｏｃＨｕｂ　Agent,dochubagent",
        "知识 路由,知识路由"
    })
    void equivalentNamesHaveOneCanonicalKey(String value, String expected) {
        assertThat(canonicalizer.canonicalKey(value)).isEqualTo(expected);
    }
}
