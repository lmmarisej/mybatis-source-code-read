package com.wjz.mybatis.binding;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代理对象工厂
 * <p>
 * 使用JDK代理方式创建代理对象
 * </p>
 *
 * @param <D>
 * @author iss002
 */
public class PersistentProxyFactory<D> {

    private Class<D> persistentInterface;
    private final Map<Method, PersistentMethod> methodCache = new ConcurrentHashMap<>();

    public PersistentProxyFactory(Class<D> persistentInterface) {
        this.persistentInterface = persistentInterface;
    }

    public void setPersistentInterface(Class<D> persistentInterface) {
        this.persistentInterface = persistentInterface;
    }

    public Map<Method, PersistentMethod> getMethodCache() {
        return methodCache;
    }

    public D newInstance(DataSource dataSource) {
        final PersistentProxy<D> proxy = new PersistentProxy<D>(dataSource, persistentInterface, methodCache);
        return newInstance(proxy);
    }

    @SuppressWarnings("unchecked")
    protected D newInstance(PersistentProxy<D> proxy) {
        return (D) Proxy.newProxyInstance(persistentInterface.getClassLoader(), new Class[]{persistentInterface}, proxy);
    }
}
