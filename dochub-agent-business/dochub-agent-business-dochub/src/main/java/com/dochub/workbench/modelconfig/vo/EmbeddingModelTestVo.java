package com.dochub.workbench.modelconfig.vo;

public record EmbeddingModelTestVo(boolean success, String message, int dimension,
                                   String finalRequestUrl, String changeMode) { }
