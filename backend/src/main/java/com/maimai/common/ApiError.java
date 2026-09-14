package com.maimai.common;

import java.time.Instant;

/** 统一错误响应体：业务码 + 可读信息 + 追踪号，不泄露内部堆栈。 */
public record ApiError(String code, String message, String traceId, Instant timestamp) {
}
