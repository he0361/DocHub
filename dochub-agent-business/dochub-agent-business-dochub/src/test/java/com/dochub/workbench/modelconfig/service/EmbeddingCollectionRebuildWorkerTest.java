package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.modelconfig.support.VersionedVectorCollectionNames;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingCollectionRebuildWorkerTest {
    @Test
    void targetCollectionsAreVersionedTogether() {
        VersionedVectorCollectionNames names = VersionedVectorCollectionNames.from("dochub_document", "dochub_memory", 8L);
        assertThat(names.document()).isEqualTo("dochub_document_v8");
        assertThat(names.memory()).isEqualTo("dochub_memory_v8");
    }
}
