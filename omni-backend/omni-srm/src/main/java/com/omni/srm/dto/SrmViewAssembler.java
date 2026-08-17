package com.omni.srm.dto;

import com.omni.srm.entity.SrmEvaluation;
import com.omni.srm.entity.SrmEvaluationItem;
import com.omni.srm.entity.SrmRiskAssessment;
import com.omni.srm.entity.SrmRiskIndicator;
import com.omni.srm.entity.SrmSupplier;
import com.omni.srm.entity.SrmSupplierBankAccount;
import com.omni.srm.entity.SrmSupplierContact;
import com.omni.srm.entity.SrmSupplierInvite;
import com.omni.srm.entity.SrmSupplierQualification;
import com.omni.srm.security.PiiMasker;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SRM 实体到安全 VO 的装配器。
 *
 * @author Omni-Stack Team
 */
public final class SrmViewAssembler {

    private SrmViewAssembler() {
    }

    /**
     * 判断当前请求是否具备完整 PII 查看权限。
     *
     * @return 是否具备权限
     */
    public static boolean canViewPii() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "srm:pii:view".equals(authority.getAuthority()));
    }

    /** 将供应商转换为列表 VO。 */
    public static SrmViews.SupplierVO supplier(SrmSupplier entity, boolean revealPii) {
        SrmViews.SupplierVO vo = new SrmViews.SupplierVO();
        vo.setId(entity.getId());
        vo.setSupplierNo(entity.getSupplierNo());
        vo.setName(entity.getName());
        vo.setSupplierType(entity.getSupplierType());
        vo.setIndustryCode(entity.getIndustryCode());
        vo.setCreditCode(entity.getCreditCode());
        vo.setWebsite(entity.getWebsite());
        vo.setPhone(revealPii ? entity.getPhone() : PiiMasker.phone(entity.getPhone()));
        vo.setEmail(revealPii ? entity.getEmail() : PiiMasker.email(entity.getEmail()));
        vo.setRegion(entity.getRegion());
        vo.setAddress(entity.getAddress());
        vo.setCategoryCode(entity.getCategoryCode());
        vo.setLevelCode(entity.getLevelCode());
        vo.setStatus(entity.getStatus());
        vo.setAssignedTime(entity.getAssignedTime());
        vo.setLastEvaluationTime(entity.getLastEvaluationTime());
        vo.setProcessInstanceId(entity.getProcessInstanceId());
        vo.setWorkflowStartStatus(entity.getWorkflowStartStatus());
        vo.setVersion(entity.getVersion());
        vo.setCreateTime(entity.getCreateTime());
        vo.setCreateBy(entity.getCreateBy());
        vo.setOwnerUserId(entity.getOwnerUserId());
        vo.setOwnerUnitId(entity.getOwnerUnitId());
        return vo;
    }

    /** 将联系人转换为 VO。 */
    public static SrmViews.ContactVO contact(SrmSupplierContact entity, boolean revealPii) {
        SrmViews.ContactVO vo = new SrmViews.ContactVO();
        vo.setId(entity.getId());
        vo.setSupplierId(entity.getSupplierId());
        vo.setName(entity.getName());
        vo.setDepartment(entity.getDepartment());
        vo.setJobTitle(entity.getJobTitle());
        vo.setMobile(revealPii ? entity.getMobile() : PiiMasker.phone(entity.getMobile()));
        vo.setPhone(revealPii ? entity.getPhone() : PiiMasker.phone(entity.getPhone()));
        vo.setEmail(revealPii ? entity.getEmail() : PiiMasker.email(entity.getEmail()));
        vo.setDecisionRole(entity.getDecisionRole());
        vo.setPrimaryFlag(entity.getPrimaryFlag());
        vo.setStatus(entity.getStatus() != null && entity.getStatus() == 1 ? "ACTIVE" : "INACTIVE");
        vo.setVersion(entity.getVersion());
        return vo;
    }

    /** 将资质转换为 VO。 */
    public static SrmViews.QualificationVO qualification(SrmSupplierQualification entity) {
        SrmViews.QualificationVO vo = new SrmViews.QualificationVO();
        vo.setId(entity.getId());
        vo.setSupplierId(entity.getSupplierId());
        vo.setQualificationName(entity.getQualificationName());
        vo.setCertificateNo(entity.getCertificateNo());
        vo.setIssuingAuthority(entity.getIssuingAuthority());
        vo.setIssueDate(entity.getIssueDate());
        vo.setExpiryDate(entity.getExpiryDate());
        vo.setStatus(entity.getStatus());
        vo.setVersion(entity.getVersion());
        return vo;
    }

    /** 将银行账户转换为 VO。 */
    public static SrmViews.BankAccountVO bankAccount(SrmSupplierBankAccount entity, boolean revealPii) {
        SrmViews.BankAccountVO vo = new SrmViews.BankAccountVO();
        vo.setId(entity.getId());
        vo.setSupplierId(entity.getSupplierId());
        vo.setAccountName(entity.getAccountName());
        vo.setAccountNo(revealPii ? entity.getAccountNo() : PiiMasker.bankAccount(entity.getAccountNo()));
        vo.setBankName(entity.getBankName());
        vo.setBankBranch(entity.getBankBranch());
        vo.setBankCode(entity.getBankCode());
        vo.setPrimaryFlag(entity.getPrimaryFlag());
        vo.setStatus(entity.getStatus() != null && entity.getStatus() == 1 ? "ACTIVE" : "INACTIVE");
        vo.setVersion(entity.getVersion());
        return vo;
    }

    /** 将评估转换为 VO。 */
    public static SrmViews.EvaluationVO evaluation(SrmEvaluation entity) {
        SrmViews.EvaluationVO vo = new SrmViews.EvaluationVO();
        vo.setId(entity.getId());
        vo.setSupplierId(entity.getSupplierId());
        vo.setTemplateId(entity.getTemplateId());
        vo.setEvaluationPeriod(entity.getEvaluationPeriod());
        vo.setTotalScore(entity.getTotalScore());
        vo.setEvaluatorUserId(entity.getEvaluatorUserId());
        vo.setEvaluationTime(entity.getEvaluationTime());
        vo.setStatus(entity.getStatus());
        vo.setVersion(entity.getVersion());
        vo.setOwnerUserId(entity.getOwnerUserId());
        vo.setOwnerUnitId(entity.getOwnerUnitId());
        return vo;
    }

    /** 将评估明细转换为 VO。 */
    public static SrmViews.EvaluationItemVO evaluationItem(SrmEvaluationItem entity) {
        SrmViews.EvaluationItemVO vo = new SrmViews.EvaluationItemVO();
        vo.setId(entity.getId());
        vo.setEvaluationId(entity.getEvaluationId());
        vo.setDimensionId(entity.getDimensionId());
        vo.setIndicatorName(entity.getIndicatorName());
        vo.setScore(entity.getScore());
        vo.setWeight(entity.getWeight());
        vo.setRemark(entity.getRemark());
        return vo;
    }

    /** 将风险指标转换为 VO。 */
    public static SrmViews.RiskIndicatorVO riskIndicator(SrmRiskIndicator entity) {
        SrmViews.RiskIndicatorVO vo = new SrmViews.RiskIndicatorVO();
        vo.setId(entity.getId());
        vo.setSupplierId(entity.getSupplierId());
        vo.setIndicatorType(entity.getIndicatorType());
        vo.setIndicatorValue(entity.getIndicatorValue());
        vo.setRiskLevel(entity.getRiskLevel());
        vo.setCriterionId(entity.getCriterionId());
        vo.setScore(entity.getScore());
        vo.setAssessmentTime(entity.getAssessmentTime());
        vo.setRemark(entity.getRemark());
        vo.setVersion(entity.getVersion());
        return vo;
    }

    /** 将风险评估转换为 VO。 */
    public static SrmViews.RiskAssessmentVO riskAssessment(SrmRiskAssessment entity) {
        SrmViews.RiskAssessmentVO vo = new SrmViews.RiskAssessmentVO();
        vo.setId(entity.getId());
        vo.setSupplierId(entity.getSupplierId());
        vo.setOverallLevel(entity.getOverallLevel());
        vo.setAssessmentTime(entity.getAssessmentTime());
        vo.setAssessorUserId(entity.getAssessorUserId());
        vo.setRemark(entity.getRemark());
        vo.setVersion(entity.getVersion());
        return vo;
    }

    /** 将邀请转换为 VO。 */
    public static SrmViews.InviteVO invite(SrmSupplierInvite entity) {
        SrmViews.InviteVO vo = new SrmViews.InviteVO();
        vo.setId(entity.getId());
        vo.setStatus(entity.getStatus());
        vo.setExpiresTime(entity.getExpiresTime());
        vo.setMaxUses(entity.getMaxUses());
        vo.setUsedCount(entity.getUsedCount());
        vo.setCreateTime(entity.getCreateTime());
        vo.setCreateBy(entity.getCreateBy());
        return vo;
    }
}
