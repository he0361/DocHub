package com.dochub.workbench.modelconfig.service;

import com.dochub.workbench.modelconfig.dto.EmbeddingMigrationRetryDto;
import com.dochub.workbench.modelconfig.dto.EmbeddingModelChangeDto;
import com.dochub.workbench.modelconfig.dto.EmbeddingRollbackDto;
import com.dochub.workbench.modelconfig.vo.EmbeddingConfigVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingMigrationVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingModelChangeVo;
import com.dochub.workbench.modelconfig.vo.EmbeddingModelTestVo;

public interface EmbeddingModelChangeService {
    EmbeddingConfigVo query(String username);
    EmbeddingModelTestVo test(String username, EmbeddingModelChangeDto dto);
    EmbeddingModelChangeVo change(String username, EmbeddingModelChangeDto dto);
    EmbeddingMigrationVo migrationStatus(String username, Long migrationId);
    EmbeddingMigrationVo retry(String username, EmbeddingMigrationRetryDto dto);
    EmbeddingModelChangeVo rollback(String username, EmbeddingRollbackDto dto);
}
