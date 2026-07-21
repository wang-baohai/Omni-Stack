package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.mapper.SrmRiskAssessmentMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.service.OverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** SRM 概览统计服务实现。 */
@Service
@RequiredArgsConstructor
public class OverviewServiceImpl implements OverviewService {

    private final SrmSupplierMapper supplierMapper;
    private final SrmRiskAssessmentMapper riskAssessmentMapper;

    /** {@inheritDoc} */
    @Override
    public SrmViews.OverviewSummaryVO summary() {
        return supplierMapper.selectOverviewSummary();
    }

    /** {@inheritDoc} */
    @Override
    public SrmViews.RiskDashboardVO riskDashboard() {
        SrmViews.RiskDashboardVO vo = riskAssessmentMapper.selectCurrentRiskCounts();
        if (vo == null) {
            vo = new SrmViews.RiskDashboardVO();
            vo.setRedCount(0L);
            vo.setYellowCount(0L);
            vo.setGreenCount(0L);
        }
        Page<SrmViews.RiskSupplierSummaryVO> top = riskAssessmentMapper.selectCurrentRiskPage(
                new Page<>(1, 10, false), null);
        vo.setTopRiskSuppliers(top.getRecords());
        return vo;
    }
}
