# java-base-singledb

中文文档: [README-CHS.md](README-CHS.md)

Open source project. Unauthorized commercial use is not allowed.

## Start And Package

This project uses JDK 21 and Spring Boot 3.4.10.

```bash
mvn clean package -Pdev
mvn clean package -Ptest
mvn clean package -Pprod
```

Run the service:

```bash
mvn -pl backend-system spring-boot:run -Pdev
java -jar backend-system/target/backend-system-1.0.0-SNAPSHOT.jar
```

`application.yml` includes `@profiles.active@, common`, so the Maven profile decides which environment file is packaged:

- `-Pdev`: `application-dev.yml` + `application-common.yml`
- `-Ptest`: `application-test.yml` + `application-common.yml`
- `-Pprod`: `application-prod.yml` + `application-common.yml`

The default context path is `/base-backend`. The port comes from `config.port`; if it is missing, `application-common.yml` falls back to `8000`.

## Controller Logs

`WebLogAspect` automatically logs public controller methods matched by:

```java
execution(public * ..module..*.*Controller.*(..))
```

Controller classes must be under a package path containing `module`, and the class name must end with `Controller`.

Each request logs:

- request URL
- controller method
- client IP
- method arguments
- elapsed seconds

Return values are logged at `debug` level. `ModelAndView` return values are skipped.

## Unified Response

Return API data with `Result`:

```java
return Result.ok(data);
return Result.ok();
return Result.error("message");
return Result.authExpire();
```

The response object contains `success`, `message`, `code`, `result`, and `timestamp`.

## Global Exceptions

`GlobalExceptionHandler` catches these exceptions automatically:

- `MethodArgumentNotValidException`: returns validation messages such as `field: message`.
- `BizException`: returns the exception code and message.
- `RedisLockException`: returns the exception code and message with the service context path.
- Other `Exception`: logs the stack trace and returns `系统异常-请联系管理员：xxx`.

Use validation like this:

```java
@PostMapping("/save")
public Result<Void> save(@Valid @RequestBody SysUserSaveDTO dto) {
    service.save(dto);
    return Result.ok();
}
```

```java
@NotBlank(message = "用户名 不能为空")
private String name;
```

## Prevent Duplicate Submit

Add `@RepeatSubmit` to a controller method:

```java
@RepeatSubmit
@PostMapping("/submit")
public Result<Void> submit(@RequestBody SubmitDTO dto) {
    service.submit(dto);
    return Result.ok();
}
```

The interceptor only checks methods with `@RepeatSubmit`. It builds a Redis key from:

- request header `URL`; if missing, `request.getRequestURI()`
- JSON body from `RepeatedlyRequestWrapper`; if body is empty, `request.getParameterMap()`

The final key is `repeat_submit:` + MD5 of the URL and parameters. The default duplicate window is 10 seconds. This feature requires Redis because the previous request key is stored in Redis.

## Redis Lock And Request Limit

Use `@RedisLock` on a service method to prevent concurrent execution for the same business key:

```java
@RedisLock(
    prefix = "order:pay",
    key = "#request.orderNo",
    waitTime = 0,
    leaseTime = 30,
    message = "订单正在处理中，请稍后再试"
)
public void pay(PayRequestDTO request) {
    // business code
}
```

Key options:

- `key = "MD5"`: MD5 of the first method argument.
- `key = "#request.orderNo"`: SpEL from method arguments.
- `key = "#header.Authorization"`: value from request header.
- `key = "fixed"`: literal key segment.

`waitTime <= 0` tries once and returns immediately if the lock is held. `leaseTime <= 0` uses the current default lease of 30 seconds. `throwException = false` returns `null` instead of throwing.

Use `limitTime` to allow the same key to succeed only once in a time window:

```java
@RedisLock(prefix = "sms:send", key = "#phone", limitTime = 60, message = "短信发送太频繁")
public void sendSms(String phone) {
    // send sms
}
```

## XSS Filter

`XssFilterConfig` registers two filters:

- `xssFilter`: strips HTML tags from query parameters and JSON body.
- `repeatableFilter`: wraps JSON requests so the request body can be read more than once.

Config keys:

```yaml
xss:
  enabled: true
  excludes: /system/notice/*
  urlPatterns: /system/*,/monitor/*,/tool/*
```

`xss.enabled=false` disables XSS filtering. `xss.excludes` skips matching URL prefixes. `xss.urlPatterns` controls which paths are filtered. JSON filtering is applied when `Content-Type` is `application/json`.

## Auth Interceptor

`RestApiInteceptor` only checks controller methods annotated with `@Check`.

```java
@Check
@PostMapping("/profile")
public Result<UserVO> profile(HttpServletRequest request) {
    String userId = (String) request.getAttribute(JwtConstants.CURRENT_USER);
    return Result.ok(userService.getProfile(userId));
}
```

The request must send:

```http
Authorization: <jwt-token>
```

The interceptor validates JWT expiration, then validates the token again in Redis. It writes the current user id to request attribute `JwtConstants.CURRENT_USER`.

Do not add `@Check` to public endpoints. Current code treats methods without `@Check` as public.

## Login And Logout

Existing login endpoint:

```http
POST /base-backend/auth/login
Content-Type: application/json

{
  "name": "admin",
  "password": "123456"
}
```

Passwords are compared with SHA-256. JWT expiration is 24 hours (`JwtConstants.EXPIRATION_TIME`). Logout reads the `Authorization` header and deletes the Redis token key for that user.

If you rely on the built-in auth interceptor, keep the Redis token read/write key strategy consistent with `RestApiInteceptor` and `RedisConstant`.

## Entity Auto Fill

Extend `BaseEntity` for common fields:

```java
@TableName("sys_user")
public class SysUserPO extends BaseEntity {
    private String name;
    private String password;
}
```

`BaseEntity` provides:

- `id`: MyBatis Plus `ASSIGN_ID`
- `createAt`
- `updateAt`
- `createBy`
- `updateBy`

`updateAt` is automatically filled on update through `MyMetaObjectHandler` and:

```java
@TableField(value = "update_at", fill = FieldFill.UPDATE)
private Date updateAt;
```

`createAt` is not filled by Java code; use the database default value if you need automatic create time.

## MyBatis Plus

`MybatisPlusConfig` enables:

- optimistic lock interceptor
- pagination interceptor
- PostgreSQL pagination dialect
- `VarcharLengthCheckInterceptor`

The mapper XML scan path is:

```yaml
mybatis-plus:
  mapper-locations: classpath*:tech/ynfy/module/**/mapper/xml/*Mapper.xml
```

For normal CRUD:

```java
public interface SysUserMapper extends BaseMapper<SysUserPO> {
}
```

For join queries, switch to `MPJBaseMapper`:

```java
public interface SysUserMapper extends MPJBaseMapper<SysUserPO> {
}
```

## Friendly Varchar Length Errors

`VarcharLengthCheckInterceptor` handles PostgreSQL `value too long` errors on insert and update. It reads the table column length metadata and throws a clearer error:

```text
Column [name] value too long.actual:[...], actual length = 120, max = 64
```

This is only for PostgreSQL length overflow errors.

## Dynamic Query DTO

Use `QueryWrapperUtil.buildQueryWrapper(dto)` to build MyBatis Plus query conditions from a DTO:

```java
QueryWrapper<SysUserPO> wrapper = QueryWrapperUtil.buildQueryWrapper(dto);
wrapper.orderByDesc("create_time");
IPage<SysUserPO> result = sysUserMapper.selectPage(page, wrapper);
```

Default behavior:

- `String` fields use `LIKE`.
- Other field types use `EQ`.
- `page`, `rows`, and `serialVersionUID` are skipped.
- Empty values are skipped.

Customize a field:

```java
@SqlUtilProperty(column = "user_name", type = SqlUtilProperty.QueryType.LIKE_RIGHT)
private String name;

@SqlUtilProperty(ignore = true)
private String frontendOnly;
```

Supported query types in code: `EQ`, `NE`, `GT`, `GE`, `LT`, `LE`, `LIKE`, `LIKE_LEFT`, `LIKE_RIGHT`, `IN`, `NOT_IN`, `IS_NULL`, `IS_NOT_NULL`.

## Redis

`RedisConfig` provides:

- `RedisTemplate`
- `CacheManager`
- JSON value serialization through `RedisValueSerializer`
- cache prefix based on `server.servlet.context-path`

Use `RedisUtil` for common operations:

```java
redisUtil.set("key", value, 60);
Object value = redisUtil.get("key");
redisUtil.del("key");
redisUtil.hget("hash", "field");
```

Redis config is under:

```yaml
spring:
  data:
    redis:
      host: ${config.redis.host}
      password: ${config.redis.password}
      database: ${config.redis.database}
      port: ${config.redis.port}
```

## Server Only, No PostgreSQL

If this service is only used as an HTTP server and does not access SQL:

1. Do not inject or call mapper/database services.
2. Remove or comment `spring.datasource.dynamic` in `application-common.yml`.
3. Remove or comment `@Import(DynamicDataSourceAutoConfiguration.class)` in `RunApplication`.
4. Keep controller/service code independent from `BaseMapper`, `ServiceImpl`, and `@Transactional` database operations.

The PostgreSQL dependency can stay in Maven if you only want a quick server mode, but the dynamic datasource auto configuration must not be forced when there is no datasource.

## Server Only, No Redis

If this service is only used as an HTTP server and does not access Redis:

1. Do not use login/logout, `@Check`, `@RepeatSubmit`, `@RedisLock`, `RedisUtil`, or Spring Cache.
2. Keep the Redis config values if you leave Redis beans enabled; the app may create Redis beans but Redis operations are only executed when code calls them.
3. For a fully Redis-free app, remove or disable `RedisConfig` and the Redis dependency, then remove Redis placeholders from `application-common.yml`.

If Redis is down and code calls Redis features, the request will fail.

## Swagger And Knife4j

Enable or disable docs with:

```yaml
config:
  swaggerEnabled: true
```

Current paths:

- Knife4j: `/base-backend/doc.html`
- Swagger UI: `/base-backend/swagger-ui.html`
- OpenAPI JSON: `/base-backend/v3/api-docs`

`SwaggerConfig` currently scans only:

```java
packagesToScan("tech.ynfy.module.system.swagger.controller")
```

To show your business controllers, add their package:

```java
GroupedOpenApi.builder()
    .packagesToScan("tech.ynfy.module.auth.controller", "tech.ynfy.module.user.controller")
    .group("business")
    .addOpenApiMethodFilter(i -> i.isAnnotationPresent(Operation.class))
    .build();
```

Controller methods need `@Operation` to pass the current method filter.

## Local File Mapping

`WebMcvConfig` maps:

```java
registry.addResourceHandler(accessPath).addResourceLocations(localPath.split(","));
```

Configure it with:

```yaml
project-config:
  path:
    accessPath: /api/**
    localPath: file:/absolute/path/
```

Use `file:/path/` format for local directories. Multiple directories are separated by commas.

## Thread Pools

Inject the basic executor:

```java
@Resource(name = ThreadConstant.BASIC_THREAD)
private Executor executor;

executor.execute(() -> {
    // async code
});
```

Inject the scheduled executor:

```java
@Resource(name = ThreadConstant.SCHEDULED_THREAD)
private ScheduledExecutorService scheduledExecutorService;
```

Thread pool values come from:

```yaml
project-config:
  thread:
    corePoolSize: 10
    maxPoolSize: 30
    queueCapacity: 100
    keepAliveSeconds: 60
```

`ContextCopyingDecorator` copies `RequestContextHolder` into async tasks when a request context exists.

## OpenFeign

Feign is enabled by `@EnableFeignClients` on `RunApplication`.

Example:

```java
@FeignClient(
    name = "ai",
    url = "${ai.baseUrl}",
    configuration = {
        FeignConfiguration.class,
        FeignResponseDecoder.class
    }
)
public interface RestAiService {
    @PostMapping("/api/test")
    String testApi(@RequestBody RequestDTO dto);
}
```

`FeignConfiguration` sets connect/read timeout to 5 minutes and uses `FeignLogger`.

`FeignResponseDecoder` behavior:

- If the Feign method returns `Result`, the raw response is decoded as `Result`.
- If the Feign method returns another type, the decoder expects the remote response to be `Result<T>` and returns `result`.
- If `success=false`, it throws `BizException(code, message)`.

## Docker Deploy

`deploy.sh` builds with `-Pprod`, rebuilds the image, removes the old container, and starts a new one.

Before using it, check these variables in `deploy.sh`:

```bash
IMAGE_NAME="java-base"
CONTAINER_NAME="java-base"
APP_JAR_NAME="$IMAGE_NAME/target/$IMAGE_NAME-1.0.0-SNAPSHOT.jar"
HOST_PORT="8000"
CONTAINER_PORT="8000"
CONTAINER_LOG_PATH="/home/hsiong/code/config/log/${CONTAINER_NAME}/prod"
```

Then run:

```bash
bash deploy.sh
```

`Dockerfile` runs the packaged jar with JDK 21 and mounts logs under `/app/logs`.
