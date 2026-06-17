package com.omni.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.base.dto.CreateDictDataRequest;
import com.omni.base.dto.UpdateDictDataRequest;
import com.omni.base.entity.SysDictData;
import com.omni.base.entity.SysDictType;
import com.omni.base.mapper.SysDictDataMapper;
import com.omni.base.mapper.SysDictTypeMapper;
import com.omni.base.service.DictDataService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 字典数据服务实现，包含 Redis 缓存策略。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictDataServiceImpl implements DictDataService {

    private final SysDictDataMapper sysDictDataMapper;
    private final SysDictTypeMapper sysDictTypeMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "dict:type:";
    private static final long CACHE_TTL_MINUTES = 30;

    @Override
    public PageResult<SysDictData> listDataByTypeCode(Long tenantId, String typeCode, int page, int size) {
        LambdaQueryWrapper<SysDictData> wrapper = new LambdaQueryWrapper<SysDictData>()
                .eq(SysDictData::getTenantId, tenantId)
                .eq(SysDictData::getTypeCode, typeCode)
                .orderByAsc(SysDictData::getSort)
                .orderByAsc(SysDictData::getId);

        Page<SysDictData> pageResult = sysDictDataMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    @Override
    public List<SysDictData> listEnabledData(Long tenantId, String typeCode) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + typeCode;

        // 优先从 Redis 读取
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (cachedJson != null) {
            try {
                return objectMapper.readValue(cachedJson, new TypeReference<List<SysDictData>>() {});
            } catch (JsonProcessingException e) {
                log.warn("反序列化字典数据缓存失败，key={}，回源查询: {}", cacheKey, e.getMessage());
            }
        }

        // 缓存未命中，查询数据库
        List<SysDictData> dataList = sysDictDataMapper.selectList(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getTenantId, tenantId)
                        .eq(SysDictData::getTypeCode, typeCode)
                        .eq(SysDictData::getStatus, 1)
                        .orderByAsc(SysDictData::getSort)
                        .orderByAsc(SysDictData::getId));

        // 写入 Redis 缓存
        try {
            String json = objectMapper.writeValueAsString(dataList);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.debug("字典数据写入缓存：key={}, count={}", cacheKey, dataList.size());
        } catch (JsonProcessingException e) {
            log.warn("序列化字典数据写入 Redis 失败，key={}: {}", cacheKey, e.getMessage());
        }

        return dataList;
    }

    @Override
    public SysDictData createData(Long tenantId, CreateDictDataRequest request, String operator) {
        // 校验父字典类型存在
        Long typeCount = sysDictTypeMapper.selectCount(
                new LambdaQueryWrapper<SysDictType>()
                        .eq(SysDictType::getTenantId, tenantId)
                        .eq(SysDictType::getTypeCode, request.getTypeCode()));
        if (typeCount == 0) {
            throw new BusinessException(404, "字典类型不存在");
        }

        SysDictData entity = new SysDictData();
        entity.setTenantId(tenantId);
        entity.setTypeCode(request.getTypeCode());
        entity.setDictValue(request.getDictValue());
        entity.setDictLabel(request.getDictLabel());
        entity.setTagType(request.getTagType());
        entity.setRemark(request.getRemark());
        entity.setSort(request.getSort() != null ? request.getSort() : 0);
        entity.setStatus(1);
        entity.setCreateBy(operator);
        entity.setUpdateBy(operator);
        sysDictDataMapper.insert(entity);

        // 失效缓存
        invalidateCache(tenantId, request.getTypeCode());

        log.info("创建字典数据：tenantId={}, typeCode={}, dictValue={}, operator={}",
                tenantId, request.getTypeCode(), request.getDictValue(), operator);
        return entity;
    }

    @Override
    public SysDictData updateData(Long id, UpdateDictDataRequest request, String operator) {
        SysDictData entity = sysDictDataMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "字典数据不存在");
        }

        if (request.getDictValue() != null) {
            entity.setDictValue(request.getDictValue());
        }
        if (request.getDictLabel() != null) {
            entity.setDictLabel(request.getDictLabel());
        }
        if (request.getTagType() != null) {
            entity.setTagType(request.getTagType());
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
        sysDictDataMapper.updateById(entity);

        invalidateCache(entity.getTenantId(), entity.getTypeCode());

        log.info("更新字典数据：id={}, typeCode={}, operator={}", id, entity.getTypeCode(), operator);
        return entity;
    }

    @Override
    public void deleteData(Long id) {
        SysDictData entity = sysDictDataMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "字典数据不存在");
        }

        sysDictDataMapper.deleteById(id);

        invalidateCache(entity.getTenantId(), entity.getTypeCode());

        log.info("删除字典数据：id={}, typeCode={}", id, entity.getTypeCode());
    }

    @Override
    public void refreshCache(Long tenantId, String typeCode) {
        String cacheKey = CACHE_KEY_PREFIX + tenantId + ":" + typeCode;
        // 先删除旧缓存
        stringRedisTemplate.delete(cacheKey);

        // 重新查询并写入
        List<SysDictData> dataList = sysDictDataMapper.selectList(
                new LambdaQueryWrapper<SysDictData>()
                        .eq(SysDictData::getTenantId, tenantId)
                        .eq(SysDictData::getTypeCode, typeCode)
                        .eq(SysDictData::getStatus, 1)
                        .orderByAsc(SysDictData::getSort)
                        .orderByAsc(SysDictData::getId));

        try {
            String json = objectMapper.writeValueAsString(dataList);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            log.info("字典缓存已刷新：key={}, count={}", cacheKey, dataList.size());
        } catch (JsonProcessingException e) {
            log.warn("刷新字典缓存序列化失败，key={}: {}", cacheKey, e.getMessage());
        }
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
