package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.modelconfig.dto.ModelConfigSaveDto;
import com.dochub.workbench.modelconfig.dto.ModelConfigTestDto;
import com.dochub.workbench.modelconfig.vo.ModelConfigVo;
import com.dochub.workbench.modelconfig.vo.ModelConnectionTestVo;

/** Administrator-only chat model configuration operations. */
public interface ModelConfigService {
    ModelConfigVo queryChat(String username);

    ModelConnectionTestVo testChat(String username, ModelConfigTestDto dto);

    ModelConfigVo saveChat(String username, ModelConfigSaveDto dto);
}
