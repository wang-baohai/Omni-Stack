package com.omni.base.job;

import com.omni.common.core.job.UserJobHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 用户任务处理器注册表。
 * <p>
 * 通过 Spring 自动注入收集所有 {@link UserJobHandler} 实现 Bean，
 * Bean 名称即为任务类型编码（{@code type_code}），用于 {@code userJobExecuteHandler}
 * 根据 {@code jobType} 路由到对应的 Handler 执行。
 * </p>
 * <p>
 * 开发者新增任务类型只需：
 * <ol>
 *     <li>在 {@code sys_user_job_type} 表注册类型定义（含参数 JSON Schema）</li>
 *     <li>实现 {@link UserJobHandler} 并以类型编码作为 Bean 名称注册</li>
 * </ol>
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
public class UserJobHandlerRegistry {

    /**
     * Spring 自动注入所有 UserJobHandler 实现 Bean。
     * <p>Map 的 key 为 Bean 名称（即 type_code），value 为 Handler 实例。</p>
     */
    private final Map<String, UserJobHandler> handlers;

    public UserJobHandlerRegistry(Map<String, UserJobHandler> handlers) {
        this.handlers = handlers;
        log.info("已注册 {} 种用户任务处理器：{}", handlers.size(), handlers.keySet());
    }

    /**
     * 根据任务类型编码获取对应的 Handler。
     *
     * @param jobType 任务类型编码（对应 sys_user_job_type.type_code）
     * @return 对应的 Handler，若未注册则返回 {@code null}
     */
    public UserJobHandler getHandler(String jobType) {
        return handlers.get(jobType);
    }

    /**
     * 判断是否存在指定类型的 Handler。
     *
     * @param jobType 任务类型编码
     * @return 是否已注册
     */
    public boolean hasHandler(String jobType) {
        return handlers.containsKey(jobType);
    }
}
