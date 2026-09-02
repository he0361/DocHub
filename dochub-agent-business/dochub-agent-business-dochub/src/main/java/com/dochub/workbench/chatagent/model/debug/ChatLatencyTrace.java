package com.dochub.workbench.chatagent.model.debug;

import java.util.Map;

/** Immutable per-exchange latency and amplification snapshot. */
public record ChatLatencyTrace(
    Long timeToFirstTokenMs,
    Long totalDurationMs,
    int modelCallCount,
    int toolCallCount,
    int promptCharacters,
    long modelConfigVersion,
    Map<String, Long> stageTimingsMs,
    String terminalState
) {
}
