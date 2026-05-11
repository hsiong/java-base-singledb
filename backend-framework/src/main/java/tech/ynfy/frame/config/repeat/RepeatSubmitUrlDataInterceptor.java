package tech.ynfy.frame.config.repeat;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;
import tech.ynfy.frame.config.xss.RepeatedlyRequestWrapper;
import tech.ynfy.frame.util.HttpUtil;
import tech.ynfy.frame.util.RedisUtil;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;


/**
 * 判断请求url和数据是否和上一次相同，
 * 如果和上次相同，则是重复提交表单。 有效时间为10秒内。
 *
 * 
 */
@Component
public class RepeatSubmitUrlDataInterceptor extends RepeatSubmitInterceptor {
    
    @Autowired
    private RedisUtil redisUtil;
    
    /**
     * 防重提交 redis key
     */
    String REPEAT_SUBMIT_KEY = "repeat_submit:";
    
    /**
     * 重复时间
     */
    Integer REPEAT_TIME_SECOND = 10;
    
    /**
     * 重复时间 - Mills
     */
    Integer REPEAT_TIME_MILL = REPEAT_TIME_SECOND * 1000;

    /**
     * 间隔时间，单位:秒 默认10秒
     * <p>
     * 两次相同参数的请求，如果间隔时间大于该参数，系统不会认定为重复提交的数据
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean isRepeatSubmit(HttpServletRequest request) {
        // 需使用 RepeatSubmit annotation = method.getAnnotation(RepeatSubmit.class);
        String nowParams = "";
        if (request instanceof RepeatedlyRequestWrapper) {
            RepeatedlyRequestWrapper repeatedlyRequest = (RepeatedlyRequestWrapper) request;
            nowParams = HttpUtil.getBodyString(repeatedlyRequest);
        }

        // body参数为空，获取Parameter的数据
        if (StringUtils.isEmpty(nowParams)) {
            nowParams = JSONObject.toJSONString(request.getParameterMap());
        }

        // 唯一标识（指定key + 消息头） 唯一值（没有消息头则使用请求地址）
        String submitKey = request.getHeader("URL");
        if (StringUtils.isEmpty(submitKey)) {
            submitKey = request.getRequestURI();
        }
        String md5Key = DigestUtils.md5DigestAsHex((submitKey + nowParams).getBytes(StandardCharsets.UTF_8));
        String cacheRepeatKey = REPEAT_SUBMIT_KEY + md5Key ;
        Object redisObj = redisUtil.get(cacheRepeatKey);
        Long nowTime = System.currentTimeMillis();
        if (redisObj != null) {
            Long foreTime = (Long) redisObj;
            if ((nowTime - foreTime) < (REPEAT_TIME_MILL)) {
                return true;
            }
            return false;
        }
        redisUtil.set(cacheRepeatKey, nowTime, REPEAT_TIME_SECOND);
        return false;
    }
    
   
}
