package com.omni.srm.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * SRM 请求对象集合，嵌套内部类模式。
 *
 * @author Omni-Stack Team
 */
public final class SrmRequests {

    private SrmRequests() {
    }

    /**
     * 供应商分页查询参数。
     */
    @Data
    public static class SupplierQuery {
        @Size(max = 200)
        private String name;
        @Size(max = 50)
        private String categoryCode;
        @Size(max = 50)
        private String levelCode;
        @Size(max = 20)
        private String status;
        @Positive
        private Long ownerUserId;
        @Positive
        private Long ownerUnitId;
        @Min(value = 1, message = "页码必须大于等于 1")
        private Integer page = 1;
        @Min(value = 1, message = "每页条数必须大于等于 1")
        @Max(value = 100, message = "每页条数不能超过 100")
        private Integer size = 10;
    }

    /**
     * 创建供应商请求。
     */
    @Data
    public static class CreateSupplierRequest {
        @NotBlank(message = "供应商名称不能为空")
        @Size(max = 200, message = "供应商名称不能超过 200 字")
        private String name;

        @NotBlank(message = "供应商类型不能为空")
        @Size(max = 50)
        private String supplierType;

        @Size(max = 50)
        private String industryCode;
        @Size(max = 50)
        private String creditCode;
        @Size(max = 300)
        private String website;
        @Size(max = 32)
        private String phone;
        @Email
        @Size(max = 200)
        private String email;
        @Size(max = 100)
        private String region;
        @Size(max = 500)
        private String address;
        @Size(max = 50)
        private String categoryCode;
        @Positive
        private Long ownerUserId;
    }

    /**
     * 更新供应商请求。
     */
    @Data
    public static class UpdateSupplierRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;

        @Size(max = 200, message = "供应商名称不能超过 200 字")
        @Pattern(regexp = ".*\\S.*")
        private String name;
        @Size(max = 50)
        @Pattern(regexp = ".*\\S.*")
        private String supplierType;
        @Size(max = 50)
        private String industryCode;
        @Size(max = 50)
        private String creditCode;
        @Size(max = 300)
        private String website;
        @Size(max = 32)
        private String phone;
        @Email
        @Size(max = 200)
        private String email;
        @Size(max = 100)
        private String region;
        @Size(max = 500)
        private String address;
        @Size(max = 50)
        private String categoryCode;
        @Positive
        private Long ownerUserId;
    }

    /**
     * 供应商负责人转移请求。
     */
    @Data
    public static class TransferOwnerRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;

        @NotNull(message = "负责人不能为空")
        @Positive
        private Long ownerUserId;
    }

    /**
     * 状态变更请求（审核/冻结/黑名单等）。
     */
    @Data
    public static class StatusRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;
        @Size(max = 500)
        private String reason;
    }

    /**
     * 删除请求。
     */
    @Data
    public static class DeleteRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;
    }

    /**
     * 创建联系人请求。
     */
    @Data
    public static class CreateContactRequest {
        @NotBlank(message = "联系人姓名不能为空")
        @Size(max = 100)
        private String name;
        @Size(max = 100)
        private String department;
        @Size(max = 100)
        private String jobTitle;
        @Size(max = 32)
        private String mobile;
        @Size(max = 32)
        private String phone;
        @Email
        @Size(max = 200)
        private String email;
        @Size(max = 50)
        private String decisionRole;
        private Boolean primaryFlag;
    }

    /**
     * 更新联系人请求。
     */
    @Data
    public static class UpdateContactRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;
        @Size(max = 100)
        @Pattern(regexp = ".*\\S.*")
        private String name;
        @Size(max = 100)
        private String department;
        @Size(max = 100)
        private String jobTitle;
        @Size(max = 32)
        private String mobile;
        @Size(max = 32)
        private String phone;
        @Email
        @Size(max = 200)
        private String email;
        @Size(max = 50)
        private String decisionRole;
        private Boolean primaryFlag;
    }

    /**
     * 创建资质请求。
     */
    @Data
    public static class CreateQualificationRequest {
        @NotBlank(message = "资质名称不能为空")
        @Size(max = 200)
        private String qualificationName;
        @Size(max = 100)
        private String certificateNo;
        @Size(max = 200)
        private String issuingAuthority;
        private LocalDate issueDate;
        private LocalDate expiryDate;
    }

    /**
     * 更新资质请求。
     */
    @Data
    public static class UpdateQualificationRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;
        @Size(max = 200)
        @Pattern(regexp = ".*\\S.*")
        private String qualificationName;
        @Size(max = 100)
        private String certificateNo;
        @Size(max = 200)
        private String issuingAuthority;
        private LocalDate issueDate;
        private LocalDate expiryDate;
    }

    /**
     * 创建银行账户请求。
     */
    @Data
    public static class CreateBankAccountRequest {
        @NotBlank(message = "账户名不能为空")
        @Size(max = 200)
        private String accountName;
        @NotBlank(message = "账号不能为空")
        @Size(max = 100)
        private String accountNo;
        @NotBlank(message = "银行名称不能为空")
        @Size(max = 200)
        private String bankName;
        @Size(max = 200)
        private String bankBranch;
        @Size(max = 50)
        private String bankCode;
        private Boolean primaryFlag;
    }

    /**
     * 更新银行账户请求。
     */
    @Data
    public static class UpdateBankAccountRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;
        @Size(max = 200)
        @Pattern(regexp = ".*\\S.*")
        private String accountName;
        @Size(max = 100)
        @Pattern(regexp = ".*\\S.*")
        private String accountNo;
        @Size(max = 200)
        @Pattern(regexp = ".*\\S.*")
        private String bankName;
        @Size(max = 200)
        private String bankBranch;
        @Size(max = 50)
        private String bankCode;
        private Boolean primaryFlag;
    }

    /**
     * 创建评估请求。
     */
    @Data
    public static class CreateEvaluationRequest {
        @NotNull(message = "供应商 ID 不能为空")
        @Positive
        private Long supplierId;
        @NotBlank(message = "评估周期不能为空")
        @Size(max = 50)
        private String evaluationPeriod;
        @NotEmpty(message = "评分明细不能为空")
        @Size(max = 100)
        @Valid
        private List<EvaluationItemInput> items;
    }

    /**
     * 评估评分输入。
     */
    @Data
    public static class EvaluationItemInput {
        @NotNull(message = "维度 ID 不能为空")
        @Positive
        private Long dimensionId;
        @NotNull(message = "评分不能为空")
        @DecimalMin(value = "1.0", message = "评分不能低于 1 分")
        @DecimalMax(value = "5.0", message = "评分不能高于 5 分")
        @Digits(integer = 1, fraction = 1, message = "评分最多保留一位小数")
        private BigDecimal score;
        @Size(max = 200)
        private String indicatorName;
        @Size(max = 500)
        private String remark;
    }

    /**
     * 更新风险指标请求。
     */
    @Data
    public static class UpdateRiskIndicatorRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;
        /** 选中的评分标准 ID。 */
        private Long criterionId;
        @Size(max = 200)
        private String indicatorValue;
        @Size(max = 20)
        private String riskLevel;
        @Size(max = 500)
        private String remark;
    }

    /**
     * 创建风险评估请求。
     */
    @Data
    public static class CreateRiskAssessmentRequest {
        @Size(max = 500)
        private String remark;
    }

    /**
     * 门户入驻请求。
     */
    @Data
    public static class EnrollRequest {
        /** 客户端生成的幂等请求 ID。 */
        @NotBlank(message = "请求 ID 不能为空")
        @Size(max = 64, message = "请求 ID 不能超过 64 字")
        private String requestId;

        /** 租户专属邀请令牌。 */
        @NotBlank(message = "邀请令牌不能为空")
        @Size(max = 256, message = "邀请令牌不能超过 256 字")
        private String inviteToken;

        @NotBlank(message = "供应商名称不能为空")
        @Size(max = 200, message = "供应商名称不能超过 200 字")
        private String name;

        @NotBlank(message = "统一社会信用代码不能为空")
        @Size(max = 50, message = "统一社会信用代码不能超过 50 字")
        private String creditCode;
        @Size(max = 50)
        private String supplierType;
        @Size(max = 50)
        private String industryCode;
        @Size(max = 300)
        private String website;
        @Size(max = 32)
        private String phone;
        @Email
        @Size(max = 200)
        private String email;
        @Size(max = 100)
        private String region;
        @Size(max = 500)
        private String address;

        /**
         * 拒绝请求体中的任何未声明字段，尤其防止客户端伪造 tenantId/userId。
         *
         * @param propertyName 未声明字段名
         * @param ignored 字段值
         */
        @JsonAnySetter
        public void rejectUnknownProperty(String propertyName, Object ignored) {
            throw new IllegalArgumentException("门户入驻请求包含不允许字段: " + propertyName);
        }
    }

    /**
     * 门户企业信息更新请求。
     */
    @Data
    public static class UpdateProfileRequest {
        @NotNull(message = "版本号不能为空")
        @Min(0)
        private Integer version;
        @Size(max = 200)
        @Pattern(regexp = ".*\\S.*")
        private String name;
        @Size(max = 300)
        private String website;
        @Size(max = 32)
        private String phone;
        @Email
        @Size(max = 200)
        private String email;
        @Size(max = 100)
        private String region;
        @Size(max = 500)
        private String address;
    }

    /**
     * 创建邀请请求。
     */
    @Data
    public static class CreateInviteRequest {
        @Min(value = 1, message = "最大使用次数不能小于 1")
        @Max(value = 1000, message = "最大使用次数不能超过 1000")
        private Integer maxUses;

        @Min(value = 1, message = "有效小时数不能小于 1")
        @Max(value = 8760, message = "有效小时数不能超过 8760")
        private Integer expiresHours;
    }
}
