package com.omni.crm.service;

import com.omni.common.core.result.PageResult;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.dto.CrmViews;

import java.util.List;

/** CRM 跟进活动应用服务。 */
public interface ActivityService {
    /** 分页查询。 */ PageResult<CrmViews.ActivityVO> list(CrmRequests.ActivityQuery query);
    /** 查询聚合根时间线。 */ List<CrmViews.ActivityVO> timeline(String rootType, Long rootId, int limit);
    /** 查询详情。 */ CrmViews.ActivityVO get(Long id);
    /** 创建活动。 */ CrmViews.ActivityVO create(CrmRequests.CreateActivityRequest request);
    /** 更新活动。 */ CrmViews.ActivityVO update(Long id, CrmRequests.UpdateActivityRequest request);
    /** 删除活动。 */ void delete(Long id, Integer version);
    /** 完成活动。 */ CrmViews.ActivityVO complete(Long id, CrmRequests.CompleteActivityRequest request);
    /** 取消活动。 */ CrmViews.ActivityVO cancel(Long id, CrmRequests.CancelActivityRequest request);
    /** 重新计划活动。 */ CrmViews.ActivityVO reschedule(Long id, CrmRequests.RescheduleActivityRequest request);
}
