package com.dochub.workbench.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * 控制台账号信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserItemVo {

    private Long id;

    private String username;

    private String displayName;

    private Boolean isAdmin;

    private List<String> permissions;

    private Integer status;

    private Date createTime;
}
