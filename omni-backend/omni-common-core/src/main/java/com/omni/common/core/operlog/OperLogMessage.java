package com.omni.common.core.operlog;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志 MQ 传输消息。
 * <p>由 {@code OperLogAspect} 切面构建，通过 RocketMQ 异步发送至日志消费端
 * （{@code OperLogConsumer}），消费端将消息写入 {@code sys_oper_log} 表。</p>
 *
 * <p>消息传递链路：
 * Controller 方法执行 → {@code OperLogAspect} 采集上下文 → 构建 {@code OperLogMessage} →
 * {@code OperLogProducer.send()} → RocketMQ Topic → {@code OperLogConsumer.accept()} →
 * INSERT INTO sys_oper_log</p>
 *
 * @author Omni-Stack Team
 * @see OperLog
 */
public class OperLogMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 操作人用户名 */
    private String operUsername;

    /** 租户ID */
    private Long tenantId;

    /** 操作时间 */
    private LocalDateTime operTime;

    /** 业务模块名称 */
    private String module;

    /** 操作类型 */
    private String operType;

    /** HTTP方法 */
    private String requestMethod;

    /** 请求URL */
    private String requestUrl;

    /** 请求参数JSON */
    private String requestParams;

    /** 响应状态码 */
    private Integer responseStatus;

    /** 客户端IP */
    private String ipAddress;

    /** User-Agent */
    private String userAgent;

    /** 执行耗时（毫秒） */
    private Long executionTime;

    /** 变更前值快照JSON */
    private String oldValue;

    /** 变更后值快照JSON */
    private String newValue;

    /** 错误信息 */
    private String errorMsg;

    public OperLogMessage() {
    }

    public String getOperUsername() {
        return operUsername;
    }

    public void setOperUsername(String operUsername) {
        this.operUsername = operUsername;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDateTime getOperTime() {
        return operTime;
    }

    public void setOperTime(LocalDateTime operTime) {
        this.operTime = operTime;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public String getOperType() {
        return operType;
    }

    public void setOperType(String operType) {
        this.operType = operType;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(String requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public String getRequestParams() {
        return requestParams;
    }

    public void setRequestParams(String requestParams) {
        this.requestParams = requestParams;
    }

    public Integer getResponseStatus() {
        return responseStatus;
    }

    public void setResponseStatus(Integer responseStatus) {
        this.responseStatus = responseStatus;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public Long getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(Long executionTime) {
        this.executionTime = executionTime;
    }

    public String getOldValue() {
        return oldValue;
    }

    public void setOldValue(String oldValue) {
        this.oldValue = oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public void setNewValue(String newValue) {
        this.newValue = newValue;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
