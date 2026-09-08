package com.dochub.workbench.modelconfig.service;

/** Small deterministic policy surface shared by UI previews and the change service. */
public final class EmbeddingModelChangeServiceImplSupport {
    private EmbeddingModelChangeServiceImplSupport() { }

    public static String changeMode(String activeModel, String candidateModel) {
        return normalize(activeModel).equals(normalize(candidateModel)) ? "HOT_SWAP" : "BLUE_GREEN_REBUILD";
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
