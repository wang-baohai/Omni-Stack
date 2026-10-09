package com.omni.auth.dto;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录记录视图对象（D02，安全审计的只读投影）。
 * <p>仅暴露白名单字段，不含 extra 原文、原始 IP/UA、OAuth code/token/state、cookie、sessionId、
 * 密码或验证码值。历史缺映射时 method/provider/reasonCode 归 UNKNOWN 或 null。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class LoginRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 记录 ID（沿用审计主键，Long，与既有审计页一致） */
    private Long id;

    /** 事件发生时间 */
    private LocalDateTime createTime;

    /** 用户名（失败输入也限长安全展示） */
    private String username;

    /** 结果：SUCCESS | FAILURE */
    private String result;

    /** 方式：PASSWORD | SESSION | SOCIAL | UNKNOWN */
    private String method;

    /** 社交登录提供方（gitee/github/google），非社交为 null */
    private String provider;

    /** 失败原因码白名单值（成功为 null） */
    private String reasonCode;

    /** 脱敏后的 IP（IPv4 隐末段/IPv6 隐后64位/非法 unavailable） */
    private String ipMasked;

    /** 客户端摘要（浏览器/平台类别 + 有界版本），不含可注入原文 */
    private String clientSummary;
}
