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
package org.apache.ibatis.plugin;

import java.util.Properties;

/**
 * @author Clinton Begin
 *
 * Mybatis中的拦截器都需要实现的接口，是插件模块的核心。
 *
 * 生效原理：
 *      Mybatis初始化时，通过XMLConfigBuilder#pluginElement(XNode)解析配置文件中相应的Interceptor对象以及相应的属性。
 */
public interface Interceptor {

    // 执行拦截逻辑的方法
    Object intercept(Invocation invocation) throws Throwable;

    // 决定是否触发拦截
    default Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    // 根据配置初始化Interceptor
    default void setProperties(Properties properties) {
        // NOP
    }

}
