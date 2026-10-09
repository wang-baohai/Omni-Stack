package com.omni.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.base.dto.CreateUserJobRequest;
import com.omni.base.dto.MyJobStats;
import com.omni.base.dto.UpdateUserJobRequest;
import com.omni.base.dto.UserJobQuery;
import com.omni.base.entity.SysUserJob;
import com.omni.base.mapper.SysUserJobLogMapper;
import com.omni.base.mapper.SysUserJobMapper;
import com.omni.base.service.UserJobService;
import com.omni.base.service.UserJobTypeService;
import com.omni.common.core.job.UserJobMessage;
import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.PageResult;
import com.omni.common.job.XxlJobAdminClient;
import com.omni.common.job.XxlJobProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户任务服务实现。
 * <p>
 * 采用全量直注册模式：用户任务创建时直接注册到 XXL-JOB 调度中心，
 * 由 XXL-JOB 原生管理 cron 调度，消除扫描层和消息中间件。
 * </p>
 *
 * @author Omni-Stack Team
 * @see UserJobService
 * @see XxlJobAdminClient
 */
@Slf4j
@Service
public class UserJobServiceImpl implements UserJobService {

    private final SysUserJobMapper sysUserJobMapper;
    private final SysUserJobLogMapper sysUserJobLogMapper;
    private final UserJobTypeService userJobTypeService;
    private final XxlJobProperties xxlJobProperties;
    private final ObjectMapper objectMapper;
    private final XxlJobAdminClient xxlJobAdminClient;

    public UserJobServiceImpl(SysUserJobMapper sysUserJobMapper,
                              SysUserJobLogMapper sysUserJobLogMapper,
                              UserJobTypeService userJobTypeService,
                              XxlJobProperties xxlJobProperties,
                              ObjectMapper objectMapper) {
        this.sysUserJobMapper = sysUserJobMapper;
        this.sysUserJobLogMapper = sysUserJobLogMapper;
        this.userJobTypeService = userJobTypeService;
        this.xxlJobProperties = xxlJobProperties;
        this.objectMapper = objectMapper;
        this.xxlJobAdminClient = new XxlJobAdminClient(
                xxlJobProperties.getAdmin().getAddresses(),
                xxlJobProperties.getAdmin().getUsername(),
                xxlJobProperties.getAdmin().getPassword());
    }

    /** {@inheritDoc} */
    @Override
    public PageResult<SysUserJob> listJobs(Long tenantId, UserJobQuery query, int page, int size) {
        LambdaQueryWrapper<SysUserJob> wrapper = new LambdaQueryWrapper<SysUserJob>()
                .eq(SysUserJob::getTenantId, tenantId)
                .eq(query.getCreateBy() != null && !query.getCreateBy().isBlank(),
                        SysUserJob::getCreateBy, query.getCreateBy())
                .like(query.getJobName() != null && !query.getJobName().isBlank(),
                        SysUserJob::getJobName, query.getJobName())
                .eq(query.getJobType() != null && !query.getJobType().isBlank(),
                        SysUserJob::getJobType, query.getJobType())
                .eq(query.getStatus() != null, SysUserJob::getStatus, query.getStatus())
                .orderByDesc(SysUserJob::getId);

        Page<SysUserJob> pageResult = sysUserJobMapper.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(pageResult.getRecords(), pageResult.getTotal(),
                pageResult.getSize(), pageResult.getCurrent());
    }

    /** {@inheritDoc} */
    @Override
    public SysUserJob getJobById(Long id) {
        SysUserJob entity = sysUserJobMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(404, "用户任务不存在");
        }
        return entity;
    }

    /** {@inheritDoc} */
    @Override
    public SysUserJob createJob(Long tenantId, CreateUserJobRequest request, String operator) {
        // 验证任务类型存在且启用（精准查询，避免全量加载）
        if (userJobTypeService.getEnabledTypeByCode(request.getJobType()) == null) {
            throw new BusinessException(400, "任务类型不存在或已禁用");
        }

        // 保存到数据库
        SysUserJob entity = new SysUserJob();
        entity.setTenantId(tenantId);
        entity.setJobName(request.getJobName());
        entity.setJobType(request.getJobType());
        entity.setCronExpression(request.getCronExpression());
        entity.setJobParams(request.getJobParams());
        entity.setStatus(1);
        entity.setCreateBy(operator);
        entity.setUpdateBy(operator);
        sysUserJobMapper.insert(entity);

        // 注册到 XXL-JOB（外部先行：失败则本地不落库；提交后丢响应时反查调度中心定点清理，不留孤儿）
        int jobGroup;
        try {
            jobGroup = getJobGroup();
        } catch (Exception e) {
            log.error("获取执行器组失败，回滚任务记录：id={}, error={}", entity.getId(), e.getMessage(), e);
            sysUserJobMapper.deleteById(entity.getId());
            throw new BusinessException(500, "任务注册到调度中心失败: " + e.getMessage());
        }
        String executorParam = buildExecutorParam(entity);
        String xxlJobIdStr;
        try {
            xxlJobIdStr = xxlJobAdminClient.addJob(
                    jobGroup,
                    entity.getJobName(),
                    entity.getCronExpression(),
                    "FIRST",
                    "userJobExecuteHandler",
                    executorParam);
        } catch (Exception e) {
            // 注册调用异常：可能是提交前失败（远端无对象）或提交后丢响应（远端已存在）。
            // 按 handler+executorParam（含 tenantId+本地 jobId）可信关联精确定位并判定，不得凭名称，
            // 且在无法确认远端时保留本地行作为可恢复关联，绝不先删本地。
            log.error("XXL-JOB 注册失败，按可信关联判定远端：id={}, error={}", entity.getId(), e.getMessage(), e);
            rollbackWithPreciseRemoteCleanup(jobGroup, null, executorParam, entity.getId(), "注册失败");
            throw new BusinessException(500, "任务注册到调度中心失败: " + e.getMessage());
        }
        try {
            entity.setXxlJobId(Long.parseLong(xxlJobIdStr));
            sysUserJobMapper.updateById(entity);
        } catch (Exception e) {
            // ID 无法落库时外部任务已成孤儿：addJob 已返回精确 ID，优先按 ID 定点清理；解析不出则回退可信关联。
            log.error("回填 xxlJobId 失败，按返回ID定点清理外部任务：id={}, error={}", entity.getId(), e.getMessage(), e);
            Integer remoteId = parseRemoteId(xxlJobIdStr);
            rollbackWithPreciseRemoteCleanup(jobGroup, remoteId, executorParam, entity.getId(), "回填失败");
            throw new BusinessException(500, "任务注册结果处理失败: " + e.getMessage());
        }
        log.info("用户任务创建并注册成功：id={}, jobName={}, xxlJobId={}",
                entity.getId(), entity.getJobName(), entity.getXxlJobId());

        return entity;
    }

    /**
     * 注册/回填失败后的定点恢复：仅清理属于本任务的远端对象，然后才回滚本地。
     * <p>有精确 XXL ID 时按 ID 定点删除；无 ID 时按 handler+executorParam（含 tenantId+本地 jobId）
     * 可信关联精确定位，不靠任务名称。查询/清理失败（不可达/超时/结构异常）时保留本地待恢复记录
     * 与停止责任，抛异常交人工/对账，绝不删除唯一的本地恢复关联。仅在确认远端确无残留后才回滚本地。</p>
     */
    private void rollbackWithPreciseRemoteCleanup(int jobGroup, Integer remoteJobId,
                                                  String executorParam, Long localId, String action) {
        try {
            if (remoteJobId != null) {
                xxlJobAdminClient.removeJob(remoteJobId);
                log.warn("{}：已按精确ID定点清理调度中心任务 xxlJobId={}, localId={}", action, remoteJobId, localId);
            } else {
                Integer orphanId = xxlJobAdminClient.findJobIdByTrustedCorrelation(
                        jobGroup, "userJobExecuteHandler", executorParam);
                if (orphanId != null) {
                    xxlJobAdminClient.removeJob(orphanId);
                    log.warn("{}：按可信关联清理调度中心孤儿任务 xxlJobId={}, localId={}", action, orphanId, localId);
                } else {
                    log.info("{}：反查确认远端无本任务残留，可安全回滚本地：localId={}", action, localId);
                }
            }
            // 远端确认无残留（或已成功定点删除）后，才回滚本地记录
            sysUserJobMapper.deleteById(localId);
        } catch (Exception cleanupFailed) {
            // 查询/清理失败：无法确认远端，保留本地可恢复关联与停止责任，绝不删本地
            log.error("{}：调度中心反查/清理失败，保留本地待恢复记录 localId={}, executorParam={}, 需对账",
                    action, localId, executorParam, cleanupFailed);
            throw new BusinessException(500, "调度中心注册结果未知且清理失败，已保留本地待恢复记录(id="
                    + localId + ")，请联系运维对账: " + cleanupFailed.getMessage());
        }
    }

    /** 安全解析 addJob 返回的远端 ID；不可解析返回 null（回退可信关联定位）。 */
    private Integer parseRemoteId(String xxlJobIdStr) {
        try {
            return Integer.parseInt(xxlJobIdStr.trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** {@inheritDoc} */
    @Override
    public SysUserJob updateJob(Long id, UpdateUserJobRequest request, String operator) {
        SysUserJob entity = getJobById(id);

        boolean cronChanged = request.getCronExpression() != null
                && !request.getCronExpression().equals(entity.getCronExpression());
        boolean paramsChanged = request.getJobParams() != null
                && !request.getJobParams().equals(entity.getJobParams());

        if (request.getJobName() != null) {
            entity.setJobName(request.getJobName());
        }
        if (request.getCronExpression() != null) {
            entity.setCronExpression(request.getCronExpression());
        }
        if (request.getJobParams() != null) {
            entity.setJobParams(request.getJobParams());
        }
        entity.setUpdateBy(operator);

        // cron 或参数变更时外部先行：调度中心失败则本地不提交，绝不返回“两边一致成功”；
        // 提交后丢响应场景下本地保持旧值，同参重试幂等收敛。
        if ((cronChanged || paramsChanged) && entity.getXxlJobId() != null) {
            try {
                String executorParam = buildExecutorParam(entity);
                xxlJobAdminClient.updateJob(getJobGroup(), entity.getXxlJobId().intValue(),
                        entity.getCronExpression(), executorParam);
                log.info("用户任务 XXL-JOB 同步更新：id={}, xxlJobId={}", id, entity.getXxlJobId());
            } catch (Exception e) {
                log.error("XXL-JOB 更新失败，本地任务保持不变：id={}, error={}", id, e.getMessage(), e);
                throw new BusinessException(500, "调度中心同步失败，任务未变更: " + e.getMessage());
            }
        }
        sysUserJobMapper.updateById(entity);

        log.info("更新用户任务：id={}, jobName={}, operator={}", id, entity.getJobName(), operator);
        return entity;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteJob(Long id) {
        SysUserJob entity = getJobById(id);

        // 从 XXL-JOB 注销（外部先行）：失败时反查确认远端确已消失才可继续；否则保留本地行与关联，绝不假报收尾
        if (entity.getXxlJobId() != null) {
            try {
                xxlJobAdminClient.removeJob(entity.getXxlJobId().intValue());
                log.info("用户任务 XXL-JOB 注销：id={}, xxlJobId={}", id, entity.getXxlJobId());
            } catch (Exception e) {
                boolean gone = remoteJobAbsent(entity.getXxlJobId().intValue());
                if (!gone) {
                    log.error("XXL-JOB 删除失败且远端仍存在，本地保留可恢复：id={}, error={}", id, e.getMessage(), e);
                    throw new BusinessException(500, "调度中心删除失败，任务及其关联已保留: " + e.getMessage());
                }
                log.warn("XXL-JOB 删除响应丢失但反查确认远端已无该任务，继续本地删除：id={}, xxlJobId={}",
                        id, entity.getXxlJobId());
            }
        }

        sysUserJobMapper.deleteById(id);
        log.info("删除用户任务：id={}, jobName={}", id, entity.getJobName());
    }

    /**
     * 反查调度中心确认任务已不存在（丢响应后的定点恢复）。
     * <p>基于完整分页的 {@code jobExistsById}，仅在全量遍历后确认不存在才返回 true；
     * 查询异常/结构异常时保守返回 false（不删本地），避免首 100 条未命中或解析异常被误判为不存在。</p>
     */
    private boolean remoteJobAbsent(int xxlJobId) {
        try {
            int jobGroup = getJobGroup();
            return !xxlJobAdminClient.jobExistsById(jobGroup, xxlJobId);
        } catch (Exception lookupFailed) {
            log.warn("反查调度中心失败，保守不删本地：{}", lookupFailed.getMessage());
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public void toggleStatus(Long id, Integer status) {
        SysUserJob entity = getJobById(id);

        // 同步 XXL-JOB 启停（外部先行）：失败则本地状态不变，避免 DB 与 triggerStatus 发散假报成功
        if (entity.getXxlJobId() != null) {
            try {
                if (status == 1) {
                    xxlJobAdminClient.startJob(entity.getXxlJobId().intValue());
                } else {
                    xxlJobAdminClient.stopJob(entity.getXxlJobId().intValue());
                }
                log.info("用户任务 XXL-JOB 状态同步：id={}, xxlJobId={}, status={}",
                        id, entity.getXxlJobId(), status == 1 ? "启动" : "停止");
            } catch (Exception e) {
                log.error("XXL-JOB 状态同步失败，本地状态保持不变：id={}, error={}", id, e.getMessage(), e);
                throw new BusinessException(500, "调度中心状态同步失败，任务状态未变更: " + e.getMessage());
            }
        }
        entity.setStatus(status);
        sysUserJobMapper.updateById(entity);

        log.info("切换用户任务状态：id={}, status={}", id, status);
    }

    /** {@inheritDoc} */
    @Override
    public void triggerNow(Long id) {
        SysUserJob entity = getJobById(id);
        if (entity.getXxlJobId() == null) {
            throw new BusinessException(400, "任务尚未注册到调度中心");
        }

        String executorParam = buildExecutorParam(entity);
        xxlJobAdminClient.triggerJob(entity.getXxlJobId().intValue(), executorParam);
        log.info("立即触发用户任务：id={}, xxlJobId={}, jobType={}",
                id, entity.getXxlJobId(), entity.getJobType());
    }

    /** {@inheritDoc} */
    @Override
    public MyJobStats getStats(Long tenantId, String createBy) {
        // 任务总数
        long totalJobs = sysUserJobMapper.selectCount(
                new LambdaQueryWrapper<SysUserJob>()
                        .eq(SysUserJob::getTenantId, tenantId)
                        .eq(SysUserJob::getCreateBy, createBy));

        // 今日执行次数
        long todayExecuted = sysUserJobLogMapper.countTodayExecuted(tenantId, createBy);

        // 今日失败次数
        long todayFailed = sysUserJobLogMapper.countTodayFailed(tenantId, createBy);

        return new MyJobStats(totalJobs, todayExecuted, todayFailed);
    }

    // ─── 内部辅助方法 ───

    /**
     * 获取执行器 ID。
     */
    private int getJobGroup() {
        String appname = xxlJobProperties.getExecutor().getAppname();
        int groupId = xxlJobAdminClient.getJobGroupId(appname);
        if (groupId < 0) {
            throw new BusinessException(500, "未找到执行器: " + appname);
        }
        return groupId;
    }

    /**
     * 构建 XXL-JOB 执行器参数 JSON。
     */
    private String buildExecutorParam(SysUserJob entity) {
        try {
            UserJobMessage message = new UserJobMessage();
            message.setJobId(entity.getId());
            message.setTenantId(entity.getTenantId());
            message.setJobType(entity.getJobType());
            message.setJobName(entity.getJobName());
            message.setJobParams(entity.getJobParams());
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new BusinessException(500, "构建执行参数失败: " + e.getMessage());
        }
    }
}
