package com.omni.crm.dto;

import com.omni.crm.entity.CrmActivity;
import com.omni.crm.entity.CrmContact;
import com.omni.crm.entity.CrmCustomer;
import com.omni.crm.entity.CrmLead;
import com.omni.crm.entity.CrmOpportunity;
import com.omni.crm.entity.CrmOpportunityStageHistory;
import com.omni.crm.entity.CrmPipeline;
import com.omni.crm.entity.CrmPipelineStage;
import com.omni.crm.security.PiiMasker;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * CRM 实体到安全 VO 的装配器。
 *
 * @author Omni-Stack Team
 */
public final class CrmViewAssembler {

    private CrmViewAssembler() {
    }

    /**
     * 判断当前请求是否具备完整 PII 查看权限。
     *
     * @return 是否具备权限
     */
    public static boolean canViewPii() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "crm:pii:view".equals(authority.getAuthority()));
    }

    /** 将线索转换为 VO。 */
    public static CrmViews.LeadVO lead(CrmLead entity, boolean revealPii) {
        CrmViews.LeadVO vo = new CrmViews.LeadVO();
        vo.setId(entity.getId()); vo.setLeadNo(entity.getLeadNo()); vo.setFullName(entity.getFullName());
        vo.setCompanyName(entity.getCompanyName()); vo.setJobTitle(entity.getJobTitle());
        vo.setMobile(revealPii ? entity.getMobile() : PiiMasker.phone(entity.getMobile()));
        vo.setPhone(revealPii ? entity.getPhone() : PiiMasker.phone(entity.getPhone()));
        vo.setEmail(revealPii ? entity.getEmail() : PiiMasker.email(entity.getEmail()));
        vo.setRegion(entity.getRegion()); vo.setAddress(revealPii ? entity.getAddress() : PiiMasker.address(entity.getAddress()));
        vo.setSourceCode(entity.getSourceCode()); vo.setIndustryCode(entity.getIndustryCode()); vo.setRating(entity.getRating());
        vo.setStatus(entity.getStatus()); vo.setDisqualifyReason(entity.getDisqualifyReason());
        vo.setOwnerUserId(entity.getOwnerUserId()); vo.setOwnerUnitId(entity.getOwnerUnitId());
        vo.setLastActivityTime(entity.getLastActivityTime()); vo.setNextFollowupTime(entity.getNextFollowupTime());
        vo.setConvertedTime(entity.getConvertedTime()); vo.setVersion(entity.getVersion());
        vo.setCreateTime(entity.getCreateTime()); vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /** 将客户转换为 VO。 */
    public static CrmViews.CustomerVO customer(CrmCustomer entity, boolean revealPii) {
        CrmViews.CustomerVO vo = new CrmViews.CustomerVO();
        vo.setId(entity.getId()); vo.setCustomerNo(entity.getCustomerNo()); vo.setName(entity.getName());
        vo.setCustomerType(entity.getCustomerType()); vo.setIndustryCode(entity.getIndustryCode());
        vo.setLevelCode(entity.getLevelCode()); vo.setSourceCode(entity.getSourceCode());
        vo.setCreditCode(revealPii ? entity.getCreditCode() : PiiMasker.phone(entity.getCreditCode()));
        vo.setWebsite(entity.getWebsite()); vo.setPhone(revealPii ? entity.getPhone() : PiiMasker.phone(entity.getPhone()));
        vo.setEmail(revealPii ? entity.getEmail() : PiiMasker.email(entity.getEmail())); vo.setRegion(entity.getRegion());
        vo.setAddress(revealPii ? entity.getAddress() : PiiMasker.address(entity.getAddress())); vo.setStatus(entity.getStatus());
        vo.setOwnerUserId(entity.getOwnerUserId()); vo.setOwnerUnitId(entity.getOwnerUnitId());
        vo.setLastActivityTime(entity.getLastActivityTime()); vo.setNextFollowupTime(entity.getNextFollowupTime());
        vo.setVersion(entity.getVersion()); vo.setCreateTime(entity.getCreateTime()); vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /** 将联系人转换为 VO。 */
    public static CrmViews.ContactVO contact(CrmContact entity, boolean revealPii) {
        CrmViews.ContactVO vo = new CrmViews.ContactVO();
        vo.setId(entity.getId()); vo.setCustomerId(entity.getCustomerId()); vo.setName(entity.getName());
        vo.setDepartment(entity.getDepartment()); vo.setJobTitle(entity.getJobTitle());
        vo.setMobile(revealPii ? entity.getMobile() : PiiMasker.phone(entity.getMobile()));
        vo.setPhone(revealPii ? entity.getPhone() : PiiMasker.phone(entity.getPhone()));
        vo.setEmail(revealPii ? entity.getEmail() : PiiMasker.email(entity.getEmail()));
        vo.setDecisionRole(entity.getDecisionRole()); vo.setPrimaryFlag(entity.getPrimaryFlag()); vo.setStatus(entity.getStatus());
        vo.setOwnerUserId(entity.getOwnerUserId()); vo.setOwnerUnitId(entity.getOwnerUnitId()); vo.setVersion(entity.getVersion());
        vo.setCreateTime(entity.getCreateTime()); vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /** 将商机转换为 VO。 */
    public static CrmViews.OpportunityVO opportunity(CrmOpportunity entity) {
        CrmViews.OpportunityVO vo = new CrmViews.OpportunityVO();
        vo.setId(entity.getId()); vo.setOpportunityNo(entity.getOpportunityNo()); vo.setName(entity.getName());
        vo.setCustomerId(entity.getCustomerId()); vo.setPrimaryContactId(entity.getPrimaryContactId());
        vo.setSourceLeadId(entity.getSourceLeadId()); vo.setPipelineId(entity.getPipelineId()); vo.setStageId(entity.getStageId());
        vo.setStatus(entity.getStatus()); vo.setAmount(entity.getAmount()); vo.setCurrencyCode(entity.getCurrencyCode());
        vo.setProbability(entity.getProbability()); vo.setExpectedCloseDate(entity.getExpectedCloseDate());
        vo.setActualCloseTime(entity.getActualCloseTime()); vo.setLossReason(entity.getLossReason());
        vo.setOwnerUserId(entity.getOwnerUserId()); vo.setOwnerUnitId(entity.getOwnerUnitId());
        vo.setStageChangeTime(entity.getStageChangeTime()); vo.setNextFollowupTime(entity.getNextFollowupTime());
        vo.setVersion(entity.getVersion()); vo.setCreateTime(entity.getCreateTime()); vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /** 将活动转换为 VO。 */
    public static CrmViews.ActivityVO activity(CrmActivity entity) {
        return activity(entity, false);
    }

    /** 将活动转换为 VO，并按权限决定是否返回自由文本内容。 */
    public static CrmViews.ActivityVO activity(CrmActivity entity, boolean revealPii) {
        CrmViews.ActivityVO vo = new CrmViews.ActivityVO();
        vo.setId(entity.getId()); vo.setRootType(entity.getRootType()); vo.setRootId(entity.getRootId());
        vo.setContactId(entity.getContactId()); vo.setActivityType(entity.getActivityType()); vo.setSubject(entity.getSubject());
        vo.setContent(revealPii || entity.getContent() == null || entity.getContent().isBlank()
                ? entity.getContent() : "[REDACTED]");
        vo.setStatus(entity.getStatus()); vo.setPlannedStartTime(entity.getPlannedStartTime());
        vo.setPlannedEndTime(entity.getPlannedEndTime()); vo.setCompletedTime(entity.getCompletedTime());
        vo.setNextActionTime(entity.getNextActionTime()); vo.setPerformedByUserId(entity.getPerformedByUserId());
        vo.setOwnerUserId(entity.getOwnerUserId()); vo.setOwnerUnitId(entity.getOwnerUnitId()); vo.setVersion(entity.getVersion());
        vo.setCreateTime(entity.getCreateTime()); vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /** 将管道转换为 VO。 */
    public static CrmViews.PipelineVO pipeline(CrmPipeline entity) {
        CrmViews.PipelineVO vo = new CrmViews.PipelineVO();
        vo.setId(entity.getId()); vo.setCode(entity.getCode()); vo.setName(entity.getName());
        vo.setDefaultFlag(entity.getDefaultFlag()); vo.setSort(entity.getSort());
        return vo;
    }

    /** 将阶段转换为 VO。 */
    public static CrmViews.PipelineStageVO stage(CrmPipelineStage entity) {
        CrmViews.PipelineStageVO vo = new CrmViews.PipelineStageVO();
        vo.setId(entity.getId()); vo.setPipelineId(entity.getPipelineId()); vo.setStageCode(entity.getStageCode());
        vo.setStageName(entity.getStageName()); vo.setStageType(entity.getStageType());
        vo.setProbability(entity.getProbability()); vo.setSort(entity.getSort());
        return vo;
    }

    /** 将阶段历史转换为 VO。 */
    public static CrmViews.StageHistoryVO history(CrmOpportunityStageHistory entity) {
        CrmViews.StageHistoryVO vo = new CrmViews.StageHistoryVO();
        vo.setId(entity.getId()); vo.setOpportunityId(entity.getOpportunityId()); vo.setFromStageId(entity.getFromStageId());
        vo.setToStageId(entity.getToStageId()); vo.setFromStatus(entity.getFromStatus()); vo.setToStatus(entity.getToStatus());
        vo.setChangeReason(entity.getChangeReason()); vo.setChangedByUserId(entity.getChangedByUserId());
        vo.setChangedTime(entity.getChangedTime());
        return vo;
    }
}
