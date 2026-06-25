package com.omni.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.base.dto.CreateUserJobTypeRequest;
import com.omni.base.dto.UpdateUserJobTypeRequest;
import com.omni.base.dto.UserJobTypeQuery;
import com.omni.base.entity.SysUserJobType;
import com.omni.base.mapper.SysUserJobTypeMapper;
import com.omni.base.service.UserJobTypeService;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务类型服务实现。
 *
 * @author Omni-Stack Team
 * @see UserJobTypeService
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserJobTypeServiceImpl implements UserJobTypeService {

    private final SysUserJobTypeMapper sysUserJobTypeMapper;

    /** {@inheritDoc} */
    @Override
    public PageResult<SysUserJobType> listTypes(UserJobTypeQuery query, int page, int size) {
        LambdaQueryWrapper<SysUserJobType> wrapper = new LambdaQueryWrapper<SysUserJobType>()
                .like(query.getTypeCode() != null && !query.getTypeCode().isBlank(),
                        SysUserJobType::getTypeCode, query.getTypeCode())
                .like(query.getTypeName() != null && !query.getTypeName().isBlank(),
                        SysUserJobType::getTypeName, query.getTypeName())
                .eq(query.getStatus() != null, SysUserJobType::getStatus, query.getStatus())
                .orderByAsc(SysUserJobType::getId);

        Page<SysUserJobType> pageResult = sysUserJobTypeMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public SysUserJobType getTypeById(Long id) {
        SysUserJobType entity = sysUserJobTypeMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "任务类型不存在");
        }
        return entity;
    }

    /** {@inheritDoc} */
    @Override
    public List<SysUserJobType> listEnabledTypes() {
        return sysUserJobTypeMapper.selectList(
                new LambdaQueryWrapper<SysUserJobType>()
                        .eq(SysUserJobType::getStatus, 1)
                        .orderByAsc(SysUserJobType::getTypeCode));
    }

    /** {@inheritDoc} */
    @Override
    public SysUserJobType createType(CreateUserJobTypeRequest request) {
        // 校验 typeCode 唯一
        Long count = sysUserJobTypeMapper.selectCount(
                new LambdaQueryWrapper<SysUserJobType>()
                        .eq(SysUserJobType::getTypeCode, request.getTypeCode()));
        if (count > 0) {
            throw new BusinessException(400, "任务类型编码已存在");
        }

        SysUserJobType entity = new SysUserJobType();
        entity.setTypeCode(request.getTypeCode());
        entity.setTypeName(request.getTypeName());
        entity.setDescription(request.getDescription());
        entity.setParamTemplate(request.getParamTemplate());
        entity.setStatus(1);
        sysUserJobTypeMapper.insert(entity);

        log.info("创建任务类型：typeCode={}", request.getTypeCode());
        return entity;
    }

    /** {@inheritDoc} */
    @Override
    public SysUserJobType updateType(Long id, UpdateUserJobTypeRequest request) {
        SysUserJobType entity = getTypeById(id);

        if (request.getTypeName() != null) {
            entity.setTypeName(request.getTypeName());
        }
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getParamTemplate() != null) {
            entity.setParamTemplate(request.getParamTemplate());
        }
        sysUserJobTypeMapper.updateById(entity);

        log.info("更新任务类型：id={}, typeCode={}", id, entity.getTypeCode());
        return entity;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteType(Long id) {
        SysUserJobType entity = getTypeById(id);
        sysUserJobTypeMapper.deleteById(id);
        log.info("删除任务类型：id={}, typeCode={}", id, entity.getTypeCode());
    }

    /** {@inheritDoc} */
    @Override
    public void toggleStatus(Long id, Integer status) {
        SysUserJobType entity = getTypeById(id);
        entity.setStatus(status);
        sysUserJobTypeMapper.updateById(entity);
        log.info("切换任务类型状态：id={}, status={}", id, status);
    }
}
