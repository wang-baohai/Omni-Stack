package com.omni.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * BPMN 校验结果。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 是否校验通过 */
    private boolean valid;

    /** 错误列表（阻塞发布） */
    private List<String> errors;

    /** 警告列表（不阻塞发布） */
    private List<String> warnings;
}
