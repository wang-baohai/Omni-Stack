# 5. 工程实践：谁发起、如何防重、下一位审批人是谁

假设请购单已经通过业务校验。工作流仍要知道：这是谁的申请，用哪一版规则，以及“部门经理”这几个字具体对应谁。

## 先分清两个启动入口

| 入口 | 谁调用 | 身份与重复请求 |
|---|---|---|
| 普通发起 | 用户经网关调用 process-instance/start | 使用当前身份，检查 workflow:instance:start；不自动获得内部启动预留幂等 |
| 内部发起 | 业务服务调用 internal/workflow/process-instance/start | 共享令牌和租户一致性校验；调用方提供可信发起人，并使用请求/业务键预留 |

内部接口不是“谁拿到编号就能替别人发起”。共享令牌保护服务调用，业务服务仍要先完成自己的用户授权。它提供的 startUserId/startUserName 不能当成由终端用户任意选择的字段。

普通发起拒绝伪装成其他用户的模拟身份，并在启动前清理来访变量里的发起人保留键。ProcessInstanceServiceImpl 原文：

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/ProcessInstanceServiceImpl.java" -->
```java
        safeVariables.remove("initiator");
        safeVariables.remove("startUserId");
        safeVariables.remove("authenticatedUserId");
        safeVariables.remove("START_USER_ID");
        // 启动会同步执行首个节点，候选人解析必须在此时已能读取可信发起人。
        safeVariables.put("initiator", String.valueOf(effectiveUserId));
```

这使首个节点找人时能读到可信发起人。Flowable 的认证上下文也会在 finally 清理，避免同一线程下一次处理时沿用上次身份。这个清洗发生在启动阶段，不代表后续审批的任意附加变量都已经过滤。

## 用确定的版本，不只记住一个名称

指定 modelVersionId 时，工程要求同租户、已发布且有引擎定义的版本。普通入口仍兼容按 processKey 和租户启动；这条路径不保证等于项目模型目录的当前发布指针。

业务内部启动会再次检查发布状态，不能只依赖保存审批路由时曾检查过。资产调拨、资产处置和供应商准入还要求模型分类与业务类型精确匹配。

## 幂等：网络重试仍认得同一件申请

幂等可以理解为“同一业务意图重放，不重复开流程”。内部启动预留同时约束租户内的 requestId，以及 businessType + businessKey。先尝试插入，冲突后再判断是不是同一件事：

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/service/impl/WorkflowProcessStartRequestServiceImpl.java" -->
```java
        if (requestMapper.insertIgnore(candidate) == 1) {
            return new Reservation(candidate, true, true);
        }
        return resolveConflict(candidate);
    }
```

这不是遇到重复就静默成功。工程会比较业务标识、模型版本和发起人，不一致就拒绝；不会比较标题、显示姓名或全部变量的完整摘要。

已 STARTED 的记录返回原实例并标 replayed=true；还在处理就返回 409。同一 requestId 的 FAILED 记录可重新预留，仅换 requestId 命中同一失败业务键，不会自动得到重试权。超时后应通过正式查询确认状态，并按同一意图使用同一 requestId 重试，不能不断换编号“碰运气”。

## “部门经理”怎样变成某个人

角色表示资格，组织表示从哪里找人，锚点表示这一次从哪个组织开始找。规则存在 BPMN 的 omni:assignment 扩展中，例如：

```json
{
  "assignmentType": "SCOPED_ROLE",
  "roleCode": "SUPER_ADMIN",
  "anchorType": "START_USER_PRIMARY_UNIT",
  "anchorParams": {},
  "scopeMode": "SAME_UNIT",
  "fallbackStrategy": "ERROR",
  "approvalMode": "ALL"
}
```

这只是配置形状，不是建议所有业务都让管理员审批，也不创建任何账号、角色或组织。角色与组织必须在目标租户实际配置。

| 锚点 | 当前怎样找起点 |
|---|---|
| START_USER_PRIMARY_UNIT | 发起人的启用主组织 |
| PARENT_BY_TYPE、PARENT_CHILDREN | 按 targetType 向上找；未给类型时使用主组织 |
| CHILD_BY_CODE、SIBLING_BY_CODE | 按编码找直属子组织或同级组织 |
| DEPT_BY_CODE | 当前租户按编码找启用组织 |
| ABSOLUTE_UNIT | 普通解析读取 unitIds 或 unitId |
| PARENT | 主组织的父组织 |
| CHILD_UNIT、SIBLING_UNIT | 直接读配置 unitId，不自动证明它与发起人的亲缘关系 |

scopeMode 中 SAME_UNIT 是同组织，UNIT_AND_BELOW 是该组织及下级，CHILDREN_ONLY 是直属子组织；未知值当前会落到同组织。它们是找候选人的组织范围，不能和数据权限的六种 dataScope 枚举混为一谈。

## 在什么时候把人写进去

设计器写入节点 start 执行监听器，**在多实例读取人员集合前**解析候选人。ScopedRoleAssignmentListener 中的工程原文：

<!-- omni:source path="omni-backend/omni-workflow/src/main/java/com/omni/workflow/listener/ScopedRoleAssignmentListener.java" -->
```java
            // 3. 将候选人列表写入流程变量，供多实例 collection 使用
            delegateExecution.setVariable("candidateUserIds",
                    candidateUserIds.stream()
                            .map(String::valueOf)
                            .collect(Collectors.toList()));

            candidateResolutionService.initApprovalCounters(
                    delegateExecution, approvalMode, candidateUserIds.size());
```

候选集合叫 candidateUserIds，每个人对应的元素变量叫 userId，任务处理人引用这个 userId。计数器在本活动的多实例根作用域初始化，用根本地的去重标志避免重复初始化或串入上一个节点的票数。

旧 TaskListener 入口只记日志，不能替代这个启动监听器。兼容的候选集合表达式共用解析服务，但旧代理的异常与回退处理并不完全一样。缺 assignment 或 roleCode 的监听器分支还可能直接返回，不能笼统承诺所有旧模型都会以同一种方式失败。

## 找不到人，绝不能凭空算通过

| 回退策略 | 当前处理 |
|---|---|
| ERROR，含缺省和其他未识别策略 | 抛错 |
| ASSIGN_ADMIN | 找当前租户、组织 ID 1 下的 SUPER_ADMIN 候选；不是硬编码用户 1，无人仍失败 |
| ESCALATE_PARENT | 从锚点父组织逐级上溯，最多 20 层，首次命中即返回；到顶仍无人则失败 |

上溯的 ABSOLUTE_UNIT 只读取单个 unitId，不会自动把 unitIds 第一个元素当起点。组织缺失、用户停用、角色或范围不匹配、查询异常，都可能使候选为空。先补正确配置和合法数据，再按业务重试规则恢复，不要绕过为自动审批。

配置变化后要发布新模型版本；本项目没有独立的运行时候选配置表来自动改动所有在途实例。下一页看这些人拿到任务之后的[动作与会签](workflow-tasks.md)。
