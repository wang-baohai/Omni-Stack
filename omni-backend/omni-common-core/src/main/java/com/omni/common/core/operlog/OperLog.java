package com.omni.common.core.operlog;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解。
 * <p>标注在 Controller 方法上，AOP 切面自动采集请求上下文、实体变更快照并通过 MQ 异步发送操作日志。</p>
 *
 * @author Omni-Stack Team
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {

    /**
     * 业务模块名称，如"字典类型管理"。
     */
    String module() default "";

    /**
     * 操作类型。
     */
    OperType operType();

    /**
     * 目标实体类，用于 AOP 自动 diff 变更快照。
     * <p>QUERY/EXPORT/IMPORT 类型无需指定。</p>
     */
    Class<?> entityClass() default Object.class;

    /**
     * SpEL 表达式，用于从方法参数或返回值中提取实体 ID。
     * <p>示例：{@code "#id"}、{@code "#result.data.id"}。</p>
     */
    String idExpr() default "";
}
