package com.omni.workflow.dto.internal;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * 内部服务发起流程响应。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
public class InternalStartProcessResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 调用方请求 ID。 */
    private String requestId;

    /** 业务类型。 */
    private String businessType;

    /** 业务主键。 */
    private String businessKey;

    /** Flowable 流程实例 ID。 */
    private String processInstanceId;

    /** 是否命中已有幂等启动结果。 */
    private boolean replayed;
}
