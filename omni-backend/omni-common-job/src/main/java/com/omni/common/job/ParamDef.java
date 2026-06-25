package com.omni.common.job;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 系统任务参数定义注解。
 * <p>
 * 嵌套在 {@link SystemJobMeta} 的 {@code params} 属性中，
 * 每个 {@code @ParamDef} 描述一个可配置参数。
 * {@link SystemJobRegistry} 在启动时读取这些定义，构建参数元数据列表，
 * 供前端管理界面动态渲染参数输入表单。</p>
 *
 * <p>支持的参数类型：</p>
 * <ul>
 *   <li>{@code "string"} — 文本输入框</li>
 *   <li>{@code "number"} — 数字输入框（受 {@link #min()} / {@link #max()} 约束）</li>
 *   <li>{@code "boolean"} — 开关控件</li>
 * </ul>
 *
 * @author Omni-Stack Team
 * @see SystemJobMeta
 * @see SystemJobRegistry
 */
@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface ParamDef {

    /**
     * 参数名。
     * <p>对应 {@code XxlJobHelper.getJobParam()} 中的 key，也是 JSON 参数对象的属性名。</p>
     */
    String name();

    /** 参数标签（显示在管理界面表单中，便于运维人员识别） */
    String label();

    /**
     * 参数类型。
     * <p>可选值: {@code "string"}、{@code "number"}、{@code "boolean"}。</p>
     */
    String type() default "string";

    /** 默认值（字符串形式，运行时按 {@link #type()} 进行类型转换） */
    String defaultValue() default "";

    /** 是否必填（为 {@code true} 时前端表单会标注必填标记） */
    boolean required() default false;

    /** 最小值（仅 {@code type="number"} 时有效，用于前端校验） */
    double min() default Double.MIN_VALUE;

    /** 最大值（仅 {@code type="number"} 时有效，用于前端校验） */
    double max() default Double.MAX_VALUE;
}
