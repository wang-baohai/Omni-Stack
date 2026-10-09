package com.omni.auth.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录保护策略更新请求（D01）。
 * <p>仅允许收紧保护：阈值 3~10，窗口 3600~86400 秒。不提供关闭锁定/验证码或修改基础秘密的能力。
 * {@code expectedVersion} 为客户端读到的版本，服务端以乐观锁校验，陈旧返回 code409。</p>
 *
 * @author Omni-Stack Team
 */
@Data
public class LoginPolicyUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 窗口内锁定阈值（3~10） */
    @NotNull(message = "failureThreshold 不能为空")
    @Min(value = 3, message = "failureThreshold 最小为 3")
    @Max(value = 10, message = "failureThreshold 最大为 10")
    private Integer failureThreshold;

    /** 固定失败窗口秒数（3600~86400） */
    @NotNull(message = "failureWindowSeconds 不能为空")
    @Min(value = 3600, message = "failureWindowSeconds 最小为 3600")
    @Max(value = 86400, message = "failureWindowSeconds 最大为 86400")
    private Integer failureWindowSeconds;

    /** 乐观并发期望版本（首次创建传 0） */
    @NotNull(message = "expectedVersion 不能为空")
    @Min(value = 0, message = "expectedVersion 不能为负")
    private Long expectedVersion;
}
