package com.omni.auth.service.impl;

import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysOrgUnitMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.internal.InternalUserOptionDTO;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link InternalDirectoryServiceImpl} 单元测试。
 */
@ExtendWith(MockitoExtension.class)
class InternalDirectoryServiceImplTest {

    /** 用户 Mapper */
    @Mock
    private SysUserMapper sysUserMapper;

    /** 组织 Mapper */
    @Mock
    private SysOrgUnitMapper sysOrgUnitMapper;

    /** 被测服务 */
    private InternalDirectoryServiceImpl service;

    /**
     * 初始化被测对象。
     */
    @BeforeEach
    void setUp() {
        service = new InternalDirectoryServiceImpl(sysUserMapper, sysOrgUnitMapper);
    }

    /**
     * 单用户查询必须把租户 ID 下推到 Mapper。
     */
    @Test
    void should_query_user_with_tenant_filter() {
        SysUser user = user(9L, 3L);
        when(sysUserMapper.selectByIdAndTenantId(9L, 3L)).thenReturn(user);

        InternalUserDTO result = service.getUserById(9L, 3L);

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getTenantId()).isEqualTo(3L);
        verify(sysUserMapper).selectByIdAndTenantId(9L, 3L);
    }

    /**
     * 负责人搜索只返回最小字段并限制到目标租户。
     */
    @Test
    void should_return_minimal_enabled_user_options() {
        SysUser user = user(9L, 3L);
        user.setUsername("alice");
        user.setNickname("销售甲");
        user.setEmail("alice@example.com");
        user.setPhone("13800000000");
        when(sysUserMapper.searchEnabledUsers(3L, "ali", 20)).thenReturn(List.of(user));

        List<InternalUserOptionDTO> result = service.searchEnabledUserOptions(3L, " ali ", 20);

        assertThat(result).singleElement().satisfies(option -> {
            assertThat(option.getId()).isEqualTo(9L);
            assertThat(option.getUsername()).isEqualTo("alice");
            assertThat(option.getTenantId()).isEqualTo(3L);
        });
        verify(sysUserMapper).searchEnabledUsers(3L, "ali", 20);
    }

    /**
     * 超过搜索上限时应拒绝请求。
     */
    @Test
    void should_reject_owner_search_above_limit() {
        assertThatThrownBy(() -> service.searchEnabledUserOptions(3L, null, 101))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getCode()).isEqualTo(400));
    }

    /**
     * 创建测试用户。
     *
     * @param id       用户 ID
     * @param tenantId 租户 ID
     * @return 用户实体
     */
    private SysUser user(Long id, Long tenantId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setStatus(1);
        return user;
    }
}
