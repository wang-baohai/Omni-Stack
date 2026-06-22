package com.omni.base.service;

import com.omni.base.entity.SysOperLog;
import com.omni.base.entity.SysOperLogArchive;
import com.omni.base.mapper.SysOperLogArchiveMapper;
import com.omni.base.mapper.SysOperLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 操作日志归档定时任务。
 * <p>每日 02:00 将超过 180 天的热表记录迁移到冷表。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperLogArchiver {

    /** 热数据保留天数 */
    private static final int RETENTION_DAYS = 180;

    /** 每批处理记录数 */
    private static final int BATCH_SIZE = 1000;

    private final SysOperLogMapper sysOperLogMapper;
    private final SysOperLogArchiveMapper sysOperLogArchiveMapper;

    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * 执行归档任务。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void archive() {
        if (!running.compareAndSet(false, true)) {
            log.warn("操作日志归档：上次任务尚未完成，跳过本次执行");
            return;
        }
        try {
            doArchive();
        } finally {
            running.set(false);
        }
    }

    private void doArchive() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
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
