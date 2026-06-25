package com.omni.base.service;

import com.omni.base.dto.RegisterSystemJobRequest;
import com.omni.base.dto.SystemJobVO;
import com.omni.common.job.SystemJobRegistry;
import com.omni.common.job.XxlJobAdminClient;
import com.omni.common.job.XxlJobProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 系统任务管理服务。
 * <p>
 * 合并 {@link SystemJobRegistry} 元数据与 XXL-JOB 调度中心的实际运行状态，
 * 提供注册/启动/停止/触发/注销等全生命周期操作。
 * </p>
 *
 * @author Omni-Stack Team
 * @see com.omni.base.controller.SystemJobController
 * @see XxlJobAdminClient
 */
@Slf4j
@Service
public class SystemJobService {

    private final SystemJobRegistry systemJobRegistry;
    private final XxlJobAdminClient xxlJobAdminClient;
    private final XxlJobProperties xxlJobProperties;

    public SystemJobService(SystemJobRegistry systemJobRegistry,
                            XxlJobProperties xxlJobProperties) {
        this.systemJobRegistry = systemJobRegistry;
        this.xxlJobProperties = xxlJobProperties;
        this.xxlJobAdminClient = new XxlJobAdminClient(
                xxlJobProperties.getAdmin().getAddresses(),
                xxlJobProperties.getAdmin().getUsername(),
                xxlJobProperties.getAdmin().getPassword());
    }

    /**
     * 获取所有系统任务列表（合并元数据 + XXL-JOB 实际状态）。
     *
     * @return 系统任务视图列表，每个元素包含元数据和 XXL-JOB 运行状态
     */
    public List<SystemJobVO> listAll() {
        Map<String, SystemJobRegistry.SystemJobInfo> registry = systemJobRegistry.getAll();
        List<SystemJobVO> result = new ArrayList<>();

        // 尝试从 XXL-JOB 查询已注册任务
        Map<String, Map<String, Object>> xxlJobMap = fetchXxlJobs();

        for (SystemJobRegistry.SystemJobInfo info : registry.values()) {
            SystemJobVO vo = new SystemJobVO();
            vo.setHandlerName(info.getHandlerName());
            vo.setName(info.getName());
            vo.setDescription(info.getDescription());
            vo.setDefaultCron(info.getDefaultCron());
            vo.setRouteStrategy(info.getRouteStrategy());
            vo.setParamDefs(info.getParamDefs());

            // 匹配 XXL-JOB 中已注册的任务
            Map<String, Object> xxlJob = xxlJobMap.get(info.getHandlerName());
            if (xxlJob != null) {
                vo.setXxlJobId(getIntValue(xxlJob, "id"));
                vo.setActualCron(getStringValue(xxlJob, "scheduleConf"));
                vo.setActualParam(getStringValue(xxlJob, "executorParam"));
                int triggerStatus = getIntValue(xxlJob, "triggerStatus");
                vo.setStatus(triggerStatus == 1 ? "RUNNING" : "STOPPED");
            } else {
                vo.setStatus("UNREGISTERED");
            }

            result.add(vo);
        }

        return result;
    }

    /**
     * 注册系统任务到 XXL-JOB。
     *
     * @param request 注册请求（包含 handlerName、cron、参数）
     * @throws IllegalArgumentException 未找到对应 Handler 时抛出
     */
    public void register(RegisterSystemJobRequest request) {
        SystemJobRegistry.SystemJobInfo meta = systemJobRegistry.get(request.getHandlerName());
        if (meta == null) {
            throw new IllegalArgumentException("未找到 Handler: " + request.getHandlerName());
        }

        int jobGroup = getJobGroup();
        String xxlJobId = xxlJobAdminClient.addJob(
                jobGroup,
                meta.getName(),
                request.getCron(),
                meta.getRouteStrategy(),
                meta.getHandlerName(),
                request.getParams());

        log.info("系统任务注册成功: {} -> XXL-JOB ID={}", meta.getHandlerName(), xxlJobId);
    }

    /**
     * 启动任务。
     *
     * @param xxlJobId XXL-JOB 任务 ID
     */
    public void start(int xxlJobId) {
        xxlJobAdminClient.startJob(xxlJobId);
        log.info("系统任务启动: xxlJobId={}", xxlJobId);
    }

    /**
     * 停止任务。
     *
     * @param xxlJobId XXL-JOB 任务 ID
     */
    public void stop(int xxlJobId) {
        xxlJobAdminClient.stopJob(xxlJobId);
        log.info("系统任务停止: xxlJobId={}", xxlJobId);
    }

    /**
     * 立即触发任务执行。
     *
     * @param xxlJobId XXL-JOB 任务 ID
     * @param param    执行参数（JSON 格式，可为 null）
     */
    public void trigger(int xxlJobId, String param) {
        xxlJobAdminClient.triggerJob(xxlJobId, param);
        log.info("系统任务触发: xxlJobId={}", xxlJobId);
    }

    /**
     * 从 XXL-JOB 注销（删除）任务。
     *
     * @param xxlJobId XXL-JOB 任务 ID
     */
    public void unregister(int xxlJobId) {
        xxlJobAdminClient.removeJob(xxlJobId);
        log.info("系统任务注销: xxlJobId={}", xxlJobId);
    }

    // ─── 内部方法 ───

    /**
     * 查询执行器 ID。
     *
     * @return 执行器 ID（jobGroup）
     * @throws RuntimeException 未找到执行器时抛出
     */
    private int getJobGroup() {
        String appname = xxlJobProperties.getExecutor().getAppname();
        int groupId = xxlJobAdminClient.getJobGroupId(appname);
        if (groupId < 0) {
            throw new RuntimeException("未找到执行器: " + appname + "，请先在 XXL-JOB 调度中心创建执行器");
        }
        return groupId;
    }

    /**
     * 从 XXL-JOB 查询当前执行器下所有已注册任务，以 executorHandler 为 key。
     *
     * @return handlerName -> 任务属性 Map，查询失败时返回空 Map
     */
    private Map<String, Map<String, Object>> fetchXxlJobs() {
        try {
            int jobGroup = getJobGroup();
            List<Map<String, Object>> jobs = xxlJobAdminClient.pageList(jobGroup, null);
            Map<String, Map<String, Object>> map = new java.util.HashMap<>();
            for (Map<String, Object> job : jobs) {
                String handler = getStringValue(job, "executorHandler");
                if (handler != null) {
                    map.put(handler, job);
                }
            }
            return map;
        } catch (Exception e) {
            log.warn("查询 XXL-JOB 任务列表失败: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 从 Map 中安全提取 int 值。
     *
     * @param map 数据源
     * @param key 键名
     * @return int 值，非 Number 类型时返回 0
     */
    private static int getIntValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    /**
     * 从 Map 中安全提取 String 值。
     *
     * @param map 数据源
     * @param key 键名
     * @return 字符串值，null 时返回 null
     */
    private static String getStringValue(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v != null ? v.toString() : null;
    }
}
