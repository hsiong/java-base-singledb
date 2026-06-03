package tech.ynfy.frame.config.log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.ValueFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import tech.ynfy.frame.util.IPUtils;

import java.util.Arrays;
import java.util.Objects;

@Aspect
@Component
public class WebLogAspect {

    private static final Logger logger = LoggerFactory.getLogger(WebLogAspect.class);

    private static final ValueFilter MULTIPART_FILE_VALUE_FILTER = (object, name, value) -> {
        if (value instanceof MultipartFile multipartFile) {
            return buildMultipartFileLog(multipartFile);
        }
        return value;
    };
    
    @Pointcut("execution(public * *..module..*Controller.*(..))")//两个..代表所有子目录，最后括号里的两个..代表所有参数
    public void logPointCut() {
    }


    /**
     * 前置通知
     *
     * @param joinPoint
     * @throws Throwable
     */
    @Before("logPointCut()")
    public void doBefore(JoinPoint joinPoint) throws Throwable {

    }

    @AfterReturning(returning = "ret", pointcut = "logPointCut()")// returning的值和doAfterReturning的参数名一致
    public void doAfterReturning(Object ret) throws Throwable {
        if (ret instanceof ModelAndView) {
            // ModelAndView不打印
            return;
        }
        // 处理完请求，返回内容
        logger.debug("返回值 : " + JSON.toJSONString(ret));
    }


    /**
     * 后置通知
     *
     * @param pjp
     * @throws Throwable
     */
    @Around("logPointCut()")
    public Object doAround(ProceedingJoinPoint pjp) throws Throwable {
        // 耗时
        long startTime = System.currentTimeMillis();
        Object ob = null;// ob 为方法的返回值
        float excTime = 0;
        try {
            ob = pjp.proceed();
            excTime = (float) (System.currentTimeMillis() - startTime) / 1000;
        } catch (Throwable throwable) {
            // 交给全局异常捕获
            throw throwable;
        } finally {
            /**
             * 无论是否有异常都打印接口日志
             */
            // 接收到请求，记录请求内容
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                String method = pjp.getSignature().getDeclaringTypeName() + "." + pjp.getSignature().getName();
                String vars = buildParamJson(pjp.getArgs());
                // 记录下请求内容 并发时会导致打印在多处, 所以改为一行
                String logStr = String.format("请求地址: %s; 方法: %s; IP: %s; 参数: %s; 耗时: %s秒",
                                              request.getRequestURL().toString(),
                                              method,
                                              IPUtils.getIpAddr(request),
                                              vars,
                                              excTime);
                logger.warn(logStr);
            }
        }

        // return放在异常外
        return ob;
    }

    /**
     * 构建接口入参JSON，避免直接打印DTO的toString格式。
     *
     * @param args Controller方法参数
     * @return JSON格式参数
     */
    private String buildParamJson(Object[] args) {
        Object[] logArgs = Arrays.stream(args)
                                 .map(WebLogAspect::buildLogArg)
                                 .filter(Objects::nonNull)
                                 .toArray();

        if (logArgs.length == 1 && logArgs[0] instanceof String str) {
            return str;
        }

        try {
            return JSON.toJSONString(logArgs, MULTIPART_FILE_VALUE_FILTER);
        } catch (Exception e) {
            return buildFallbackParamText(args);
        }
    }

    /**
     * 参数JSON构建失败时兜底输出，单个String参数仍保留原始内容。
     *
     * @param args Controller方法参数
     * @return 兜底日志参数
     */
    private String buildFallbackParamText(Object[] args) {
        Object[] logArgs = Arrays.stream(args)
                                 .map(WebLogAspect::buildLogArg)
                                 .filter(Objects::nonNull)
                                 .toArray();

        if (logArgs.length == 1 && logArgs[0] instanceof String str) {
            return str;
        }
        return Arrays.toString(logArgs);
    }

    /**
     * 过滤框架对象，并处理文件参数，避免日志序列化Servlet对象或文件流。
     *
     * @param arg Controller方法单个参数
     * @return 可打印日志参数
     */
    private static Object buildLogArg(Object arg) {
        if (arg instanceof HttpServletRequest) {
            return null;
        }
        if (arg instanceof HttpServletResponse) {
            return null;
        }
        if (arg instanceof BindingResult) {
            return null;
        }
        if (arg instanceof MultipartFile multipartFile) {
            return buildMultipartFileLog(multipartFile);
        }
        return arg;
    }

    /**
     * 构建上传文件日志内容，只保留文件基础信息。
     *
     * @param multipartFile 上传文件
     * @return 文件日志内容
     */
    private static String buildMultipartFileLog(MultipartFile multipartFile) {
        return String.format("MultipartFile(originalFilename=%s, contentType=%s, size=%s)",
                             multipartFile.getOriginalFilename(),
                             multipartFile.getContentType(),
                             multipartFile.getSize());
    }
}
