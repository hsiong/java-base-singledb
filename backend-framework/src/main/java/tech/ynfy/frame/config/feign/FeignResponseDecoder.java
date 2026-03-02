//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//

package tech.ynfy.frame.config.feign;

import tech.ynfy.frame.config.exception.BizException;
import tech.ynfy.frame.module.Result;
import feign.codec.Decoder;
import feign.optionals.OptionalDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.ResponseEntityDecoder;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.context.annotation.Bean;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class FeignResponseDecoder {
	public FeignResponseDecoder() {
	}
	
	@Bean
	public Decoder feignDecoder(ObjectProvider<HttpMessageConverters> messageConverters) {
		SpringDecoder decoder = new SpringDecoder(messageConverters);
		return new OptionalDecoder(new ResponseEntityDecoder((response, type) -> {
			Method method = response.request().requestTemplate().methodMetadata().method();
			boolean notTheSame = method.getReturnType() != Result.class;
			if (notTheSame) {
				Type newType = new ParameterizedType() {
					public Type[] getActualTypeArguments() {
						return new Type[]{type};
					}
					
					public Type getRawType() {
						return Result.class;
					}
					
					public Type getOwnerType() {
						return null;
					}
				};
				Result<?> result = (Result) decoder.decode(response, newType);
				if (!result.isSuccess()) {
					throw new BizException(result.getCode(), result.getMessage());
				}
				return result.getResult();
			} else {
				return decoder.decode(response, type);
			}
		}));
	}
}
