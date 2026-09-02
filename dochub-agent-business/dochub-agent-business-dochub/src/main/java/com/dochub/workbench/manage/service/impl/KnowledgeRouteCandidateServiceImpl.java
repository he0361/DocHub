package com.dochub.workbench.manage.service.impl;

import com.dochub.workbench.manage.model.classify.ClassificationMaterial;
import com.dochub.workbench.manage.model.classify.RouteCandidate;
import com.dochub.workbench.manage.model.classify.RouteDescriptor;
import com.dochub.workbench.manage.service.KnowledgeRouteCandidateService;
import com.dochub.workbench.manage.support.KnowledgeRouteCanonicalizer;
import org.springframework.stereotype.Service;

import java.util.*;
import java.text.Normalizer;

@Service
public class KnowledgeRouteCandidateServiceImpl implements KnowledgeRouteCandidateService {

    private final KnowledgeRouteCanonicalizer canonicalizer;

    public KnowledgeRouteCandidateServiceImpl(KnowledgeRouteCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    @Override
    public List<RouteCandidate> rankScopes(ClassificationMaterial material, List<RouteDescriptor> routes, int limit) {
        if (routes == null || routes.isEmpty() || limit <= 0) return List.of();
        return routes.stream()
            .filter(Objects::nonNull)
            .map(route -> {
                double identity = Math.max(bestIdentity(material.projectIdentifiers(), route.identifiers()),
                    similarity(material.titleAndSummary(), String.join(" ", route.nameAndAliases())));
                double description = similarity(material.semanticText(), route.descriptionAndExamples());
                double topic = similarity(String.join(" ", material.topics()), route.topicNamesAndAliases());
                double score = clamp(identity * .50 + description * .30 + topic * .20);
                return new RouteCandidate(route, score, 0, score,
                    "标识匹配 " + format(identity) + "，描述匹配 " + format(description) + "，主题匹配 " + format(topic));
            })
            .sorted(Comparator.comparingDouble(RouteCandidate::deterministicScore).reversed()
                .thenComparing(RouteCandidate::routeCode))
            .limit(limit)
            .toList();
    }

    private double bestIdentity(List<String> sources, List<String> targets) {
        double best = 0;
        for (String source : sources) for (String target : targets) {
            String a = canonicalizer.canonicalKey(source);
            String b = canonicalizer.canonicalKey(target);
            if (a.isBlank() || b.isBlank()) continue;
            if (a.equals(b)) best = Math.max(best, 1);
            else if (a.contains(b) || b.contains(a)) best = Math.max(best, .95);
            else best = Math.max(best, dice(a, b));
        }
        return best;
    }

    private double similarity(String left, String right) {
        String a = canonicalizer.canonicalKey(left);
        String b = canonicalizer.canonicalKey(right);
        if (a.isBlank() || b.isBlank()) return 0;
        if (a.equals(b)) return 1;
        if (a.contains(b) || b.contains(a)) return .9;
        Set<String> aParts = parts(left);
        Set<String> bParts = parts(right);
        long overlap = aParts.stream().filter(bParts::contains).count();
        double token = aParts.isEmpty() || bParts.isEmpty() ? 0 : (2d * overlap) / (aParts.size() + bParts.size());
        return Math.max(token, dice(a, b));
    }

    private Set<String> parts(String text) {
        String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        LinkedHashSet<String> values = new LinkedHashSet<>(Arrays.asList(normalized.split("[^\\p{L}\\p{N}]+")));
        String compact = canonicalizer.canonicalKey(normalized);
        for (int i = 0; i + 1 < compact.length(); i++) values.add(compact.substring(i, i + 2));
        values.removeIf(String::isBlank);
        return values;
    }

    private double dice(String a, String b) {
        Set<String> x = grams(a), y = grams(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        long overlap = x.stream().filter(y::contains).count();
        return 2d * overlap / (x.size() + y.size());
    }

    private Set<String> grams(String value) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (value.length() == 1) values.add(value);
        for (int i = 0; i + 1 < value.length(); i++) values.add(value.substring(i, i + 2));
        return values;
    }

    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }
    private String format(double value) { return String.format(Locale.ROOT, "%.2f", value); }
}
