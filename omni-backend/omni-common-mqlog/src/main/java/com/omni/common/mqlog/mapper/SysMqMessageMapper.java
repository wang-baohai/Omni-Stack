package com.omni.common.mqlog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.common.mqlog.entity.SysMqMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * MQ 消息记录 Mapper。
 * <p>
 * 除 BaseMapper 通用方法外，提供 Outbox 状态机的原子流转语句：
 * relay 投递采用「先抢占后发送」的租约模型（PROCESSING=5），
 * 人工 resend/skip 采用带状态前置条件的 CAS 更新，
 * 保证双 executor 并行或人工与后台任务竞争时不会互相覆盖状态。
 * 所有 UPDATE 均为单语句原子操作，必须保持自动提交、不得包裹在跨网络发送的长事务中。
 * </p>
 *
 * @author Omni-Stack Team
 */
public interface SysMqMessageMapper extends BaseMapper<SysMqMessage> {

    /**
     * relay 原子抢占（claim）：将一条到期可投递的消息置为投递中租约态。
     * <p>
     * 抢占成功（返回 1）的条件：消息处于 PENDING/FAILED 且已到重试时间，
     * 或处于 PROCESSING 但租约已到期（原持有 executor 崩溃，允许回收）。
     * 租约到期时间借用 {@code next_retry_time} 列存储。返回 0 表示消息已被
     * 其他 executor 持有或状态已变化，调用方必须跳过该消息。
     * </p>
     *
     * @param id         消息主键
     * @param now        本轮判定基准时间
     * @param leaseUntil 租约到期时间（now + 租约时长）
     * @return 受影响行数：1=抢占成功；0=未抢占到
     */
    @Update("UPDATE sys_mq_message "
            + "SET status = 5, next_retry_time = #{leaseUntil}, update_time = #{now} "
            + "WHERE id = #{id} "
            + "AND ((status IN (0, 2) AND (next_retry_time IS NULL OR next_retry_time <= #{now})) "
            + "  OR (status = 5 AND next_retry_time < #{now}))")
    int claimForRelay(@Param("id") Long id,
                      @Param("now") LocalDateTime now,
                      @Param("leaseUntil") LocalDateTime leaseUntil);

    /**
     * CAS 终态写入：仅当消息仍处于 PROCESSING 租约态时标记为 SENT。
     * <p>返回 0 说明租约期间已被其他执行流接管，调用方必须放弃写入（不得覆盖新状态）。</p>
     *
     * @param id  消息主键
     * @param now 写入时间
     * @return 受影响行数
     */
    @Update("UPDATE sys_mq_message "
            + "SET status = 1, error_msg = NULL, next_retry_time = NULL, update_time = #{now} "
            + "WHERE id = #{id} AND status = 5")
    int casMarkSent(@Param("id") Long id,
                    @Param("now") LocalDateTime now);

    /**
     * CAS 失败状态写入：仅当消息仍处于 PROCESSING 租约态时写入失败结果。
     * <p>
     * {@code retry_count} 通过数据库端自增保证原子性（并发下不丢计数）；
     * 目标状态（FAILED=2 / DEAD_LETTER=3）由调用方按 maxRetry 判定后传入。
     * </p>
     *
     * @param id            消息主键
     * @param status        目标状态（FAILED 或 DEAD_LETTER）
     * @param nextRetryTime 下次重试时间（DEAD_LETTER 传 null 清空退避时间）
     * @param errorMsg      脱敏后的错误信息
     * @param now           写入时间
     * @return 受影响行数：0 表示租约已被接管，调用方放弃写入
     */
    @Update("UPDATE sys_mq_message "
            + "SET status = #{status}, retry_count = retry_count + 1, "
            + "next_retry_time = #{nextRetryTime,jdbcType=TIMESTAMP}, error_msg = #{errorMsg}, update_time = #{now} "
            + "WHERE id = #{id} AND status = 5")
    int casApplyFailure(@Param("id") Long id,
                        @Param("status") int status,
                        @Param("nextRetryTime") LocalDateTime nextRetryTime,
                        @Param("errorMsg") String errorMsg,
                        @Param("now") LocalDateTime now);

    /**
     * 人工重发 CAS：仅 FAILED/DEAD_LETTER 状态可重置回 PENDING。
     * <p>
     * 条件中包含当前状态与租户，杜绝「先读后写」把 SENT/SKIPPED/PROCESSING
     * 覆盖回 PENDING 造成重复投递。返回 0 表示状态已被并发修改，操作未生效。
     * </p>
     *
     * @param tenantId 租户 ID（隔离条件，必须保留）
     * @param msgId    业务消息 ID
     * @param now      写入时间
     * @return 受影响行数
     */
    @Update("UPDATE sys_mq_message "
            + "SET status = 0, retry_count = 0, next_retry_time = NULL, error_msg = NULL, update_time = #{now} "
            + "WHERE tenant_id = #{tenantId} AND msg_id = #{msgId} AND status IN (2, 3)")
    int casAdminResend(@Param("tenantId") Long tenantId,
                       @Param("msgId") String msgId,
                       @Param("now") LocalDateTime now);

    /**
     * 人工忽略 CAS：仅 DEAD_LETTER 状态可标记为 SKIPPED。
     * <p>返回 0 表示状态已被并发修改（例如已被重发），操作未生效。</p>
     *
     * @param tenantId 租户 ID（隔离条件，必须保留）
     * @param msgId    业务消息 ID
     * @param now      写入时间
     * @return 受影响行数
     */
    @Update("UPDATE sys_mq_message "
            + "SET status = 4, update_time = #{now} "
            + "WHERE tenant_id = #{tenantId} AND msg_id = #{msgId} AND status = 3")
    int casAdminSkip(@Param("tenantId") Long tenantId,
                     @Param("msgId") String msgId,
                     @Param("now") LocalDateTime now);
}
