package com.omni.procurement.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.mapper.ProcApprovalRouteMapper;
import com.omni.procurement.security.ProcTenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 按租户、品类和金额解析唯一工作流模型路由。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class ApprovalRouteResolver {

    private final ProcApprovalRouteMapper routeMapper;

    /**
     * 解析唯一活动审批路由。
     *
     * @param categoryCode 物料品类编码
     * @param totalAmount 服务端重算的请购总金额
     * @return 唯一审批路由
     */
    public ProcApprovalRoute resolve(String categoryCode, BigDecimal totalAmount) {
        Long tenantId = ProcTenantContext.requireTenantId();
        String normalizedCategory = ApprovalRoutePolicy.normalizeCategoryCode(categoryCode);
        List<ProcApprovalRoute> candidates = routeMapper.selectList(
                new LambdaQueryWrapper<ProcApprovalRoute>()
                        .eq(ProcApprovalRoute::getTenantId, tenantId)
                        .eq(ProcApprovalRoute::getStatus, ApprovalRoutePolicy.ACTIVE)
                        .in(ProcApprovalRoute::getCategoryCode,
                                List.of(normalizedCategory, ApprovalRoutePolicy.WILDCARD_CATEGORY))
                        .orderByAsc(ProcApprovalRoute::getPriority)
                        .orderByAsc(ProcApprovalRoute::getId));
        return ApprovalRoutePolicy.select(normalizedCategory, totalAmount, candidates);
    }
}
