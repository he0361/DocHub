package com.dochub.workbench.auth.dto;

import lombok.Data;

/**
 * 新增/编辑控制台账号请求。
 */
@Data
public class AdminUserSaveDto {

    /** 新增为空；编辑时必填 */
    private Long id;

    private String username;

    /** 新增必填；编辑时为空则不修改密码 */
    private String password;

    private String displayName;

    /** 是否管理员 */
    private Boolean isAdmin;

    /** 逗号分隔的权限码 */
    private String permissions;
}
