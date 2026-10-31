# 4. 操作日志：记录动作，也守住敏感信息

运行日志解释技术过程；操作日志记录业务动作。项目用 @OperLog 注解声明采集点，由切面读取必要信息、计算差异并清洗内容，再由生产者发送。Auth 的认证行为单独进入 sys_audit_log，不引入操作日志模块。

## 注解为什么能减少重复代码

切面相当于业务方法外面的一层统一处理：在方法执行前后收集信息，业务本身仍由原方法完成。它需要实际经过 Spring 代理；同类内部直接互调不会自动触发这层代理。快照配置不足、实体无法读取或没有可读变化时，差异可能为空。

## 记录怎样送到查询页面

下面是操作日志生产者的完整发送方法：

<!-- omni:source path="omni-backend/omni-common-operlog/src/main/java/com/omni/common/operlog/producer/OperLogProducer.java" -->
```java
    public void send(OperLogMessage message) {
        try {
            Long tenantId = message.getTenantId();
            if (reliableRelay != null) {
                reliableRelay.send(BINDING_NAME, message, tenantId, message.getEventId());
            } else {
                streamBridge.send(BINDING_NAME, message);
            }
        } catch (Exception e) {
            log.warn("操作日志：MQ 消息发送失败: {}", e.getMessage(), e);
        }
    }
```

存在可靠消息组件时优先写 Outbox；组件没有接入时才直发。注意 catch：发送异常被记录，业务返回不会因此自动失败；Outbox 写失败时也不会偷偷再走直发。不能据此宣称“每次业务成功都一定有日志”或“控制器后处理与已经结束的业务事务必定原子提交”。

## 脱敏是写入前的责任

清洗器明确列出必须遮盖的字段，下面是实际名单：

<!-- omni:source path="omni-backend/omni-common-operlog/src/main/java/com/omni/common/operlog/sanitize/OperLogSanitizer.java" -->
```java
    private static final Set<String> REDACTED_FIELDS = Set.of(
            "password", "passwd", "pwd", "credential", "authorization", "token",
            "accesstoken", "refreshtoken", "secret", "clientsecret", "idcard",
            "identityno", "address", "content", "remark", "remarks", "note", "notes",
            "description", "customername", "contactname", "leadname", "companyname",
            "fullname", "opportunityname", "subject", "reason"
    );
```

它还处理电话、邮箱和常见凭证文本，快照、参数和异常内容都需要经过适用的清洗路径。字段名单不可能认识所有业务秘密；新加敏感字段时仍要维护规则和负例，不能把 JSON 格式当成脱敏证明。

不要记录密码、令牌、Cookie、Authorization、完整请求体和消息体来“方便排障”。运行日志、数据库审计、截图和导出各有边界；一种记录经过清洗，不代表另一种出口也安全。

下一篇用[真实页面认识查询与恢复](logging-practice.md)。
