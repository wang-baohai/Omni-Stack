package com.omni.srm.service.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.entity.SrmEvaluation;
import com.omni.srm.entity.SrmOwnedEntity;
import com.omni.srm.entity.SrmRiskIndicator;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierBankAccount;
import com.omni.srm.entity.SrmSupplierContact;
import com.omni.srm.entity.SrmSupplierQualification;
import com.omni.srm.mapper.SrmEvaluationMapper;
import com.omni.srm.mapper.SrmRiskIndicatorMapper;
import com.omni.srm.mapper.SrmSupplierBankAccountMapper;
import com.omni.srm.mapper.SrmSupplierContactMapper;
import com.omni.srm.mapper.SrmSupplierMapper;
import com.omni.srm.mapper.SrmSupplierQualificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * SRM 详情和写命令统一行级访问守卫，不可见记录统一返回 404。
 *
 * @author Omni-Stack Team
 */
@Component
@RequiredArgsConstructor
public class SrmRecordAccessGuard {

    private final SrmSupplierMapper supplierMapper;
    private final SrmSupplierContactMapper contactMapper;
    private final SrmSupplierQualificationMapper qualificationMapper;
    private final SrmSupplierBankAccountMapper bankAccountMapper;
    private final SrmEvaluationMapper evaluationMapper;
    private final SrmRiskIndicatorMapper riskIndicatorMapper;

    /** 查询可见供应商。 */
    public SrmSupplier requireSupplier(Long id) {
        return required(supplierMapper.selectOne(new LambdaQueryWrapper<SrmSupplier>()
                .eq(SrmSupplier::getId, id)), "供应商不存在");
    }

    /** 查询可见联系人。 */
    public SrmSupplierContact requireContact(Long id) {
        return required(contactMapper.selectOne(new LambdaQueryWrapper<SrmSupplierContact>()
                .eq(SrmSupplierContact::getId, id)), "联系人不存在");
    }

    /** 查询可见资质。 */
    public SrmSupplierQualification requireQualification(Long id) {
        return required(qualificationMapper.selectOne(new LambdaQueryWrapper<SrmSupplierQualification>()
                .eq(SrmSupplierQualification::getId, id)), "资质不存在");
    }

    /** 查询可见银行账户。 */
    public SrmSupplierBankAccount requireBankAccount(Long id) {
        return required(bankAccountMapper.selectOne(new LambdaQueryWrapper<SrmSupplierBankAccount>()
                .eq(SrmSupplierBankAccount::getId, id)), "银行账户不存在");
    }

    /** 查询可见评估。 */
    public SrmEvaluation requireEvaluation(Long id) {
        return required(evaluationMapper.selectOne(new LambdaQueryWrapper<SrmEvaluation>()
                .eq(SrmEvaluation::getId, id)), "评估不存在");
    }

    /** 查询可见风险指标。 */
    public SrmRiskIndicator requireRiskIndicator(Long id) {
        return required(riskIndicatorMapper.selectOne(new LambdaQueryWrapper<SrmRiskIndicator>()
                .eq(SrmRiskIndicator::getId, id)), "风险指标不存在");
    }

    /**
     * 获取实体的 owner 快照。
     *
     * @param entity 带 owner 的实体
     * @return owner 快照
     */
    public SrmOwnerResolver.Owner ownerOf(SrmOwnedEntity entity) {
        return new SrmOwnerResolver.Owner(entity.getOwnerUserId(), entity.getOwnerUnitId());
    }

    private <T> T required(T value, String message) {
        if (value == null) {
            throw new BusinessException(404, message);
        }
        return value;
    }
}
