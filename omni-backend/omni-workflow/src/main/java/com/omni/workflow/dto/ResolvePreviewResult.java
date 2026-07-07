package com.omni.workflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 角色解析预览结果。
 *
 * @author Omni-Stack Team
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolvePreviewResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 候选人数量 */
    private int candidateCount;

    /** 候选人列表 */
    private List<CandidateUser> candidates;

    /**
     * 候选用户信息。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CandidateUser implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 用户 ID */
        private Long userId;

        /** 用户名 */
        private String username;

        /** 昵称 */
        private String nickname;

        /** 所属组织名称 */
        private String unitName;
    }
}
