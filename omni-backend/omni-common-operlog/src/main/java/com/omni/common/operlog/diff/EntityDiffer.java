package com.omni.common.operlog.diff;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体 JSON diff 工具。
 * <p>将新旧实体序列化为 JSON，仅输出变更字段。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@RequiredArgsConstructor
public class EntityDiffer {

    private final ObjectMapper objectMapper;

    /**
     * 计算两个实体的 JSON diff。
     *
     * @param oldEntity 变更前的实体（可为 null）
     * @param newEntity 变更后的实体（可为 null）
     * @return 包含 oldValue 和 newValue 的结果，仅含变更字段；若两端都为 null 则返回空字符串
     */
    public DiffResult diff(Object oldEntity, Object newEntity) {
        if (oldEntity == null && newEntity == null) {
            return new DiffResult(null, null);
        }
        try {
            String oldJson = oldEntity != null ? objectMapper.writeValueAsString(oldEntity) : null;
            String newJson = newEntity != null ? objectMapper.writeValueAsString(newEntity) : null;

            if (oldJson != null && newJson != null) {
                return computeFieldDiff(oldJson, newJson);
            }
            return new DiffResult(oldJson, newJson);
        } catch (JsonProcessingException e) {
            log.warn("操作日志：实体序列化失败: {}", e.getMessage());
            return new DiffResult(null, null);
        }
    }

    /**
     * 对比新旧 JSON，仅保留变更字段。
     */
    @SuppressWarnings("unchecked")
    private DiffResult computeFieldDiff(String oldJson, String newJson) {
        try {
            Map<String, Object> oldMap = objectMapper.readValue(oldJson, LinkedHashMap.class);
            Map<String, Object> newMap = objectMapper.readValue(newJson, LinkedHashMap.class);

            Map<String, Object> changedOld = new LinkedHashMap<>();
            Map<String, Object> changedNew = new LinkedHashMap<>();

            for (Map.Entry<String, Object> entry : newMap.entrySet()) {
                String key = entry.getKey();
                Object newVal = entry.getValue();
                Object oldVal = oldMap.get(key);

                if (!valuesEqual(oldVal, newVal)) {
                    changedOld.put(key, oldVal);
                    changedNew.put(key, newVal);
                }
            }

            // 检查旧值中有但新值中没有的字段（被删除的字段）
            for (Map.Entry<String, Object> entry : oldMap.entrySet()) {
                String key = entry.getKey();
                if (!newMap.containsKey(key)) {
                    changedOld.put(key, entry.getValue());
                    changedNew.put(key, null);
                }
            }

            if (changedOld.isEmpty() && changedNew.isEmpty()) {
                return new DiffResult(null, null);
            }

            String diffOldJson = objectMapper.writeValueAsString(changedOld);
            String diffNewJson = objectMapper.writeValueAsString(changedNew);
            return new DiffResult(diffOldJson, diffNewJson);
        } catch (JsonProcessingException e) {
            log.warn("操作日志：JSON diff 计算失败: {}", e.getMessage());
            return new DiffResult(oldJson, newJson);
        }
    }

    private boolean valuesEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b);
    }

    /**
     * Diff 结果。
     *
     * @param oldValue 变更前 JSON（仅变更字段）
     * @param newValue 变更后 JSON（仅变更字段）
     */
    public record DiffResult(String oldValue, String newValue) {
    }
}
