package com.resumeai.notification.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import java.util.stream.IntStream;

@Aspect
@Component
@Slf4j
public class LoggingAspect {
    private static final String[] SENSITIVE_PARAMS = {"password", "token", "secret", "credential", "authorization"};

    @Pointcut("within(@org.springframework.web.bind.annotation.RestController *) || within(@org.springframework.stereotype.Service *)")
    public void springBeanPointcut() {}

    @Pointcut("within(com.resumeai.notification.consumer..*) || within(com.resumeai.notification.email..*)")
    public void applicationPackagePointcut() {}

    @Around("applicationPackagePointcut() && springBeanPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getSignature().getDeclaringTypeName();
        String methodName = joinPoint.getSignature().getName();
        log.info("Enter: {}.{}() with argument[s] = [{}]", className, methodName, maskSensitiveArgs(joinPoint));
        StopWatch sw = new StopWatch(); sw.start();
        try {
            Object result = joinPoint.proceed(); sw.stop();
            log.info("Exit: {}.{}() | result = {} | time = {} ms", className, methodName, summarizeResult(result), sw.getTotalTimeMillis());
            return result;
        } catch (Exception e) { sw.stop();
            log.error("Exception in {}.{}() | time = {} ms | message = {}", className, methodName, sw.getTotalTimeMillis(), e.getMessage(), e);
            throw e;
        }
    }

    private String maskSensitiveArgs(ProceedingJoinPoint jp) {
        Object[] args = jp.getArgs(); if (args == null || args.length == 0) return "";
        String[] pn; try { pn = ((MethodSignature) jp.getSignature()).getParameterNames(); } catch (Exception e) { pn = null; }
        String[] fpn = pn;
        return IntStream.range(0, args.length).mapToObj(i -> {
            String p = (fpn != null && i < fpn.length) ? fpn[i] : "arg" + i;
            if (isSensitive(p)) return p + "=****";
            return p + "=" + summarizeArg(args[i]);
        }).reduce((a, b) -> a + ", " + b).orElse("");
    }

    private boolean isSensitive(String p) { String l = p.toLowerCase(); for (String s : SENSITIVE_PARAMS) { if (l.contains(s)) return true; } return false; }
    private String summarizeArg(Object a) { if (a == null) return "null"; if (a instanceof byte[]) return "byte[" + ((byte[]) a).length + "]"; if (a instanceof String s && s.length() > 200) return s.substring(0, 200) + "...[truncated]"; return String.valueOf(a); }
    private String summarizeResult(Object r) { if (r == null) return "null"; if (r instanceof byte[]) return "byte[" + ((byte[]) r).length + "]"; String s = String.valueOf(r); return s.length() > 200 ? s.substring(0, 200) + "...[truncated]" : s; }
}
