package com.wjz.mybatis.reflection.bridge;

import java.util.List;

/**
 * <b>接口为泛型类型接口</b>
 * <p>
 * 范型T被换成了Object
 * </p>
 *
 * @param <T>
 * @author iss002
 */
public interface Parent<T> {

    /**
     * 编译后的方法签名为：public abstract Object bridgeMethod(Object param)
     *
     * @param t
     * @return
     */
    T bridgeMethod(T t);

    List<String> getMethod();
}
