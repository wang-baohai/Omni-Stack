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
        // 验证任务类型存在且启用
        userJobTypeService.listEnabledTypes().stream()
                .filter(t -> t.getTypeCode().equals(request.getJobType()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(400, "任务类型不存在或已禁用"));

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

        // 注册到 XXL-JOB
        try {
            int jobGroup = getJobGroup();
            String executorParam = buildExecutorParam(entity);
            String xxlJobIdStr = xxlJobAdminClient.addJob(
                    jobGroup,
                    entity.getJobName(),
                    entity.getCronExpression(),
                    "FIRST",
                    "userJobExecuteHandler",
                    executorParam);
            entity.setXxlJobId(Long.parseLong(xxlJobIdStr));
            sysUserJobMapper.updateById(entity);
            log.info("用户任务创建并注册成功：id={}, jobName={}, xxlJobId={}",
                    entity.getId(), entity.getJobName(), entity.getXxlJobId());
        } catch (Exception e) {
            // XXL-JOB 注册失败，回滚 DB 记录
            log.error("XXL-JOB 注册失败，回滚任务记录：id={}, error={}", entity.getId(), e.getMessage(), e);
            sysUserJobMapper.deleteById(entity.getId());
            throw new BusinessException(500, "任务注册到调度中心失败: " + e.getMessage());
        }

        return entity;
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
        sysUserJobMapper.updateById(entity);

        // cron 或参数变更时同步更新 XXL-JOB
        if ((cronChanged || paramsChanged) && entity.getXxlJobId() != null) {
            try {
                String executorParam = buildExecutorParam(entity);
                xxlJobAdminClient.updateJob(entity.getXxlJobId().intValue(),
                        entity.getCronExpression(), executorParam);
                log.info("用户任务 XXL-JOB 同步更新：id={}, xxlJobId={}", id, entity.getXxlJobId());
            } catch (Exception e) {
                log.warn("XXL-JOB 更新失败（DB 已更新）：id={}, error={}", id, e.getMessage(), e);
            }
        }

        log.info("更新用户任务：id={}, jobName={}, operator={}", id, entity.getJobName(), operator);
        return entity;
    }

    /** {@inheritDoc} */
    @Override
    public void deleteJob(Long id) {
        SysUserJob entity = getJobById(id);

        // 从 XXL-JOB 注销
        if (entity.getXxlJobId() != null) {
            try {
                xxlJobAdminClient.removeJob(entity.getXxlJobId().intValue());
                log.info("用户任务 XXL-JOB 注销：id={}, xxlJobId={}", id, entity.getXxlJobId());
            } catch (Exception e) {
                log.warn("XXL-JOB 删除失败（DB 继续删除）：id={}, error={}", id, e.getMessage(), e);
            }
        }

        sysUserJobMapper.deleteById(id);
        log.info("删除用户任务：id={}, jobName={}", id, entity.getJobName());
    }

    /** {@inheritDoc} */
    @Override
    public void toggleStatus(Long id, Integer status) {
        SysUserJob entity = getJobById(id);
        entity.setStatus(status);
        sysUserJobMapper.updateById(entity);

        // 同步 XXL-JOB 启停
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
                log.warn("XXL-JOB 状态同步失败（DB 已更新）：id={}, error={}", id, e.getMessage(), e);
            }
        }

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
