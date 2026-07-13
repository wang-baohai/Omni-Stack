package com.omni.crm.service;

import com.omni.crm.dto.CrmViews;

import java.util.List;

/** CRM 销售管道只读服务。 */
public interface PipelineService {
    /** 查询启用管道。 */ List<CrmViews.PipelineVO> list();
    /** 查询管道阶段。 */ List<CrmViews.PipelineStageVO> stages(Long pipelineId);
}
