package com.omni.procurement.service;

import com.omni.procurement.dto.WorkflowContracts;

/**
 * Workflow 完成事件 Inbox 处理服务。
 *
 * @author Omni-Stack Team
 */
public interface WorkflowCompletionService {

    /**
     * 幂等处理流程完成事件。
     *
     * @param event 完成事件
     * @return 是否推进了请购状态
     */
    boolean handle(WorkflowContracts.ProcessCompletedEvent event);
}
