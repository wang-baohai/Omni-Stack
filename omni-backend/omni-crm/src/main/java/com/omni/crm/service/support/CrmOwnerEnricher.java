package com.omni.crm.service.support;

import com.omni.common.core.internal.InternalOrgDTO;
import com.omni.common.core.internal.InternalUserDTO;
import com.omni.common.core.result.R;
import com.omni.crm.client.AuthInternalClient;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.security.CrmTenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRM 列表和详情负责人一次性批量展示增强器，Auth 不可用时保留 ID 降级。
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CrmOwnerEnricher {

    private final AuthInternalClient authInternalClient;

    /**
     * 一次批量补全负责人和组织名称，禁止逐行 Feign。
     *
     * @param records 视图列表
     * @param <T> 视图类型
     * @return 原列表
     */
    public <T extends CrmViews.OwnedVO> List<T> enrich(List<T> records) {
        if (records == null || records.isEmpty()) {
            return records == null ? Collections.emptyList() : records;
        }
        Long tenantId = CrmTenantContext.requireTenantId();
        String userIds = join(records.stream().map(CrmViews.OwnedVO::getOwnerUserId).toList());
        String unitIds = join(records.stream().map(CrmViews.OwnedVO::getOwnerUnitId).toList());
        try {
            Map<Long, InternalUserDTO> users = users(userIds, tenantId);
            Map<Long, InternalOrgDTO> orgs = orgs(unitIds, tenantId);
            records.forEach(record -> {
                InternalUserDTO user = users.get(record.getOwnerUserId());
                if (user != null) record.setOwnerName(hasText(user.getNickname()) ? user.getNickname() : user.getUsername());
                InternalOrgDTO org = orgs.get(record.getOwnerUnitId());
                if (org != null) record.setOwnerUnitName(org.getName());
            });
        } catch (RuntimeException exception) {
            log.warn("CRM 负责人展示增强降级为 ID：tenantId={}, message={}", tenantId, exception.getMessage());
        }
        return records;
    }

    /**
     * 补全单条视图。
     *
     * @param record 视图
     * @param <T> 视图类型
     * @return 原视图
     */
    public <T extends CrmViews.OwnedVO> T enrichOne(T record) {
        enrich(List.of(record));
        return record;
    }

    private Map<Long, InternalUserDTO> users(String ids, Long tenantId) {
        if (ids.isBlank()) return Map.of();
        R<List<InternalUserDTO>> response = authInternalClient.getUsers(ids, tenantId);
        if (response == null || response.getCode() != 200 || response.getData() == null) return Map.of();
        return response.getData().stream().filter(item -> tenantId.equals(item.getTenantId()))
                .collect(Collectors.toMap(InternalUserDTO::getId, Function.identity(), (left, right) -> left));
    }

    private Map<Long, InternalOrgDTO> orgs(String ids, Long tenantId) {
        if (ids.isBlank()) return Map.of();
        R<List<InternalOrgDTO>> response = authInternalClient.getOrgs(ids, tenantId);
        if (response == null || response.getCode() != 200 || response.getData() == null) return Map.of();
        return response.getData().stream().filter(item -> tenantId.equals(item.getTenantId()))
                .collect(Collectors.toMap(InternalOrgDTO::getId, Function.identity(), (left, right) -> left));
    }

    private String join(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).distinct().map(String::valueOf).collect(Collectors.joining(","));
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
