package com.orange.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康检查接口：访问根路径返回启动成功提示，用于部署后验证服务可用
 *
 * @author UserCenter
 */
@Tag(name = "系统健康检查")
@RestController
public class HealthController {

    /**
     * 根路径健康检查
     *
     * @return 后端启动成功提示文本
     */
    @Operation(summary = "根路径健康检查")
    @GetMapping("/")
    public String health() {
        return "用户中心后端启动成功";
    }
}
