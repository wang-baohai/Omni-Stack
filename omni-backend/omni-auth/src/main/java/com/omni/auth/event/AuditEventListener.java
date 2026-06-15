package com.omni.auth.event;

import com.omni.auth.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 审计事件监听器，异步将审计事件写入数据库。
 *
 * <p>所有异常均被捕获并记录警告日志，不会影响主业务流程。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    private final AuditLogService auditLogService;

    /**
     * 处理审计事件：异步写入数据库。
     *
     * @param event 审计事件
     */
    @Async("auditExecutor")
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        try {
            auditLogService.save(event);
        } catch (Exception e) {
            log.warn("审计日志写入失败: eventType={}, username={}, error={}",
                    event.getEventType(), event.getUsername(), e.getMessage());
        }

        // 账号锁定时输出 SMS 占位日志
        if (AuditEvent.ACCOUNT_LOCKED.equals(event.getEventType())) {
            log.warn("[SMS占位] 账户锁定通知: 用户 {} 在租户 {} 中连续登录失败，账户已被锁定",
                    event.getUsername(), event.getTenantId());
        }
    }
}
