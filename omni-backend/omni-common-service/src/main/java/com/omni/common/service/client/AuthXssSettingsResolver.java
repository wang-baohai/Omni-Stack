package com.omni.common.service.client;

import com.omni.common.core.result.BusinessException;
import com.omni.common.core.result.R;
import com.omni.common.core.security.XssSettings;
import com.omni.common.service.xss.XssSettingsResolver;
import lombok.RequiredArgsConstructor;

/**
 * 通过 Auth 内部接口读取权威 XSS 设置的默认实现。
 *
 * @author Omni-Stack Team
 */
@RequiredArgsConstructor
public class AuthXssSettingsResolver implements XssSettingsResolver {

    private final AuthSecuritySettingsClient client;

    /** {@inheritDoc} */
    @Override
    public XssSettings resolve(Long tenantId) {
        R<XssSettings> response = client.getXssSettings(tenantId);
        if (response == null || response.getCode() != 200 || response.getData() == null) {
            throw new BusinessException(503, "XSS 权威配置暂时不可用");
        }
        return response.getData();
    }
}
