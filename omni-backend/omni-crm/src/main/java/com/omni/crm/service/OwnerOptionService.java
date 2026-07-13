package com.omni.crm.service;

import com.omni.crm.dto.CrmViews;

import java.util.List;

/** CRM 负责人候选服务。 */
public interface OwnerOptionService {
    /** 查询当前数据范围内负责人候选。 */ List<CrmViews.OwnerOptionVO> list(String keyword, int limit);
}
