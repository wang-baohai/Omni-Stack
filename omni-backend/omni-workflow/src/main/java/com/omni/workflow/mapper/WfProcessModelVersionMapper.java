package com.omni.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.workflow.dto.internal.InternalModelVersionProjection;
import com.omni.workflow.dto.internal.InternalModelVersionResponse;
import com.omni.workflow.entity.WfProcessModelVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

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

    /**
     * 查询指定分类的全部当前已发布模型版本。
     *
     * @param tenantId 租户 ID
     * @param category 模型分类
     * @return 当前已发布模型版本
     */
    @Select("""
            SELECT version_record.id,
                   version_record.model_id AS modelId,
                   model_record.model_key AS modelKey,
                   model_record.model_name AS modelName,
                   model_record.category,
                   version_record.version,
                   version_record.publish_time AS publishTime,
                   version_record.process_definition_id AS processDefinitionId,
                   version_record.status,
                   model_record.status AS modelStatus,
                   model_record.current_published_version_id AS currentPublishedVersionId
            FROM wf_process_model model_record
            JOIN wf_process_model_version version_record
              ON version_record.id = model_record.current_published_version_id
             AND version_record.model_id = model_record.id
             AND version_record.tenant_id = model_record.tenant_id
            WHERE model_record.tenant_id = #{tenantId}
              AND model_record.category = #{category}
              AND model_record.status = 1
              AND version_record.status = 'PUBLISHED'
              AND version_record.process_definition_id IS NOT NULL
              AND version_record.process_definition_id <> ''
            ORDER BY model_record.model_name, version_record.version DESC, version_record.id DESC
            """)
    List<InternalModelVersionProjection> selectPublishedByCategory(
            @Param("tenantId") Long tenantId,
            @Param("category") String category);

    /**
     * 批量查询租户内模型版本元数据。
     *
     * @param tenantId 租户 ID
     * @param modelVersionIds 模型版本 ID
     * @return 已找到的模型版本投影
     */
    @Select("""
            <script>
            SELECT version_record.id,
                   version_record.model_id AS modelId,
                   model_record.model_key AS modelKey,
                   model_record.model_name AS modelName,
                   model_record.category,
                   version_record.version,
                   version_record.publish_time AS publishTime,
                   version_record.process_definition_id AS processDefinitionId,
                   version_record.status,
                   model_record.status AS modelStatus,
                   model_record.current_published_version_id AS currentPublishedVersionId
            FROM wf_process_model_version version_record
            JOIN wf_process_model model_record
              ON model_record.id = version_record.model_id
             AND model_record.tenant_id = version_record.tenant_id
            WHERE version_record.tenant_id = #{tenantId}
              AND version_record.id IN
              <foreach collection="modelVersionIds" item="modelVersionId" open="(" separator="," close=")">
                #{modelVersionId}
              </foreach>
            </script>
            """)
    List<InternalModelVersionProjection> selectInternalBatch(
            @Param("tenantId") Long tenantId,
            @Param("modelVersionIds") List<Long> modelVersionIds);

    /**
     * 查询安全预览所需的模型版本及 BPMN XML。
     *
     * @param tenantId 租户 ID
     * @param modelVersionId 模型版本 ID
     * @return 模型版本投影
     */
    @Select("""
            SELECT version_record.id,
                   version_record.model_id AS modelId,
                   model_record.model_key AS modelKey,
                   model_record.model_name AS modelName,
                   model_record.category,
                   version_record.version,
                   version_record.publish_time AS publishTime,
                   version_record.process_definition_id AS processDefinitionId,
                   version_record.status,
                   model_record.status AS modelStatus,
                   model_record.current_published_version_id AS currentPublishedVersionId,
                   version_record.bpmn_xml AS bpmnXml
            FROM wf_process_model_version version_record
            JOIN wf_process_model model_record
              ON model_record.id = version_record.model_id
             AND model_record.tenant_id = version_record.tenant_id
            WHERE version_record.tenant_id = #{tenantId}
              AND version_record.id = #{modelVersionId}
            """)
    InternalModelVersionProjection selectPreviewDetails(
            @Param("tenantId") Long tenantId,
            @Param("modelVersionId") Long modelVersionId);
}
