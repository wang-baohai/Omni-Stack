package com.omni.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.base.entity.SysUserJobLog;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户任务执行日志 Mapper。
 *
 * @author Omni-Stack Team
 */
public interface SysUserJobLogMapper extends BaseMapper<SysUserJobLog> {

    /**
     * 统计今日执行次数（按任务归属用户）。
     */
    @Select("SELECT COUNT(*) FROM sys_user_job_log l "
            + "INNER JOIN sys_user_job j ON l.job_id = j.id "
            + "WHERE j.tenant_id = #{tenantId} AND j.create_by = #{createBy} "
            + "AND DATE(l.fire_time) = CURDATE()")
    long countTodayExecuted(@Param("tenantId") Long tenantId, @Param("createBy") String createBy);

    /**
     * 统计今日失败次数（按任务归属用户）。
     */
    @Select("SELECT COUNT(*) FROM sys_user_job_log l "
            + "INNER JOIN sys_user_job j ON l.job_id = j.id "
            + "WHERE j.tenant_id = #{tenantId} AND j.create_by = #{createBy} "
            + "AND DATE(l.fire_time) = CURDATE() AND l.status = 0")
    long countTodayFailed(@Param("tenantId") Long tenantId, @Param("createBy") String createBy);
}
