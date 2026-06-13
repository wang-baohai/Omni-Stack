package com.omni.auth.service;

import com.omni.common.core.result.PageResult;

/**
 * 授权记录服务接口，提供 OAuth2 授权记录的查询操作。
 */
public interface AuthRecordService {

    /**
     * 分页查询授权记录。
     *
     * @param page 页码
     * @param size 每页大小
     * @return 授权记录分页结果
     */
    PageResult<AuthRecordVO> listRecords(int page, int size);
}
