package com.omni.base.dto;

import com.omni.common.job.SystemJobRegistry;
import lombok.Data;

import java.util.List;

/**
 * 系统任务视图对象。
 * <p>
 * 合并 {@link SystemJobRegistry.SystemJobInfo} 元数据与 XXL-JOB 调度中心的实际运行状态。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Data
public class SystemJobVO {

    /** Handler 名称 */
    private String handlerName;

    /** 任务名称 */
    private String name;

    /** 任务描述 */
    private String description;

    /** 注解声明的默认 Cron */
    private String defaultCron;

    /** 路由策略 */
    private String routeStrategy;

    /** 参数定义列表 */
    private List<SystemJobRegistry.ParamDefInfo> paramDefs;

    /** XXL-JOB 中的任务 ID（未注册时为 null） */
    private Integer xxlJobId;

    /** XXL-JOB 中实际生效的 Cron（未注册时为 null） */
    private String actualCron;

    /** XXL-JOB 中实际生效的执行参数（未注册时为 null） */
    private String actualParam;

    /**
     * 任务状态。
     * <ul>
     *   <li>{@code UNREGISTERED} — 尚未注册到 XXL-JOB</li>
     *   <li>{@code RUNNING} — 调度中</li>
     *   <li>{@code STOPPED} — 已停止</li>
     * </ul>
     */
    private String status;
}
