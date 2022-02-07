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

import java.util.Deque;
import java.util.LinkedList;

/**
 * FIFO (first in, first out) cache decorator.
 *
 * @author Clinton Begin
 *
 * 缓存项达到上限，将清除最老的缓存。
 */
public class FifoCache implements Cache {

    private final Cache delegate;
    private final Deque<Object> keyList;        // 记录key进入缓存的先后顺序
    private int size;       // 记录缓存上限

    public FifoCache(Cache delegate) {
        this.delegate = delegate;
        this.keyList = new LinkedList<>();
        this.size = 1024;
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        return delegate.getSize();
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Override
    public void putObject(Object key, Object value) {
        cycleKeyList(key);      // 记录key缓存顺序
        delegate.putObject(key, value);     // 缓存key
    }

    @Override
    public Object getObject(Object key) {
        return delegate.getObject(key);
    }

    @Override
    public Object removeObject(Object key) {
        return delegate.removeObject(key);
    }

    @Override
    public void clear() {
        delegate.clear();
        keyList.clear();
    }

    private void cycleKeyList(Object key) {
        keyList.addLast(key);       // 先维护key顺序，最后进入的
        if (keyList.size() > size) {    // 需要执行FIFO缓存清除
            Object oldestKey = keyList.removeFirst();       // 获取最先进入的
            delegate.removeObject(oldestKey);       // 从底层的数据结构中删除指定缓存
        }
    }

}
