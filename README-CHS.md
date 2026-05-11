# java-base-singledb

英文文档: [README.md](README.md)

开源项目，欢迎参与。未经授权的商用行为，我们保留追责权利。

## 启动和打包

当前项目使用 JDK 21、Spring Boot 3.4.10。

```bash
mvn clean package -Pdev
mvn clean package -Ptest
mvn clean package -Pprod
```

启动服务：

```bash
mvn -pl backend-system spring-boot:run -Pdev
java -jar backend-system/target/backend-system-1.0.0-SNAPSHOT.jar
```

`application.yml` 中使用 `@profiles.active@, common`，所以 Maven profile 决定最终打包哪个环境：

- `-Pdev`：`application-dev.yml` + `application-common.yml`
- `-Ptest`：`application-test.yml` + `application-common.yml`
- `-Pprod`：`application-prod.yml` + `application-common.yml`

默认访问前缀是 `/base-backend`。端口读取 `config.port`，如果没有配置则使用 `application-common.yml` 里的默认值 `8000`。

## 接口日志

`WebLogAspect` 会自动打印 Controller 接口日志，切点是：

```java
execution(public * ..module..*.*Controller.*(..))
```

生效条件：

- Controller 包路径中包含 `module`
- Controller 类名以 `Controller` 结尾
- 方法是 `public`

每次请求会打印：

- 请求地址
- Controller 方法
- 客户端 IP
- 方法参数
- 耗时秒数

返回值会用 `debug` 级别打印。`ModelAndView` 返回值不会打印。

## 统一返回

接口返回使用 `Result`：

```java
return Result.ok(data);
return Result.ok();
return Result.error("message");
return Result.authExpire();
```

返回对象包含 `success`、`message`、`code`、`result`、`timestamp`。

## 全局异常捕获

`GlobalExceptionHandler` 会自动捕获：

- `MethodArgumentNotValidException`：返回参数校验错误，例如 `field: message`
- `BizException`：返回业务异常 code 和 message
- `RedisLockException`：返回业务异常 code 和 message，并带上服务 context path
- 其它 `Exception`：打印堆栈，并返回 `系统异常-请联系管理员：xxx`

接口参数校验这样写：

```java
@PostMapping("/save")
public Result<Void> save(@Valid @RequestBody SysUserSaveDTO dto) {
    service.save(dto);
    return Result.ok();
}
```

DTO 字段这样写：

```java
@NotBlank(message = "用户名 不能为空")
private String name;
```

## 防重复提交

在 Controller 方法上加 `@RepeatSubmit`：

```java
@RepeatSubmit
@PostMapping("/submit")
public Result<Void> submit(@RequestBody SubmitDTO dto) {
    service.submit(dto);
    return Result.ok();
}
```

拦截器只处理带 `@RepeatSubmit` 的方法。它会用下面内容生成 Redis key：

- 请求头 `URL`；如果没有，则使用 `request.getRequestURI()`
- JSON body；如果 body 为空，则使用 `request.getParameterMap()`

最终 key 是 `repeat_submit:` + URL 和参数 MD5。默认 10 秒内相同 URL + 相同参数会被认为重复提交。这个功能依赖 Redis。

## RedisLock 分布式锁和限流

在 service 方法上加 `@RedisLock`，用于同一个业务 key 防并发：

```java
@RedisLock(
    prefix = "order:pay",
    key = "#request.orderNo",
    waitTime = 0,
    leaseTime = 30,
    message = "订单正在处理中，请稍后再试"
)
public void pay(PayRequestDTO request) {
    // 业务逻辑
}
```

`key` 支持：

- `key = "MD5"`：把第一个方法参数转 JSON 后做 MD5
- `key = "#request.orderNo"`：从方法参数里用 SpEL 取值
- `key = "#header.Authorization"`：从请求头取值
- `key = "fixed"`：固定字符串

`waitTime <= 0` 表示只抢一次锁，不等待。`leaseTime <= 0` 当前会使用默认 30 秒租约。`throwException = false` 时，抢锁失败直接返回 `null`。

用 `limitTime` 可以做同一个 key 的时间窗口限流：

```java
@RedisLock(prefix = "sms:send", key = "#phone", limitTime = 60, message = "短信发送太频繁")
public void sendSms(String phone) {
    // 发送短信
}
```

上面表示同一个手机号 60 秒内只允许成功一次。

## XSS 拦截

`XssFilterConfig` 注册了两个过滤器：

- `xssFilter`：过滤 query 参数和 JSON body 里的 HTML 标签
- `repeatableFilter`：把 JSON 请求包装成可重复读取 body 的 request，供防重复提交等逻辑使用

配置项：

```yaml
xss:
  enabled: true
  excludes: /system/notice/*
  urlPatterns: /system/*,/monitor/*,/tool/*
```

`xss.enabled=false` 表示关闭 XSS 过滤。`xss.excludes` 表示排除的 URL 前缀。`xss.urlPatterns` 表示哪些路径进入 XSS 过滤。JSON body 过滤要求 `Content-Type` 是 `application/json`。

## 鉴权拦截

`RestApiInteceptor` 只检查加了 `@Check` 的 Controller 方法。

```java
@Check
@PostMapping("/profile")
public Result<UserVO> profile(HttpServletRequest request) {
    String userId = (String) request.getAttribute(JwtConstants.CURRENT_USER);
    return Result.ok(userService.getProfile(userId));
}
```

请求头必须带：

```http
Authorization: <jwt-token>
```

拦截器会先校验 JWT 是否过期，再到 Redis 二次校验 token，校验通过后把当前用户 ID 写入 request attribute：`JwtConstants.CURRENT_USER`。

公开接口不要加 `@Check`。当前代码逻辑是：没有 `@Check` 的方法直接放行。

## 登录和登出

已有登录接口：

```http
POST /base-backend/auth/login
Content-Type: application/json

{
  "name": "admin",
  "password": "123456"
}
```

密码使用 SHA-256 后再和数据库比较。JWT 过期时间是 24 小时，即 `JwtConstants.EXPIRATION_TIME`。登出接口读取 `Authorization` 请求头，并删除 Redis 中当前用户的 token。

如果要使用内置鉴权拦截器，注意 `RestApiInteceptor` 和 `RedisConstant` 中的 Redis token 读写 key 策略需要保持一致。

## 实体自动字段

实体继承 `BaseEntity`：

```java
@TableName("sys_user")
public class SysUserPO extends BaseEntity {
    private String name;
    private String password;
}
```

`BaseEntity` 自带：

- `id`：MyBatis Plus `ASSIGN_ID`
- `createAt`
- `updateAt`
- `createBy`
- `updateBy`

`updateAt` 会在更新时自动刷新，依赖 `MyMetaObjectHandler` 和字段注解：

```java
@TableField(value = "update_at", fill = FieldFill.UPDATE)
private Date updateAt;
```

`createAt` 当前 Java 代码不自动填充，需要自动创建时间时建议用数据库默认值。

## MyBatis Plus

`MybatisPlusConfig` 已启用：

- 乐观锁插件
- 分页插件
- PostgreSQL 分页方言
- `VarcharLengthCheckInterceptor`

Mapper XML 扫描路径：

```yaml
mybatis-plus:
  mapper-locations: classpath*:tech/ynfy/module/**/mapper/xml/*Mapper.xml
```

普通 CRUD：

```java
public interface SysUserMapper extends BaseMapper<SysUserPO> {
}
```

如果要连表查询，改成 `MPJBaseMapper`：

```java
public interface SysUserMapper extends MPJBaseMapper<SysUserPO> {
}
```

## 字符串长度错误提示

`VarcharLengthCheckInterceptor` 会处理 PostgreSQL 的 `value too long` 插入和更新错误。它会读取表字段长度，并抛出更明确的错误：

```text
Column [name] value too long.actual:[...], actual length = 120, max = 64
```

这个功能只针对 PostgreSQL 字段长度超限错误。

## DTO 自动构建查询条件

使用 `QueryWrapperUtil.buildQueryWrapper(dto)` 可以根据 DTO 自动构建 MyBatis Plus 查询条件：

```java
QueryWrapper<SysUserPO> wrapper = QueryWrapperUtil.buildQueryWrapper(dto);
wrapper.orderByDesc("create_time");
IPage<SysUserPO> result = sysUserMapper.selectPage(page, wrapper);
```

默认规则：

- `String` 字段使用 `LIKE`
- 其它字段使用 `EQ`
- 自动跳过 `page`、`rows`、`serialVersionUID`
- 空值自动跳过

字段自定义：

```java
@SqlUtilProperty(column = "user_name", type = SqlUtilProperty.QueryType.LIKE_RIGHT)
private String name;

@SqlUtilProperty(ignore = true)
private String frontendOnly;
```

当前代码支持的查询类型：`EQ`、`NE`、`GT`、`GE`、`LT`、`LE`、`LIKE`、`LIKE_LEFT`、`LIKE_RIGHT`、`IN`、`NOT_IN`、`IS_NULL`、`IS_NOT_NULL`。

## Redis

`RedisConfig` 提供：

- `RedisTemplate`
- `CacheManager`
- `RedisValueSerializer` JSON 序列化
- 基于 `server.servlet.context-path` 的缓存前缀

常用操作通过 `RedisUtil`：

```java
redisUtil.set("key", value, 60);
Object value = redisUtil.get("key");
redisUtil.del("key");
redisUtil.hget("hash", "field");
```

Redis 配置位置：

```yaml
spring:
  data:
    redis:
      host: ${config.redis.host}
      password: ${config.redis.password}
      database: ${config.redis.database}
      port: ${config.redis.port}
```

## 只作为 Server，不连接 PostgreSQL

如果项目只作为 HTTP 服务，不访问 SQL：

1. 不要注入或调用 mapper/database service。
2. 删除或注释 `application-common.yml` 里的 `spring.datasource.dynamic`。
3. 删除或注释 `RunApplication` 上的 `@Import(DynamicDataSourceAutoConfiguration.class)`。
4. Controller/service 不要依赖 `BaseMapper`、`ServiceImpl`、数据库事务类逻辑。

只想快速保留 server 能力时，PostgreSQL Maven 依赖可以先不删；关键是不要在无数据源时强制导入 dynamic datasource 自动配置。

## 只作为 Server，不连接 Redis

如果项目只作为 HTTP 服务，不访问 Redis：

1. 不要使用登录/登出、`@Check`、`@RepeatSubmit`、`@RedisLock`、`RedisUtil`、Spring Cache。
2. 如果保留 Redis Bean，可以保留 Redis 配置值；只要业务不调用 Redis 操作，就不会执行 Redis 命令。
3. 如果要彻底无 Redis，删除或停用 `RedisConfig` 和 Redis 依赖，再移除 `application-common.yml` 中的 Redis 占位配置。

Redis 服务不可用时，只要代码调用了 Redis 功能，请求就会失败。

## Swagger 和 Knife4j

通过下面配置开关文档：

```yaml
config:
  swaggerEnabled: true
```

当前访问地址：

- Knife4j：`/base-backend/doc.html`
- Swagger UI：`/base-backend/swagger-ui.html`
- OpenAPI JSON：`/base-backend/v3/api-docs`

`SwaggerConfig` 当前只扫描：

```java
packagesToScan("tech.ynfy.module.system.swagger.controller")
```

如果要展示业务 Controller，需要把业务包加进去：

```java
GroupedOpenApi.builder()
    .packagesToScan("tech.ynfy.module.auth.controller", "tech.ynfy.module.user.controller")
    .group("business")
    .addOpenApiMethodFilter(i -> i.isAnnotationPresent(Operation.class))
    .build();
```

当前还加了 `@Operation` 过滤，所以 Controller 方法需要写 `@Operation` 才会进入文档。

## 本地文件映射

`WebMcvConfig` 里做了本地文件映射：

```java
registry.addResourceHandler(accessPath).addResourceLocations(localPath.split(","));
```

配置示例：

```yaml
project-config:
  path:
    accessPath: /api/**
    localPath: file:/absolute/path/
```

本地目录用 `file:/path/` 格式。多个目录用英文逗号分隔。

## 线程池

注入普通线程池：

```java
@Resource(name = ThreadConstant.BASIC_THREAD)
private Executor executor;

executor.execute(() -> {
    // 异步逻辑
});
```

注入定时任务线程池：

```java
@Resource(name = ThreadConstant.SCHEDULED_THREAD)
private ScheduledExecutorService scheduledExecutorService;
```

线程池配置：

```yaml
project-config:
  thread:
    corePoolSize: 10
    maxPoolSize: 30
    queueCapacity: 100
    keepAliveSeconds: 60
```

`ContextCopyingDecorator` 会在存在请求上下文时，把 `RequestContextHolder` 复制到异步线程中。

## OpenFeign

`RunApplication` 上已经启用 `@EnableFeignClients`。

示例：

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

`FeignConfiguration` 设置连接和读取超时时间为 5 分钟，并使用 `FeignLogger` 打印请求日志。

`FeignResponseDecoder` 规则：

- Feign 方法返回 `Result` 时，直接按 `Result` 解码。
- Feign 方法返回其它类型时，远端响应需要是 `Result<T>`，decoder 会返回其中的 `result`。
- 如果远端 `success=false`，抛出 `BizException(code, message)`。

## Docker 部署

`deploy.sh` 会使用 `-Pprod` 打包，重建镜像，删除旧容器并启动新容器。

使用前先检查这些变量：

```bash
IMAGE_NAME="java-base"
CONTAINER_NAME="java-base"
APP_JAR_NAME="$IMAGE_NAME/target/$IMAGE_NAME-1.0.0-SNAPSHOT.jar"
HOST_PORT="8000"
CONTAINER_PORT="8000"
CONTAINER_LOG_PATH="/home/hsiong/code/config/log/${CONTAINER_NAME}/prod"
```

执行：

```bash
bash deploy.sh
```

`Dockerfile` 使用 JDK 21 运行 jar，容器内日志目录是 `/app/logs`。
