package com.omni.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志归档实体（冷表），对应 {@code sys_oper_log_archive} 表。
 * <p>与热表 {@link SysOperLog} 结构基本相同，额外增加 {@code archivedTime} 字段记录归档时间。
 * 由定时任务将热表中超过保留期限的日志迁移至此表，用于长期存储和审计查询。</p>
 *
 * @author Omni-Stack Team
 * @see SysOperLog
 */
@TableName("sys_oper_log_archive")
public class SysOperLogArchive implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID，用于多租户数据隔离 */
    private Long tenantId;

    /** 操作用户名，记录执行操作的用户登录名 */
    private String operUsername;

    /** 操作时间，记录操作发生的精确时间 */
    private LocalDateTime operTime;

    /** 所属模块，如 "用户管理"、"角色管理"、"字典管理" 等 */
    private String module;

    /** 操作类型，如 "新增"、"修改"、"删除"、"查询"、"导入"、"导出" 等 */
    private String operType;

    /** HTTP 请求方法，如 GET、POST、PUT、DELETE */
    private String requestMethod;

    /** 请求 URL，完整的接口访问地址 */
    private String requestUrl;

    /** 请求参数，JSON 格式的入参内容，可能包含敏感信息需脱敏处理 */
    private String requestParams;

    /** 响应状态码，HTTP 响应状态（如 200-成功、500-服务器错误） */
    private Integer responseStatus;

    /** 客户端 IP 地址，记录操作来源的网络地址 */
    private String ipAddress;

    /** 浏览器 User-Agent，记录客户端浏览器及操作系统信息 */
    private String userAgent;

    /** 执行耗时（毫秒），记录接口从接收到响应的耗时 */
    private Long executionTime;

    /** 变更前值，JSON 格式，记录数据修改前的原始值，用于审计追溯 */
    private String oldValue;

    /** 变更后值，JSON 格式，记录数据修改后的新值，用于审计追溯 */
    private String newValue;

    /** 错误信息，操作失败时记录的异常堆栈或错误描述 */
    private String errorMsg;

    /** 创建时间，原始日志入库时间 */
    private LocalDateTime createTime;

    /** 归档时间，记录从热表迁移至冷表的时间 */
    private LocalDateTime archivedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getOperUsername() {
        return operUsername;
    }

    public void setOperUsername(String operUsername) {
        this.operUsername = operUsername;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getArchivedTime() {
        return archivedTime;
    }

    public void setArchivedTime(LocalDateTime archivedTime) {
        this.archivedTime = archivedTime;
    }
}
