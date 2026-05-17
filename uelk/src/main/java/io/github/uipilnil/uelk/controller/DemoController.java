package io.github.uipilnil.uelk.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ELK 日志采集链路验证控制器
 * 
 * 三种日志场景，
 * 用于验证 Filebeat -> Logstash -> Elasticsearch -> Kibana 采集链路是否正常工作
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
