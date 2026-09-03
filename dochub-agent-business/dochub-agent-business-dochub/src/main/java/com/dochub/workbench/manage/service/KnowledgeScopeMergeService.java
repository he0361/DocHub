package com.dochub.workbench.manage.service;

import com.dochub.workbench.manage.dto.KnowledgeScopeMergeDto;
import com.dochub.workbench.manage.vo.KnowledgeScopeMergeVo;

public interface KnowledgeScopeMergeService {
    KnowledgeScopeMergeVo merge(String operator, KnowledgeScopeMergeDto dto);
}
