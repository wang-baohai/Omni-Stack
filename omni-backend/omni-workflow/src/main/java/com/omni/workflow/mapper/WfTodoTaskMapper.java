package com.omni.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.omni.workflow.entity.WfTodoTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办任务缓存表 Mapper。
 *
 * @author Omni-Stack Team
 */
@Mapper
public interface WfTodoTaskMapper extends BaseMapper<WfTodoTask> {
}
