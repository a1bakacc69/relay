package com.example.relay.common;

import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 接住业务异常（我们主动抛的） */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e) {
        return R.error(e.getCode(), e.getMessage());
    }

    /**
     * @Valid 校验没过。
     *
     * <p>不加这一条的话，它会被下面的 Exception 兜住返回「服务器内部错误」——
     * 明明是「密码太短」这种用户能自己改的问题，却提示成系统故障。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("请求参数不合法");
        return R.error(400, message);
    }

    /** 兜底的兜底：接住所有其他异常（我们没预料到的） */
    @ExceptionHandler(Exception.class)
    public R<Void> handleException(Exception e) {
        e.printStackTrace();                        // 真实错误打到控制台，方便你自己排查
        return R.error(500, "服务器内部错误");        // 给前端的只说这么一句
    }
}
