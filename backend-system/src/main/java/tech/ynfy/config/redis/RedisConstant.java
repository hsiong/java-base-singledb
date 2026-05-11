package tech.ynfy.config.redis;

/**
 * 〈〉
 *
 * @author Hsiong
 * @version 1.0.0
 * @since 2022/7/20
 */
public interface RedisConstant {

    /**
     * 测试缓存key
     */
    String TEST_DEMO_CACHE = "test:demo";

    /*************************** 用户登录 ****************************/

    String PREFIX_USER_TOKEN_API  = "system:user_key:login:";
    
    
}
