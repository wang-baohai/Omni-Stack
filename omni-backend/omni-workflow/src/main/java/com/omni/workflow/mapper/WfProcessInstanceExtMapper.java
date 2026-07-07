package com.omni.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.workflow.entity.WfProcessInstanceExt;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程实例扩展表 Mapper。
 *
 * @author Omni-Stack Team
 */
@Mapper
public interface WfProcessInstanceExtMapper extends BaseMapper<WfProcessInstanceExt> {
}
