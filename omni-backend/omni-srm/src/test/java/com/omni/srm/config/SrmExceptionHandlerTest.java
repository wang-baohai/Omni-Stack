package com.omni.srm.config;

import com.omni.common.core.result.R;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** SRM 请求反序列化异常响应测试。 */
class SrmExceptionHandlerTest {

    /** 未知身份字段触发的反序列化异常必须映射为业务码 400。 */
    @Test
    void shouldReturnBadRequestForUnreadableJson() {
        R<Void> response = new SrmExceptionHandler().handleUnreadableRequest();

        assertThat(response.getCode()).isEqualTo(400);
    }
}
