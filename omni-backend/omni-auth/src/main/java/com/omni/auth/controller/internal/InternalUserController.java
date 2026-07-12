package com.omni.auth.controller.internal;

import com.omni.auth.entity.SysOrgUnit;
import com.omni.auth.entity.SysUser;
import com.omni.auth.mapper.SysOrgUnitMapper;
import com.omni.auth.mapper.SysUserMapper;
import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 内部 API 控制器，供其他微服务通过 Feign 调用。
 * <p>不经过 Gateway，仅通过 {@code X-Internal-Token} 认证。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalUserController {

    private final SysUserMapper sysUserMapper;
    private final SysOrgUnitMapper sysOrgUnitMapper;

    /**
     * 根据用户 ID 获取用户基本信息。
     *
     * @param id 用户 ID
     * @return 用户 DTO
     */
    @GetMapping("/users/{id}")
    public R<InternalUserDTO> getUserById(@PathVariable Long id) {
        SysUser user = sysUserMapper.selectById(id);
        if (user == null) {
            return R.ok(null);
        }
        return R.ok(toUserDTO(user));
    }

    /**
     * 批量获取用户基本信息。
     *
     * @param ids 用户 ID 列表（逗号分隔）
     * @return 用户 DTO 列表
     */
    @GetMapping("/users/batch")
    public R<List<InternalUserDTO>> getUsersByIds(@RequestParam String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();

        if (idList.isEmpty()) {
            return R.ok(List.of());
        }

        List<SysUser> users = sysUserMapper.selectBatchIds(idList);
        List<InternalUserDTO> dtos = users.stream().map(this::toUserDTO).toList();
        return R.ok(dtos);
    }

    /**
     * 根据组织单元 ID 获取组织信息。
     *
     * @param id 组织单元 ID
     * @return 组织 DTO
     */
    @GetMapping("/orgs/{id}")
    public R<InternalOrgDTO> getOrgById(@PathVariable Long id) {
        SysOrgUnit unit = sysOrgUnitMapper.selectById(id);
        if (unit == null) {
            return R.ok(null);
        }
        return R.ok(toOrgDTO(unit));
    }

    /**
     * 批量获取组织单元信息。
     *
     * @param ids 组织单元 ID 列表（逗号分隔）
     * @return 组织 DTO 列表
     */
    @GetMapping("/orgs/batch")
    public R<List<InternalOrgDTO>> getOrgsByIds(@RequestParam String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .toList();

        if (idList.isEmpty()) {
            return R.ok(List.of());
        }

        List<SysOrgUnit> units = sysOrgUnitMapper.selectBatchIds(idList);
        List<InternalOrgDTO> dtos = units.stream().map(this::toOrgDTO).toList();
        return R.ok(dtos);
    }

    private InternalUserDTO toUserDTO(SysUser user) {
        InternalUserDTO dto = new InternalUserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setNickname(user.getNickname());
        dto.setTenantId(user.getTenantId());
        dto.setPrimaryUnitId(user.getPrimaryUnitId());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setAvatar(user.getAvatar());
        dto.setStatus(user.getStatus());
        return dto;
    }

    private InternalOrgDTO toOrgDTO(SysOrgUnit unit) {
        InternalOrgDTO dto = new InternalOrgDTO();
        dto.setId(unit.getId());
        dto.setTenantId(unit.getTenantId());
        dto.setParentId(unit.getParentId());
        dto.setName(unit.getName());
        dto.setType(unit.getType());
        dto.setUnitCode(unit.getUnitCode());
        dto.setPath(unit.getPath());
        dto.setStatus(unit.getStatus());
        return dto;
    }
}
