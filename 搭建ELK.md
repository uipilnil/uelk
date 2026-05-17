# 搭建 ELK 并分析日志

**目的**：

搭建 ELK，接口 AOP 模式输出日志到 ELK，然后使用。



**拆解步骤**：

1、搭建好 ELK；

2、用 AOP 自动采集接口日志；

3、把日志送给 ELK 做分析。



## 搭建 ELK

### 下载同一版本

Elasticsearch 8.17.0

https://www.elastic.co/downloads/past-releases/elasticsearch-8-17-0

Logstash 8.17.0

https://www.elastic.co/downloads/past-releases/logstash-8-17-0

Kibana 8.17.0

https://www.elastic.co/cn/downloads/past-releases/kibana-8-17-0

IK 分词器 8.17.0

https://release.infinilabs.com/analysis-ik/stable/

下载完成后解压安装包。



### 启动 ES、Logstash、Kibana

打开终端，进入 `bin` 目录，输入 `./elasticsearch` 启动 ES。

```bash
./elasticsearch
```



打开终端，进入 `bin` 目录，输入 `./logstash -e "input { stdin {} } output { stdout {} }"` 启动 Logstash。

```bash
./logstash -e "input { stdin {} } output { stdout {} }"
```

在终端输入文本，会出现处理后的结果：

![image-20260517143009150](/Users/admin/uipilnil/project/demo/uelk/%E6%90%AD%E5%BB%BAELK.assets/image-20260517143009150.png)



打开终端，进入 `bin` 目录，输入 `./kibana` 启动 Kibana。

```bash
./kibana
```

启动后，要在界面输入 Token，在 Elasticsearch 目录下通过命令获取 Token：（需要保证 Elasticsearch 是启动状态）

```bash
bin/elasticsearch-create-enrollment-token --scope kibana
```

在浏览器输入 http://localhost:5601，进入 Kibana 可视化界面。

![image-20260517145119611](/Users/admin/uipilnil/project/demo/uelk/%E6%90%AD%E5%BB%BAELK.assets/image-20260517145119611.png)



## 用 ELK 收集日志

一、导入依赖

```xml
<!-- AOP -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
<!-- 日志框架 (logback) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-logging</artifactId>
</dependency>
<!-- 把日志以 JSON 格式发送到 Logstash -->
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```



二、编写 logback-spring.xml

**是什么**：这是 Logback 日志框架的 SpringBoot 配置文件；

**有什么用**：它把日志收集起来，同时发送到控制台和 Logstash。（通过 TCP 把日志以 JSON 格式发送到 Logstash）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- 引用 Spring Boot 的默认日志配置（色彩、格式等） -->
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <include resource="org/springframework/boot/logging/logback/console-appender.xml"/>

    <!-- 从 application.yml 读取的应用名 -->
    <!-- 把 source 读到变量 name 里，读不到就用默认值 defaultValue -->
    <springProperty scope="context" name="appName" source="spring.application.name" defaultValue="uelk"/>
    <springProperty scope="context" name="logstashHost" source="logstash.host" defaultValue="localhost"/>
    <springProperty scope="context" name="logstashPort" source="logstash.port" defaultValue="5000"/>

    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>${CONSOLE_LOG_PATTERN}</pattern>
            <charset>${CONSOLE_LOG_CHARSET}</charset>
        </encoder>
    </appender>

    <!-- 把日志以 JSON 格式发送到 Logstash(通过 TCP 协议) -->
    <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>${logstashHost}:${logstashPort}</destination>
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"> <!-- 把日志编码成 JSON -->
            <!-- 自定义额外字段，方便在 Kibana 中筛选（示例中用服务名筛选） -->
            <customFields>{"app_name":"${appName}"}</customFields>
        </encoder>
        <keepAliveDuration>1 minute</keepAliveDuration> <!-- TCP 长连接时长 -->
        <writeTimeout>10 seconds</writeTimeout> <!-- 发送日志超时时间 -->
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="LOGSTASH"/>
    </root>
</configuration>

```



三、编写 application.yml

配置 Logstash 的端口、地址等信息。

```yml
# Logstash 日志配置
logstash:
  host: localhost
  port: 5000
```



四、编写测试 Controller 验证 AOP + ELK 流程

```java
/**
 * ELK 日志采集链路验证控制器
 *
 * <p>三种日志场景，
 * 用于验证 Filebeat -> Logstash -> Elasticsearch -> Kibana 采集链路是否正常工作</p>
 *
 * @author uipilnil
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    /**
     * GET 请求日志采集验证
     *
     * @param name 用户名，默认 "ELK"
     * @return {"message": "Hello {name}"}
     */
    @GetMapping("/hello")
    public Map<String, Object> hello(@RequestParam(defaultValue = "ELK") String name) {
        log.info("DemoController.hello 被调用, name={}", name);
        return Map.of("message", "Hello " + name);
    }

    /**
     * POST 请求日志采集验证（含请求体）
     *
     * @param body 任意 JSON 请求体，使用 Map 接收以保持通用性
     * @return {"received": {body}}
     */
    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        log.info("DemoController.echo 被调用, body={}", body);
        return Map.of("received", body);
    }

    /**
     * 异常日志采集验证
     *
     * <p>主动抛出 RuntimeException，
     * 验证 ELK 能否正确采集 ERROR 级别日志及完整堆栈轨迹</p>
     *
     * @throws RuntimeException 始终抛出，用于验证异常日志采集
     */
    @GetMapping("/error")
    public Map<String, Object> error() {
        // 异常产生的 ERROR 日志和堆栈轨迹由 Spring MVC 框架自行输出，无需手动记录
        throw new RuntimeException("模拟异常，验证 ELK 对 ERROR 级别日志及堆栈轨迹的采集能力");
    }
}
```



五、编写接口日志切面

```java
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
```



六、测试结果
