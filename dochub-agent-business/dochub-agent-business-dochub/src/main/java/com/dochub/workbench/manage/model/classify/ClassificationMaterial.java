package com.dochub.workbench.manage.model.classify;

import java.util.List;

public record ClassificationMaterial(String documentName, String summary, List<String> projectIdentifiers,
                                     List<String> topics, String semanticText) {
    public ClassificationMaterial {
        documentName = safe(documentName);
        summary = safe(summary);
        projectIdentifiers = projectIdentifiers == null ? List.of() : List.copyOf(projectIdentifiers);
        topics = topics == null ? List.of() : List.copyOf(topics);
        semanticText = safe(semanticText);
    }

    public String titleAndSummary() { return documentName + " " + summary; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
