package com.alipay.ticketbacked.web.exception;

import com.alipay.ticketbacked.core.model.BizException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
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

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return ResponseEntity.status(400).body(Map.of("detail", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("[全局异常] 类型: {}, 消息: {}", e.getClass().getName(), e.getMessage(), e);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", "内部错误: " + e.getMessage());
        body.put("exception", e.getClass().getName());
        if (e.getCause() != null) {
            body.put("cause", e.getCause().getClass().getName() + ": " + e.getCause().getMessage());
        }
        StackTraceElement[] st = e.getStackTrace();
        if (st != null && st.length > 0) {
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(st.length, 20);
            for (int i = 0; i < limit; i++) {
                sb.append(st[i].toString()).append('\n');
            }
            body.put("stacktrace", sb.toString());
        }
        return ResponseEntity.status(500).body(body);
    }
}