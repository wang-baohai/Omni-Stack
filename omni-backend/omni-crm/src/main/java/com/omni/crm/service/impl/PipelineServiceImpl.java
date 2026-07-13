package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.crm.dto.CrmViewAssembler;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.entity.CrmPipeline;
import com.omni.crm.entity.CrmPipelineStage;
import com.omni.crm.mapper.CrmPipelineMapper;
import com.omni.crm.mapper.CrmPipelineStageMapper;
import com.omni.crm.service.CrmTenantInitializer;
import com.omni.crm.service.PipelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** CRM 销售管道只读服务实现。 */
@Service
@RequiredArgsConstructor
public class PipelineServiceImpl implements PipelineService {

    private final CrmTenantInitializer tenantInitializer;
    private final CrmPipelineMapper pipelineMapper;
    private final CrmPipelineStageMapper stageMapper;

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.PipelineVO> list() {
        tenantInitializer.ensureInitialized();
        return pipelineMapper.selectList(new LambdaQueryWrapper<CrmPipeline>()
                        .eq(CrmPipeline::getStatus, 1).orderByAsc(CrmPipeline::getSort).orderByAsc(CrmPipeline::getId))
                .stream().map(CrmViewAssembler::pipeline).toList();
    }

    /** {@inheritDoc} */
    @Override
    public List<CrmViews.PipelineStageVO> stages(Long pipelineId) {
        tenantInitializer.ensureInitialized();
        Long exists = pipelineMapper.selectCount(new LambdaQueryWrapper<CrmPipeline>()
                .eq(CrmPipeline::getId, pipelineId).eq(CrmPipeline::getStatus, 1));
        if (exists == 0) throw new BusinessException(404, "销售管道不存在");
        return stageMapper.selectList(new LambdaQueryWrapper<CrmPipelineStage>()
                        .eq(CrmPipelineStage::getPipelineId, pipelineId).eq(CrmPipelineStage::getStatus, 1)
                        .orderByAsc(CrmPipelineStage::getSort).orderByAsc(CrmPipelineStage::getId))
                .stream().map(CrmViewAssembler::stage).toList();
    }
}
