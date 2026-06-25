package com.omni.base.controller;

import com.omni.base.dto.RegisterSystemJobRequest;
import com.omni.base.dto.SystemJobVO;
import com.omni.base.service.SystemJobService;
import com.omni.common.core.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系统任务管理控制器。
 * <p>
 * 提供系统级定时任务的查询、注册、启动、停止、触发、注销等全生命周期管理接口。
 * </p>
 *
 * @author Omni-Stack Team
 * @see com.omni.base.service.SystemJobService
 */
@RestController
@RequestMapping("/api/job/system-job")
@RequiredArgsConstructor
public class SystemJobController {

    private final SystemJobService systemJobService;

    /**
     * 查询所有系统任务列表（合并元数据 + XXL-JOB 实际状态）。
     *
     * @return 系统任务视图列表
     */
    @GetMapping("/list")
    @PreAuthorize("hasAuthority('job:system-job:list')")
    public R<List<SystemJobVO>> list() {
        return R.ok(systemJobService.listAll());
    }

    /**
     * 注册系统任务到 XXL-JOB。
     *
     * @param request 注册请求（包含 handlerName、cron、参数）
     * @return 空结果
     */
    @PostMapping("/register")
    @PreAuthorize("hasAuthority('job:system-job:manage')")
    public R<Void> register(@Valid @RequestBody RegisterSystemJobRequest request) {
        systemJobService.register(request);
        return R.ok();
    }

    /**
     * 启动任务。
     *
     * @param xxlJobId XXL-JOB 任务 ID
     * @return 空结果
     */
    @PostMapping("/{xxlJobId}/start")
    @PreAuthorize("hasAuthority('job:system-job:manage')")
    public R<Void> start(@PathVariable int xxlJobId) {
        systemJobService.start(xxlJobId);
        return R.ok();
    }

    /**
     * 停止任务。
     *
     * @param xxlJobId XXL-JOB 任务 ID
     * @return 空结果
     */
    @PostMapping("/{xxlJobId}/stop")
    @PreAuthorize("hasAuthority('job:system-job:manage')")
    public R<Void> stop(@PathVariable int xxlJobId) {
        systemJobService.stop(xxlJobId);
        return R.ok();
    }

    /**
     * 立即触发任务执行。
     *
     * @param xxlJobId XXL-JOB 任务 ID
     * @param param    执行参数（JSON 格式，可选）
     * @return 空结果
     */
    @PostMapping("/{xxlJobId}/trigger")
    @PreAuthorize("hasAuthority('job:system-job:manage')")
    public R<Void> trigger(@PathVariable int xxlJobId,
                           @RequestParam(required = false) String param) {
        systemJobService.trigger(xxlJobId, param);
        return R.ok();
    }

    /**
     * 从 XXL-JOB 注销（删除）任务。
     *
     * @param xxlJobId XXL-JOB 任务 ID
     * @return 空结果
     */
    @DeleteMapping("/{xxlJobId}")
    @PreAuthorize("hasAuthority('job:system-job:manage')")
    public R<Void> unregister(@PathVariable int xxlJobId) {
        systemJobService.unregister(xxlJobId);
        return R.ok();
    }
}
