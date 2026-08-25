package com.dochub.workbench.skill.dto;

import lombok.Data;

/**
 * 管理员密码校验请求（用于查看技能存放位置等敏感信息）。
 */
@Data
public class AdminPasswordVerifyDto {

    private String password;
}
