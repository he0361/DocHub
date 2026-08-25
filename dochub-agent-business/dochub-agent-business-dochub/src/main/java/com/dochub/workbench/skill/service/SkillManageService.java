package com.dochub.workbench.skill.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dochub.workbench.skill.constant.SkillCode;
import com.dochub.workbench.skill.data.DocSkillEntity;
import com.dochub.workbench.skill.data.DocSkillUsageEntity;
import com.dochub.workbench.skill.dto.SkillIdDto;
import com.dochub.workbench.skill.dto.SkillMarketQueryDto;
import com.dochub.workbench.skill.dto.SkillUsageQueryDto;
import com.dochub.workbench.skill.loader.SkillFrontMatterParser;
import com.dochub.workbench.skill.mapper.DocSkillMapper;
import com.dochub.workbench.skill.mapper.DocSkillUsageMapper;
import com.dochub.workbench.skill.model.SkillDefinition;
import com.dochub.workbench.skill.registry.SkillRegistry;
import com.dochub.workbench.skill.vo.SkillInstallResultVo;
import com.dochub.workbench.skill.vo.SkillMarketItemVo;
import com.dochub.workbench.skill.vo.SkillMarketPageVo;
import com.dochub.workbench.skill.vo.SkillUsageItemVo;
import com.dochub.workbench.skill.vo.SkillUsagePageVo;
import lombok.extern.slf4j.Slf4j;
import org.javaup.enums.BusinessStatus;
import org.javaup.exception.DochubFrameException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文枢 DocHub 技能管理服务：安装/启停/删除/调用统计。
 */
@Slf4j
@Service
public class SkillManageService {

    private final SkillRegistry registry;
    private final DocSkillMapper docSkillMapper;
    private final DocSkillUsageMapper usageMapper;
    private final UidGenerator uidGenerator;
    private final SkillFrontMatterParser frontMatterParser;

    public SkillManageService(SkillRegistry registry,
                              DocSkillMapper docSkillMapper,
                              DocSkillUsageMapper usageMapper,
                              UidGenerator uidGenerator,
                              SkillFrontMatterParser frontMatterParser) {
        this.registry = registry;
        this.docSkillMapper = docSkillMapper;
        this.usageMapper = usageMapper;
        this.uidGenerator = uidGenerator;
        this.frontMatterParser = frontMatterParser;
    }

    public SkillInstallResultVo install(SkillIdDto dto) {
        String skillName = requireSkillName(dto);
        SkillDefinition definition = registry.get(skillName);
        if (definition == null) {
            throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "技能不存在: " + skillName);
        }
        // 已存在（含软删除 status=0）则复活/更新；不存在则新增
        DocSkillEntity existing = findByName(skillName);
        DocSkillEntity entity;
        if (existing != null) {
            entity = existing;
        }
        else {
            entity = new DocSkillEntity();
            entity.setId(uidGenerator.getUid());
            entity.setInstallCount(1);
        }
        entity.setSkillName(definition.getName());
        entity.setDisplayName(definition.getDisplayName());
        entity.setVersion(StrUtil.blankToDefault(definition.getVersion(), "1.0.0"));
        entity.setDescription(definition.getDescription());
        entity.setWhenToUse(definition.getWhenToUse());
        entity.setInstructions(definition.getInstructions());
        entity.setCategory(definition.getCategory());
        entity.setTags(definition.getTags());
        entity.setSkillType(definition.getSkillType());
        entity.setSourceType(definition.getSourceType());
        entity.setObjectPrefix(definition.getObjectPrefix());
        entity.setScriptExecEnabled(definition.isScriptExecEnabled() ? 1 : 0);
        entity.setRunState(1);
        entity.setStatus(BusinessStatus.YES.getCode());
        if (existing != null) {
            docSkillMapper.updateById(entity);
        }
        else {
            docSkillMapper.insert(entity);
        }
        definition.setId(entity.getId());
        definition.setRunState(1);
        registry.register(definition);
        log.info("技能安装成功: {}", skillName);
        return new SkillInstallResultVo(skillName, displayName(definition), 1);
    }

    /**
     * 上传用户下载好的技能（SKILL.md 或 .zip 压缩包），解析后入库并注册，之后与内置技能一样可启停/删除/触发。
     */
    public SkillInstallResultVo uploadSkill(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "请上传技能文件（SKILL.md 或 .zip 压缩包）");
        }
        String originalName = StrUtil.blankToDefault(file.getOriginalFilename(), "skill");
        String lowerName = originalName.toLowerCase();
        String content;
        String skillName;
        try {
            if (lowerName.endsWith(".zip")) {
                String[] extracted = extractSkillFromZip(file.getInputStream());
                content = extracted[0];
                skillName = extracted[1];
            }
            else if (lowerName.endsWith(".md")) {
                content = new String(file.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                skillName = stripExtension(originalName);
            }
            else {
                throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "不支持的文件类型，请上传 .md 或 .zip");
            }
        }
        catch (DochubFrameException exception) {
            throw exception;
        }
        catch (Exception exception) {
            throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "读取技能文件失败: " + exception.getMessage(), exception);
        }
        if (StrUtil.isBlank(content) || StrUtil.isBlank(skillName)) {
            throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "技能文件内容为空");
        }

        SkillDefinition definition = frontMatterParser.parse(skillName, content);
        if (StrUtil.isBlank(definition.getName())) {
            throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "技能 frontmatter 缺少 name");
        }
        fillUploadDefaults(definition);

        // 同名技能已存在则覆盖更新，否则新增；上传即启用
        DocSkillEntity existing = findByName(definition.getName());
        DocSkillEntity entity;
        if (existing != null) {
            entity = existing;
        }
        else {
            entity = new DocSkillEntity();
            entity.setId(uidGenerator.getUid());
            entity.setInstallCount(1);
        }
        // 无条件恢复有效状态（覆盖重传已被软删除/停用的同名技能）
        entity.setStatus(BusinessStatus.YES.getCode());
        entity.setSkillName(definition.getName());
        entity.setDisplayName(definition.getDisplayName());
        entity.setVersion(definition.getVersion());
        entity.setDescription(definition.getDescription());
        entity.setWhenToUse(definition.getWhenToUse());
        entity.setInstructions(definition.getInstructions());
        entity.setCategory(definition.getCategory());
        entity.setTags(definition.getTags());
        entity.setSkillType("UPLOAD");
        entity.setSourceType("upload");
        entity.setObjectPrefix("upload/" + definition.getName() + "/");
        entity.setScriptExecEnabled(definition.isScriptExecEnabled() ? 1 : 0);
        entity.setRunState(1);
        if (existing != null) {
            docSkillMapper.updateById(entity);
        }
        else {
            docSkillMapper.insert(entity);
        }
        definition.setId(entity.getId());
        definition.setRunState(1);
        registry.register(definition);
        log.info("技能上传成功并启用: {}", definition.getName());
        return new SkillInstallResultVo(definition.getName(), definition.getDisplayName(), 1);
    }

    /**
     * 从 .zip 压缩包中查找 SKILL.md 并提取其内容，技能名取所在目录名。
     */
    private String[] extractSkillFromZip(InputStream inputStream) throws Exception {
        try (ZipInputStream zipInputStream = new ZipInputStream(inputStream, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().toLowerCase().endsWith("skill.md")) {
                    String content = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                    return new String[]{content, deriveZipSkillName(entry.getName())};
                }
            }
        }
        throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "压缩包中未找到 SKILL.md");
    }

    private String deriveZipSkillName(String entryName) {
        String normalized = entryName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String parent = slash >= 0 ? normalized.substring(0, slash) : "";
        int parentSlash = parent.lastIndexOf('/');
        return parentSlash >= 0 ? parent.substring(parentSlash + 1) : parent;
    }

    /**
     * 补齐上传技能缺少的文枢字段，保证能被自动路由匹配到。
     */
    private void fillUploadDefaults(SkillDefinition definition) {
        if (StrUtil.isBlank(definition.getWhenToUse())) {
            definition.setWhenToUse("用户明确提到「" + definition.getName() + "」或需要该类能力时使用");
        }
        if (StrUtil.isBlank(definition.getCategory())) {
            definition.setCategory("upload");
        }
        if (StrUtil.isBlank(definition.getTags())) {
            definition.setTags(definition.getName());
        }
        if (StrUtil.isBlank(definition.getDisplayName())) {
            definition.setDisplayName(definition.getName());
        }
    }

    private String stripExtension(String fileName) {
        if (StrUtil.isBlank(fileName)) {
            return "skill";
        }
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    public SkillInstallResultVo enable(SkillIdDto dto) {
        String skillName = requireSkillName(dto);
        DocSkillEntity entity = requireInstalled(skillName);
        entity.setRunState(1);
        docSkillMapper.updateById(entity);
        SkillDefinition definition = registry.get(skillName);
        if (definition == null) {
            definition = toDefinition(entity);
        }
        definition.setRunState(1);
        registry.register(definition);
        return new SkillInstallResultVo(skillName, entity.getDisplayName(), 1);
    }

    public SkillInstallResultVo disable(SkillIdDto dto) {
        String skillName = requireSkillName(dto);
        DocSkillEntity entity = requireInstalled(skillName);
        entity.setRunState(2);
        docSkillMapper.updateById(entity);
        registry.unregister(skillName);
        return new SkillInstallResultVo(skillName, entity.getDisplayName(), 2);
    }

    public void delete(SkillIdDto dto) {
        String skillName = requireSkillName(dto);
        DocSkillEntity entity = requireInstalled(skillName);
        entity.setStatus(BusinessStatus.NO.getCode());
        docSkillMapper.updateById(entity);
        registry.unregister(skillName);
    }

    public SkillMarketPageVo listInstalled(SkillMarketQueryDto query) {
        int pageNo = query.getPageNo() == null || query.getPageNo() <= 0 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 10 : query.getPageSize();
        LambdaQueryWrapper<DocSkillEntity> wrapper = new LambdaQueryWrapper<DocSkillEntity>()
            .eq(DocSkillEntity::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DocSkillEntity::getEditTime, DocSkillEntity::getId);
        if (StrUtil.isNotBlank(query.getKeyword())) {
            wrapper.and(w -> w.like(DocSkillEntity::getSkillName, query.getKeyword())
                .or().like(DocSkillEntity::getDisplayName, query.getKeyword())
                .or().like(DocSkillEntity::getDescription, query.getKeyword()));
        }
        IPage<DocSkillEntity> page = docSkillMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<SkillMarketItemVo> records = page.getRecords().stream()
            .map(entity -> new SkillMarketItemVo(entity.getSkillName(), entity.getDisplayName(), entity.getVersion(),
                entity.getDescription(), entity.getCategory(), entity.getTags(), entity.getSkillType(),
                entity.getRunState(), true, entity.getInstallCount()))
            .toList();
        return new SkillMarketPageVo(pageNo, pageSize, page.getTotal(), records);
    }

    public SkillUsagePageVo usage(SkillUsageQueryDto query) {
        int pageNo = query.getPageNo() == null || query.getPageNo() <= 0 ? 1 : query.getPageNo();
        int pageSize = query.getPageSize() == null || query.getPageSize() <= 0 ? 10 : query.getPageSize();
        LambdaQueryWrapper<DocSkillUsageEntity> wrapper = new LambdaQueryWrapper<DocSkillUsageEntity>()
            .eq(DocSkillUsageEntity::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(DocSkillUsageEntity::getCreateTime, DocSkillUsageEntity::getId);
        IPage<DocSkillUsageEntity> page = usageMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        List<DocSkillUsageEntity> records = page.getRecords();

        Map<Long, String> skillNameMap = records.isEmpty() ? Map.of()
            : docSkillMapper.selectBatchIds(records.stream().map(DocSkillUsageEntity::getSkillId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(DocSkillEntity::getId, DocSkillEntity::getSkillName, (a, b) -> a));
        List<SkillUsageItemVo> items = records.stream()
            .map(record -> new SkillUsageItemVo(
                skillNameMap.getOrDefault(record.getSkillId(), String.valueOf(record.getSkillId())),
                record.getConversationId(), record.getScene(), record.getMatchedScore(),
                record.getMatchedReason(), record.getCreateTime()))
            .toList();
        return new SkillUsagePageVo(pageNo, pageSize, page.getTotal(), items);
    }

    private DocSkillEntity requireInstalled(String skillName) {
        DocSkillEntity entity = findByName(skillName);
        if (entity == null || !BusinessStatus.YES.getCode().equals(entity.getStatus())) {
            throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "技能未安装: " + skillName);
        }
        return entity;
    }

    private DocSkillEntity findByName(String skillName) {
        return docSkillMapper.selectOne(new LambdaQueryWrapper<DocSkillEntity>()
            .eq(DocSkillEntity::getSkillName, skillName)
            .last("LIMIT 1"));
    }

    private String requireSkillName(SkillIdDto dto) {
        if (dto == null || StrUtil.isBlank(dto.getSkillName())) {
            throw new DochubFrameException(SkillCode.SKILL_NOT_FOUND.getCode(), "技能名称不能为空");
        }
        return dto.getSkillName().trim();
    }

    private String displayName(SkillDefinition definition) {
        return StrUtil.isNotBlank(definition.getDisplayName()) ? definition.getDisplayName() : definition.getName();
    }

    private SkillDefinition toDefinition(DocSkillEntity entity) {
        return SkillDefinition.builder()
            .id(entity.getId())
            .name(entity.getSkillName())
            .displayName(entity.getDisplayName())
            .version(entity.getVersion())
            .description(entity.getDescription())
            .whenToUse(entity.getWhenToUse())
            .instructions(entity.getInstructions())
            .category(entity.getCategory())
            .tags(entity.getTags())
            .skillType(entity.getSkillType())
            .sourceType(entity.getSourceType())
            .objectPrefix(entity.getObjectPrefix())
            .runState(entity.getRunState())
            .scriptExecEnabled(entity.getScriptExecEnabled() != null && entity.getScriptExecEnabled() == 1)
            .build();
    }
}
