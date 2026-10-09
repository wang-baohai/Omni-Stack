package com.omni.crm.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.omni.common.core.mq.ReliableMessageRelay;
import com.omni.crm.dto.CrmRequests;
import com.omni.crm.entity.CrmActivity;
import com.omni.crm.entity.CrmLead;
import com.omni.crm.mapper.CrmActivityMapper;
import com.omni.crm.mapper.CrmContactMapper;
import com.omni.crm.mapper.CrmCustomerMapper;
import com.omni.crm.mapper.CrmLeadMapper;
import com.omni.crm.mapper.CrmOpportunityMapper;
import com.omni.common.service.identity.ServiceIdentityContext;
import com.omni.common.service.identity.ServiceRequestIdentity;
import com.omni.crm.service.support.CrmOwnerEnricher;
import com.omni.crm.service.support.CrmRecordAccessGuard;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 活动计划与根对象下次跟进时间一致性测试。 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest {

    /** 初始化 Lambda Update 所需的 MyBatis-Plus 元数据。 */
    @BeforeAll
    static void initTableMetadata() {
        init(CrmActivity.class);
        init(CrmLead.class);
    }

    @Mock private CrmActivityMapper activityMapper;
    @Mock private CrmLeadMapper leadMapper;
    @Mock private CrmCustomerMapper customerMapper;
    @Mock private CrmOpportunityMapper opportunityMapper;
    @Mock private CrmContactMapper contactMapper;
    @Mock private CrmRecordAccessGuard accessGuard;
    @Mock private ReliableMessageRelay reliableMessageRelay;
    @Mock private CrmOwnerEnricher ownerEnricher;
    @InjectMocks private ActivityServiceImpl activityService;

    /** 设置请求上下文和公共桩。 */
    @BeforeEach
    void setUp() {
        ServiceIdentityContext.set(new ServiceRequestIdentity(12L, 3L, "sales"));
        when(activityMapper.update(any(), any())).thenReturn(1);
        lenient().when(ownerEnricher.enrichOne(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    /** 清理请求上下文。 */
    @AfterEach
    void clear() {
        ServiceIdentityContext.clear();
    }

    /** 修改计划时间后重新计算根对象下次跟进时间。 */
    @Test
    void shouldRefreshRootFollowupAfterPlannedActivityUpdate() {
        CrmActivity activity = activity("PLANNED");
        when(accessGuard.requireActivity(50L)).thenReturn(activity);
        stubFollowupTimes();
        CrmRequests.UpdateActivityRequest request = new CrmRequests.UpdateActivityRequest();
        request.setVersion(1);
        request.setPlannedStartTime(LocalDateTime.now().plusDays(3));

        activityService.update(50L, request);

        verifyRefreshed();
    }

    /** 取消计划活动后从剩余计划和最近完成活动重新计算。 */
    @Test
    void shouldRefreshRootFollowupAfterCancel() {
        CrmActivity activity = activity("PLANNED");
        when(accessGuard.requireActivity(50L)).thenReturn(activity);
        stubFollowupTimes();
        CrmRequests.CancelActivityRequest request = new CrmRequests.CancelActivityRequest();
        request.setVersion(1);
        request.setReason("计划变化");

        activityService.cancel(50L, request);

        verifyRefreshed();
    }

    /** 恢复取消活动后重新计算根对象下次跟进时间。 */
    @Test
    void shouldRefreshRootFollowupAfterReschedule() {
        CrmActivity activity = activity("CANCELLED");
        when(accessGuard.requireActivity(50L)).thenReturn(activity);
        stubFollowupTimes();
        CrmRequests.RescheduleActivityRequest request = new CrmRequests.RescheduleActivityRequest();
        request.setVersion(1);
        request.setPlannedStartTime(LocalDateTime.now().plusDays(1));
        request.setPlannedEndTime(LocalDateTime.now().plusDays(1).plusHours(1));

        activityService.reschedule(50L, request);

        verifyRefreshed();
    }

    /** 删除未完成活动后清除可能已经失效的下次跟进时间。 */
    @Test
    void shouldRefreshRootFollowupAfterDelete() {
        CrmActivity activity = activity("PLANNED");
        when(accessGuard.requireActivity(50L)).thenReturn(activity);
        stubFollowupTimes();

        activityService.delete(50L, 1);

        verifyRefreshed();
    }

    private CrmActivity activity(String status) {
        CrmActivity activity = new CrmActivity();
        activity.setId(50L);
        activity.setRootType("LEAD");
        activity.setRootId(60L);
        activity.setStatus(status);
        activity.setVersion(1);
        return activity;
    }

    private void stubFollowupTimes() {
        when(activityMapper.selectEarliestPlannedTime("LEAD", 60L))
                .thenReturn(LocalDateTime.now().plusDays(2));
        when(activityMapper.selectLatestCompletedNextActionTime("LEAD", 60L))
                .thenReturn(LocalDateTime.now().plusDays(4));
    }

    private void verifyRefreshed() {
        verify(activityMapper).selectEarliestPlannedTime("LEAD", 60L);
        verify(activityMapper).selectLatestCompletedNextActionTime("LEAD", 60L);
        verify(leadMapper).update(eq(null), any());
    }

    private static void init(Class<?> entityClass) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "crm-activity-test");
        assistant.setCurrentNamespace("com.omni.crm.activity." + entityClass.getSimpleName());
        TableInfoHelper.initTableInfo(assistant, entityClass);
    }
}
