package com.omni.workflow.controller;

import com.omni.common.result.GlobalExceptionHandler;
import com.omni.workflow.service.impl.ProcessInstanceServiceImpl;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricActivityInstanceQuery;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.task.Comment;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 流程进度和审批记录的租户边界回归。
 * <p>使用真实 MVC 参数绑定和服务实现，Flowable 历史数据为受控双租户模拟数据；
 * 不替代真实数据库或 Gateway 认证集成验收。</p>
 */
class ProcessInstanceReadTenantBoundaryTest {

    private static final String INSTANCE_ID = "tenant-two-instance";
    private static final String PRIVATE_NODE = "租户二内部审批节点";
    private static final String PRIVATE_COMMENT = "租户二保密审批意见";

    private HistoryService historyService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private JdbcTemplate jdbcTemplate;
    private MockMvc mvc;

    /** 模拟租户二的历史节点、任务与意见，保留未按租户过滤时的真实查询语义。 */
    @BeforeEach
    void setUp() {
        historyService = mock(HistoryService.class);
        runtimeService = mock(RuntimeService.class);
        taskService = mock(TaskService.class);
        jdbcTemplate = mock(JdbcTemplate.class);

        HistoricProcessInstance instance = mock(HistoricProcessInstance.class);
        when(instance.getTenantId()).thenReturn("2");
        HistoricProcessInstanceQuery instanceQuery = mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        AtomicReference<String> requestedTenant = new AtomicReference<>();
        doAnswer(call -> {
            requestedTenant.set(call.getArgument(0));
            return instanceQuery;
        }).when(instanceQuery).processInstanceTenantId(anyString());
        when(instanceQuery.singleResult()).thenAnswer(call ->
                requestedTenant.get() == null || "2".equals(requestedTenant.get()) ? instance : null);
        when(historyService.createHistoricProcessInstanceQuery()).thenReturn(instanceQuery);

        HistoricActivityInstance activity = mock(HistoricActivityInstance.class);
        when(activity.getActivityId()).thenReturn("approval-node");
        when(activity.getActivityName()).thenReturn(PRIVATE_NODE);
        when(activity.getActivityType()).thenReturn("userTask");
        HistoricActivityInstanceQuery activityQuery = mock(HistoricActivityInstanceQuery.class, RETURNS_SELF);
        when(activityQuery.list()).thenReturn(List.of(activity));
        when(historyService.createHistoricActivityInstanceQuery()).thenReturn(activityQuery);

        HistoricTaskInstance task = mock(HistoricTaskInstance.class);
        when(task.getId()).thenReturn("tenant-two-task");
        when(task.getTaskDefinitionKey()).thenReturn("approval-node");
        when(task.getAssignee()).thenReturn("202");
        HistoricTaskInstanceQuery taskQuery = mock(HistoricTaskInstanceQuery.class, RETURNS_SELF);
        when(taskQuery.list()).thenReturn(List.of(task));
        when(historyService.createHistoricTaskInstanceQuery()).thenReturn(taskQuery);

        Comment comment = mock(Comment.class);
        when(comment.getTaskId()).thenReturn("tenant-two-task");
        when(comment.getFullMessage()).thenReturn(PRIVATE_COMMENT);
        when(taskService.getProcessInstanceComments(INSTANCE_ID)).thenReturn(List.of(comment));
        when(runtimeService.getActiveActivityIds(INSTANCE_ID)).thenReturn(List.of("approval-node"));

        ProcessInstanceServiceImpl service = new ProcessInstanceServiceImpl(
                runtimeService, historyService, mock(RepositoryService.class), taskService,
                null, null, null, null, jdbcTemplate, null);
        mvc = MockMvcBuilders.standaloneSetup(new ProcessInstanceController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    /** 租户一不能通过已知实例 ID 读取租户二的流程节点。 */
    @Test
    void should_hide_progress_when_instance_belongs_to_other_tenant() throws Exception {
        mvc.perform(get("/api/workflow/process-instance/{id}/progress", INSTANCE_ID)
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(content().string(not(containsString(PRIVATE_NODE))));
        verifyNoInteractions(runtimeService, taskService, jdbcTemplate);
    }

    /** 租户一不能通过已知实例 ID 读取租户二的审批意见。 */
    @Test
    void should_hide_records_when_instance_belongs_to_other_tenant() throws Exception {
        mvc.perform(get("/api/workflow/process-instance/{id}/approval-records", INSTANCE_ID)
                        .header("X-Tenant-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(content().string(not(containsString(PRIVATE_COMMENT))));
        verifyNoInteractions(runtimeService, taskService, jdbcTemplate);
    }

    /** 同租户仍可获取原有节点结构。 */
    @Test
    void should_preserve_progress_when_instance_belongs_to_current_tenant() throws Exception {
        mvc.perform(get("/api/workflow/process-instance/{id}/progress", INSTANCE_ID)
                        .header("X-Tenant-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(content().string(containsString(PRIVATE_NODE)));
    }

    /** 同租户仍可获取原有审批意见与结果字段。 */
    @Test
    void should_preserve_records_when_instance_belongs_to_current_tenant() throws Exception {
        mvc.perform(get("/api/workflow/process-instance/{id}/approval-records", INSTANCE_ID)
                        .header("X-Tenant-Id", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].comment").value(PRIVATE_COMMENT))
                .andExpect(jsonPath("$.data[0].result").value("pending"));
    }

    /** 无效租户在任何引擎或用户数据读取之前被拒绝。 */
    @Test
    void should_reject_invalid_tenant_before_reading_progress() throws Exception {
        mvc.perform(get("/api/workflow/process-instance/{id}/progress", INSTANCE_ID)
                        .header("X-Tenant-Id", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(historyService, runtimeService, taskService, jdbcTemplate);
    }

    /** 无效租户不能绕过审批意见读取边界。 */
    @Test
    void should_reject_invalid_tenant_before_reading_records() throws Exception {
        mvc.perform(get("/api/workflow/process-instance/{id}/approval-records", INSTANCE_ID)
                        .header("X-Tenant-Id", "-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(historyService, runtimeService, taskService, jdbcTemplate);
    }
}
