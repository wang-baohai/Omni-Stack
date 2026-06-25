package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysXssBlacklistRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * XSS 黑名单规则 Mapper 接口。
 * <p>提供 {@code sys_xss_blacklist_rule} 表的 CRUD 操作，
 * 规则按 {@code sort_order} 排序加载。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.SysXssBlacklistRule
 * @see SysXssConfigMapper
 */
@Mapper
public interface SysXssBlacklistRuleMapper extends BaseMapper<SysXssBlacklistRule> {
}
