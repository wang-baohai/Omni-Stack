package com.omni.crm.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.crm.entity.CrmActivity;
import com.omni.crm.entity.CrmContact;
import com.omni.crm.entity.CrmCustomer;
import com.omni.crm.entity.CrmLead;
import com.omni.crm.entity.CrmOpportunity;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * CRM 详情和写命令统一行级访问守卫，不可见记录统一返回 404。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class CrmRecordAccessGuard {

    private final CrmLeadMapper leadMapper;
    private final CrmCustomerMapper customerMapper;
    private final CrmContactMapper contactMapper;
    private final CrmOpportunityMapper opportunityMapper;
    private final CrmActivityMapper activityMapper;

    /** 查询可见线索。 */
    public CrmLead requireLead(Long id) {
        return required(leadMapper.selectOne(new LambdaQueryWrapper<CrmLead>().eq(CrmLead::getId, id)), "线索不存在");
    }

    /** 查询可见客户。 */
    public CrmCustomer requireCustomer(Long id) {
        return required(customerMapper.selectOne(new LambdaQueryWrapper<CrmCustomer>().eq(CrmCustomer::getId, id)), "客户不存在");
    }

    /** 查询可见联系人。 */
    public CrmContact requireContact(Long id) {
        return required(contactMapper.selectOne(new LambdaQueryWrapper<CrmContact>().eq(CrmContact::getId, id)), "联系人不存在");
    }

    /** 查询可见商机。 */
    public CrmOpportunity requireOpportunity(Long id) {
        return required(opportunityMapper.selectOne(new LambdaQueryWrapper<CrmOpportunity>().eq(CrmOpportunity::getId, id)), "商机不存在");
    }

    /** 查询可见活动。 */
    public CrmActivity requireActivity(Long id) {
        return required(activityMapper.selectOne(new LambdaQueryWrapper<CrmActivity>().eq(CrmActivity::getId, id)), "活动不存在");
    }

    /**
     * 根据多态访问根验证目标并返回 owner 快照。
     *
     * @param rootType 根类型
     * @param rootId 根 ID
     * @return owner 快照
     */
    public CrmOwnerResolver.Owner requireRootOwner(String rootType, Long rootId) {
        return switch (rootType == null ? "" : rootType.toUpperCase()) {
            case "LEAD" -> ownerOf(requireLead(rootId));
            case "CUSTOMER" -> ownerOf(requireCustomer(rootId));
            case "OPPORTUNITY" -> ownerOf(requireOpportunity(rootId));
            default -> throw new BusinessException(400, "不支持的活动访问根类型");
        };
    }

    private CrmOwnerResolver.Owner ownerOf(com.omni.crm.entity.CrmOwnedEntity entity) {
        return owner(entity.getOwnerUserId(), entity.getOwnerUnitId());
    }

    private CrmOwnerResolver.Owner owner(Long userId, Long unitId) {
        return new CrmOwnerResolver.Owner(userId, unitId);
    }

    private <T> T required(T value, String message) {
        if (value == null) {
            throw new BusinessException(404, message);
        }
        return value;
    }
}
