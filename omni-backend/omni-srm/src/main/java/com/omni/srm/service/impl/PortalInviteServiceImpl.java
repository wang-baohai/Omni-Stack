package com.omni.srm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.omni.common.core.result.BusinessException;
import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViewAssembler;
import com.omni.srm.dto.SrmViews;
import com.omni.srm.entity.SrmSupplierInvite;
import com.omni.srm.mapper.SrmSupplierInviteMapper;
import com.omni.srm.security.SrmTenantContext;
import com.omni.srm.service.PortalInviteService;
import com.omni.srm.service.support.SrmAuditSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** SRM 门户邀请服务实现。 */
@Service
@RequiredArgsConstructor
public class PortalInviteServiceImpl implements PortalInviteService {

    private final SrmSupplierInviteMapper inviteMapper;

    /** {@inheritDoc} */
    @Override
    public List<SrmViews.InviteVO> list() {
        Long tenantId = SrmTenantContext.requireTenantId();
        return inviteMapper.selectList(new LambdaQueryWrapper<SrmSupplierInvite>()
                        .eq(SrmSupplierInvite::getTenantId, tenantId)
                        .eq(SrmSupplierInvite::getDeleted, 0)
                        .orderByDesc(SrmSupplierInvite::getCreateTime)).stream()
                .map(this::inviteView).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public SrmViews.InviteVO create(SrmRequests.CreateInviteRequest request) {
        String rawCode = UUID.randomUUID().toString();
        String hash = sha256(rawCode);
        int maxUses = request.getMaxUses() != null ? Math.max(1, request.getMaxUses()) : 10;
        int expiresHours = request.getExpiresHours() != null ? Math.max(1, request.getExpiresHours()) : 72;
        SrmSupplierInvite invite = new SrmSupplierInvite();
        invite.setTenantId(SrmTenantContext.requireTenantId());
        invite.setInviteCodeHash(hash);
        invite.setStatus("ACTIVE");
        invite.setExpiresTime(LocalDateTime.now().plusHours(expiresHours));
        invite.setMaxUses(maxUses);
        invite.setUsedCount(0);
        invite.setVersion(0);
        invite.setDeleted(0);
        SrmAuditSupport.created(invite);
        inviteMapper.insert(invite);
        SrmViews.InviteVO vo = SrmViewAssembler.invite(invite);
        // 返回原始令牌（仅创建时可见，不存库）
        vo.setInviteToken(rawCode);
        return vo;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public void revoke(Long inviteId) {
        Long tenantId = SrmTenantContext.requireTenantId();
        SrmSupplierInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<SrmSupplierInvite>()
                .eq(SrmSupplierInvite::getTenantId, tenantId)
                .eq(SrmSupplierInvite::getId, inviteId)
                .eq(SrmSupplierInvite::getDeleted, 0));
        if (invite == null) throw new BusinessException(404, "邀请不存在");
        if (!"ACTIVE".equals(invite.getStatus())) throw new BusinessException(409, "邀请已非活跃状态");
        if (inviteMapper.revoke(inviteId, tenantId, invite.getVersion(), LocalDateTime.now(), operator()) != 1) {
            throw new BusinessException(409, "邀请已被其他用户修改，请刷新后重试");
        }
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public Long consumeInviteToken(String inviteToken) {
        if (inviteToken == null || inviteToken.isBlank()) {
            throw new BusinessException(400, "邀请令牌不能为空");
        }
        Long tenantId = SrmTenantContext.requireTenantId();
        String hash = sha256(inviteToken);
        SrmSupplierInvite invite = inviteMapper.selectOne(new LambdaQueryWrapper<SrmSupplierInvite>()
                .eq(SrmSupplierInvite::getTenantId, tenantId)
                .eq(SrmSupplierInvite::getInviteCodeHash, hash)
                .eq(SrmSupplierInvite::getStatus, "ACTIVE")
                .eq(SrmSupplierInvite::getDeleted, 0));
        if (invite == null) throw new BusinessException(404, "邀请不存在或已失效");
        LocalDateTime now = LocalDateTime.now();
        if (invite.getExpiresTime() != null && !invite.getExpiresTime().isAfter(now)) {
            throw new BusinessException(410, "邀请已过期");
        }
        if (invite.getMaxUses() != null && invite.getUsedCount() >= invite.getMaxUses()) {
            throw new BusinessException(410, "邀请已达到最大使用次数");
        }
        if (inviteMapper.consume(invite.getId(), tenantId, invite.getVersion(), now, operator()) != 1) {
            throw new BusinessException(409, "邀请已被使用或状态已变化，请重试");
        }
        return invite.getId();
    }

    private String operator() {
        String username = SrmTenantContext.require().username();
        return username == null || username.isBlank()
                ? String.valueOf(SrmTenantContext.require().userId()) : username;
    }

    private SrmViews.InviteVO inviteView(SrmSupplierInvite invite) {
        SrmViews.InviteVO vo = SrmViewAssembler.invite(invite);
        if ("ACTIVE".equals(invite.getStatus())) {
            LocalDateTime now = LocalDateTime.now();
            if (invite.getExpiresTime() != null && !invite.getExpiresTime().isAfter(now)) {
                vo.setStatus("EXPIRED");
            } else if (invite.getMaxUses() != null && invite.getUsedCount() >= invite.getMaxUses()) {
                vo.setStatus("USED");
            }
        }
        return vo;
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}
