package com.omni.base.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字典选项 VO，用于前端下拉组件。
 * <p>仅包含 {@code value}（存储值）和 {@code label}（显示标签）两个字段，轻量传输。</p>
 *
 * @author Omni-Stack Team
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DictOptionVO {

    /** 字典值（存储值） */
    private String value;

    /** 字典标签（显示值） */
    private String label;
}
