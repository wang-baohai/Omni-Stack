package com.omni.base.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.base.entity.SysUserJob;
import com.omni.base.entity.SysUserJobLog;
import com.omni.base.mapper.SysUserJobLogMapper;
import com.omni.base.mapper.SysUserJobMapper;
import com.omni.common.core.job.UserJobHandler;
import com.omni.common.core.job.UserJobMessage;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 用户任务通用执行 Handler。
 * <p>
 * 每个用户任务在 XXL-JOB 中注册为独立调度条目，均指向此 handler。
 * XXL-JOB 触发时，从执行器参数（JSON）中解析任务上下文，
 * 通过 {@link UserJobHandlerRegistry} 路由到具体 {@link UserJobHandler} 执行。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserJobExecuteHandler {

    private final UserJobHandlerRegistry handlerRegistry;
    private final SysUserJobMapper sysUserJobMapper;
    private final SysUserJobLogMapper sysUserJobLogMapper;
    private final ObjectMapper objectMapper;

    /**
     * 通用用户任务执行入口。
     * <p>
     * 执行器参数 JSON 格式：
     * {@code {"jobId":1, "tenantId":1, "jobType":"Task-00001", "jobName":"喝水提醒", "jobParams":"{...}"}}
     * </p>
     */
    @XxlJob("userJobExecuteHandler")
    public void execute() {
        String param = XxlJobHelper.getJobParam();
        XxlJobHelper.log("用户任务执行开始，参数：{}", param);

        // 解析 JSON 参数
        UserJobMessage message;
        try {
            message = objectMapper.readValue(param, UserJobMessage.class);
        } catch (Exception e) {
            XxlJobHelper.log("参数解析失败：{}", e.getMessage());
            XxlJobHelper.handleFail("参数解析失败: " + e.getMessage());
            return;
        }

        String jobType = message.getJobType();
        Long jobId = message.getJobId();
        LocalDateTime fireTime = LocalDateTime.now();
        long startTime = System.currentTimeMillis();

        UserJobHandler handler = handlerRegistry.getHandler(jobType);

        int status = 1;
        String errorMsg = null;
        String resultMessage = null;

        if (handler == null) {
            status = 0;
            errorMsg = "未找到任务类型 [" + jobType + "] 对应的处理器";
            log.warn("{}: jobId={}", errorMsg, jobId);
        } else {
            try {
                log.debug("开始执行用户任务：jobId={}, jobType={}, jobName={}",
                        jobId, jobType, message.getJobName());
                handler.execute(message);
                log.debug("用户任务执行完成：jobId={}, jobType={}", jobId, jobType);

                resultMessage = handler.getResultMessage(message);
                if (resultMessage != null && resultMessage.length() > 500) {
                    resultMessage = resultMessage.substring(0, 500);
                }
            } catch (Exception e) {
                status = 0;
                errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.length() > 2000) {
                    errorMsg = errorMsg.substring(0, 2000);
                }
                log.error("用户任务执行失败：jobId={}, jobType={}, error={}",
                        jobId, jobType, e.getMessage(), e);
            }
        }

        long executeTimeMs = System.currentTimeMillis() - startTime;

        // 写入执行日志
        SysUserJobLog logEntity = new SysUserJobLog();
        logEntity.setJobId(jobId);
        logEntity.setTenantId(message.getTenantId());
        logEntity.setJobName(message.getJobName());
        logEntity.setJobType(jobType);
        logEntity.setFireTime(fireTime);
        logEntity.setExecuteTimeMs(executeTimeMs);
        logEntity.setStatus(status);
        logEntity.setErrorMessage(errorMsg);
        logEntity.setResultMessage(resultMessage);
        sysUserJobLogMapper.insert(logEntity);

        // 更新任务的最后执行时间
        SysUserJob jobUpdate = new SysUserJob();
        jobUpdate.setId(jobId);
        jobUpdate.setLastFireTime(fireTime);
        sysUserJobMapper.updateById(jobUpdate);

        // 设置 XXL-JOB 执行结果
        if (status == 1) {
            XxlJobHelper.handleSuccess(resultMessage != null ? resultMessage : "执行成功");
        } else {
            XxlJobHelper.handleFail(errorMsg);
        }

        XxlJobHelper.log("用户任务执行完成：jobId={}, jobType={}, status={}, 耗时={}ms",
                jobId, jobType, status == 1 ? "成功" : "失败", executeTimeMs);
    }
}
