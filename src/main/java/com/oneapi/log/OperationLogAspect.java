package com.oneapi.log;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 操作日志切面。
 * <p>
 * 只切 Spring 管理的附加业务 bean（Service/Controller），不处理 Vert.x Relay 主链路。
 * 默认仅记录方法名、耗时和异常信息，不输出参数或返回值。
 */
@Aspect
@Component
@Slf4j
public class OperationLogAspect {

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
        try {
            Object result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - start;
            if (log.isDebugEnabled()) {
                log.debug("[OP-END] {} cost={}ms", method, cost);
            }
            return result;
        } catch (Throwable t) {
            long cost = System.currentTimeMillis() - start;
            log.warn("[OP-ERROR] {} cost={}ms error={}", method, cost, t.getMessage(), t);
            throw t;
        }
    }
}
