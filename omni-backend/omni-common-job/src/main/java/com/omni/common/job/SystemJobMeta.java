package com.omni.common.job;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 系统级定时任务元数据注解。
 * <p>
 * 标注在 {@code @XxlJob} 方法上，声明该 Handler 的名称、描述、默认 Cron、
 * 路由策略及参数定义，供 {@link SystemJobRegistry} 在启动时收集并在管理界面中展示。
 * </p>
 *
 * <p>示例：</p>
 * <pre>{@code
 * @XxlJob("operLogArchiveHandler")
 * @SystemJobMeta(
 *     name = "操作日志归档",
 *     description = "将超过保留天数的热表记录迁移到冷表",
 *     defaultCron = "0 0 2 * * ?",
 *     params = {
 *         @ParamDef(name = "retentionDays", label = "保留天数",
 *                   type = "number", defaultValue = "180", required = true, min = 1, max = 3650)
 *     }
 * )
 * public void archive() { ... }
 * }</pre>
 *
 * @author Omni-Stack Team
 * @see SystemJobRegistry
 * @see ParamDef
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SystemJobMeta {

    /** 任务名称（显示在管理界面，建议简洁明了） */
    String name();

    /** 任务描述（显示在管理界面，可说明任务的业务作用及注意事项） */
    String description() default "";

    /**
     * 默认 Cron 表达式。
     * <p>在管理界面创建任务时作为初始值，运维人员可在界面上修改。</p>
     */
    String defaultCron() default "";

    /**
     * XXL-JOB 路由策略。
     * <p>
     * 可选值: FIRST, LAST, ROUND, RANDOM, CONSISTENT_HASH,
     * LEAST_FREQUENTLY_USED, LEAST_RECENTLY_USED, FAILOVER, BUSYOVER, SHARDING_BROADCAST
     * </p>
     *
     * @see <a href="https://www.xuxueli.com/xxl-job/">XXL-JOB 官方文档</a>
     */
    String routeStrategy() default "FIRST";

    /**
     * 参数定义数组。
     * <p>每个元素描述一个可配置参数，前端根据这些定义动态渲染输入表单。</p>
     *
     * @see ParamDef
     */
    ParamDef[] params() default {};
}
