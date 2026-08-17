package com.omni.crm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.omni.crm.dto.CrmViews;
import com.omni.crm.entity.CrmCustomer;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/** CRM 客户 Mapper。 */
public interface CrmCustomerMapper extends BaseMapper<CrmCustomer> {

    /**
     * 批量查询客户名称（不受数据权限限制，仅用于列表展示填充）。
     *
     * @param ids 客户 ID 列表
     * @return 客户列表（仅含 id 和 name）
     */
    @InterceptorIgnore(dataPermission = "true")
    @Select("<script>SELECT id, name FROM crm_customer WHERE deleted = 0 AND id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<CrmCustomer> selectNamesByIds(@Param("ids") List<Long> ids);

    /**
     * 在数据权限范围内锁定客户。
     *
     * @param id 客户 ID
     * @return 客户
     */
    @Select("SELECT * FROM crm_customer WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    CrmCustomer selectVisibleForUpdate(@Param("id") Long id);

    /** 赢单后激活已授权商机所属潜客，仅忽略客户 owner 数据权限并保留租户隔离。 */
    @InterceptorIgnore(dataPermission = "true")
    @Update("UPDATE crm_customer SET status = 'ACTIVE', version = version + 1, "
            + "update_time = #{now}, update_by = #{operator} "
            + "WHERE id = #{customerId} AND status = 'POTENTIAL' AND deleted = 0")
    int activatePotentialAfterOpportunityWin(@Param("customerId") Long customerId,
                                              @Param("now") LocalDateTime now,
                                              @Param("operator") String operator);

    /**
     * 统计指定时间范围内活跃客户的待跟进数。
     *
     * @param rangeStart 范围起始（含）
     * @param rangeEnd   范围结束（含）
     * @return 待跟进数
     */
    @Select("SELECT COUNT(*) FROM crm_customer WHERE deleted = 0 AND next_followup_time IS NOT NULL "
            + "AND next_followup_time >= #{rangeStart} AND next_followup_time <= #{rangeEnd} "
            + "AND status IN ('POTENTIAL', 'ACTIVE', 'DORMANT')")
    long countFollowups(@Param("rangeStart") LocalDateTime rangeStart, @Param("rangeEnd") LocalDateTime rangeEnd);
}
