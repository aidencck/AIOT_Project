# AIoT 后端基础组件库说明 (aiot-common)

`aiot-common` 是 AIoT 微服务体系的基础设施组件库，旨在为所有上层微服务（如 Gateway, Device, Auth 等）提供一致的编码规范、统一响应格式以及全局异常处理机制。

## 1. 组件概览

目前基础组件库主要提供以下四大核心能力：

1. **统一 API 响应对象 (`Result<T>`)**：确保所有 HTTP 接口的返回结构强一致（包含 `code`, `message`, `data`）。
2. **全局状态码定义 (`ResultCode`)**：集中管理业务状态码，避免魔法数字。
3. **全局统一异常处理 (`GlobalExceptionHandler`)**：消除 Controller 层的冗余 `try-catch`，拦截未预期的系统异常和业务主动抛出的 `BusinessException`。
4. **全局自动响应封装 (`GlobalResponseHandler`)**：允许 Controller 直接返回业务实体对象，框架底层自动包装成 `Result`。

---

## 2. 核心组件详解

### 2.1 统一 API 响应 (`Result` 与 `ResultCode`)
*   **路径**：`com.aiot.common.api.Result`
*   **设计目的**：将业务数据包装在统一的协议壳中，方便前端拦截器统一处理错误和 Loading 状态。
*   **响应结构**：
    ```json
    {
      "code": 200,
      "message": "操作成功",
      "data": { ...业务数据... }
    }
    ```
*   **核心状态码 (`ResultCode`)**：
    *   `200`: SUCCESS (操作成功)
    *   `500`: FAILED (操作失败/系统异常)
    *   `400`: VALIDATE_FAILED (参数校验失败)
    *   `401`: UNAUTHORIZED (暂未登录或 token 过期)
    *   `403`: FORBIDDEN (没有相关权限)
    *   `4004`: DEVICE_NOT_FOUND (业务错误：设备不存在)

### 2.2 全局自动响应封装 (`GlobalResponseHandler`)
*   **路径**：`com.aiot.common.config.GlobalResponseHandler`
*   **实现原理**：实现了 Spring MVC 的 `ResponseBodyAdvice<Object>` 接口。
*   **优势**：在 `beforeBodyWrite` 阶段，如果判断 Controller 返回的对象不是 `Result` 类型，会自动调用 `Result.success(body)` 进行包装（对 String 类型使用 Jackson 特殊处理以防止强转异常）。
*   **开发范式对比**：
    *   *传统写法*：`return Result.success(deviceService.get(id));`
    *   *VibeCoding 写法*：`return deviceService.get(id);`

### 2.3 全局统一异常处理 (`GlobalExceptionHandler`)
*   **路径**：`com.aiot.common.exception.GlobalExceptionHandler`
*   **实现原理**：使用 `@RestControllerAdvice` 注解，结合 `@ExceptionHandler` 拦截全局异常。
*   **异常分类处理**：
    *   **业务异常 (`BusinessException`)**：当业务逻辑不符合预期时（如设备离线无法下发指令），Service 层直接 `throw new BusinessException(ResultCode.DEVICE_OFFLINE)`。拦截器捕获后，返回对应的错误 Code 和 Message。
    *   **系统异常 (`Exception`)**：拦截未捕获的空指针、数据库超时等，统一返回 `500 - 系统内部异常，请联系管理员`，防止堆栈信息泄露给前端。

---

## 3. 开发使用规范 (Usage Guidelines)

在引入了 `aiot-common` 的微服务中，研发人员需遵循以下准则：

1. **Controller 层极简原则**：
   不允许在 Controller 层手动 `new Result()` 或调用 `Result.success()`。直接返回 DTO 对象或基本数据类型。
2. **异常抛出原则**：
   不允许在 Controller 层写 `try-catch`。遇到不满足业务前置条件的情况，直接抛出异常。
   ```java
   // 推荐做法 (Service 层)
   if (device == null) {
       throw new BusinessException(ResultCode.DEVICE_NOT_FOUND);
   }
   ```
3. **日志打印原则**：
   配合 `logback-spring.xml`，所有微服务的日志已自带 `[%X{traceId}]`。在捕获和处理重要异常时，使用 `log.error("业务场景描述", e)` 打印堆栈。
