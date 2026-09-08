package com.dochub.workbench.modelconfig.runtime;

import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Atomically publishes immutable chat and embedding runtime snapshots. */
public final class ModelRuntimeRegistry {

    private final AtomicReference<ModelRuntimeSnapshot<ChatModel>> chat = new AtomicReference<>();
    private final AtomicReference<EmbeddingRuntimeSnapshot> embedding = new AtomicReference<>();

    public void activateChat(long version, ChatModel model, ModelRuntimeSpec spec) {
        chat.set(new ModelRuntimeSnapshot<>(version, Objects.requireNonNull(model, "model must not be null"),
            Objects.requireNonNull(spec, "spec must not be null")));
    }

    public void activateEmbedding(long version, EmbeddingModel model, ModelRuntimeSpec spec) {
        EmbeddingRuntimeSnapshot current = embedding.get();
        activateEmbedding(new EmbeddingRuntimeSnapshot(version, model, spec,
            current == null ? Math.max(0, model.dimensions()) : current.dimension(),
            current == null ? "dochub_document" : current.documentCollection(),
            current == null ? "dochub_memory" : current.memoryCollection()));
    }

    public void activateEmbedding(EmbeddingRuntimeSnapshot snapshot) {
        embedding.set(Objects.requireNonNull(snapshot, "snapshot must not be null"));
    }

    public ModelRuntimeSnapshot<ChatModel> requireChat() {
        return captureChat().orElseThrow(() -> new IllegalStateException("No active chat model runtime snapshot"));
    }

    /** Returns the active chat snapshot when one has been configured. */
    public Optional<ModelRuntimeSnapshot<ChatModel>> captureChat() {
        return Optional.ofNullable(chat.get());
    }

    public EmbeddingRuntimeSnapshot captureEmbedding() {
        return findEmbedding().orElseThrow(
            () -> new IllegalStateException("No active embedding model runtime snapshot"));
    }

    /** Returns no value while an administrator has not configured an embedding runtime yet. */
    public Optional<EmbeddingRuntimeSnapshot> findEmbedding() {
        return Optional.ofNullable(embedding.get());
    }

    public EmbeddingRuntimeSnapshot requireEmbedding() {
        return captureEmbedding();
    }
}
