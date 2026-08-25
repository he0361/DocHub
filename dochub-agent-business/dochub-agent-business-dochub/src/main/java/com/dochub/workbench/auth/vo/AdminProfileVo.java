package com.dochub.workbench.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 文枢 DocHub 控制台当前登录账号信息（含权限）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminProfileVo {

    private String username;

    private String displayName;

    /** 是否管理员 */
    private Boolean isAdmin;

    /** 权限码列表（管理员返回全部） */
    private List<String> permissions;
}
