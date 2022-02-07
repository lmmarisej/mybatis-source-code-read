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
package org.apache.ibatis.reflection.wrapper;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.util.List;

/**
 * @author Clinton Begin
 *
 * 对类级别的元信息的封装和处理，抽象了对象的属性信息，提供了查询、更新属性的方法。
 */
public interface ObjectWrapper {

    // 直接获取getter返回值，对于集合直接通过下标获取返回值
    Object get(PropertyTokenizer prop);

    void set(PropertyTokenizer prop, Object value);

    // 查找属性表达式指定的属性，第二个参数表示是否忽略表达式中的下划线
    String findProperty(String name, boolean useCamelCaseMapping);

    String[] getGetterNames();      // 可写属性的名称集合

    String[] getSetterNames();

    Class<?> getSetterType(String name);    // 解析setter方法的参数类型

    Class<?> getGetterType(String name);    // 解析getter方法的返回值类型

    boolean hasSetter(String name); // 判断属性表达式指定的属性是否有setter方法

    boolean hasGetter(String name);

    // 为属性表达式指定的属性创建MetaObject对象
    MetaObject instantiatePropertyValue(String name, PropertyTokenizer prop, ObjectFactory objectFactory);

    boolean isCollection();     // 封装的对象是否为集合

    void add(Object element);   // 调用集合的add方法

    <E> void addAll(List<E> element);       // 调用集合的addAll

}
