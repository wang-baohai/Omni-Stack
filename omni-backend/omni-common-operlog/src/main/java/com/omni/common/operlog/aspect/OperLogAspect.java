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
 * 实体变更快照，并通过 RocketMQ 异步发送操作日志。</p>
 *
 * <p>执行流程：</p>
 * <ol>
 *   <li>采集请求上下文（URL、IP、User-Agent、请求参数、用户名、租户 ID）</li>
 *   <li>UPDATE/DELETE 操作前通过 Mapper 查询旧值快照</li>
 *   <li>执行目标 Controller 方法</li>
 *   <li>UPDATE 操作后查询新值并通过 {@link EntityDiffer} 计算字段级 diff</li>
 *   <li>CREATE 操作后从返回值提取新实体 ID 并查询新值快照</li>
 *   <li>构建 {@link OperLogMessage} 并通过 {@link OperLogProducer} 发送至 RocketMQ</li>
 * </ol>
 *
 * <p>异常处理：日志采集过程中的任何异常均不影响业务逻辑，
 * 仅记录 WARN 级别日志。目标方法抛出的异常会原样向上抛出。</p>
 *
 * @author Omni-Stack Team
 * @see OperLog
 * @see OperLogMessage
 * @see EntityDiffer
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

    /**
     * 环绕通知，拦截 {@link OperLog} 注解标注的方法执行。
     * <p>采集请求上下文 → 操作前快照 → 执行目标方法 → 操作后快照 → 构建日志消息并发送。</p>
     *
     * @param joinPoint AOP 切入点，提供方法参数和返回值访问
     * @param operLog   Controller 方法上的 {@link OperLog} 注解实例
     * @return 目标方法的原始返回值
     * @throws Throwable 目标方法抛出的异常，原样向上抛出
     */
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
     * <p>通过 {@link GenericTypeResolver} 解析 Bean 的泛型参数，
     * 匹配与目标实体类类型一致的 Mapper。</p>
     *
     * @param entityClass 目标实体类
     * @return 匹配的 BaseMapper 实例，未找到时返回 null
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

    /**
     * 获取当前 HTTP 请求。
     * <p>通过 Spring 的 {@link RequestContextHolder} 获取绑定到当前线程的请求。</p>
     *
     * @return 当前 HttpServletRequest，非 Web 上下文时返回 null
     */
    private HttpServletRequest getRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs != null ? attrs.getRequest() : null;
    }

    /**
     * 获取当前登录用户名。
     * <p>从 Spring Security 的 {@link SecurityContextHolder} 中提取认证信息。</p>
     *
     * @return 用户名，未认证时返回 null
     */
    private String getOperUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            return auth != null ? auth.getName() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从请求头提取租户 ID。
     * <p>读取网关注入的 {@code X-Tenant-Id} 请求头并解析为 Long。</p>
     *
     * @param request HTTP 请求
     * @return 租户 ID，解析失败或请求为 null 时返回 null
     */
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

    /**
     * 获取客户端真实 IP 地址。
     * <p>依次检查 {@code X-Forwarded-For}、{@code X-Real-IP} 请求头，
     * 均未命中时回退到 {@code request.getRemoteAddr()}。
     * 对 {@code X-Forwarded-For} 多 IP 场景取第一个（即原始客户端 IP）。</p>
     *
     * @param request HTTP 请求
     * @return 客户端 IP 地址
     */
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

    /**
     * 序列化方法参数为 JSON 字符串。
     * <p>过滤掉 {@link HttpServletRequest}、{@code HttpServletResponse} 等框架对象，
     * 替换为 {@code [FILTERED]} 标记，避免序列化异常或敏感信息泄露。</p>
     *
     * @param args 方法参数数组
     * @return JSON 字符串，参数为空或序列化失败时返回 null
     */
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

    /**
     * 将实体对象序列化为 JSON 字符串。
     *
     * @param obj 实体对象
     * @return JSON 字符串，序列化失败时返回 null
     */
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("操作日志：实体序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 将 JSON 字符串反序列化为 Object。
     *
     * @param json JSON 字符串
     * @return 反序列化后的对象，json 为 null 或解析失败时返回 null
     */
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

    /**
     * 截断超长字符串。
     * <p>超过 {@link #MAX_JSON_LENGTH}（4000）字符的 JSON 快照会被截断并追加
     * {@code ...[TRUNCATED]} 标记，避免 MQ 消息体过大。</p>
     *
     * @param value 原始字符串
     * @return 截断后的字符串，value 为 null 时返回 null
     */
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
