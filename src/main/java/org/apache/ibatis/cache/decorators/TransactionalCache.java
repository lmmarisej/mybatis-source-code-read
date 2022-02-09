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
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The 2nd level cache transactional buffer.
 * <p>
 * This class holds all cache entries that are to be added to the 2nd level cache during a Session.
 * Entries are sent to the cache when commit is called or discarded if the Session is rolled back.
 * Blocking cache support has been added. Therefore any get() that returns a cache miss
 * will be followed by a put() so any lock associated with the key can be released.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 *
 * 保存在某个SQLSession的某个事务中需要向某个二级缓存中添加的缓存数据。
 */
public class TransactionalCache implements Cache {

    private static final Log log = LogFactory.getLog(TransactionalCache.class);

    private final Cache delegate;       // 二级缓存对应的Cache对象
    private boolean clearOnCommit;      // true表示当前TransactionalCache不可查询，提交事务时会将delegate清空
    private final Map<Object, Object> entriesToAddOnCommit;     // 记录事务提交前的结果，事务提交时将这些结果添加到二级缓存
    // 主要针对BlockingCache装饰器，在缓存未命中的情况下也能在提交或回滚时调用delegate.putObject(entry, null)，保证BlockingCache#releaseLock的调用，释放锁
    private final Set<Object> entriesMissedInCache;     // 记录缓存未命中的cacheKey

    public TransactionalCache(Cache delegate) {
        this.delegate = delegate;
        this.clearOnCommit = false;
        this.entriesToAddOnCommit = new HashMap<>();
        this.entriesMissedInCache = new HashSet<>();
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
    public Object getObject(Object key) {
        // issue #116
        Object object = delegate.getObject(key);        // 底层缓存中查询
        if (object == null) {
            entriesMissedInCache.add(key);      // 记录缓存未命中的key
        }
        // issue #146
        if (clearOnCommit) {
            return null;
        } else {
            return object;
        }
    }

    @Override
    public void putObject(Object key, Object object) {
        entriesToAddOnCommit.put(key, object);      // 事务还未提交，将其缓存在此
    }

    @Override
    public Object removeObject(Object key) {
        return null;
    }

    @Override
    public void clear() {
        clearOnCommit = true;
        entriesToAddOnCommit.clear();
    }

    public void commit() {
        if (clearOnCommit) {
            delegate.clear();
        }
        flushPendingEntries();
        reset();        // 缓存设置为不可用
    }

    public void rollback() {
        unlockMissedEntries();
        reset();
    }

    private void reset() {
        clearOnCommit = false;
        entriesToAddOnCommit.clear();
        entriesMissedInCache.clear();
    }

    // 事物提交和回滚时调用
    private void flushPendingEntries() {
        // 加入二级缓存
        for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
            delegate.putObject(entry.getKey(), entry.getValue());
        }
        // 将未命中的缓存加入二级缓存
        for (Object entry : entriesMissedInCache) {
            if (!entriesToAddOnCommit.containsKey(entry)) {     // 在这个事务中，该key始终没有缓存命中
                delegate.putObject(entry, null);        // BlockingCache在读缓存时加锁，在缓存未命中时（null）始终不释放锁，这里保证putObject的调用，从而保证锁的释放
            }
        }
    }

    private void unlockMissedEntries() {
        for (Object entry : entriesMissedInCache) {
            try {
                delegate.removeObject(entry);
            } catch (Exception e) {
                log.warn("Unexpected exception while notifying a rollback to the cache adapter. "
                        + "Consider upgrading your cache adapter to the latest version. Cause: " + e);
            }
        }
    }

}
