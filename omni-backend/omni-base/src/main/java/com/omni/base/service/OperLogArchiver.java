package com.omni.base.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.base.entity.SysOperLog;
import com.omni.base.entity.SysOperLogArchive;
import com.omni.base.mapper.SysOperLogArchiveMapper;
import com.omni.base.mapper.SysOperLogMapper;
import com.omni.common.job.ParamDef;
import com.omni.common.job.SystemJobMeta;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作日志归档定时任务。
 * <p>每日凌晨将超过保留天数的热表记录迁移到冷表，保留天数通过 XXL-JOB 任务参数配置。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.base.entity.SysOperLog
 * @see com.omni.base.entity.SysOperLogArchive
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogArchiver {

    /** 默认保留天数（当 XXL-JOB 未传参时使用） */
    private static final int DEFAULT_RETENTION_DAYS = 180;

    /** 每批处理记录数 */
    private static final int BATCH_SIZE = 1000;

    private final SysOperLogMapper sysOperLogMapper;
    private final SysOperLogArchiveMapper sysOperLogArchiveMapper;
    private final ObjectMapper objectMapper;

    /**
     * 操作日志归档任务。
     * <p>在 XXL-JOB 调度中心中配置 cron 表达式 {@code 0 0 2 * * ?}，
     * 路由策略为“第一个”，Bean 模式，JobHandler 名称为 {@code operLogArchiveHandler}。</p>
     */
    @XxlJob("operLogArchiveHandler")
    @SystemJobMeta(
            name = "操作日志归档",
            description = "将超过保留天数的热表记录迁移到冷表",
            defaultCron = "0 0 2 * * ?",
            params = {
                    @ParamDef(name = "retentionDays", label = "保留天数",
                              type = "number", defaultValue = "180", required = true, min = 1, max = 3650)
            }
    )
    public void archive() {
        int retentionDays = parseRetentionDays();
        XxlJobHelper.log("操作日志归档任务开始，保留天数: {}", retentionDays);
        doArchive(retentionDays);
        XxlJobHelper.log("操作日志归档任务完成");
    }

    /**
     * 从 XXL-JOB 任务参数中解析保留天数。
     */
    private int parseRetentionDays() {
        String param = XxlJobHelper.getJobParam();
        if (param == null || param.isBlank()) {
            return DEFAULT_RETENTION_DAYS;
        }
        try {
            JsonNode node = objectMapper.readTree(param);
            JsonNode retentionNode = node.get("retentionDays");
            if (retentionNode != null && retentionNode.isNumber()) {
                return retentionNode.intValue();
            }
        } catch (Exception e) {
            log.warn("解析任务参数失败，使用默认值: {}", e.getMessage());
        }
        return DEFAULT_RETENTION_DAYS;
    }

    /**
     * 执行归档核心逻辑：分批查询过期记录并迁移到冷表。
     *
     * @param retentionDays 保留天数
     */
    private void doArchive(int retentionDays) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retentionDays);
        log.info("操作日志归档开始：截止时间 {}", cutoff);
        int totalArchived = 0;

        while (true) {
            List<Long> batchIds = selectExpiredIds(cutoff);
            if (batchIds.isEmpty()) {
                break;
            }
            try {
                archiveBatch(batchIds, cutoff);
                totalArchived += batchIds.size();
            } catch (Exception e) {
                log.error("操作日志归档：批次处理失败 ({}条): {}", batchIds.size(), e.getMessage());
                break;
            }
        }

        log.info("操作日志归档完成：共归档 {} 条记录", totalArchived);
    }

    /**
     * 查询一批超过保留期限的日志 ID。
     *
     * @param cutoff 截止时间
     * @return 过期记录 ID 列表，每批最多 {@link #BATCH_SIZE} 条
     */
    private List<Long> selectExpiredIds(LocalDateTime cutoff) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysOperLog> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        wrapper.select(SysOperLog::getId);
        wrapper.lt(SysOperLog::getOperTime, cutoff);
        wrapper.last("LIMIT " + BATCH_SIZE);
        return sysOperLogMapper.selectList(wrapper).stream()
                .map(SysOperLog::getId)
                .toList();
    }

    /**
     * 归档一批记录：复制到冷表后从热表删除（事务内执行）。
     *
     * @param ids    待归档的记录 ID 列表
     * @param cutoff 截止时间（当前未使用，保留扩展）
     */
    @Transactional
    public void archiveBatch(List<Long> ids, LocalDateTime cutoff) {
        // 查询待归档记录
        List<SysOperLog> records = sysOperLogMapper.selectBatchIds(ids);

        // 复制到归档表
        LocalDateTime archivedTime = LocalDateTime.now();
        for (SysOperLog record : records) {
            SysOperLogArchive archive = new SysOperLogArchive();
            archive.setTenantId(record.getTenantId());
            archive.setOperUsername(record.getOperUsername());
            archive.setOperTime(record.getOperTime());
            archive.setModule(record.getModule());
            archive.setOperType(record.getOperType());
            archive.setRequestMethod(record.getRequestMethod());
            archive.setRequestUrl(record.getRequestUrl());
            archive.setRequestParams(record.getRequestParams());
            archive.setResponseStatus(record.getResponseStatus());
            archive.setIpAddress(record.getIpAddress());
            archive.setUserAgent(record.getUserAgent());
            archive.setExecutionTime(record.getExecutionTime());
            archive.setOldValue(record.getOldValue());
            archive.setNewValue(record.getNewValue());
            archive.setErrorMsg(record.getErrorMsg());
            archive.setCreateTime(record.getCreateTime());
            archive.setArchivedTime(archivedTime);
            sysOperLogArchiveMapper.insert(archive);
        }

        // 从热表删除
        sysOperLogMapper.deleteBatchIds(ids);
    }
}
