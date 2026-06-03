package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.OAuth2RegisteredClient;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2 注册客户端 Mapper，提供 {@code oauth2_registered_client} 表的基础查询。
 */
@Mapper
public interface OAuth2ClientMapper extends BaseMapper<OAuth2RegisteredClient> {
}
