package com.dochub.workbench.manage.support;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class KnowledgeRouteCanonicalizer {

    public String canonicalKey(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[\\p{P}\\p{S}\\s]+", "")
            .trim();
    }
}
