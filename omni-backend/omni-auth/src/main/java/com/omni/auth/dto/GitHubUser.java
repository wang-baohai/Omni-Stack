package com.omni.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * GitHub 用户信息 DTO。
 * <p>
 * 对应 GitHub API {@code GET /user} 返回的用户资料，
 * 仅映射社交登录流程所需的字段。
 * </p>
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GitHubUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** GitHub 用户 ID（数字） */
    private Long id;
    /** GitHub 登录名（如 "octocat"） */
    private String login;
    /** 显示名称（可能为 null） */
    private String name;
    /** 邮箱地址（用户设为隐私时可能为 null） */
    private String email;
    /** 头像 URL */
    @JsonProperty("avatar_url")
    private String avatarUrl;
}
