package com.dochub.workbench.modelconfig.support;

public record VersionedVectorCollectionNames(String document, String memory) {
    public static VersionedVectorCollectionNames from(String documentBase, String memoryBase, long version) {
        return new VersionedVectorCollectionNames(base(documentBase) + "_v" + version, base(memoryBase) + "_v" + version);
    }
    private static String base(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("collection base must not be blank");
        return value.trim().replaceFirst("_v\\d+$", "");
    }
}
