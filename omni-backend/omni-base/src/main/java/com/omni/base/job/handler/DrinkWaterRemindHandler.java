package com.omni.base.job.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.job.UserJobHandler;
import com.omni.common.core.job.UserJobMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 提醒喝水任务处理器。
 * <p>
 * 对应 {@code sys_user_job_type} 中 {@code type_code = Task-00001} 的任务类型。
 * Bean 名称必须与 {@code type_code} 完全一致，以便 {@link com.omni.base.job.UserJobHandlerRegistry} 路由。
 * </p>
 * <p>
 * 该类型定义了 {@code cupShape}（杯型）参数，Handler 解析后生成用户可读的提醒消息。
 * 结果消息存储在 {@code sys_user_job_log.result_message}，前端轮询后弹出通知。
 * </p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Component("Task-00001")
@RequiredArgsConstructor
public class DrinkWaterRemindHandler implements UserJobHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void execute(UserJobMessage message) throws Exception {
        String reminder = buildReminder(message);
        log.info("【喝水提醒】任务 [{}] 已触发：{}", message.getJobName(), reminder);
    }

    @Override
    public String getResultMessage(UserJobMessage message) {
        try {
            return buildReminder(message);
        } catch (Exception e) {
            log.warn("构建喝水提醒消息失败", e);
            return "请喝水，保持身体健康！";
        }
    }

    /**
     * 根据任务参数构建喝水提醒文本。
     */
    private String buildReminder(UserJobMessage message) throws Exception {
        String cupShape = parseCupShape(message.getJobParams());
        return "请喝一杯" + cupShape + "水，保持身体健康！";
    }

    /**
     * 从任务参数 JSON 中解析杯型配置。
     */
    private String parseCupShape(String jobParams) {
        if (jobParams == null || jobParams.isBlank()) {
            return "中杯";
        }
        try {
            JsonNode params = objectMapper.readTree(jobParams);
            JsonNode cupNode = params.get("cupShape");
            if (cupNode != null && !cupNode.isNull() && !cupNode.asText().isBlank()) {
                return cupNode.asText();
            }
        } catch (Exception ignored) {
            // JSON 解析失败，使用默认值
        }
        return "中杯";
    }
}
