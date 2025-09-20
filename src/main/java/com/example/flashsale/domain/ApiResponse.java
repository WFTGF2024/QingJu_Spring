package com.example.flashsale.domain;

import lombok.Data;

@Data
public class ApiResponse<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 0; r.msg = "ok"; r.data = data; return r;
    }
    public static <T> ApiResponse<T> fail(String msg) {
        ApiResponse<T> r = new ApiResponse<>();
        r.code = 1; r.msg = msg; return r;
    }
}
