package com.dochub.workbench.modelconfig.vo;

import java.util.List;

/** Result of a candidate connection test; it never contains credentials. */
public record ModelConnectionTestVo(boolean success, String message, List<String> rejectedReasoningPatterns) {
}
