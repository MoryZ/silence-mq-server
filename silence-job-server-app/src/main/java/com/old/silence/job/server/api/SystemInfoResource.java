package com.old.silence.job.server.api;


import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.old.silence.job.common.model.ApiResult;

/**
 * 系统信息
 *
 */
@RestController
@RequestMapping("/system")
public class SystemInfoResource {

    @GetMapping("/version")
    public ApiResult<String> version() {
        return ApiResult.success("v1.8");
    }
}
