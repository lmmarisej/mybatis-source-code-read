/*
 *    Copyright 2009-2022 the original author or authors.
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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;

/**
 * Soft Reference cache decorator
 * Thanks to Dr. Heinz Kabutz for his guidance here.
 *
 * @author Clinton Begin
 *
 * 强引用：在被引用时，JVM不会删除。
 * 软引用：在被引用时，JVM内存紧张时会被删除。
 * 弱引用：在被引用时，GC时被扫描到了会被回收。
 * 幽灵引用：上面的在被引用时，若未被GC删除时可以通过get获取到，而幽灵引用始终拿不到，但JVM删除其时会将其加入引用队列，用于检测对象可达性。
 */
public class SoftCache implements Cache {
    private final Deque<Object> hardLinksToAvoidGarbageCollection;      // 最近使用的缓存不会被删除，强引用FIFO队列
    // 当SoftReference被GC回收是，JVM会将SoftReference对象加入ReferenceQueue（包括弱引用、幽灵引用）
    private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;    // 用于记录已经被GC删除的Soft引用
    private final Cache delegate;
    private int numberOfHardLinks;      // 强引用的个数

    public SoftCache(Cache delegate) {
        this.delegate = delegate;
        this.numberOfHardLinks = 256;
        this.hardLinksToAvoidGarbageCollection = new LinkedList<>();
        this.queueOfGarbageCollectedEntries = new ReferenceQueue<>();
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public int getSize() {
        removeGarbageCollectedItems();
        return delegate.getSize();
    }

    public void setSize(int size) {
        this.numberOfHardLinks = size;
    }

    @Override
    public void putObject(Object key, Object value) {
        removeGarbageCollectedItems();
        // 可以看到，底层缓存中不是直接缓存value，而是包装value的SoftEntry
        delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries));
    }

    @Override
    public Object getObject(Object key) {
        Object result = null;
        @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
                // 从缓存中查找
        SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key);
        if (softReference != null) {
            result = softReference.get();
            if (result == null) {       // 拿不到
                // key随着栈桢的出栈而消失，无需担心
                delegate.removeObject(key);     // 说明被GC干掉了，手动从底层缓存中删除SoftReference
            } else {
                // See #586 (and #335) modifications need more than a read lock
                synchronized (hardLinksToAvoidGarbageCollection) {
                    hardLinksToAvoidGarbageCollection.addFirst(result);     // 刚用的result缓存，将其加入Deque-FIFO队列，为其建立强引用
                    if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {     // 缓存数超过了
                        hardLinksToAvoidGarbageCollection.removeLast();     // 删除最先进入的
                    }
                }
            }
        }
        return result;
    }

    @Override
    public Object removeObject(Object key) {
        removeGarbageCollectedItems();
        @SuppressWarnings("unchecked")
        SoftReference<Object> softReference = (SoftReference<Object>) delegate.removeObject(key);
        return softReference == null ? null : softReference.get();
    }

    @Override
    public void clear() {
        synchronized (hardLinksToAvoidGarbageCollection) {
            hardLinksToAvoidGarbageCollection.clear();
        }
        removeGarbageCollectedItems();
        delegate.clear();
    }

    // 删除被GC干掉的缓存
    private void removeGarbageCollectedItems() {
        SoftEntry sv;
        while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) {
            delegate.removeObject(sv.key);      // 从底层缓存中删除
        }
    }

    private static class SoftEntry extends SoftReference<Object> {
        private final Object key;

        SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
            super(value, garbageCollectionQueue);       // value为软引用，关联了引用队列
            this.key = key;     // key为强引用
        }
    }

}
