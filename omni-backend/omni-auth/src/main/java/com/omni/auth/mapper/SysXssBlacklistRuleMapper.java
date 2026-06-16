package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysXssBlacklistRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * XSS 黑名单规则 Mapper 接口。
 * <p>继承 MyBatis-Plus 的 BaseMapper，提供基础的 CRUD 操作。</p>
 */
@Mapper
public interface SysXssBlacklistRuleMapper extends BaseMapper<SysXssBlacklistRule> {
}
