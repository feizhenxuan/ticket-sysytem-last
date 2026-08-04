package com.alipay.ticketbacked.web.exception;

import com.alipay.ticketbacked.core.model.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 全局异常处理 — 对应 FastAPI 的 HTTPException
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<Map<String, Object>> handleBizException(BizException e) {
        return ResponseEntity.status(e.getStatus()).body(Map.of("detail", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("[全局异常] 类型: {}, 消息: {}", e.getClass().getName(), e.getMessage(), e);
        // 不向前端泄露内部堆栈信息
        return ResponseEntity.status(500).body(Map.of("detail", "服务器内部错误"));
    }
}