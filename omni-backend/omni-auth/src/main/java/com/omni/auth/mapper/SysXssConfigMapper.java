package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysXssConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * XSS 防护配置 Mapper 接口。
 * <p>提供 {@code sys_xss_config} 表的 CRUD 操作，
 * 每个租户一条配置记录，控制 XSS 防护的开关状态。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysXssConfig
 * @see SysXssBlacklistRuleMapper
 */
@Mapper
public interface SysXssConfigMapper extends BaseMapper<SysXssConfig> {
}
