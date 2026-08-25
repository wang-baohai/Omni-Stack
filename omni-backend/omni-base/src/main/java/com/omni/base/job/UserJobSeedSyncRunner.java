package com.omni.base.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.base.entity.SysUserJob;
import com.omni.base.mapper.SysUserJobMapper;
import com.omni.common.core.job.UserJobMessage;
import com.omni.common.job.XxlJobAdminClient;
import com.omni.common.job.XxlJobProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 用户任务种子数据 XXL-JOB 同步器。
 * <p>
 * 解决 SQL 种子脚本（init-all.sql）插入的用户任务记录（{@code xxl_job_id = NULL}）
 * 不会自动注册到 XXL-JOB 的问题。
 * 应用启动后，扫描 {@code sys_user_job} 表中 {@code xxl_job_id IS NULL} 且 {@code status = 1}
 * 的记录，逐一调用 XXL-JOB Admin API 完成注册，并回写 {@code xxl_job_id}。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class UserJobSeedSyncRunner {

    private final SysUserJobMapper sysUserJobMapper;
    private final XxlJobProperties xxlJobProperties;
    private final ObjectMapper objectMapper;

    /**
     * 应用启动完成后，自动同步未注册的用户种子任务到 XXL-JOB。
     *
     * @return 启动后执行的同步逻辑
     */
    @Bean
    @ConditionalOnProperty(prefix = "xxl.job.executor", name = "enabled", havingValue = "true", matchIfMissing = true)
    public ApplicationRunner syncUserJobSeeds() {
        return args -> {
            String appname = xxlJobProperties.getExecutor().getAppname();
            if (appname == null || appname.isBlank()) {
                return;
            }

            // 查询 xxl_job_id 为空且启用的种子任务
            List<SysUserJob> unregistered = sysUserJobMapper.selectList(
                    new LambdaQueryWrapper<SysUserJob>()
                            .isNull(SysUserJob::getXxlJobId)
                            .eq(SysUserJob::getStatus, 1));

            if (unregistered.isEmpty()) {
                log.debug("无需同步的用户种子任务");
                return;
            }

            log.info("发现 {} 个未注册到 XXL-JOB 的用户任务，开始同步注册", unregistered.size());

            XxlJobAdminClient client = new XxlJobAdminClient(
                    xxlJobProperties.getAdmin().getAddresses(),
                    xxlJobProperties.getAdmin().getUsername(),
                    xxlJobProperties.getAdmin().getPassword());

            int groupId;
            try {
                groupId = client.getJobGroupId(appname);
                if (groupId < 0) {
                    log.warn("未找到执行器组: appname={}, 跳过用户任务种子同步", appname);
                    return;
                }
            } catch (Exception e) {
                log.warn("查询执行器组失败: appname={}, error={}, 跳过用户任务种子同步",
                        appname, e.getMessage());
                return;
            }

            int synced = 0;
            for (SysUserJob job : unregistered) {
                try {
                    String executorParam = buildExecutorParam(job);
                    if (executorParam.isEmpty()) {
                        continue;
                    }
                    String xxlJobIdStr = client.addJob(
                            groupId,
                            job.getJobName(),
                            job.getCronExpression(),
                            "FIRST",
                            "userJobExecuteHandler",
                            executorParam);

                    job.setXxlJobId(Long.parseLong(xxlJobIdStr));
                    sysUserJobMapper.updateById(job);

                    log.info("用户种子任务同步成功: id={}, jobName={}, xxlJobId={}",
                            job.getId(), job.getJobName(), job.getXxlJobId());
                    synced++;
                } catch (Exception e) {
                    log.warn("用户种子任务同步失败: id={}, jobName={}, error={}",
                            job.getId(), job.getJobName(), e.getMessage());
                }
            }

            if (synced > 0) {
                log.info("用户种子任务同步完成，本次同步 {} 个", synced);
            }
        };
    }

    /**
     * 构建 XXL-JOB 执行器参数 JSON。
     *
     * @param job 用户任务实体
     * @return JSON 格式的执行器参数
     */
    private String buildExecutorParam(SysUserJob job) {
        try {
            UserJobMessage message = new UserJobMessage();
            message.setJobId(job.getId());
            message.setTenantId(job.getTenantId());
            message.setJobType(job.getJobType());
            message.setJobName(job.getJobName());
            message.setJobParams(job.getJobParams());
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            log.warn("构建执行参数失败: jobId={}, error={}", job.getId(), e.getMessage());
            return "";
        }
    }
}
