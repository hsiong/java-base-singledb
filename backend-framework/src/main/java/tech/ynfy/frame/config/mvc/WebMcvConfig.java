package tech.ynfy.frame.config.mvc;

import com.alibaba.fastjson.serializer.SerializerFeature;
import com.alibaba.fastjson.serializer.ValueFilter;
import com.alibaba.fastjson.support.config.FastJsonConfig;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.web.context.request.RequestContextListener;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tech.ynfy.frame.config.repeat.RepeatSubmitInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 通用配置
 */
@Configuration
public class WebMcvConfig implements WebMvcConfigurer {
    
    @Autowired
    private RepeatSubmitInterceptor repeatSubmitInterceptor;
    
    // /api/**
    @Value("${project-config.path.accessPath}")
    private String accessPath;
    
    // file:绝对路径, file:绝对路径
    @Value("${project-config.path.localPath}")
    private String localPath;
    
    @Value("${project-config.path.externalPath}")
    private String externalPath;
    
    @Value("${springdoc.api-docs.path}")
    private String openApiPath;
    
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 本地文件上传路径
        registry.addResourceHandler(accessPath).addResourceLocations(localPath.split(","));
        
    }
    
    /**
     * 自定义拦截规则
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(repeatSubmitInterceptor).addPathPatterns("/**");
        registry.addInterceptor(new RestApiInteceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/doc.html",
                                     openApiPath + "/**",
                                     externalPath
                );
    }
    
    //    /**
    //     * 方案一： 默认访问根路径跳转 doc.html页面 （swagger文档页面）
    //     * 方案二： 访问根路径改成跳转 index.html页面 （简化部署方案： 可以把前端打包直接放到项目的 webapp，上面的配置）
    //     */
    //    @Override
    //    public void addViewControllers(ViewControllerRegistry registry) {
    //        registry.addViewController("/").setViewName("doc.html");
    //    }
    
    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Keep default converters and prepend customized converters for compatibility.
        converters.add(0, new ByteArrayHttpMessageConverter());
        converters.add(1, stringConverter());
        converters.add(2, fastConverter());
    }
    
    /**
     * 非Controller层通过RequestContextHolder.getRequestAttributes()
     * 获取HttpServletRequest，HttpServletRespon空指针问题
     * RequestContextListener监听器
     * @return
     */
    @Bean
    public RequestContextListener requestContextListenerBean() {
        return new RequestContextListener();
    }
    
    /**
     * 跨域配置
     */
    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // 设置访问源地址
        config.addAllowedOriginPattern("*");
        // 设置访问源请求头
        config.addAllowedHeader("*");
        // 设置访问源请求方法
        config.addAllowedMethod("*");
        // 对接口配置跨域设置
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
    
    
    private HttpMessageConverter<String> stringConverter() {
        return new StringHttpMessageConverter(StandardCharsets.UTF_8);
    }
    
    private HttpMessageConverter fastConverter() {
        //1、定义一个convert转换消息的对象
        FastJsonHttpMessageConverter fastConverter = new FastJsonHttpMessageConverter();
        //2、添加fastjson的配置信息
        FastJsonConfig fastJsonConfig = new FastJsonConfig();
        fastJsonConfig.setSerializerFeatures(
            //List字段如果为null,输出为[],而非null
            SerializerFeature.WriteNullListAsEmpty,
            //是否输出值为null的字段,默认为false
            SerializerFeature.WriteMapNullValue,
            //字符串null返回空字符串
            SerializerFeature.WriteNullStringAsEmpty,
            // 将 Number 类型的 null 转成 0
            SerializerFeature.WriteNullNumberAsZero,
            //空布尔值返回false
            SerializerFeature.WriteNullBooleanAsFalse,
            //结果是否格式化,默认为false
            SerializerFeature.PrettyFormat);
        fastJsonConfig.setSerializeFilters((ValueFilter) (o, s, source) -> {
            //此处是关键,如果返回对象的变量为null,则自动变成""
            return Objects.requireNonNullElse(source, "");
        });
        //格式化日期
        fastJsonConfig.setCharset(StandardCharsets.UTF_8);
        //2-1 处理中文乱码问题：显式在文本类型上声明 UTF-8
        List<MediaType> supportedMediaTypes = new ArrayList<>();
        supportedMediaTypes.add(MediaType.APPLICATION_JSON);
        supportedMediaTypes.add(MediaType.parseMediaType("application/*+json")); // 补全 JSON 媒体类型，修正 curl 下的异常返回链路
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.APPLICATION_ATOM_XML_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.APPLICATION_FORM_URLENCODED_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.APPLICATION_OCTET_STREAM);
        supportedMediaTypes.add(MediaType.APPLICATION_PDF);
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.APPLICATION_RSS_XML_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.APPLICATION_XHTML_XML_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.APPLICATION_XML_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.IMAGE_GIF);
        supportedMediaTypes.add(MediaType.IMAGE_JPEG);
        supportedMediaTypes.add(MediaType.IMAGE_PNG);
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.TEXT_HTML_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.TEXT_MARKDOWN_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8"));
        supportedMediaTypes.add(MediaType.parseMediaType(MediaType.TEXT_XML_VALUE + ";charset=UTF-8"));
        
        fastConverter.setDefaultCharset(StandardCharsets.UTF_8);
        fastConverter.setSupportedMediaTypes(supportedMediaTypes);
        //3、在convert中添加配置信息
        fastConverter.setFastJsonConfig(fastJsonConfig);
        return fastConverter;
    }
}