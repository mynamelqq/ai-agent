package com.ai.aigent.exception;

import com.ai.aigent.constant.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.util.Map;
import java.util.stream.Collectors;
@Order(100)
@RestControllerAdvice(basePackages = "com.ai.aigent.controller")
@Slf4j
public class AllExceptionHandler  {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<?>> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: {} - URL: {}", e.getMessage(), request.getRequestURI(), e);
        return ResponseEntity.status(e.getHttpStatus())
                .body(Result.error(e.getCode(), e.getMessage())
                        .path(request.getRequestURI()));
    }

    /**
     * 处理参数验证异常 (@RequestBody @Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {

        Map<String, String> errors = e.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ?
                                fieldError.getDefaultMessage() : "参数错误"
                ));

        log.warn("参数验证失败: {} - URL: {}", errors, request.getRequestURI());

        return Result.error(400, "参数验证失败");
    }

    /**
     * 处理参数验证异常 (@RequestParam @Valid)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {

        Map<String, String> errors = e.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        ConstraintViolation::getMessage
                ));

        log.warn("参数验证失败: {} - URL: {}", errors, request.getRequestURI());

        return Result.error(400, "参数验证失败");
    }

    /**
     * 处理参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Map<String, String>> handleBindException(
            BindException e, HttpServletRequest request) {

        Map<String, String> errors = e.getBindingResult().getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() != null ?
                                fieldError.getDefaultMessage() : "参数错误"
                ));

        log.warn("参数绑定失败: {} - URL: {}", errors, request.getRequestURI());

        return Result.error(400, "参数绑定失败");
    }

    /**
     * 处理资源不存在异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {

        log.warn("资源不存在: {} {} - URL: {}",
                e.getHttpMethod(), e.getRequestURL(), request.getRequestURI());

        return Result.error(404, "请求的资源不存在");
    }

    /**
     * 处理JWT认证异常
     */
    @ExceptionHandler(io.jsonwebtoken.JwtException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Result<Void> handleJwtException(io.jsonwebtoken.JwtException e, HttpServletRequest request) {
        String message;
        if (e instanceof io.jsonwebtoken.ExpiredJwtException) {
            message = "Token已过期，请重新登录";
        } else if (e instanceof io.jsonwebtoken.UnsupportedJwtException) {
            message = "不支持的Token格式";
        } else if (e instanceof io.jsonwebtoken.MalformedJwtException) {
            message = "Token格式错误";
        } else if (e instanceof io.jsonwebtoken.SignatureException) {
            message = "Token签名验证失败";
        } else {
            message = "Token验证失败: " + e.getMessage();
        }

        log.warn("JWT异常: {} - URL: {}", message, request.getRequestURI(), e);

        return Result.error(401, message);
    }

    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntimeException(
            RuntimeException e, HttpServletRequest request) {

        log.error("运行时异常: {} - URL: {}", e.getMessage(), request.getRequestURI(), e);

        // 对常见运行时异常进行友好提示
        String message;
        if (e instanceof NullPointerException) {
            message = "系统内部错误：空指针异常";
        } else if (e instanceof IllegalArgumentException) {
            message = "参数错误：" + e.getMessage();
        } else if (e instanceof IllegalStateException) {
            message = "系统状态错误：" + e.getMessage();
        } else {
            message = "系统运行时异常：" + e.getMessage();
        }

        return Result.error(500, message);
    }

    /**
     * 处理所有其他异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(
            Exception e, HttpServletRequest request) {

        log.error("系统异常: {} - URL: {}", e.getMessage(), request.getRequestURI(), e);

        return Result.error(500, "系统内部错误，请联系管理员");
    }
}
