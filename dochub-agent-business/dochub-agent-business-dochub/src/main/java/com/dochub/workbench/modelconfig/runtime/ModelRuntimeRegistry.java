package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/** Atomically publishes immutable chat and embedding runtime snapshots. */
public final class ModelRuntimeRegistry {

    private final AtomicReference<ModelRuntimeSnapshot<ChatModel>> chat = new AtomicReference<>();
    private final AtomicReference<ModelRuntimeSnapshot<EmbeddingModel>> embedding = new AtomicReference<>();

    public void activateChat(long version, ChatModel model, ModelRuntimeSpec spec) {
        chat.set(new ModelRuntimeSnapshot<>(version, Objects.requireNonNull(model, "model must not be null"),
            Objects.requireNonNull(spec, "spec must not be null")));
    }

    public void activateEmbedding(long version, EmbeddingModel model, ModelRuntimeSpec spec) {
        embedding.set(new ModelRuntimeSnapshot<>(version, Objects.requireNonNull(model, "model must not be null"),
            Objects.requireNonNull(spec, "spec must not be null")));
    }

    public ModelRuntimeSnapshot<ChatModel> requireChat() {
        ModelRuntimeSnapshot<ChatModel> snapshot = chat.get();
        if (snapshot == null) {
            throw new IllegalStateException("No active chat model runtime snapshot");
        }
        return snapshot;
    }

    public ModelRuntimeSnapshot<EmbeddingModel> requireEmbedding() {
        ModelRuntimeSnapshot<EmbeddingModel> snapshot = embedding.get();
        if (snapshot == null) {
            throw new IllegalStateException("No active embedding model runtime snapshot");
        }
        return snapshot;
    }
}
