package com.oneapi.log;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 操作日志切面。
 * <p>
 * 只切 Spring 管理的附加业务 bean（Service/Controller），不处理 Vert.x Relay 主链路。
 * 默认仅记录方法名、耗时和异常信息，不输出参数或返回值，避免敏感字段泄露。
 * 启用 DEBUG 后会输出已脱敏的参数与返回值（详见 {@link #sanitize(Object)}）。
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

    /** 类名列表：这些类型的对象在 DEBUG 输出时会整体打码。 */
    private static final Set<String> REDACTED_TYPE_NAMES = Set.of(
        "com.oneapi.model.Vendor",
        "com.oneapi.model.Instance",
        "com.oneapi.model.VirtualModel"
    );

    /** 字段名列表：对象内出现的这些字段名会被替换为 ***REDACTED***。 */
    private static final Set<String> REDACTED_FIELD_NAMES = Set.of(
        "apiKey", "api_key",
        "balanceCredential", "balance_credential",
        "authorization", "Authorization",
        "password", "Password",
        "secret", "Secret",
        "token", "Token"
    );

    @Pointcut("@annotation(com.oneapi.log.LogOperation) || @within(com.oneapi.log.LogOperation)")
    public void logOperationPointcut() {
    }

    @Pointcut("execution(* com.oneapi.service.*.*(..))")
    public void serviceLayerPointcut() {
    }

    @Around("logOperationPointcut() || serviceLayerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String method = signature.getDeclaringTypeName() + "." + signature.getName();

        long start = System.currentTimeMillis();
        if (log.isDebugEnabled()) {
            log.debug("[OP-START] {} args={}", method, summarize(joinPoint.getArgs()));
        }

        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            if (log.isDebugEnabled()) {
                log.debug("[OP-END] {} cost={}ms result={}", method, cost, summarize(result));
            }
            return result;
        } catch (Throwable t) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[OP-ERROR] {} cost={}ms error={}", method, cost, t.getMessage(), t);
            throw t;
        }
    }

    private String summarize(Object... objects) {
        if (objects == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < objects.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(summarize(objects[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    private String summarize(Object obj) {
        if (obj == null) {
            return "null";
        }
        if (obj instanceof String s) {
            return s.length() > 200 ? s.substring(0, 200) + "..." : s;
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        if (obj.getClass().isArray()) {
            return summarize((Object[]) obj);
        }
        return sanitize(obj);
    }

    /**
     * 将对象内常见敏感字段打码，避免出现在日志中。
     * 仅基于 {@link #toString()} 做字符串过滤；不依赖反射读取字段。
     */
    private String sanitize(Object obj) {
        String str;
        try {
            str = obj.toString();
        } catch (Exception e) {
            return obj.getClass().getSimpleName() + "@toStringFailed";
        }
        if (str.length() > 500) {
            str = str.substring(0, 500) + "...";
        }
        if (REDACTED_TYPE_NAMES.contains(obj.getClass().getName())) {
            return obj.getClass().getSimpleName() + "{***REDACTED***}";
        }
        for (String field : REDACTED_FIELD_NAMES) {
            str = str.replaceAll("(?i)(" + java.util.regex.Pattern.quote(field) + "\\s*[:=]\\s*)([^,}\\s]+)",
                "$1***REDACTED***");
        }
        return str;
    }
}
