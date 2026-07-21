package com.omni.srm.service;

import com.omni.common.core.result.PageResult;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;

import java.util.List;

/** SRM 绩效评估服务。 */
public interface EvaluationService {
    /** 分页查询评估列表。 */ PageResult<SrmViews.EvaluationVO> list(Long supplierId, int page, int size);
    /** 查询评估详情。 */ SrmViews.EvaluationVO get(Long id);
    /** 查询供应商评估历史。 */ List<SrmViews.EvaluationVO> supplierHistory(Long supplierId);
    /** 查询当前租户默认评估模板。 */ SrmViews.EvaluationTemplateVO defaultTemplate();
    /** 创建评估。 */ SrmViews.EvaluationVO create(SrmRequests.CreateEvaluationRequest request);
}
