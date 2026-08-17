package com.omni.procurement.dto;

import com.omni.procurement.entity.ProcApprovalRoute;
import com.omni.procurement.entity.ProcMaterial;
import com.omni.procurement.entity.ProcMaterialCategory;
import com.omni.procurement.entity.ProcRequisition;
import com.omni.procurement.entity.ProcRequisitionLine;

import java.util.List;

/**
 * 采购主数据实体到响应视图的转换器。
 *
 * @author Omni-Stack Team
 */
public final class ProcViewAssembler {

    private ProcViewAssembler() {
    }

    /**
     * 转换品类节点。
     *
     * @param category 品类实体
     * @return 品类视图
     */
    public static MaterialViews.CategoryVO category(ProcMaterialCategory category) {
        MaterialViews.CategoryVO result = new MaterialViews.CategoryVO();
        result.setId(category.getId());
        result.setParentId(category.getParentId());
        result.setCategoryCode(category.getCategoryCode());
        result.setCategoryName(category.getCategoryName());
        result.setSort(category.getSort());
        result.setStatus(category.getStatus());
        result.setVersion(category.getVersion());
        return result;
    }

    /**
     * 转换物料。
     *
     * @param material 物料实体
     * @param category 品类实体
     * @return 物料视图
     */
    public static MaterialViews.MaterialVO material(ProcMaterial material, ProcMaterialCategory category) {
        MaterialViews.MaterialVO result = new MaterialViews.MaterialVO();
        result.setId(material.getId());
        result.setCategoryId(material.getCategoryId());
        result.setCategoryCode(category == null ? null : category.getCategoryCode());
        result.setCategoryName(category == null ? null : category.getCategoryName());
        result.setMaterialCode(material.getMaterialCode());
        result.setMaterialName(material.getMaterialName());
        result.setSpecification(material.getSpecification());
        result.setUnit(material.getUnit());
        result.setAssetManaged(material.getAssetManaged());
        result.setStatus(material.getStatus());
        result.setVersion(material.getVersion());
        result.setCreateTime(material.getCreateTime());
        result.setUpdateTime(material.getUpdateTime());
        return result;
    }

    /**
     * 转换审批路由。
     *
     * @param route 审批路由实体
     * @return 审批路由视图
     */
    public static ApprovalRouteViews.RouteVO route(ProcApprovalRoute route) {
        ApprovalRouteViews.RouteVO result = new ApprovalRouteViews.RouteVO();
        result.setId(route.getId());
        result.setRouteCode(route.getRouteCode());
        result.setCategoryCode(route.getCategoryCode());
        result.setMinAmount(route.getMinAmount());
        result.setMaxAmount(route.getMaxAmount());
        result.setModelVersionId(route.getModelVersionId());
        result.setPriority(route.getPriority());
        result.setStatus(route.getStatus());
        result.setVersion(route.getVersion());
        result.setCreateTime(route.getCreateTime());
        result.setUpdateTime(route.getUpdateTime());
        return result;
    }

    /**
     * 转换请购列表摘要。
     *
     * @param requisition 请购实体
     * @return 请购摘要
     */
    public static RequisitionViews.Summary requisitionSummary(ProcRequisition requisition) {
        RequisitionViews.Summary result = new RequisitionViews.Summary();
        fillRequisitionSummary(result, requisition);
        return result;
    }

    /**
     * 转换请购详情。
     *
     * @param requisition 请购实体
     * @param lines 请购明细
     * @return 请购详情
     */
    public static RequisitionViews.Detail requisitionDetail(ProcRequisition requisition,
                                                             List<ProcRequisitionLine> lines) {
        RequisitionViews.Detail result = new RequisitionViews.Detail();
        fillRequisitionSummary(result, requisition);
        result.setReason(requisition.getReason());
        result.setWorkflowBusinessKey(requisition.getWorkflowBusinessKey());
        result.setWorkflowModelVersionId(requisition.getWorkflowModelVersionId());
        result.setProcessInstanceId(requisition.getProcessInstanceId());
        result.setApprovedTime(requisition.getApprovedTime());
        result.setWorkflowCompletedTime(requisition.getWorkflowCompletedTime());
        result.setLines(lines == null ? List.of() : lines.stream().map(ProcViewAssembler::requisitionLine).toList());
        return result;
    }

    /**
     * 转换请购明细行。
     *
     * @param line 明细实体
     * @return 明细视图
     */
    public static RequisitionViews.Line requisitionLine(ProcRequisitionLine line) {
        RequisitionViews.Line result = new RequisitionViews.Line();
        result.setId(line.getId());
        result.setLineNo(line.getLineNo());
        result.setMaterialId(line.getMaterialId());
        result.setMaterialCode(line.getMaterialCode());
        result.setMaterialName(line.getMaterialName());
        result.setCategoryCode(line.getCategoryCode());
        result.setUnit(line.getUnit());
        result.setQuantity(line.getQuantity());
        result.setEstimatedUnitPrice(line.getEstimatedUnitPrice());
        result.setEstimatedTotalPrice(line.getEstimatedTotalPrice());
        result.setRemark(line.getRemark());
        result.setVersion(line.getVersion());
        return result;
    }

    private static void fillRequisitionSummary(RequisitionViews.Summary result,
                                               ProcRequisition requisition) {
        result.setId(requisition.getId());
        result.setRequisitionNo(requisition.getRequisitionNo());
        result.setTitle(requisition.getTitle());
        result.setRequesterUserId(requisition.getRequesterUserId());
        result.setRequesterUnitId(requisition.getRequesterUnitId());
        result.setPrimaryCategoryCode(requisition.getPrimaryCategoryCode());
        result.setTotalAmount(requisition.getTotalAmount());
        result.setCurrencyCode(requisition.getCurrencyCode());
        result.setStatus(requisition.getStatus());
        result.setWorkflowStartStatus(requisition.getWorkflowStartStatus());
        result.setApprovalAttempt(requisition.getApprovalAttempt());
        result.setVersion(requisition.getVersion());
        result.setCreateTime(requisition.getCreateTime());
        result.setUpdateTime(requisition.getUpdateTime());
    }
}
