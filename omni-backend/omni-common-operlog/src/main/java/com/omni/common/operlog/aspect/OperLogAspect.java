package com.omni.common.operlog.aspect;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.common.core.operlog.OperLog;
import com.omni.common.core.operlog.OperLogMessage;
import com.omni.common.core.operlog.OperType;
import com.omni.common.operlog.diff.EntityDiffer;
import com.omni.common.operlog.producer.OperLogProducer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 操作日志 AOP 切面。
 * <p>拦截 {@link OperLog} 注解标注的 Controller 方法，自动采集请求上下文、
 * 实体变更快照，并通过 MQ 异步发送操作日志。</p>
 *
 * @author Omni-Stack Team
 */
@Slf4j
@Aspect
@RequiredArgsConstructor
public class OperLogAspect {

    /** JSON 快照最大字符数，超出则截断 */
    private static final int MAX_JSON_LENGTH = 4000;

    private final OperLogProducer operLogProducer;
    private final EntityDiffer entityDiffer;
    private final ObjectMapper objectMapper;
    private final ApplicationContext applicationContext;

    private final ExpressionParser spelParser = new SpelExpressionParser();

    @Around("@annotation(operLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperLog operLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 采集请求上下文
        HttpServletRequest request = getRequest();
        String operUsername = getOperUsername();
        Long tenantId = getTenantId(request);
        String requestMethod = request != null ? request.getMethod() : null;
        String requestUrl = request != null ? request.getRequestURI() : null;
        String ipAddress = request != null ? getClientIp(request) : null;
        String userAgent = request != null ? request.getHeader("User-Agent") : null;
        String requestParams = serializeArgs(joinPoint.getArgs());

        // 操作前快照（UPDATE / DELETE）
        String oldValue = null;
        Object entityId = null;
        BaseMapper<Object> mapper = null;
        Class<?> entityClass = operLog.entityClass();
        boolean needDiff = entityClass != Object.class
                && (operLog.operType() == OperType.UPDATE || operLog.operType() == OperType.DELETE);

        if (needDiff) {
            mapper = findMapper(entityClass);
            if (mapper != null) {
                entityId = evaluateSpEL(operLog.idExpr(), joinPoint, null);
                if (entityId != null) {
                    Object oldEntity = mapper.selectById((Serializable) entityId);
                    if (oldEntity != null) {
                        oldValue = toJson(oldEntity);
                    }
                }
            } else {
                log.warn("操作日志：未找到实体 {} 的 Mapper，跳过变更快照", entityClass.getSimpleName());
            }
        }

        // 执行目标方法
        Object result = null;
        String errorMsg = null;
        int responseStatus = 200;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            errorMsg = ex.getMessage();
            responseStatus = 500;
            throw ex;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;

            try {
                // 操作后快照
                String newValue = null;
                if (needDiff && mapper != null && entityId != null) {
                    if (operLog.operType() == OperType.UPDATE) {
                        Object newEntity = mapper.selectById((Serializable) entityId);
                        if (newEntity != null) {
                            newValue = toJson(newEntity);
                        }
                        // 计算字段级 diff
                        EntityDiffer.DiffResult diffResult = entityDiffer.diff(
                                parseJson(oldValue), parseJson(newValue));
                        oldValue = diffResult.oldValue();
                        newValue = diffResult.newValue();
                    }
                    // DELETE: newValue 保持 null，oldValue 已在操作前获取
                }

                // CREATE: 从返回值提取新实体 ID
                if (operLog.operType() == OperType.CREATE && entityClass != Object.class) {
                    BaseMapper<Object> createMapper = findMapper(entityClass);
                    if (createMapper != null && !operLog.idExpr().isEmpty()) {
                        Object newId = evaluateSpEL(operLog.idExpr(), joinPoint, result);
                        if (newId != null) {
                            Object newEntity = createMapper.selectById((Serializable) newId);
                            if (newEntity != null) {
                                newValue = toJson(newEntity);
                            }
                        }
                    }
                }

                // 构建并发送日志消息
                OperLogMessage message = new OperLogMessage();
                message.setOperUsername(operUsername);
                message.setTenantId(tenantId);
                message.setOperTime(LocalDateTime.now());
                message.setModule(operLog.module());
                message.setOperType(operLog.operType().name());
                message.setRequestMethod(requestMethod);
                message.setRequestUrl(requestUrl);
                message.setRequestParams(truncate(requestParams));
                message.setResponseStatus(responseStatus);
                message.setIpAddress(ipAddress);
                message.setUserAgent(truncate(userAgent));
                message.setExecutionTime(executionTime);
                message.setOldValue(truncate(oldValue));
                message.setNewValue(truncate(newValue));
                message.setErrorMsg(truncate(errorMsg));

                operLogProducer.send(message);
            } catch (Exception e) {
                log.warn("操作日志：后处理失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 从 ApplicationContext 中查找指定实体类的 BaseMapper。
     */
    @SuppressWarnings("unchecked")
    private BaseMapper<Object> findMapper(Class<?> entityClass) {
        try {
            String[] beanNames = applicationContext.getBeanNamesForType(BaseMapper.class);
            for (String beanName : beanNames) {
                Object bean = applicationContext.getBean(beanName);
                Class<?> resolved = GenericTypeResolver.resolveTypeArgument(bean.getClass(), BaseMapper.class);
                if (entityClass.equals(resolved)) {
                    return (BaseMapper<Object>) bean;
                }
            }
        } catch (Exception e) {
            log.warn("操作日志：查找 Mapper 失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 求值 SpEL 表达式。
     *
     * @param expression SpEL 表达式
     * @param joinPoint  切入点（用于获取方法参数）
     * @param result     方法返回值（可为 null）
     * @return 表达式结果
     */
    private Object evaluateSpEL(String expression, ProceedingJoinPoint joinPoint, Object result) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        try {
            StandardEvaluationContext context = new StandardEvaluationContext();
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            String[] paramNames = signature.getParameterNames();
            Object[] args = joinPoint.getArgs();

            if (paramNames != null) {
                for (int i = 0; i < paramNames.length; i++) {
                    context.setVariable(paramNames[i], args[i]);
                }
            }
            if (result != null) {
                context.setVariable("result", result);
            }
            return spelParser.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            log.warn("操作日志：SpEL 表达式 {} 执行失败: {}", expression, e.getMessage());
            return null;
        }
    }

    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    private String getOperUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long getTenantId(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String tenantIdStr = request.getHeader("X-Tenant-Id");
        if (tenantIdStr != null) {
            try {
                return Long.parseLong(tenantIdStr);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For 可能包含多个 IP，取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String serializeArgs(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        try {
            // 过滤掉 HttpServletRequest 等框架对象
            Object[] filteredArgs = new Object[args.length];
            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof HttpServletRequest || args[i] instanceof jakarta.servlet.http.HttpServletResponse) {
                    filteredArgs[i] = "[FILTERED]";
                } else {
                    filteredArgs[i] = args[i];
                }
            }
            return objectMapper.writeValueAsString(filteredArgs);
        } catch (JsonProcessingException e) {
            log.warn("操作日志：请求参数序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("操作日志：实体序列化失败: {}", e.getMessage());
            return null;
        }
    }

    private Object parseJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() > MAX_JSON_LENGTH) {
            log.warn("操作日志：JSON 快照超过 {} 字符限制，已截断", MAX_JSON_LENGTH);
            return value.substring(0, MAX_JSON_LENGTH) + "...[TRUNCATED]";
        }
        return value;
    }
}
