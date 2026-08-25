package com.dochub.workbench.auth.dto;

import lombok.Data;

/**
 * 停用/启用账号请求。
 */
@Data
public class AdminUserStatusDto {

    private Long id;

    /** 1=启用，0=停用 */
    private Integer status;
}
