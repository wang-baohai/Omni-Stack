package com.omni.common.result;

import lombok.Data;

import java.io.Serializable;

/**
 * 统一 API 响应封装类。
 * <p>
 * 所有接口返回数据均使用此包装结构，确保前后端响应格式统一：
 * <pre>{"code": 200, "message": "success", "data": {...}}</pre>
 * </p>
 * <p>使用方式：通过静态工厂方法 {@link #ok()} / {@link #ok(Object)} 构建成功响应，
 * 通过 {@link #fail(String)} / {@link #fail(int, String)} 构建失败响应。
 * 禁止直接实例化（私有构造函数）。</p>
 *
 * @param <T> 响应数据的泛型类型
 * @see PageResult
 * @see BusinessException
 */
@Data
public class R<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 响应状态码，200 表示成功 */
    private int code;
    /** 响应消息 */
    private String message;
    /** 响应数据体 */
    private T data;

    /** 私有构造函数，防止外部直接实例化 */
    private R() {}

    /**
     * 返回无数据的成功响应。
     *
     * @param <T> 泛型类型
     * @return 成功的空响应
     */
    public static <T> R<T> ok() {
        return ok(null);
    }

    /**
     * 返回携带数据的成功响应。
     *
     * @param data 响应数据
     * @param <T>  泛型类型
     * @return 成功的响应
     */
    public static <T> R<T> ok(T data) {
        R<T> r = new R<>();
        r.setCode(200);
        r.setMessage("success");
        r.setData(data);
        return r;
    }

    /**
     * 返回默认错误码（500）的失败响应。
     *
     * @param message 错误消息
     * @param <T>     泛型类型
     * @return 失败的响应
     */
    public static <T> R<T> fail(String message) {
        return fail(500, message);
    }

    /**
     * 返回指定错误码的失败响应。
     *
     * @param code    错误码
     * @param message 错误消息
     * @param <T>     泛型类型
     * @return 失败的响应
     */
    public static <T> R<T> fail(int code, String message) {
        R<T> r = new R<>();
        r.setCode(code);
        r.setMessage(message);
        return r;
    }
}
