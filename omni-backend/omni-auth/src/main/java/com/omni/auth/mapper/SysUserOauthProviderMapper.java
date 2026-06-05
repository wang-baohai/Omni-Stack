package com.omni.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.auth.entity.SysUserOauthProvider;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户第三方身份关联 Mapper 接口。
 */
public interface SysUserOauthProviderMapper extends BaseMapper<SysUserOauthProvider> {

    /**
     * 根据提供商标识和第三方用户 ID 查询关联记录。
     * <p>利用唯一索引 {@code uk_provider_user (provider, provider_user_id)} 进行精确查找。</p>
     *
     * @param provider       提供商标识（如 "github"）
     * @param providerUserId 第三方用户 ID
     * @return 关联记录，不存在时返回 null
     */
    @Select("SELECT * FROM sys_user_oauth_provider WHERE provider = #{provider} AND provider_user_id = #{providerUserId}")
    SysUserOauthProvider selectByProviderAndUserId(@Param("provider") String provider,
                                                   @Param("providerUserId") String providerUserId);
}
