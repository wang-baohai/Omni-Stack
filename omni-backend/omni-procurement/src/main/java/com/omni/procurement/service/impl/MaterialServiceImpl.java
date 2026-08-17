package com.omni.procurement.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.procurement.domain.MaterialDomainPolicy;
import com.omni.procurement.dto.MaterialRequests;
import com.omni.procurement.dto.MaterialViews;
import com.omni.procurement.dto.ProcViewAssembler;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.mapper.ProcMaterialCategoryMapper;
import com.omni.procurement.mapper.ProcMaterialMapper;
import com.omni.procurement.security.ProcTenantContext;
import com.omni.procurement.service.MaterialService;
import com.omni.procurement.service.ProcTenantInitializer;
import com.omni.procurement.service.support.ProcAuditSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物料目录服务实现。
 *
 * @author Omni-Stack Team
 */
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private final ProcTenantInitializer tenantInitializer;
    private final ProcMaterialCategoryMapper categoryMapper;
    private final ProcMaterialMapper materialMapper;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<MaterialViews.CategoryVO> listCategories() {
        tenantInitializer.ensureInitialized();
        Long tenantId = ProcTenantContext.requireTenantId();
        List<ProcMaterialCategory> categories = categoryMapper.selectList(
                new LambdaQueryWrapper<ProcMaterialCategory>()
                        .eq(ProcMaterialCategory::getTenantId, tenantId)
                        .orderByAsc(ProcMaterialCategory::getSort)
                        .orderByAsc(ProcMaterialCategory::getId));
        Map<Long, MaterialViews.CategoryVO> byId = new LinkedHashMap<>(Math.max(16, categories.size()));
        for (ProcMaterialCategory category : categories) {
            byId.put(category.getId(), ProcViewAssembler.category(category));
        }
        List<MaterialViews.CategoryVO> roots = new ArrayList<>(categories.size());
        for (ProcMaterialCategory category : categories) {
            MaterialViews.CategoryVO node = byId.get(category.getId());
            if (Long.valueOf(MaterialDomainPolicy.ROOT_PARENT_ID).equals(category.getParentId())) {
                roots.add(node);
            } else {
                MaterialViews.CategoryVO parent = byId.get(category.getParentId());
                if (parent == null) {
                    throw new BusinessException(500, "物料品类树存在孤立节点");
                }
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public MaterialViews.CategoryVO createCategory(MaterialRequests.CreateCategoryRequest request) {
        tenantInitializer.ensureInitialized();
        Long tenantId = ProcTenantContext.requireTenantId();
        String code = MaterialDomainPolicy.normalizeCode(request.getCategoryCode(), "品类编码");
        ProcMaterialCategory parent = Long.valueOf(MaterialDomainPolicy.ROOT_PARENT_ID).equals(request.getParentId())
                ? null : requireLockedCategory(lockCategories(tenantId, request.getParentId()), request.getParentId());
        MaterialDomainPolicy.validateCategoryLevel(request.getParentId(), parent);
        if (findCategoryByCode(tenantId, code) != null) {
            throw new BusinessException(409, "品类编码已存在");
        }
        ProcMaterialCategory category = new ProcMaterialCategory();
        category.setTenantId(tenantId);
        category.setParentId(request.getParentId());
        category.setCategoryCode(code);
        category.setCategoryName(requiredText(request.getCategoryName(), "品类名称"));
        category.setSort(request.getSort());
        category.setStatus(request.getStatus());
        category.setVersion(0);
        category.setDeleted(0);
        ProcAuditSupport.created(category);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "品类编码已存在");
        }
        return ProcViewAssembler.category(category);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public MaterialViews.CategoryVO updateCategory(Long id, MaterialRequests.UpdateCategoryRequest request) {
        Long tenantId = ProcTenantContext.requireTenantId();
        if (id.equals(request.getParentId())) {
            throw new BusinessException(400, "品类不能作为自身父节点");
        }
        ProcMaterialCategory snapshot = requireCategory(tenantId, id);
        Map<Long, ProcMaterialCategory> locked = lockCategories(
                tenantId, id, snapshot.getParentId(), request.getParentId());
        ProcMaterialCategory current = requireLockedCategory(locked, id);
        requireUnchangedCategoryParent(snapshot, current);
        ProcMaterialCategory parent = Long.valueOf(MaterialDomainPolicy.ROOT_PARENT_ID).equals(request.getParentId())
                ? null : requireLockedCategory(locked, request.getParentId());
        MaterialDomainPolicy.validateCategoryLevel(request.getParentId(), parent);
        long childCount = countChildren(tenantId, id, null);
        if (!Long.valueOf(MaterialDomainPolicy.ROOT_PARENT_ID).equals(request.getParentId()) && childCount > 0) {
            throw new BusinessException(409, "包含子品类的品类不能移动到其他品类下");
        }
        if (Integer.valueOf(0).equals(request.getStatus())) {
            requireCategoryCanDeactivate(tenantId, id);
        }
        LambdaUpdateWrapper<ProcMaterialCategory> update = versionedCategory(tenantId, id, request.getVersion())
                .set(ProcMaterialCategory::getParentId, request.getParentId())
                .set(ProcMaterialCategory::getCategoryName, requiredText(request.getCategoryName(), "品类名称"))
                .set(ProcMaterialCategory::getSort, request.getSort())
                .set(ProcMaterialCategory::getStatus, request.getStatus());
        auditCategory(update);
        requireUpdated(categoryMapper.update(null, update), "物料品类已被其他请求修改");
        ProcMaterialCategory updated = requireCategory(tenantId, current.getId());
        return ProcViewAssembler.category(updated);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void deleteCategory(Long id, Integer version) {
        Long tenantId = ProcTenantContext.requireTenantId();
        ProcMaterialCategory snapshot = requireCategory(tenantId, id);
        ProcMaterialCategory current = requireLockedCategory(
                lockCategories(tenantId, id, snapshot.getParentId()), id);
        requireUnchangedCategoryParent(snapshot, current);
        if (countChildren(tenantId, id, null) > 0) {
            throw new BusinessException(409, "存在子品类，不能删除");
        }
        if (countMaterials(tenantId, id, null) > 0) {
            throw new BusinessException(409, "品类下存在物料，不能删除");
        }
        LambdaUpdateWrapper<ProcMaterialCategory> update = versionedCategory(tenantId, id, version)
                .set(ProcMaterialCategory::getDeleted, 1);
        auditCategory(update);
        requireUpdated(categoryMapper.update(null, update), "物料品类已被其他请求修改");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public PageResult<MaterialViews.MaterialVO> page(MaterialRequests.MaterialQuery query) {
        tenantInitializer.ensureInitialized();
        Long tenantId = ProcTenantContext.requireTenantId();
        LambdaQueryWrapper<ProcMaterial> wrapper = new LambdaQueryWrapper<ProcMaterial>()
                .eq(ProcMaterial::getTenantId, tenantId);
        String keyword = MaterialDomainPolicy.trimToNull(query.getKeyword());
        if (keyword != null) {
            wrapper.and(nested -> nested.like(ProcMaterial::getMaterialCode, keyword)
                    .or().like(ProcMaterial::getMaterialName, keyword));
        }
        if (query.getCategoryId() != null) {
            List<Long> leafIds = collectLeafIds(tenantId, query.getCategoryId());
            if (leafIds.size() == 1) {
                wrapper.eq(ProcMaterial::getCategoryId, leafIds.getFirst());
            } else {
                wrapper.in(ProcMaterial::getCategoryId, leafIds);
            }
        }
        if (MaterialDomainPolicy.trimToNull(query.getStatus()) != null) {
            wrapper.eq(ProcMaterial::getStatus, MaterialDomainPolicy.normalizeStatus(query.getStatus()));
        }
        if (query.getAssetManaged() != null) {
            wrapper.eq(ProcMaterial::getAssetManaged, query.getAssetManaged());
        }
        wrapper.orderByDesc(ProcMaterial::getCreateTime).orderByDesc(ProcMaterial::getId);
        Page<ProcMaterial> page = materialMapper.selectPage(new Page<>(query.getPage(), query.getSize()), wrapper);
        Map<Long, ProcMaterialCategory> categories = categoriesByIds(tenantId,
                page.getRecords().stream().map(ProcMaterial::getCategoryId).toList());
        List<MaterialViews.MaterialVO> records = page.getRecords().stream()
                .map(material -> ProcViewAssembler.material(material, categories.get(material.getCategoryId())))
                .toList();
        return new PageResult<>(records, page.getTotal(), page.getSize(), page.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public MaterialViews.MaterialVO get(Long id) {
        Long tenantId = ProcTenantContext.requireTenantId();
        ProcMaterial material = requireMaterial(tenantId, id);
        return ProcViewAssembler.material(material, findCategory(tenantId, material.getCategoryId()));
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public MaterialViews.MaterialVO create(MaterialRequests.CreateMaterialRequest request) {
        tenantInitializer.ensureInitialized();
        Long tenantId = ProcTenantContext.requireTenantId();
        ProcMaterialCategory category = requireLockedCategory(
                lockCategories(tenantId, request.getCategoryId()), request.getCategoryId());
        MaterialDomainPolicy.requireActiveCategory(category);
        MaterialDomainPolicy.requireLeafCategory(countChildren(tenantId, request.getCategoryId(), null) > 0);
        String materialCode = MaterialDomainPolicy.normalizeCode(request.getMaterialCode(), "物料编码");
        String unit = MaterialDomainPolicy.normalizeUnit(request.getUnit());
        MaterialDomainPolicy.validateAssetManaged(request.getAssetManaged(), unit);
        if (findMaterialByCode(tenantId, materialCode) != null) {
            throw new BusinessException(409, "物料编码已存在");
        }
        ProcMaterial material = new ProcMaterial();
        material.setTenantId(tenantId);
        material.setCategoryId(category.getId());
        material.setMaterialCode(materialCode);
        material.setMaterialName(requiredText(request.getMaterialName(), "物料名称"));
        material.setSpecification(MaterialDomainPolicy.trimToNull(request.getSpecification()));
        material.setUnit(unit);
        material.setAssetManaged(request.getAssetManaged());
        material.setStatus(MaterialDomainPolicy.normalizeStatus(request.getStatus()));
        material.setVersion(0);
        material.setDeleted(0);
        ProcAuditSupport.created(material);
        try {
            materialMapper.insert(material);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "物料编码已存在");
        }
        return ProcViewAssembler.material(material, category);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public MaterialViews.MaterialVO update(Long id, MaterialRequests.UpdateMaterialRequest request) {
        Long tenantId = ProcTenantContext.requireTenantId();
        ProcMaterial snapshot = requireMaterial(tenantId, id);
        Map<Long, ProcMaterialCategory> lockedCategories = lockCategories(
                tenantId, snapshot.getCategoryId(), request.getCategoryId());
        ProcMaterial current = materialMapper.selectForUpdate(tenantId, id);
        if (current == null) {
            throw new BusinessException(404, "物料不存在");
        }
        if (!Objects.equals(snapshot.getCategoryId(), current.getCategoryId())) {
            throw new BusinessException(409, "物料品类已被其他请求修改");
        }
        ProcMaterialCategory category = requireLockedCategory(lockedCategories, request.getCategoryId());
        MaterialDomainPolicy.requireActiveCategory(category);
        MaterialDomainPolicy.requireLeafCategory(countChildren(tenantId, request.getCategoryId(), null) > 0);
        String unit = MaterialDomainPolicy.normalizeUnit(request.getUnit());
        MaterialDomainPolicy.validateAssetManaged(request.getAssetManaged(), unit);
        LambdaUpdateWrapper<ProcMaterial> update = versionedMaterial(tenantId, id, request.getVersion())
                .set(ProcMaterial::getCategoryId, category.getId())
                .set(ProcMaterial::getMaterialName, requiredText(request.getMaterialName(), "物料名称"))
                .set(ProcMaterial::getSpecification, MaterialDomainPolicy.trimToNull(request.getSpecification()))
                .set(ProcMaterial::getUnit, unit)
                .set(ProcMaterial::getAssetManaged, request.getAssetManaged())
                .set(ProcMaterial::getStatus, MaterialDomainPolicy.normalizeStatus(request.getStatus()));
        auditMaterial(update);
        requireUpdated(materialMapper.update(null, update), "物料已被其他请求修改");
        return get(id);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void delete(Long id, Integer version) {
        Long tenantId = ProcTenantContext.requireTenantId();
        requireMaterial(tenantId, id);
        LambdaUpdateWrapper<ProcMaterial> update = versionedMaterial(tenantId, id, version)
                .set(ProcMaterial::getDeleted, 1);
        auditMaterial(update);
        requireUpdated(materialMapper.update(null, update), "物料已被其他请求修改");
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ProcMaterial requireActiveForRequisition(Long id) {
        Long tenantId = ProcTenantContext.requireTenantId();
        ProcMaterial material = requireMaterial(tenantId, id);
        MaterialDomainPolicy.requireActiveMaterial(material);
        MaterialDomainPolicy.requireActiveCategory(requireCategory(tenantId, material.getCategoryId()));
        return material;
    }

    private void requireCategoryCanDeactivate(Long tenantId, Long categoryId) {
        if (countChildren(tenantId, categoryId, 1) > 0) {
            throw new BusinessException(409, "存在启用的子品类，不能停用品类");
        }
        if (countMaterials(tenantId, categoryId, MaterialDomainPolicy.ACTIVE) > 0) {
            throw new BusinessException(409, "存在启用物料，不能停用品类");
        }
    }

    private long countChildren(Long tenantId, Long parentId, Integer status) {
        LambdaQueryWrapper<ProcMaterialCategory> wrapper = new LambdaQueryWrapper<ProcMaterialCategory>()
                .eq(ProcMaterialCategory::getTenantId, tenantId)
                .eq(ProcMaterialCategory::getParentId, parentId);
        if (status != null) {
            wrapper.eq(ProcMaterialCategory::getStatus, status);
        }
        return categoryMapper.selectCount(wrapper);
    }

    private long countMaterials(Long tenantId, Long categoryId, String status) {
        LambdaQueryWrapper<ProcMaterial> wrapper = new LambdaQueryWrapper<ProcMaterial>()
                .eq(ProcMaterial::getTenantId, tenantId)
                .eq(ProcMaterial::getCategoryId, categoryId);
        if (status != null) {
            wrapper.eq(ProcMaterial::getStatus, status);
        }
        return materialMapper.selectCount(wrapper);
    }

    private ProcMaterialCategory requireCategory(Long tenantId, Long id) {
        ProcMaterialCategory category = findCategory(tenantId, id);
        if (category == null) {
            throw new BusinessException(404, "物料品类不存在");
        }
        return category;
    }

    private Map<Long, ProcMaterialCategory> lockCategories(Long tenantId, Long... categoryIds) {
        Map<Long, ProcMaterialCategory> locked = new LinkedHashMap<>();
        Arrays.stream(categoryIds)
                .filter(Objects::nonNull)
                .filter(id -> !Long.valueOf(MaterialDomainPolicy.ROOT_PARENT_ID).equals(id))
                .distinct()
                .sorted()
                .forEach(id -> locked.put(id, categoryMapper.selectForUpdate(tenantId, id)));
        return locked;
    }

    private ProcMaterialCategory requireLockedCategory(Map<Long, ProcMaterialCategory> locked, Long id) {
        ProcMaterialCategory category = locked.get(id);
        if (category == null) {
            throw new BusinessException(404, "物料品类不存在");
        }
        return category;
    }

    private void requireUnchangedCategoryParent(ProcMaterialCategory snapshot,
                                                ProcMaterialCategory current) {
        if (!Objects.equals(snapshot.getParentId(), current.getParentId())) {
            throw new BusinessException(409, "物料品类父节点已被其他请求修改");
        }
    }

    private ProcMaterialCategory findCategory(Long tenantId, Long id) {
        return categoryMapper.selectOne(new LambdaQueryWrapper<ProcMaterialCategory>()
                .eq(ProcMaterialCategory::getTenantId, tenantId)
                .eq(ProcMaterialCategory::getId, id));
    }

    private ProcMaterialCategory findCategoryByCode(Long tenantId, String code) {
        return categoryMapper.selectOne(new LambdaQueryWrapper<ProcMaterialCategory>()
                .eq(ProcMaterialCategory::getTenantId, tenantId)
                .eq(ProcMaterialCategory::getCategoryCode, code));
    }

    private ProcMaterial requireMaterial(Long tenantId, Long id) {
        ProcMaterial material = materialMapper.selectOne(new LambdaQueryWrapper<ProcMaterial>()
                .eq(ProcMaterial::getTenantId, tenantId)
                .eq(ProcMaterial::getId, id));
        if (material == null) {
            throw new BusinessException(404, "物料不存在");
        }
        return material;
    }

    private ProcMaterial findMaterialByCode(Long tenantId, String materialCode) {
        return materialMapper.selectOne(new LambdaQueryWrapper<ProcMaterial>()
                .eq(ProcMaterial::getTenantId, tenantId)
                .eq(ProcMaterial::getMaterialCode, materialCode));
    }

    private Map<Long, ProcMaterialCategory> categoriesByIds(Long tenantId, Collection<Long> categoryIds) {
        List<Long> ids = categoryIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return categoryMapper.selectList(new LambdaQueryWrapper<ProcMaterialCategory>()
                        .eq(ProcMaterialCategory::getTenantId, tenantId)
                        .in(ProcMaterialCategory::getId, ids))
                .stream().collect(Collectors.toMap(ProcMaterialCategory::getId, Function.identity()));
    }

    private LambdaUpdateWrapper<ProcMaterialCategory> versionedCategory(Long tenantId, Long id, Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "乐观锁版本不能为空且不能小于 0");
        }
        return new LambdaUpdateWrapper<ProcMaterialCategory>()
                .eq(ProcMaterialCategory::getTenantId, tenantId)
                .eq(ProcMaterialCategory::getId, id)
                .eq(ProcMaterialCategory::getVersion, version)
                .eq(ProcMaterialCategory::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private LambdaUpdateWrapper<ProcMaterial> versionedMaterial(Long tenantId, Long id, Integer version) {
        if (version == null || version < 0) {
            throw new BusinessException(400, "乐观锁版本不能为空且不能小于 0");
        }
        return new LambdaUpdateWrapper<ProcMaterial>()
                .eq(ProcMaterial::getTenantId, tenantId)
                .eq(ProcMaterial::getId, id)
                .eq(ProcMaterial::getVersion, version)
                .eq(ProcMaterial::getDeleted, 0)
                .setSql("version = version + 1");
    }

    private void auditCategory(LambdaUpdateWrapper<ProcMaterialCategory> update) {
        update.set(ProcMaterialCategory::getUpdateTime, LocalDateTime.now())
                .set(ProcMaterialCategory::getUpdateBy, ProcTenantContext.require().username());
    }

    private void auditMaterial(LambdaUpdateWrapper<ProcMaterial> update) {
        update.set(ProcMaterial::getUpdateTime, LocalDateTime.now())
                .set(ProcMaterial::getUpdateBy, ProcTenantContext.require().username());
    }

    private String requiredText(String value, String fieldName) {
        String normalized = MaterialDomainPolicy.trimToNull(value);
        if (normalized == null) {
            throw new BusinessException(400, fieldName + "不能为空");
        }
        return normalized;
    }

    /**
     * 收集指定品类及其子树中所有叶子节点 ID（无子品类的节点）。
     *
     * @param tenantId 租户 ID
     * @param categoryId 品类 ID
     * @return 叶子节点 ID 列表，至少包含自身
     */
    private List<Long> collectLeafIds(Long tenantId, Long categoryId) {
        List<ProcMaterialCategory> allCategories = categoryMapper.selectList(
                new LambdaQueryWrapper<ProcMaterialCategory>()
                        .eq(ProcMaterialCategory::getTenantId, tenantId)
                        .eq(ProcMaterialCategory::getDeleted, 0)
                        .select(ProcMaterialCategory::getId, ProcMaterialCategory::getParentId));
        Map<Long, List<Long>> childrenMap = allCategories.stream()
                .collect(Collectors.groupingBy(
                        ProcMaterialCategory::getParentId,
                        Collectors.mapping(ProcMaterialCategory::getId, Collectors.toList())));
        List<Long> leafIds = new ArrayList<>();
        collectLeavesRecursive(categoryId, childrenMap, leafIds);
        return leafIds;
    }

    private void collectLeavesRecursive(Long nodeId, Map<Long, List<Long>> childrenMap, List<Long> leafIds) {
        List<Long> children = childrenMap.get(nodeId);
        if (children == null || children.isEmpty()) {
            leafIds.add(nodeId);
        } else {
            for (Long childId : children) {
                collectLeavesRecursive(childId, childrenMap, leafIds);
            }
        }
    }

    private void requireUpdated(int affected, String message) {
        if (affected != 1) {
            throw new BusinessException(409, message);
        }
    }
}
