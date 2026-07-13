package com.omni.crm.service;

import com.omni.common.core.result.PageResult;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;

import java.util.List;

/**
 * CRM 线索应用服务。
 *
 * @author Omni-Stack Team
 */
public interface LeadService {
    /** 分页查询。 */ PageResult<CrmViews.LeadVO> list(CrmRequests.LeadQuery query);
    /** 查询详情。 */ CrmViews.LeadVO get(Long id);
    /** 创建线索。 */ CrmViews.LeadVO create(CrmRequests.CreateLeadRequest request);
    /** 更新线索。 */ CrmViews.LeadVO update(Long id, CrmRequests.UpdateLeadRequest request);
    /** 删除线索。 */ void delete(Long id, CrmRequests.VersionRequest request);
    /** 重复检测。 */ List<CrmViews.DuplicateCandidateVO> duplicateCheck(CrmRequests.LeadDuplicateRequest request);
    /** 分配负责人。 */ CrmViews.LeadVO assign(Long id, CrmRequests.AssignRequest request);
    /** 批量分配。 */ List<CrmViews.LeadVO> batchAssign(CrmRequests.BatchAssignRequest request);
    /** 标记合格。 */ CrmViews.LeadVO qualify(Long id, CrmRequests.VersionRequest request);
    /** 判定无效。 */ CrmViews.LeadVO disqualify(Long id, CrmRequests.DisqualifyLeadRequest request);
    /** 重新激活。 */ CrmViews.LeadVO reopen(Long id, CrmRequests.VersionRequest request);
    /** 幂等转换。 */ CrmViews.ConversionResultVO convert(Long id, CrmRequests.ConvertLeadRequest request);
}
