package com.omni.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.base.dto.CreateDictTypeRequest;
import com.omni.base.dto.DictTypeQuery;
import com.omni.base.dto.UpdateDictTypeRequest;
import com.omni.base.entity.SysDictData;
import com.omni.base.entity.SysDictType;
import com.omni.base.mapper.SysDictDataMapper;
import com.omni.base.mapper.SysDictTypeMapper;
import com.omni.base.service.DictTypeService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 字典类型服务实现。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictTypeServiceImpl implements DictTypeService {

    private final SysDictTypeMapper sysDictTypeMapper;
    private final SysDictDataMapper sysDictDataMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String CACHE_KEY_PREFIX = "dict:type:";

    @Override
    public PageResult<SysDictType> listTypes(Long tenantId, DictTypeQuery query, int page, int size) {
        LambdaQueryWrapper<SysDictType> wrapper = new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getTenantId, tenantId)
                .like(query.getTypeCode() != null && !query.getTypeCode().isBlank(),
                        SysDictType::getTypeCode, query.getTypeCode())
                .like(query.getTypeName() != null && !query.getTypeName().isBlank(),
                        SysDictType::getTypeName, query.getTypeName())
                .eq(query.getStatus() != null, SysDictType::getStatus, query.getStatus())
                .orderByAsc(SysDictType::getSort)
                .orderByAsc(SysDictType::getId);

        Page<SysDictType> pageResult = sysDictTypeMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    @Override
    public SysDictType getTypeById(Long id) {
        SysDictType entity = sysDictTypeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "字典类型不存在");
        }
        return entity;
    }

    @Override
    public SysDictType createType(Long tenantId, CreateDictTypeRequest request, String operator) {
        // 校验唯一性
        Long count = sysDictTypeMapper.selectCount(
                new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getTenantId, tenantId)
                        .eq(SysDictType::getTypeCode, request.getTypeCode()));
        if (count > 0) {
            throw new BusinessException(400, "字典类型编码已存在");
        }

        SysDictType entity = new SysDictType();
        entity.setTenantId(tenantId);
        entity.setTypeCode(request.getTypeCode());
        entity.setTypeName(request.getTypeName());
        entity.setRemark(request.getRemark());
        entity.setSort(request.getSort() != null ? request.getSort() : 0);
        entity.setStatus(1);
        entity.setCreateBy(operator);
        entity.setUpdateBy(operator);
        sysDictTypeMapper.insert(entity);

        log.info("创建字典类型：tenantId={}, typeCode={}, operator={}", tenantId, request.getTypeCode(), operator);
        return entity;
    }

    @Override
    public SysDictType updateType(Long id, UpdateDictTypeRequest request, String operator) {
        SysDictType entity = getTypeById(id);

        if (request.getTypeName() != null) {
            entity.setTypeName(request.getTypeName());
        }
        if (request.getRemark() != null) {
            entity.setRemark(request.getRemark());
        }
        if (request.getSort() != null) {
            entity.setSort(request.getSort());
        }
        if (request.getStatus() != null) {
            entity.setStatus(request.getStatus());
        }
        entity.setUpdateBy(operator);
        sysDictTypeMapper.updateById(entity);

        // 写操作后失效缓存
        invalidateCache(entity.getTenantId(), entity.getTypeCode());

        log.info("更新字典类型：id={}, typeCode={}, operator={}", id, entity.getTypeCode(), operator);
        return entity;
    }

    @Override
    @Transactional
    public void deleteType(Long id) {
        SysDictType entity = getTypeById(id);

        // 级联删除关联的字典数据
        sysDictDataMapper.delete(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getTenantId, entity.getTenantId())
                        .eq(SysDictData::getTypeCode, entity.getTypeCode()));

        sysDictTypeMapper.deleteById(id);

        // 失效缓存
        invalidateCache(entity.getTenantId(), entity.getTypeCode());

        log.info("删除字典类型：id={}, typeCode={}", id, entity.getTypeCode());
    }

    @Override
    public void toggleStatus(Long id, Integer status) {
        SysDictType entity = getTypeById(id);
        entity.setStatus(status);
        sysDictTypeMapper.updateById(entity);

        invalidateCache(entity.getTenantId(), entity.getTypeCode());

        log.info("切换字典类型状态：id={}, status={}", id, status);
    }

    /**
     * 失效字典缓存。
     *
     * @param tenantId 租户 ID
     * @param typeCode 字典类型编码
     */
    private void invalidateCache(Long tenantId, String typeCode) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + typeCode;
        stringRedisTemplate.delete(cacheKey);
        log.debug("失效字典缓存：{}", cacheKey);
    }
}
