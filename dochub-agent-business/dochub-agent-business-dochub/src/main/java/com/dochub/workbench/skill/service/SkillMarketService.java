package com.dochub.workbench.skill.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dochub.workbench.skill.data.DocSkillEntity;
import com.dochub.workbench.skill.dto.SkillIdDto;
import com.dochub.workbench.skill.dto.SkillMarketQueryDto;
import com.dochub.workbench.skill.mapper.DocSkillMapper;
import com.dochub.workbench.skill.model.SkillDefinition;
import com.dochub.workbench.skill.registry.SkillRegistry;
import com.dochub.workbench.skill.vo.SkillDetailVo;
import com.dochub.workbench.skill.vo.SkillMarketItemVo;
import com.dochub.workbench.skill.vo.SkillMarketPageVo;
import org.javaup.enums.BusinessStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 文枢 DocHub 技能市场服务：浏览/搜索/详情。
 */
@Service
public class SkillMarketService {

    private final SkillRegistry registry;
    private final DocSkillMapper docSkillMapper;

    public SkillMarketService(SkillRegistry registry, DocSkillMapper docSkillMapper) {
        this.registry = registry;
        this.docSkillMapper = docSkillMapper;
    }

    public SkillMarketPageVo listMarket(SkillMarketQueryDto query) {
        List<SkillDefinition> skills = registry.all();
        if (skills.isEmpty()) {
            return new SkillMarketPageVo(1, 10, 0L, List.of());
        }
        Set<String> installedNames = loadInstalledNames();

        List<SkillMarketItemVo> items = new ArrayList<>();
        for (SkillDefinition skill : skills) {
            if (StrUtil.isNotBlank(query.getKeyword()) && !matchesKeyword(skill, query.getKeyword())) {
                continue;
            }
            if (StrUtil.isNotBlank(query.getCategory()) && !query.getCategory().equals(skill.getCategory())) {
                continue;
            }
            boolean installed = installedNames.contains(skill.getName());
            items.add(toItemVo(skill, installed));
        }
        items.sort((left, right) -> {
            int installedOrder = Boolean.compare(left.isInstalled(), right.isInstalled());
            if (installedOrder != 0) {
                return -installedOrder;
            }
            return StrUtil.compare(left.getSkillName(), right.getSkillName(), true);
        });

        int pageNo = query.getPageNo() == null || query.getPageNo() <= 0 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 10 : query.getPageSize();
        int from = Math.min((pageNo - 1) * pageSize, items.size());
        int to = Math.min(from + pageSize, items.size());
        return new SkillMarketPageVo(pageNo, pageSize, (long) items.size(), List.copyOf(items.subList(from, to)));
    }

    public SkillDetailVo detail(SkillIdDto dto) {
        SkillDefinition skill = registry.get(dto.getSkillName());
        if (skill == null) {
            return null;
        }
        return new SkillDetailVo(skill.getName(), skill.getDisplayName(), skill.getVersion(), skill.getDescription(),
            skill.getWhenToUse(), skill.getInstructions(), skill.getCategory(), skill.getTags(), null,
            skill.getSkillType(), skill.getSourceType(), skill.getObjectPrefix(), skill.getRunState(),
            skill.isScriptExecEnabled());
    }

    private Set<String> loadInstalledNames() {
        return docSkillMapper.selectList(new LambdaQueryWrapper<DocSkillEntity>()
                .eq(DocSkillEntity::getStatus, BusinessStatus.YES.getCode()))
            .stream()
            .map(DocSkillEntity::getSkillName)
            .collect(Collectors.toSet());
    }

    private boolean matchesKeyword(SkillDefinition skill, String keyword) {
        String normalized = keyword.trim().toLowerCase();
        return StrUtil.containsIgnoreCase(skill.getName(), normalized)
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(skill.getDisplayName()), normalized)
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(skill.getDescription()), normalized)
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(skill.getCategory()), normalized)
            || StrUtil.containsIgnoreCase(StrUtil.nullToEmpty(skill.getTags()), normalized);
    }

    private SkillMarketItemVo toItemVo(SkillDefinition skill, boolean installed) {
        return new SkillMarketItemVo(skill.getName(), skill.getDisplayName(), skill.getVersion(), skill.getDescription(),
            skill.getCategory(), skill.getTags(), skill.getSkillType(), skill.getRunState(), installed, null);
    }
}
