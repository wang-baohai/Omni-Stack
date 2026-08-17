package com.omni.auth.service.impl;

import com.omni.auth.dto.UpdateUserRequest;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysRoleMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.auth.mapper.SysUserRoleMapper;
import com.omni.auth.service.CaptchaService;
import com.omni.common.core.result.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户查询与安全更新测试。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private SysUserMapper userMapper;
    @Mock private SysUserRoleMapper userRoleMapper;
    @Mock private SysRoleMapper roleMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CaptchaService captchaService;
    @Mock private ApplicationEventPublisher eventPublisher;

    /** 查询结果必须映射为不含密码的专用视图。 */
    @Test
    void shouldReturnPasswordFreeUserView() {
        UserServiceImpl service = service();
        SysUser user = user(10L, 3L);
        user.setPassword("$2a$10$sensitive-hash");
        when(userMapper.selectByIdAndTenantId(10L, 3L)).thenReturn(user);

        var result = service.getUserDetail(10L, 3L);

        assertThat(result.getId()).isEqualTo(10L);
        assertThat(result.getUsername()).isEqualTo("alice");
        assertThat(result.getClass().getDeclaredFields())
                .noneMatch(field -> "password".equals(field.getName()));
    }

    /** 更新只能构造白名单字段，不能批量绑定租户、密码、账号或状态。 */
    @Test
    void shouldUpdateOnlyAllowedProfileFields() {
        UserServiceImpl service = service();
        when(userMapper.selectByIdAndTenantId(10L, 3L)).thenReturn(user(10L, 3L));
        when(userMapper.updateById(org.mockito.ArgumentMatchers.any(SysUser.class))).thenReturn(1);
        UpdateUserRequest request = new UpdateUserRequest();
        request.setNickname("新昵称");
        request.setEmail("alice@example.com");
        request.setGender(2);

        service.updateUser(10L, 3L, request);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(captor.capture());
        SysUser update = captor.getValue();
        assertThat(update.getNickname()).isEqualTo("新昵称");
        assertThat(update.getEmail()).isEqualTo("alice@example.com");
        assertThat(update.getGender()).isEqualTo(2);
        assertThat(update.getTenantId()).isNull();
        assertThat(update.getUsername()).isNull();
        assertThat(update.getPassword()).isNull();
        assertThat(update.getStatus()).isNull();
    }

    /** 跨租户 ID 查询必须按不存在处理。 */
    @Test
    void shouldFailClosedForCrossTenantUserId() {
        UserServiceImpl service = service();
        when(userMapper.selectByIdAndTenantId(10L, 3L)).thenReturn(null);

        assertThatThrownBy(() -> service.getUserDetail(10L, 3L))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    private UserServiceImpl service() {
        return new UserServiceImpl(userMapper, userRoleMapper, roleMapper, passwordEncoder,
                captchaService, eventPublisher);
    }

    private SysUser user(Long id, Long tenantId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setTenantId(tenantId);
        user.setUsername("alice");
        user.setNickname("Alice");
        user.setStatus(1);
        return user;
    }
}
