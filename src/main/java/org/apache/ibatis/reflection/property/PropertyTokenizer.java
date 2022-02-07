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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * @author Clinton Begin
 *
 * 解析：orders[0].items[0].name
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {     // 支持嵌套
    private String name;                // 当前表达式名称
    private final String indexedName;   // 当前表达式索引名
    private String index;   // 索引下标
    private final String children;  // 子表达式

    public PropertyTokenizer(String fullname) {
        int delim = fullname.indexOf('.');
        if (delim > -1) {
            name = fullname.substring(0, delim);        // orders[0]
            children = fullname.substring(delim + 1);   // items[0].name
        } else {
            name = fullname;    // orders[0]
            children = null;
        }
        indexedName = name;     // orders[0]
        delim = name.indexOf('[');  // 6
        if (delim > -1) {
            index = name.substring(delim + 1, name.length() - 1);   // orders[0]        =>      0
            name = name.substring(0, delim);        // orders[0]        =>      orders
        }
    }

    public String getName() {
        return name;
    }

    public String getIndex() {
        return index;
    }

    public String getIndexedName() {
        return indexedName;
    }

    public String getChildren() {
        return children;
    }

    @Override
    public boolean hasNext() {
        return children != null;
    }

    @Override
    public PropertyTokenizer next() {
        return new PropertyTokenizer(children);
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
    }
}
