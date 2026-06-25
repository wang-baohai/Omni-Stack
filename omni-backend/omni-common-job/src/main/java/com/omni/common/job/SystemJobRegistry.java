package com.omni.common.job;

import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统任务元数据注册中心。
 * <p>
 * 启动时通过 {@code @PostConstruct} 扫描所有 Spring Bean，收集
 * {@code @XxlJob} + {@link SystemJobMeta} 双注解标注的方法，
 * 将元数据存入内存 {@link #registry} 供 Controller 查询展示。
 * </p>
 *
 * <p>扫描逻辑：遍历所有 Bean 的 declared methods，
 * 同时检查 {@code @XxlJob} 和 {@code @SystemJobMeta} 注解，
 * 只有双注解并存时才注册到 registry（key 为 handlerName）。</p>
 *
 * @author Omni-Stack Team
 * @see SystemJobMeta
 * @see ParamDef
 */
@Slf4j
@Component
public class SystemJobRegistry implements ApplicationContextAware {

    private ApplicationContext applicationContext;

    private final Map<String, SystemJobInfo> registry = new LinkedHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext ctx) throws BeansException {
        this.applicationContext = ctx;
    }

    /**
     * 扫描所有 Bean，收集带 {@code @XxlJob} + {@code @SystemJobMeta} 的方法。
     * <p>通过 {@link AnnotationUtils#findAnnotation} 查找注解，
     * 提取 handlerName、任务名称、描述、默认 Cron、路由策略和参数定义，
     * 存入 {@link #registry}。</p>
     */
    @PostConstruct
    public void scan() {
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                continue;
            }
            Class<?> beanClass = bean.getClass();
            for (Method method : beanClass.getDeclaredMethods()) {
                XxlJob xxlJob = AnnotationUtils.findAnnotation(method, XxlJob.class);
                SystemJobMeta meta = AnnotationUtils.findAnnotation(method, SystemJobMeta.class);
                if (xxlJob != null && meta != null) {
                    String handlerName = xxlJob.value();
                    SystemJobInfo info = new SystemJobInfo();
                    info.setHandlerName(handlerName);
                    info.setName(meta.name());
                    info.setDescription(meta.description());
                    info.setDefaultCron(meta.defaultCron());
                    info.setRouteStrategy(meta.routeStrategy());
                    info.setParamDefs(buildParamDefs(meta.params()));
                    registry.put(handlerName, info);
                    log.info("系统任务注册: {} ({})", handlerName, meta.name());
                }
            }
        }
        log.info("系统任务扫描完成，共注册 {} 个 Handler", registry.size());
    }

    /**
     * 将 {@link ParamDef} 注解数组转换为 {@link ParamDefInfo} DTO 列表。
     *
     * @param defs ParamDef 注解数组
     * @return 参数定义 DTO 列表
     */
    private List<ParamDefInfo> buildParamDefs(ParamDef[] defs) {
        List<ParamDefInfo> list = new ArrayList<>();
        for (ParamDef d : defs) {
            ParamDefInfo info = new ParamDefInfo();
            info.setName(d.name());
            info.setLabel(d.label());
            info.setType(d.type());
            info.setDefaultValue(d.defaultValue());
            info.setRequired(d.required());
            info.setMin(d.min());
            info.setMax(d.max());
            list.add(info);
        }
        return list;
    }

    /**
     * 获取所有已注册的系统任务元数据。
     *
     * @return 不可变的 handlerName → {@link SystemJobInfo} 映射
     */
    public Map<String, SystemJobInfo> getAll() {
        return Collections.unmodifiableMap(registry);
    }

    /**
     * 根据 Handler 名称获取元数据。
     *
     * @param handlerName Handler 名称（即 {@code @XxlJob.value()}）
     * @return 系统任务元数据，未找到时返回 null
     */
    public SystemJobInfo get(String handlerName) {
        return registry.get(handlerName);
    }

    /**
     * 系统任务元数据 DTO。
     * <p>包含 Handler 名称、显示名称、描述、默认 Cron、路由策略和参数定义。</p>
     */
    @Data
    public static class SystemJobInfo {
        /** Handler 名称，对应 {@code @XxlJob.value()} */
        private String handlerName;
        /** 任务显示名称，用于管理界面展示 */
        private String name;
        /** 任务描述 */
        private String description;
        /** 默认 Cron 表达式 */
        private String defaultCron;
        /** XXL-JOB 路由策略 */
        private String routeStrategy;
        /** 参数定义列表 */
        private List<ParamDefInfo> paramDefs;
    }

    /**
     * 参数定义 DTO，对应 {@link ParamDef} 注解的属性。
     */
    @Data
    public static class ParamDefInfo {
        /** 参数名 */
        private String name;
        /** 参数标签（表单显示名） */
        private String label;
        /** 参数类型：string/number/boolean */
        private String type;
        /** 默认值（字符串形式） */
        private String defaultValue;
        /** 是否必填 */
        private boolean required;
        /** 最小值（仅 number 类型） */
        private double min;
        /** 最大值（仅 number 类型） */
        private double max;
    }
}
