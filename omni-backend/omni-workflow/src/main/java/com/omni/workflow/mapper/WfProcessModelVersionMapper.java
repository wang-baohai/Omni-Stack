package com.omni.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.workflow.dto.internal.InternalModelVersionResponse;
import com.omni.workflow.entity.WfProcessModelVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 流程模型版本 Mapper 接口。
 *
 * @author Omni-Stack Team
 */
public interface WfProcessModelVersionMapper extends BaseMapper<WfProcessModelVersion> {

    /**
     * 查询租户内模型版本及其模型业务标识。
     *
     * @param tenantId 租户 ID
     * @param modelVersionId 模型版本 ID
     * @return 内部模型版本详情
     */
    @Select("""
            SELECT version_record.id,
                   version_record.model_id AS modelId,
                   model_record.model_key AS modelKey,
                   model_record.category,
                   version_record.version,
                   version_record.process_definition_id AS processDefinitionId,
                   version_record.status
            FROM wf_process_model_version version_record
            JOIN wf_process_model model_record
              ON model_record.id = version_record.model_id
             AND model_record.tenant_id = version_record.tenant_id
             AND model_record.status = 1
            WHERE version_record.tenant_id = #{tenantId}
              AND version_record.id = #{modelVersionId}
            """)
    InternalModelVersionResponse selectInternalDetails(
            @Param("tenantId") Long tenantId,
            @Param("modelVersionId") Long modelVersionId);

    /**
     * 按业务分类查询租户当前已发布模型版本。
     *
     * @param tenantId 租户 ID
     * @param category 业务分类
     * @return 当前已发布模型版本，不存在时返回 null
     */
    @Select("""
            SELECT version_record.id,
                   version_record.model_id AS modelId,
                   model_record.model_key AS modelKey,
                   model_record.category,
                   version_record.version,
                   version_record.process_definition_id AS processDefinitionId,
                   version_record.status
            FROM wf_process_model model_record
            JOIN wf_process_model_version version_record
              ON version_record.id = model_record.current_published_version_id
             AND version_record.model_id = model_record.id
             AND version_record.tenant_id = model_record.tenant_id
            WHERE model_record.tenant_id = #{tenantId}
              AND model_record.category = #{category}
              AND model_record.status = 1
              AND version_record.status = 'PUBLISHED'
            ORDER BY version_record.version DESC, version_record.id DESC
            LIMIT 1
            """)
    InternalModelVersionResponse selectCurrentPublishedByCategory(
            @Param("tenantId") Long tenantId,
            @Param("category") String category);
}
