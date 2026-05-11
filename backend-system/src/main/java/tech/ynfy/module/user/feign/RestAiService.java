package tech.ynfy.module.user.feign;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import tech.ynfy.frame.config.feign.FeignConfiguration;
import tech.ynfy.frame.config.feign.FeignResponseDecoder;

/**
 * 〈〉
 *
 * @author Hsiong
 * @version 1.0.0
 * @since 11/23/25
 */
@FeignClient(
	name = "ai",
	url = "${feign.test-url}",                    // 配置文件里定义 baseUrl
	configuration = {
		FeignConfiguration.class,
		FeignResponseDecoder.class
	}
)
public interface RestAiService {
	
	@PostMapping(value = "/api/test")
	@Operation(summary = "测试 feign 接口")
	String testApi(@PathVariable String profileId);
	
	
	
}
