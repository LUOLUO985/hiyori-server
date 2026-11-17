package com.hiyori.mall.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(40000, "参数错误", HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(40100, "请先登录", HttpStatus.UNAUTHORIZED),
    FORBIDDEN(40300, "没有权限", HttpStatus.FORBIDDEN),
    NOT_FOUND(40400, "资源不存在", HttpStatus.NOT_FOUND),
    WX_LOGIN_FAILED(50010, "微信登录失败", HttpStatus.BAD_GATEWAY),
    INTERNAL(50000, "服务器开小差了", HttpStatus.INTERNAL_SERVER_ERROR);

    private final int code;
    private final String msg;
    private final HttpStatus status;

    ErrorCode(int code, String msg, HttpStatus status) {
        this.code = code;
        this.msg = msg;
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
