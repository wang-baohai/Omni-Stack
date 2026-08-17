package com.omni.workflow.dto.internal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 内部服务发起流程请求。
 *
 * @author Omni-Stack Team
 */
@Data
public class InternalStartProcessRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 调用方请求 ID，用于跨服务链路追踪和幂等关联。 */
    @NotBlank(message = "请求 ID 不能为空")
    @Size(max = 64, message = "请求 ID 长度不能超过 64")
    private String requestId;

    /** 租户 ID。 */
    @NotNull(message = "租户 ID 不能为空")
    @Positive(message = "租户 ID 必须为正整数")
    private Long tenantId;

    /** 已发布的流程模型版本 ID。 */
    @NotNull(message = "流程模型版本 ID 不能为空")
    @Positive(message = "流程模型版本 ID 必须为正整数")
    private Long modelVersionId;

    /** 调用方业务类型。 */
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 100, message = "业务类型长度不能超过 100")
    private String businessType;

    /** 调用方业务主键。 */
    @NotBlank(message = "业务主键不能为空")
    @Size(max = 255, message = "业务主键长度不能超过 255")
    private String businessKey;

    /** 流程发起人用户 ID。 */
    @NotNull(message = "发起人用户 ID 不能为空")
    @Positive(message = "发起人用户 ID 必须为正整数")
    private Long startUserId;

    /** 流程发起人用户名。 */
    @Size(max = 100, message = "发起人用户名长度不能超过 100")
    private String startUserName;

    /** 流程标题，为空时由服务按业务类型和业务主键生成。 */
    @Size(max = 500, message = "流程标题长度不能超过 500")
    private String title;

    /** 流程变量。 */
    private Map<String, Object> variables;
}
