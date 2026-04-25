package com.guatai.yangaicodemother.exception;

import lombok.Getter;

/**
 * ClassName: businessexception
 * Package: com.guatai.yangaicodemother.exception
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午3:19
 * @Version 1.0
 */
@Getter
public class BusinessException extends RuntimeException {

    /**
     * 错误码
     */
    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }
}

