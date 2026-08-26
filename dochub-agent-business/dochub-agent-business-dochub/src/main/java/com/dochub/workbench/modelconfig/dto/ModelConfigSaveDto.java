package com.dochub.workbench.modelconfig.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** Candidate settings that may become the active chat configuration after a successful test. */
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelConfigSaveDto extends ModelConfigTestDto {
    /** Only valid for local deployments; an empty key otherwise retains the currently encrypted key. */
    private Boolean clearApiKey;
}
