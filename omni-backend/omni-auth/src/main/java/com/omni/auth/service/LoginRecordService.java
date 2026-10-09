package com.omni.auth.service;

import com.omni.auth.dto.LoginRecordQuery;
import com.omni.auth.dto.LoginRecordVO;
import com.omni.common.core.result.PageResult;

/**
 * 登录记录查询服务接口（D02）。
 * <p>数据源唯一为 {@code sys_audit_log}，服务端固定谓词只读投影，不新增重复写入。</p>
 *
 * @author Omni-Stack Team
 */
public interface LoginRecordService {

    /**
     * 分页查询本租户登录记录。
     *
     * @param tenantId 可信租户 ID（强制隔离）
     * @param query    查询参数
     * @return 脱敏后的登录记录分页结果
     */
    PageResult<LoginRecordVO> listLoginRecords(Long tenantId, LoginRecordQuery query);
}
