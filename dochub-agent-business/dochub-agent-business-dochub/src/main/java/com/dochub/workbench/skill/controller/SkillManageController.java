package com.dochub.workbench.skill.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.auth.data.AdminUserEntity;
import com.dochub.workbench.auth.mapper.AdminUserMapper;
import com.dochub.workbench.auth.support.PasswordHasher;
import com.dochub.workbench.skill.dto.AdminPasswordVerifyDto;
import com.dochub.workbench.skill.dto.SkillIdDto;
import com.dochub.workbench.skill.dto.SkillMarketQueryDto;
import com.dochub.workbench.skill.dto.SkillUsageQueryDto;
import com.dochub.workbench.skill.service.SkillManageService;
import com.dochub.workbench.skill.vo.SkillInstallResultVo;
import com.dochub.workbench.skill.vo.SkillMarketPageVo;
import com.dochub.workbench.skill.vo.SkillUsagePageVo;
import io.swagger.v3.oas.annotations.Operation;
import org.javaup.common.ApiResponse;
import org.javaup.enums.BusinessStatus;
import org.springframework.http.MediaType;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文枢 DocHub 已装技能管理接口。
 */
@RestController
@RequestMapping("/manage/workbench/skill/installed")
public class SkillManageController {

    private final SkillManageService manageService;
    private final AdminUserMapper adminUserMapper;
    private final PasswordHasher passwordHasher;

    public SkillManageController(SkillManageService manageService,
                                 AdminUserMapper adminUserMapper,
                                 PasswordHasher passwordHasher) {
        this.manageService = manageService;
        this.adminUserMapper = adminUserMapper;
        this.passwordHasher = passwordHasher;
    }

    @Operation(summary = "校验管理员密码（任意已启用管理员账号的密码均可，用于查看技能存放位置等敏感信息）")
    @PostMapping("/verify-admin")
    public ApiResponse<Boolean> verifyAdmin(@RequestBody AdminPasswordVerifyDto dto) {
        String input = dto == null ? null : dto.getPassword();
        if (StrUtil.isBlank(input)) {
            return ApiResponse.ok(false);
        }
        List<AdminUserEntity> admins = adminUserMapper.selectList(new LambdaQueryWrapper<AdminUserEntity>()
            .eq(AdminUserEntity::getIsAdmin, 1)
            .eq(AdminUserEntity::getStatus, BusinessStatus.YES.getCode()));
        boolean ok = admins.stream().anyMatch(user -> passwordHasher.matches(input, user.getPasswordHash()));
        return ApiResponse.ok(ok);
    }

    @Operation(summary = "已安装技能列表")
    @PostMapping("/list")
    public ApiResponse<SkillMarketPageVo> list(@RequestBody SkillMarketQueryDto dto) {
        return ApiResponse.ok(manageService.listInstalled(dto));
    }

    @Operation(summary = "启用技能")
    @PostMapping("/enable")
    public ApiResponse<SkillInstallResultVo> enable(@RequestBody SkillIdDto dto) {
        return ApiResponse.ok(manageService.enable(dto));
    }

    @Operation(summary = "停用技能")
    @PostMapping("/disable")
    public ApiResponse<SkillInstallResultVo> disable(@RequestBody SkillIdDto dto) {
        return ApiResponse.ok(manageService.disable(dto));
    }

    @Operation(summary = "删除已安装技能")
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody SkillIdDto dto) {
        manageService.delete(dto);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "上传技能包（SKILL.md 或 .zip），解析入库并启用")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillInstallResultVo> upload(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(manageService.uploadSkill(file));
    }

    @Operation(summary = "技能调用统计")
    @PostMapping("/usage")
    public ApiResponse<SkillUsagePageVo> usage(@RequestBody SkillUsageQueryDto dto) {
        return ApiResponse.ok(manageService.usage(dto));
    }
}
