package com.dochub.workbench.manage.model.classify;

import java.util.List;

public record RouteDescriptor(String routeCode, String routeName, List<String> aliases, String description,
                              List<String> examples, List<String> topicNames, List<String> documentEvidence) {
    public RouteDescriptor {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        examples = examples == null ? List.of() : List.copyOf(examples);
        topicNames = topicNames == null ? List.of() : List.copyOf(topicNames);
        documentEvidence = documentEvidence == null ? List.of() : List.copyOf(documentEvidence);
    }

    public List<String> identifiers() { return join(routeCode, routeName, aliases); }
    public List<String> nameAndAliases() { return join(routeName, routeCode, aliases); }
    public String descriptionAndExamples() { return safe(description) + " " + String.join(" ", examples) + " " + String.join(" ", documentEvidence); }
    public List<String> semanticEvidence() {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (!safe(description).isBlank()) values.add(description);
        examples.stream().filter(value -> !safe(value).isBlank()).forEach(values::add);
        documentEvidence.stream().filter(value -> !safe(value).isBlank()).forEach(values::add);
        if (values.isEmpty()) values.add("");
        return List.copyOf(values);
    }
    public String topicNamesAndAliases() { return String.join(" ", topicNames); }
    public String searchableText() { return String.join(" ", identifiers()) + " " + descriptionAndExamples() + " " + topicNamesAndAliases(); }

    private static List<String> join(String first, String second, List<String> rest) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        if (!safe(first).isBlank()) values.add(first);
        if (!safe(second).isBlank()) values.add(second);
        values.addAll(rest);
        return List.copyOf(values);
    }
    private static String safe(String value) { return value == null ? "" : value; }
}
