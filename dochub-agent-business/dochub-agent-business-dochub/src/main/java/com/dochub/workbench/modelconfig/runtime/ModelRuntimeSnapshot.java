package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;

import java.util.Objects;

/** Immutable model and settings captured by each newly-started model invocation. */
public record ModelRuntimeSnapshot<T>(long version, T model, ModelRuntimeSpec spec) {

    public ModelRuntimeSnapshot {
        Objects.requireNonNull(model, "model must not be null");
        Objects.requireNonNull(spec, "spec must not be null");
    }
}
