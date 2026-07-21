package com.omni.srm.service;

import com.omni.srm.dto.SrmViews;

import java.util.List;

/** SRM 负责人候选服务。 */
public interface OwnerOptionService {
    /** 查询当前数据范围内负责人候选。 */ List<SrmViews.OwnerOptionVO> list(String keyword, int limit);
}
