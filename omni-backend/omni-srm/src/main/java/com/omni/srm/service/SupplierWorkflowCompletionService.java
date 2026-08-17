package com.omni.srm.service;

import com.omni.srm.dto.WorkflowContracts;

/**
 * 供应商准入 Workflow 完成事件处理服务。
 *
 * @author Omni-Stack Team
 */
public interface SupplierWorkflowCompletionService {

    /**
     * 处理 Workflow 流程完成事件。
     *
     * @param event 流程完成事件
     * @return 是否实际处理了事件
     */
    boolean handle(WorkflowContracts.ProcessCompletedEvent event);
}
