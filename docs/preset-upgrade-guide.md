# 预设工程升级指南

预设工程通过 `scaffold.lock` 记录生成时的源版本、catalog 版本、预设版本、模块版本以及 service/CRUD/preset 模板版本。升级以“生成新目录并迁移手写改动”为默认策略，不支持原地破坏性裁剪。

## 升级前评估

1. 提交或备份现有工程、数据库和环境配置。
2. 保存当前 `scaffold.lock`，确认受管理文件是否存在人工漂移。
3. 阅读源仓库 release note、catalog 的 `compatibility`/`deprecation` 和预设版本变化。
4. 使用 `omni preset diff <old> <new>` 比较正式预设；自定义预设则比较解析后的依赖闭包。
5. 识别数据库 expand/migrate/contract 窗口和跨服务契约变化。

## 推荐升级流程

1. 使用新版本 CLI 对原预设或自定义 YAML 执行 `validate`、`explain` 和 `--dry-run`。
2. 生成到一个全新的空目录。
3. 比较新旧 `scaffold.lock` 和生成文件；不要直接复制旧的 Maven、Compose、Gateway、种子或 catalog 注册。
4. 迁移手写领域代码、测试和非敏感环境配置；生成区改动应回到声明或模板中重做。
5. 对数据库执行备份、fresh 和正式快照 clone-upgrade 演练。
6. 依次通过后端、前端、Compose、残留、登录/菜单/health、核心流程和 E2E 门禁。
7. 切换部署并保留一个发布周期的兼容回退窗口。

## 版本判断

- `preset.version`：模块组合和默认边界版本。
- catalog `version`：模块事实结构版本。
- `modules[].version`：具体模块实现版本。
- `templates.*`：生成文件形态版本。
- `source.version`：生成所用 Omni-Stack 源版本。

任一模板 major 变化或预设 major 变化都必须按迁移指南人工评审，不能自动覆盖手写文件。

## 回滚

应用回滚使用上一版镜像/工程和兼容数据库结构。数据库不执行未经演练的 down SQL；出现结构问题时优先追加前向修复 changeSet。切换回旧应用前必须确认它能读取扩展后的结构。

若新生成工程尚未部署，直接弃用该新目录即可，原工程不会被生成命令修改。若已部署，保留日志、迁移报告、`scaffold.lock` 和失败证据，完成根因分析后再重新生成。

## 升级完成标准

- 新锁文件与实际模块、路由、权限、数据库和 Compose 一致。
- fresh 与 clone-upgrade 均通过，重复迁移和种子执行幂等。
- 被移除模块没有 Maven、前端、权限、DB、MQ 或文档残留。
- 用户核心流程与角色/租户隔离无回归。
- README、依赖矩阵和维护文档已由新 catalog 重新生成或同步。
