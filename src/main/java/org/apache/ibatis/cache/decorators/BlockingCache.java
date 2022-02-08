/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.cache.decorators;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * <p>Simple blocking decorator
 *
 * <p>Simple and inefficient version of EhCache's BlockingCache decorator.
 * It sets a lock over a cache key when the element is not found in cache.
 * This way, other threads will wait until this element is filled instead of hitting the database.
 *
 * <p>By its nature, this implementation can cause deadlock when used incorrectly.
 *
 * @author Eduardo Macarron
 *
 * 保证只有一个线程到数据库中查找指定key对应的数据。
 */
public class BlockingCache implements Cache {

    private long timeout;
    private final Cache delegate;       // 被装饰的底层Cache对象
    private final ConcurrentHashMap<Object, CountDownLatch> locks;      // 每个key都有对应的锁对象

    public BlockingCache(Cache delegate) {
        this.delegate = delegate;
        this.locks = new ConcurrentHashMap<>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    @Override
    public void putObject(Object key, Object value) {
        try {
            delegate.putObject(key, value);
        } finally {
            releaseLock(key);       // 唤醒后续的读线程，可以读了
        }
    }

    @Override
    public Object getObject(Object key) {
        acquireLock(key);
        Object value = delegate.getObject(key);
        if (value != null) {        // 加锁成功，但是没有命中缓存，不能释放锁。因为，没有命中缓存的当前线程回去准备缓存，让后续线程等待当前线程准备完成缓存。
            releaseLock(key);       // 没有命中缓存让putObject来释放锁
        }
        return value;       // 未找到缓存将不会释放锁，后续获取缓存的线程将被阻塞
    }

    @Override
    public Object removeObject(Object key) {
        // despite of its name, this method is called only to release locks
        releaseLock(key);       // 唤醒阻塞的线程，告诉阻塞的线程你们不用等了，等不到的
        return null;
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    private void acquireLock(Object key) {
        CountDownLatch newLatch = new CountDownLatch(1);
        while (true) {
            CountDownLatch latch = locks.putIfAbsent(key, newLatch);
            if (latch == null) {        // 访问同一个key的当前线程前没有其它线程
                break;      // 退出，继续执行业务
            }
            try {
                if (timeout > 0) {
                    boolean acquired = latch.await(timeout, TimeUnit.MILLISECONDS);     // 超时阻塞
                    if (!acquired) {
                        throw new CacheException(
                                "Couldn't get a lock in " + timeout + " for the key " + key + " at the cache " + delegate.getId());
                    }
                } else {
                    latch.await();      // 阻塞，乖乖到上一个线程的后面去排队
                }
            } catch (InterruptedException e) {
                throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
            }
        }
    }

    private void releaseLock(Object key) {
        CountDownLatch latch = locks.remove(key);       // 释放锁
        if (latch == null) {
            throw new IllegalStateException("Detected an attempt at releasing unacquired lock. This should never happen.");
        }
        latch.countDown();      // 唤醒操作同一个key的后续线程
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }
}
