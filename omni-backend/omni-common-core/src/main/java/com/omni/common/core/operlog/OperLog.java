package com.omni.common.core.operlog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 * <p>标注在 Controller 方法上，AOP 切面 {@code OperLogAspect} 自动采集请求上下文、
 * 实体变更快照并通过 RocketMQ 异步发送操作日志到日志消费端。</p>
 *
 * <p>生效机制：
 * {@code @OperLog} → {@code OperLogAspect.around()} → 采集请求参数/响应状态/执行耗时 →
 * 通过 {@code EntityDiffer} 生成变更快照 diff → 构建 {@link OperLogMessage} →
 * {@code OperLogProducer.send()} → RocketMQ → 消费端写入 {@code sys_oper_log} 表。</p>
 *
 * @author Omni-Stack Team
 * @see OperType
 * @see OperLogMessage
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /**
     * 业务模块名称，如“字典类型管理”“用户管理”。
     * <p>用于在操作日志列表中分组和筛选，建议使用中文名称。</p>
     */
    String module() default "";
    
    /**
     * 操作类型，决定切面是否采集实体变更快照。
     * <p>CREATE/UPDATE/DELETE 类型会触发 diff 采集；QUERY/EXPORT/IMPORT 类型仅记录请求上下文。</p>
     */
    OperType operType();
    
    /**
     * 目标实体类，用于 AOP 自动 diff 变更快照。
     * <p>仅 CREATE/UPDATE/DELETE 类型需要指定，切面会通过此类反射获取字段列表生成快照。
     * QUERY/EXPORT/IMPORT 类型无需指定，默认值为 {@code Object.class}。</p>
     */
    Class<?> entityClass() default Object.class;
    
    /**
     * SpEL 表达式，用于从方法参数或返回值中提取实体 ID。
     * <p>示例：{@code "#id"}（从方法参数提取）、{@code "#result.data.id"}（从返回值提取）。
     * 提取的 ID 存储在 {@code sys_oper_log.biz_id} 字段中。</p>
     */
    String idExpr() default "";

    /**
     * 是否记录请求参数。
     * <p>包含大段文本或不适合进入审计日志的接口可显式关闭。</p>
     *
     * @return {@code true} 表示记录脱敏后的请求参数
     */
    boolean recordParams() default true;

    /**
     * 是否记录实体变更快照。
     * <p>CRM 等包含个人信息的业务默认应关闭，仅保留操作元数据。</p>
     *
     * @return {@code true} 表示采集并记录脱敏后的实体快照
     */
    boolean recordSnapshot() default true;

    /**
     * 额外排除的字段名。
     * <p>字段名不区分大小写，命中后统一替换为脱敏占位符。</p>
     *
     * @return 需要额外排除的字段名
     */
    String[] excludeFields() default {};
}
