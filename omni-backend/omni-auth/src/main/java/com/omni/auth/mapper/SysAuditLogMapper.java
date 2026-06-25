package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全审计日志 Mapper 接口。
 * <p>提供 {@code sys_audit_log} 表的 CRUD 操作，
 * 审计日志为追加写入，不支持修改和删除。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysAuditLog
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
