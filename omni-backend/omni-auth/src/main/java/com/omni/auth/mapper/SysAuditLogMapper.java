package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全审计日志 Mapper 接口。
 */
@Mapper
public interface SysAuditLogMapper extends BaseMapper<SysAuditLog> {
}
