package com.omni.srm.controller;

import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperType;
import com.omni.common.core.result.R;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.service.RiskIndicatorConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SRM 风险指标配置控制器。
 * <p>管理指标类型、评分标准和得分阈值。</p>
 */
@RestController
@RequestMapping("/api/srm/risk/config")
@RequiredArgsConstructor
@Validated
public class RiskIndicatorConfigController {

    private final RiskIndicatorConfigService configService;

    /** 查询所有启用的指标类型及其评分标准。 */
    @GetMapping("/types")
    @PreAuthorize("hasAuthority('srm:risk:config:list')")
    public R<List<SrmViews.RiskIndicatorTypeVO>> listTypes() {
        return R.ok(configService.listIndicatorTypes());
    }

    /** 创建指标类型。 */
    @PostMapping("/types")
    @PreAuthorize("hasAuthority('srm:risk:config:update')")
    @OperLog(module = "SRM风险配置", operType = OperType.CREATE, recordSnapshot = false)
    public R<SrmViews.RiskIndicatorTypeVO> createType(@RequestBody SrmViews.RiskIndicatorTypeVO request) {
        return R.ok(configService.createIndicatorType(request));
    }

    /** 更新指标类型。 */
    @PutMapping("/types/{id}")
    @PreAuthorize("hasAuthority('srm:risk:config:update')")
    @OperLog(module = "SRM风险配置", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.RiskIndicatorTypeVO> updateType(@PathVariable Long id,
                                                      @RequestBody SrmViews.RiskIndicatorTypeVO request) {
        return R.ok(configService.updateIndicatorType(id, request));
    }

    /** 删除指标类型。 */
    @DeleteMapping("/types/{id}")
    @PreAuthorize("hasAuthority('srm:risk:config:update')")
    @OperLog(module = "SRM风险配置", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> deleteType(@PathVariable Long id) {
        configService.deleteIndicatorType(id);
        return R.ok();
    }

    /** 创建评分标准。 */
    @PostMapping("/criteria")
    @PreAuthorize("hasAuthority('srm:risk:config:update')")
    @OperLog(module = "SRM风险配置", operType = OperType.CREATE, recordSnapshot = false)
    public R<SrmViews.RiskCriterionVO> createCriterion(@RequestBody SrmViews.RiskCriterionVO request) {
        return R.ok(configService.createCriterion(request));
    }

    /** 更新评分标准。 */
    @PutMapping("/criteria/{id}")
    @PreAuthorize("hasAuthority('srm:risk:config:update')")
    @OperLog(module = "SRM风险配置", operType = OperType.UPDATE, idExpr = "#id")
    public R<SrmViews.RiskCriterionVO> updateCriterion(@PathVariable Long id,
                                                       @RequestBody SrmViews.RiskCriterionVO request) {
        return R.ok(configService.updateCriterion(id, request));
    }

    /** 删除评分标准。 */
    @DeleteMapping("/criteria/{id}")
    @PreAuthorize("hasAuthority('srm:risk:config:update')")
    @OperLog(module = "SRM风险配置", operType = OperType.DELETE, idExpr = "#id")
    public R<Void> deleteCriterion(@PathVariable Long id) {
        configService.deleteCriterion(id);
        return R.ok();
    }

    /** 查询得分阈值列表。 */
    @GetMapping("/thresholds")
    @PreAuthorize("hasAuthority('srm:risk:config:list')")
    public R<List<SrmViews.RiskScoreThresholdVO>> listThresholds() {
        return R.ok(configService.listThresholds());
    }

    /** 批量更新得分阈值。 */
    @PutMapping("/thresholds")
    @PreAuthorize("hasAuthority('srm:risk:config:update')")
    @OperLog(module = "SRM风险配置", operType = OperType.UPDATE, recordSnapshot = false)
    public R<List<SrmViews.RiskScoreThresholdVO>> updateThresholds(
            @RequestBody List<SrmViews.RiskScoreThresholdVO> requests) {
        return R.ok(configService.updateThresholds(requests));
    }
}
