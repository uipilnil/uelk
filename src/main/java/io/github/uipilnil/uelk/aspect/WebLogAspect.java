package io.github.uipilnil.uelk.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.UUID;

/**
 * 接口日志切面
 * 
 * 通过 AOP 拦截 Controller 层方法，自动记录请求日志，便于后续接入 ELK 进行日志分析
 *
 * @author uipilnil
 */
@Aspect // 声明切面类，也就是要拦截方法，在方法执行前后插入逻辑
@Component
public class WebLogAspect {

    private static final Logger log = LoggerFactory.getLogger(WebLogAspect.class);

    /**
     * 定义切点，也就是要拦截的方法
     * execution：表示拦截的是方法
     * public *：表示拦截所有 public 方法
     * io.github.uipilnil.uelk.controller..*：表示匹配 Controller 包及其子包下的所有类
     * .*(..)：表示匹配所有方法，不限制参数
     */
    @Pointcut("execution(public * io.github.uipilnil.uelk.controller..*.*(..))")
    public void controllerPointcut() {
    }

    /**
     * 环绕执行，环绕在目标方法前后
     * <p>
     * 记录请求的 URL、IP、请求方法、类名方法、参数、执行时长
     *
     * @param joinPoint 被拦截的方法
     * @return 方法执行结果
     * @throws Throwable 方法执行异常
     */
    @Around("controllerPointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        // 对于非 HTTP 请求线程（如测试或内部调用）直接放行，不记录日志
        if (attributes == null) {
            return joinPoint.proceed();
        }
        // 拿到 HTTP 请求对象，从而拿到 URL、IP、请求方法
        HttpServletRequest request = attributes.getRequest();

        // 为 MDC 注入 traceId 与请求标识，日志框架会自动携带这些字段到结构化日志
        MDC.put("traceId", UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        MDC.put("uri", request.getRequestURI());
        MDC.put("method", request.getMethod());

        long startTime = System.currentTimeMillis();

        log.info("՞⦁ ᴥ ⦁՞ 请求开始 ૮・ᴥ - ა");
        log.info("URL: {} {}", request.getMethod(), request.getRequestURL());
        log.info("IP: {}", request.getRemoteAddr());
        log.info("调用: {}.{}", joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName());
        log.info("参数: {}", Arrays.toString(joinPoint.getArgs()));

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            MDC.put("executionTime", String.valueOf(executionTime));

            log.info("响应: {}", result);
            log.info("时长: {}ms", executionTime);
            log.info("՞⦁ ᴥ ⦁՞ 请求结束 ૮・ᴥ - ა");

            return result;
        } catch (Throwable ex) {
            long executionTime = System.currentTimeMillis() - startTime;

            MDC.put("executionTime", String.valueOf(executionTime));
            // 记录异常类型，便于日志聚合时按异常维度聚合统计
            MDC.put("exception", ex.getClass().getName());

            log.error("异常   : {}: {}", ex.getClass().getName(), ex.getMessage());
            log.info("时长   : {}ms", executionTime);
            log.info("⦁ ᴥ ⦁՞ 请求异常结束 ૮・ᴥ - ა");

            throw ex;
        } finally {
            // 清除 MDC 上下文，避免 traceId 等残留数据污染线程池复用时的下一个请求
            MDC.clear();
        }
    }
}
