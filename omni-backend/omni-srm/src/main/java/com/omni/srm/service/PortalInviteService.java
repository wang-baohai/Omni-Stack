package com.omni.srm.service;

import com.omni.srm.dto.SrmRequests;
import com.omni.srm.dto.SrmViews;

import java.util.List;

/** SRM 门户邀请服务。 */
public interface PortalInviteService {
    /** 查询邀请列表。 */ List<SrmViews.InviteVO> list();
    /** 创建邀请。 */ SrmViews.InviteVO create(SrmRequests.CreateInviteRequest request);
    /** 撤销邀请。 */ void revoke(Long inviteId);
    /** 校验并原子消费邀请令牌，返回邀请 ID。 */ Long consumeInviteToken(String inviteToken);
}
