package com.omni.asset.service;

import com.omni.asset.dto.WorkflowContracts;

/**
 * Workflow 流程完成事件处理服务。
 *
 * @author Omni-Stack Team
 */
public interface WorkflowCompletionService {

    /**
     * 幂等处理流程完成事件。
     *
     * @param event 完成事件
     * @return 是否更新了业务状态
     */
    boolean handle(WorkflowContracts.ProcessCompletedEvent event);
}
