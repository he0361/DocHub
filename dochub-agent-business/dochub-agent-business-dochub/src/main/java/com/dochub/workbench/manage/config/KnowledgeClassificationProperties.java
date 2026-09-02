package com.dochub.workbench.manage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "dochub.knowledge.classification")
public class KnowledgeClassificationProperties {
    private int lexicalLimit = 10;
    private int semanticLimit = 10;
    private int rerankLimit = 8;
    private Threshold scope = new Threshold();
    private Threshold topic = new Threshold();

    @Data
    public static class Threshold {
        private double automaticReuse = .82;
        private double reviewLowerBound = .55;
        private double maximumExistingForAutomaticNew = .42;
        private double llmNewConfidence = .92;
        private double validatorConfidence = .90;
        private double minimumEvidenceMargin = .15;
    }
}
