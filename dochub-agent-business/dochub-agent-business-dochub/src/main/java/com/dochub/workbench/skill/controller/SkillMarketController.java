package com.dochub.workbench.skill.controller;

import com.dochub.workbench.skill.dto.SkillIdDto;
import com.dochub.workbench.skill.dto.SkillMarketQueryDto;
import com.dochub.workbench.skill.service.SkillManageService;
import com.dochub.workbench.skill.service.SkillMarketService;
import com.dochub.workbench.skill.vo.SkillDetailVo;
import com.dochub.workbench.skill.vo.SkillInstallResultVo;
import com.dochub.workbench.skill.vo.SkillMarketPageVo;
import io.swagger.v3.oas.annotations.Operation;
import org.javaup.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文枢 DocHub 技能市场接口。
 */
@RestController
@RequestMapping("/manage/workbench/skill/market")
public class SkillMarketController {

    private final SkillMarketService marketService;
    private final SkillManageService manageService;

    public SkillMarketController(SkillMarketService marketService, SkillManageService manageService) {
        this.marketService = marketService;
        this.manageService = manageService;
    }

    @Operation(summary = "技能市场分页浏览/搜索")
    @PostMapping("/list")
    public ApiResponse<SkillMarketPageVo> list(@RequestBody SkillMarketQueryDto dto) {
        return ApiResponse.ok(marketService.listMarket(dto));
    }

    @Operation(summary = "技能详情")
    @PostMapping("/detail")
    public ApiResponse<SkillDetailVo> detail(@RequestBody SkillIdDto dto) {
        return ApiResponse.ok(marketService.detail(dto));
    }

    @Operation(summary = "安装技能")
    @PostMapping("/install")
    public ApiResponse<SkillInstallResultVo> install(@RequestBody SkillIdDto dto) {
        return ApiResponse.ok(manageService.install(dto));
    }
}
