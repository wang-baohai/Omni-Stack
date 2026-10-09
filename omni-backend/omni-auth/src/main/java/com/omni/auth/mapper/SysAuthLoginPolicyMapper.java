package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysAuthLoginPolicy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录保护策略 Mapper 接口（D01）。
 * <p>提供 {@code sys_auth_login_policy} 表的读写。乐观并发更新通过
 * {@code LambdaUpdateWrapper.eq(tenantId).eq(version)} 实现，首次插入依赖 tenant_id 主键冲突判定。</p>
 *
 * @author Omni-Stack Team
 * @see SysAuthLoginPolicy
 */
@Mapper
public interface SysAuthLoginPolicyMapper extends BaseMapper<SysAuthLoginPolicy> {
}
