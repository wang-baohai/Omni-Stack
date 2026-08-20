package com.omni.common.core.tenant;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * 跨服务租户初始化事件契约。
 *
 * <p>请求事件只包含租户标识、模块集合和追踪字段，禁止携带管理员密码、密码哈希、令牌或联系人隐私信息。</p>
 */
public final class TenantProvisionContracts {

    /** 租户初始化请求事件类型。 */
    public static final String REQUESTED_EVENT_TYPE = "tenant.provision-requested.v1";
    /** 租户初始化结果事件类型。 */
    public static final String RESULT_EVENT_TYPE = "tenant.provision-result.v1";

    /** 工具类禁止实例化。 */
    private TenantProvisionContracts() {
    }

    /**
     * Auth 在本地初始化与 Outbox 同一事务中发布的模块初始化请求。
     *
     * @param eventId    事件唯一 ID
     * @param requestId  本次初始化请求 ID
     * @param tenantId   租户 ID
     * @param tenantCode 租户稳定编码
     * @param tenantName 租户显示名称
     * @param moduleIds  需要异步初始化的模块 ID
     * @param occurredAt 事件发生时间
     */
    public record ProvisionRequestedEvent(
            String eventId,
            String requestId,
            Long tenantId,
            String tenantCode,
            String tenantName,
            List<String> moduleIds,
            Instant occurredAt) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 固化模块集合，避免发布后被调用方修改。
         */
        public ProvisionRequestedEvent {
            moduleIds = moduleIds == null ? List.of() : List.copyOf(moduleIds);
        }
    }

    /**
     * 单个模块完成幂等初始化后的结果事件。
     *
     * @param eventId     结果事件唯一 ID
     * @param requestId   原始请求 ID
     * @param tenantId    租户 ID
     * @param moduleId    模块 ID
     * @param status      模块结果状态
     * @param errorCode   稳定错误码，成功时为 null
     * @param errorMessage 脱敏错误摘要，成功时为 null
     * @param occurredAt  事件发生时间
     */
    public record ProvisionResultEvent(
            String eventId,
            String requestId,
            Long tenantId,
            String moduleId,
            ModuleResultStatus status,
            String errorCode,
            String errorMessage,
            Instant occurredAt) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }

    /**
     * 模块初始化终态。
     */
    public enum ModuleResultStatus {
        /** 初始化成功。 */
        SUCCESS,
        /** 初始化失败，可由相同 requestId 重试。 */
        FAILED
    }
}
