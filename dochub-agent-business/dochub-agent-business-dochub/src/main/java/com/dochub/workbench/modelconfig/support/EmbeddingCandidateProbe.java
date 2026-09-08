package com.dochub.workbench.modelconfig.support;

import com.dochub.workbench.modelconfig.model.ModelRuntimeSpec;
import com.dochub.workbench.modelconfig.runtime.OpenAiCompatibleModelFactory;
import org.javaup.exception.DochubFrameException;
import org.springframework.ai.embedding.EmbeddingModel;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Probes the backend-visible endpoint twice and rejects unstable/non-finite vectors. */
public final class EmbeddingCandidateProbe {
    private final Function<ModelRuntimeSpec, EmbeddingModel> modelFactory;

    public EmbeddingCandidateProbe(OpenAiCompatibleModelFactory factory) {
        this(factory::embeddingModel);
    }

    public EmbeddingCandidateProbe(Function<ModelRuntimeSpec, EmbeddingModel> modelFactory) {
        this.modelFactory = Objects.requireNonNull(modelFactory);
    }

    public Result test(ModelRuntimeSpec spec) {
        EmbeddingModel model = modelFactory.apply(spec);
        float[] first = one(model.embed(List.of("DocHub embedding connection probe")));
        float[] second = one(model.embed(List.of("DocHub embedding stability probe")));
        if (first.length == 0 || first.length != second.length) {
            throw new DochubFrameException(400, "向量维度测试不稳定");
        }
        for (float value : first) if (!Float.isFinite(value)) throw new DochubFrameException(400, "向量包含非有限数值");
        for (float value : second) if (!Float.isFinite(value)) throw new DochubFrameException(400, "向量包含非有限数值");
        return new Result(model, first.length);
    }

    private float[] one(List<float[]> result) {
        if (result == null || result.size() != 1 || result.get(0) == null) {
            throw new DochubFrameException(400, "向量连接测试未返回有效结果");
        }
        return result.get(0);
    }

    public record Result(EmbeddingModel model, int dimension) { }
}
