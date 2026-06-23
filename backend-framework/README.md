# backend-framework

`backend-framework` is a core module that provides foundational framework configurations, utility classes, and common annotations for the application.

## Main Features

- **Unified Web Log Aspect**: Automatic execution pointcut logging for API controllers.
- **Unified API Response**: `Result` wrappers for success/error statuses.
- **Global Exception Handling**: Consolidated exception routing and user-friendly messaging.
- **Redis Operations**: Custom cache managers, `RedisUtil` helpers, and context-based key prefixes.
- **Distributed Lock & Request Limiter**: Easy synchronization via `@RedisLock` and `@RepeatSubmit` annotations.
- **MyBatis-Plus Integration**: Pre-configured query wrappers, pagination handlers, and varchar overflow checks.
- **OpenFeign Integration**: Support for automatic decoding, load balancing, and structured timeouts.

---

## Change Notes

- v1.0.4
  - resolved #49
  - resolved #48

- v1.0.3
  - resolved #46
  - resolved #44
  - resolved #42

- v1.0.2
  - resolved #35
  - resolved #34
  - resolved #33
  - resolved #32
  - resolved #31
  - resolved #30
  - resolved #26
  - resolved #25
  - resolved #22
  - resolved #21
  - resolved #15
  - resolved #14
  - resolved #13

- v1.0.1
  - resolved #12
  - Migrate to Central Publisher Portal
  - resolved #11

- v1.0.0
  - resolved #9
  - resolved #7
  - resolved #5
  - resolved #4
  - resolved #3
  - resolved #1
