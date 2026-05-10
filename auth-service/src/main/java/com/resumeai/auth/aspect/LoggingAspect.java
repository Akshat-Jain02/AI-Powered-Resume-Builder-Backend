package com.resumeai.auth.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.util.stream.IntStream;

/**
 * Aspect for logging execution of service and controller Spring components.
 * Provides entry/exit logging and performance monitoring.
 * 
 * Industry standards applied:
 * - Sensitive parameters (password, token, secret) are masked
 * - Large return values (byte[], ResponseEntity) are summarized
 * - Full stack traces are logged on exceptions
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    private static final String[] SENSITIVE_PARAMS = {"password", "token", "secret", "credential", "authorization"};

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || " +
            "within(@org.springframework.stereotype.Service *) || " +
            "within(@org.springframework.stereotype.Repository *)")
    public void springBeanPointcut() {
        // Pointcut definition — implementations are in the advices
    }

    @Pointcut("within(com.resumeai.auth.controller..*) || " +
            "within(com.resumeai.auth.service..*) || " +
            "within(com.resumeai.auth.repository..*)")
    public void applicationPackagePointcut() {
        // Pointcut definition — implementations are in the advices
    }

    @Around("applicationPackagePointcut() && springBeanPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String returnType = signature.getReturnType().getSimpleName();
        String threadName = Thread.currentThread().getName();

        log.info("[THREAD:{}] >> ENTER: {}.{}() | Returns: {} | Args: [{}]", 
            threadName, className, methodName, returnType, maskSensitiveArgs(joinPoint));

        StopWatch sw = new StopWatch(); sw.start();
        try {
            Object result = joinPoint.proceed(); sw.stop();
            log.info("[THREAD:{}] << EXIT: {}.{}() | Result: {} | Time: {}ms", 
                threadName, className, methodName, summarizeResult(result), sw.getTotalTimeMillis());
            return result;
        } catch (Exception e) { sw.stop();
            log.error("[THREAD:{}] !! ERROR: {}.{}() | Message: {} | Time: {}ms", 
                threadName, className, methodName, e.getMessage(), sw.getTotalTimeMillis(), e);
            throw e;
        }
    }

    /**
     * Masks sensitive parameters like passwords and tokens before logging.
     */
    private String maskSensitiveArgs(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "";
        }

        String[] paramNames;
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            paramNames = signature.getParameterNames();
        } catch (Exception e) {
            paramNames = null;
        }

        String[] finalParamNames = paramNames;
        return IntStream.range(0, args.length)
            .mapToObj(i -> {
                String paramName = (finalParamNames != null && i < finalParamNames.length)
                    ? finalParamNames[i] : "arg" + i;
                if (isSensitive(paramName) || isSensitiveType(args[i])) {
                    return paramName + "=****";
                }
                return paramName + "=" + summarizeArg(args[i]);
            })
            .reduce((a, b) -> a + ", " + b)
            .orElse("");
    }

    private boolean isSensitive(String paramName) {
        String lower = paramName.toLowerCase();
        for (String sensitive : SENSITIVE_PARAMS) {
            if (lower.contains(sensitive)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSensitiveType(Object arg) {
        if (arg == null) return false;
        String className = arg.getClass().getSimpleName().toLowerCase();
        return className.contains("login") || className.contains("registration")
            || className.contains("credential") || className.contains("password");
    }

    private String summarizeArg(Object arg) {
        if (arg == null) return "null";
        if (arg instanceof byte[]) return "byte[" + ((byte[]) arg).length + "]";
        if (arg instanceof String s && s.length() > 200) return s.substring(0, 200) + "...[truncated]";
        return String.valueOf(arg);
    }

    private String summarizeResult(Object r) {
        if (r == null) return "null";
        if (r instanceof ResponseEntity<?> re) 
            return "ResponseEntity[status=" + re.getStatusCode() + ", hasBody=" + (re.getBody() != null) + "]";
        if (r instanceof byte[]) return "byte[" + ((byte[]) r).length + "]";
        if (r instanceof java.util.Collection<?> c) return "Collection[size=" + c.size() + "]";
        if (r instanceof java.util.Map<?,?> m) return "Map[size=" + m.size() + "]";
        String s = String.valueOf(r);
        return s.length() > 250 ? s.substring(0, 250) + "...[truncated]" : s;
    }
}
