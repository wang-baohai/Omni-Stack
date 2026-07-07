package com.omni.common.workflow.identity;

import java.util.List;

/**
 * 用户-组查询 SPI 接口。
 * <p>
 * 桥接 Flowable 身份系统与现有 RBAC 体系的核心抽象。
 * Flowable 引擎在任务分配和候选组查询时通过此接口解析用户与角色的映射关系，
 * 无需依赖 Flowable 内置的 {@code ACT_ID_*} 身份表。</p>
 * <p>
 * 使用方式：业务服务模块提供此接口的实现（如 {@code omni-workflow} 中
 * 查询 {@code sys_user_role} + {@code sys_role} 表），
 * Starter 自动发现并注入到 Flowable 引擎配置中。</p>
 *
 * @author Omni-Stack Team
 */
public interface UserGroupLookup {

    /**
     * 查询指定用户所属的角色/组编码列表。
     * <p>
     * Flowable 在解析 {@code candidateGroups} 时调用此方法，
     * 判断用户是否有资格处理某个任务。</p>
     *
     * @param userId 用户 ID（对应 {@code sys_user.id} 的字符串形式）
     * @return 该用户所属的角色编码列表，无角色时返回空列表
     */
    List<String> getGroupsForUser(String userId);

    /**
     * 查询系统中所有可用的角色/组编码列表。
     * <p>
     * 用于流程设计器中展示候选组下拉选项等场景。</p>
     *
     * @return 所有角色编码列表
     */
    List<String> getAllGroups();
}
