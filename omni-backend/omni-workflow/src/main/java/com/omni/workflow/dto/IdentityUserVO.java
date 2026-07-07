package com.omni.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户身份视图对象。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdentityUserVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    /** 昵称 */
    private String nickname;

    /** 所属组织 ID */
    private Long unitId;

    /** 所属组织名称 */
    private String unitName;
}
