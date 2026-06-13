package com.omni.auth.service.impl;

import com.omni.auth.service.AuthRecordService;
import com.omni.auth.service.AuthRecordVO;
import com.omni.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 授权记录服务实现类。
 * <p>查询 Spring Authorization Server 的 {@code oauth2_authorization} 表，
 * 提供授权记录的分页查询功能。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthRecordServiceImpl implements AuthRecordService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * {@inheritDoc}
     *
     * <p>直接通过 JDBC 查询 {@code oauth2_authorization} 表，
     * 按创建时间倒序排列，使用 LIMIT/OFFSET 实现分页。</p>
     */
    @Override
    public PageResult<AuthRecordVO> listRecords(int page, int size) {
        int offset = (page - 1) * size;

        // 查询总数
        Long totalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_authorization", Long.class);
        long total = totalCount != null ? totalCount : 0L;

        // 分页查询
        List<AuthRecordVO> records = jdbcTemplate.query(
                "SELECT id, registered_client_id, principal_name, "
                        + "authorization_grant_type, authorized_scopes, access_token_issued_at "
                        + "FROM oauth2_authorization "
                        + "ORDER BY access_token_issued_at DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> AuthRecordVO.builder()
                        .id(rs.getString("id"))
                        .registeredClientId(rs.getString("registered_client_id"))
                        .principalName(rs.getString("principal_name"))
                        .authorizationGrantType(rs.getString("authorization_grant_type"))
                        .authorizedScopes(rs.getString("authorized_scopes"))
                        .createdAt(rs.getTimestamp("access_token_issued_at") != null
                                ? rs.getTimestamp("access_token_issued_at").toLocalDateTime() : null)
                        .build(),
                size, offset);

        return new PageResult<>(records, total, size, page);
    }
}
