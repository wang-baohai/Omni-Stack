package com.omni.crm.dto;

import com.omni.crm.entity.CrmLead;
import com.omni.crm.entity.CrmActivity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** CRM 安全 VO 装配测试。 */
class CrmViewAssemblerTest {

    /** 清理认证上下文。 */
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    /** 列表视图必须按后端规则掩码。 */
    @Test
    void shouldMaskLeadWithoutPiiFlag() {
        CrmLead lead = lead();
        CrmViews.LeadVO vo = CrmViewAssembler.lead(lead, false);
        assertThat(vo.getMobile()).isEqualTo("138****1234");
        assertThat(vo.getEmail()).isEqualTo("a***@example.com");
        assertThat(vo.getAddress()).endsWith("******");
    }

    /** 仅精确 PII 权限可打开详情明文。 */
    @Test
    void shouldRecognizeExactPiiAuthority() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "sales", null, List.of(new SimpleGrantedAuthority("crm:pii:view"))));
        assertThat(CrmViewAssembler.canViewPii()).isTrue();
        assertThat(CrmViewAssembler.lead(lead(), true).getMobile()).isEqualTo("13812341234");
    }

    /** 活动自由文本在无 PII 权限的列表中必须完全隐藏。 */
    @Test
    void shouldRedactActivityContent() {
        CrmActivity activity = new CrmActivity(); activity.setContent("客户手机号 13812341234");
        assertThat(CrmViewAssembler.activity(activity).getContent()).isEqualTo("[REDACTED]");
        assertThat(CrmViewAssembler.activity(activity, true).getContent()).contains("13812341234");
    }

    private CrmLead lead() {
        CrmLead lead = new CrmLead(); lead.setMobile("13812341234"); lead.setEmail("alice@example.com");
        lead.setAddress("上海市浦东新区世纪大道100号"); return lead;
    }
}
