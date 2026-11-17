package com.hiyori.mall.common;

/**
 * 统一返回体：{ code, msg, data }
 * code = 0 表示成功，其余见 {@link ErrorCode}。
 */
public record Result<T>(int code, String msg, T data) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }

    public static Result<Void> ok() {
        return new Result<>(0, "ok", null);
    }

    public static <T> Result<T> fail(ErrorCode code, String msg) {
        return new Result<>(code.getCode(), msg == null ? code.getMsg() : msg, null);
    }
}
