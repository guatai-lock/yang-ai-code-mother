package com.guatai.yangaicodemother.common;

import com.guatai.yangaicodemother.exception.ErrorCode;
import lombok.Data;

import java.io.Serializable;

/**
 * ClassName: base
 * Package: com.guatai.yangaicodemother.common
 * Description:
 *
 * @Author 尚硅谷-宋红康
 * @Create 2026/4/22 下午3:20
 * @Version 1.0
 */
@Data
public class BaseResponse<T> implements Serializable {

    private int code;

    private T data;

    private String message;

    public BaseResponse(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public BaseResponse(int code, T data) {
        this(code, data, "");
    }

    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), null, errorCode.getMessage());
    }
}

