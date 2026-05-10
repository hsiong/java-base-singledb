package tech.ynfy.frame.config.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * 〈〉
 *
 * @author Hsiong
 * @version 1.0.0
 * @since 11/23/25
 */
public class FeignRequestInterceptor implements RequestInterceptor {
	
	@Override
	public void apply(RequestTemplate requestTemplate) {
		
//		requestTemplate.header("Authorization", "auth"); // 加入自定义 Header
//		requestTemplate.query("sign", "sign");
		
		/**
		 *  load balancer 调用需要取消注释， 
		 *  url调用 注释这句话 + 使用 url=#{url}
		 */
		//		requestTemplate.target(requestTemplate.url());
	}
}
