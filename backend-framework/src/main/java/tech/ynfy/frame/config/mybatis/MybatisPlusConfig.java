package tech.ynfy.frame.config.mybatis;

import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MybatisPlusConfig {
	
	
	/**
	 * MybatisPlus拦截器配置
	 * 包含：分页插件、乐观锁插件、MPJ连表插件
	 *
	 * @return
	 */
	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
		// 乐观锁插件
		interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
		// 动态方言
		PaginationInnerInterceptor paginationInnerInterceptor = new PaginationInnerInterceptor();
		paginationInnerInterceptor.setDialect(new DynamicRoutingDialect());
		interceptor.addInnerInterceptor(paginationInnerInterceptor);
		// MPJ 连表插件 - 无需手动注册
		// interceptor.addInnerInterceptor(new MPJInterceptor());
		return interceptor;
	}
	
	/**
	 * 把插件加到 MyBatis Configuration 中
	 */
	@Bean
	public ConfigurationCustomizer configurationCustomizer(VarcharLengthCheckInterceptor varcharLengthCheckInterceptor) {
		return configuration -> configuration.addInterceptor(varcharLengthCheckInterceptor);
	}
	
	@Bean
	public VarcharLengthCheckInterceptor varcharLengthCheckInterceptor() {
		return new VarcharLengthCheckInterceptor();
	}
}
