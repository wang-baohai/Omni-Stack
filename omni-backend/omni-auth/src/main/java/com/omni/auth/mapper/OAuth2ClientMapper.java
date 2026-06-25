package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.OAuth2RegisteredClient;
import org.apache.ibatis.annotations.Mapper;

/**
 * OAuth2 注册客户端 Mapper 接口。
 * <p>提供 {@code oauth2_registered_client} 表的基础查询，
 * 主要用于管理界面的客户端列表分页查询。
 * 客户端的创建/修改/删除应通过 SAS 的 {@code RegisteredClientRepository} 接口。</p>
 *
 * @author Omni-Stack Team
 * @see com.omni.auth.entity.OAuth2RegisteredClient
 */
@Mapper
public interface OAuth2ClientMapper extends BaseMapper<OAuth2RegisteredClient> {
}
