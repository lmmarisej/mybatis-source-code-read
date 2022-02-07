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
package org.apache.ibatis.reflection;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;

/**
 * 处理mapper接口中定义的方法的参数列表。
 */
public class ParamNameResolver {

    public static final String GENERIC_NAME_PREFIX = "param";

    private final boolean useActualParamName;

    /**
     * 方法中 RowBounds 或 ResultHandler 类型的参数不会被记录到name集合中。
     * <p>
     * The key is the index and the value is the name of the parameter.<br />
     * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
     * the parameter index is used. Note that this index could be different from the actual index
     * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
     * </p>
     * <ul>
     * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
     * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
     * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
     * </ul>
     */
    private final SortedMap<Integer, String> names;

    private boolean hasParamAnnotation;     // 记录对应的方法参数列表中是否使用了@Param注解

    // 通过反射读取Mapper接口中对应方法的信息，并初始化对象字段
    public ParamNameResolver(Configuration config, Method method) {
        this.useActualParamName = config.isUseActualParamName();
        final Class<?>[] paramTypes = method.getParameterTypes();                       // 参数列表中每个参数的类型
        final Annotation[][] paramAnnotations = method.getParameterAnnotations();       // 参数列表上的注解
        final SortedMap<Integer, String> map = new TreeMap<>();
        int paramCount = paramAnnotations.length;
        // get names from @Param annotations
        for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
            if (isSpecialParameter(paramTypes[paramIndex])) {
                // skip special parameters
                continue;       // 跳过 RowBounds 或 ResultHandler 类型的参数
            }
            String name = null;
            // 遍历该参数的注解集合
            for (Annotation annotation : paramAnnotations[paramIndex]) {        // 获取指定位置的参数注解
                if (annotation instanceof Param) {      // 针对Param
                    hasParamAnnotation = true;
                    name = ((Param) annotation).value();        // Param注解用来给参数起别名
                    break;
                }
            }
            if (name == null) {
                // @Param was not specified.
                if (useActualParamName) {       // 没有使用@Param注解，根据配置决定是否使用实际参数名作为名称
                    name = getActualParamName(method, paramIndex);      // 获取指定方法的指定位置参数的实际名称
                }
                if (name == null) {
                    // use the parameter index as the name ("0", "1", ...)
                    // gcode issue #71
                    name = String.valueOf(map.size());      // 将有效的第n个参数索引作为参数名称
                }
            }
            map.put(paramIndex, name);      // 记录方法的第n的下标对应的参数的名称
        }
        names = Collections.unmodifiableSortedMap(map);
    }

    private String getActualParamName(Method method, int paramIndex) {
        return ParamNameUtil.getParamNames(method).get(paramIndex);
    }

    // 过滤某些特殊的参数
    private static boolean isSpecialParameter(Class<?> clazz) {
        return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
    }

    /**
     * Returns parameter names referenced by SQL providers.
     *
     * @return the names
     */
    public String[] getNames() {
        return names.values().toArray(new String[0]);
    }

    /**
     * <p>
     * A single non-special parameter is returned without a name.
     * Multiple parameters are named using the naming rule.
     * In addition to the default names, this method also adds the generic names (param1, param2,
     * ...).
     * </p>
     *
     * @param args the args     用户传入的实参列表。
     * @return the named params
     *
     * 将用户传入的参数转换为SQL语句对应的参数列表。
     *
     * 返回HashMap类型。
     */
    public Object getNamedParams(Object[] args) {
        final int paramCount = names.size();
        if (args == null || paramCount == 0) {      // 无参数返回null
            return null;
        } else if (!hasParamAnnotation && paramCount == 1) {            // 未使用@Param，且只有一个参数
            Object value = args[names.firstKey()];
            return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
        }
        // 处理使用@Param注解了参数，或有多个参数的情况
        else {
            final Map<String, Object> param = new ParamMap<>();
            int i = 0;
            for (Map.Entry<Integer, String> entry : names.entrySet()) {
                param.put(entry.getValue(), args[entry.getKey()]);      // 将参数与用户传入的参数关联
                // add generic param names (param1, param2, ...)
                // mapper接口定义时使用了 (param1, param2, ...) 这种参数定义形式
                final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
                // ensure not to overwrite parameter named with @Param
                if (!names.containsValue(genericParamName)) {       // @Param注解瞄准了param1这种格式的参数，无需添加，@Param优先
                    param.put(genericParamName, args[entry.getKey()]);
                }
                i++;
            }
            return param;
        }
    }

    /**
     * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
     *
     * @param object          a parameter object
     * @param actualParamName an actual parameter name
     *                        (If specify a name, set an object to {@link ParamMap} with specified name)
     * @return a {@link ParamMap}
     * @since 3.5.5
     */
    public static Object wrapToMapIfCollection(Object object, String actualParamName) {
        if (object instanceof Collection) {
            ParamMap<Object> map = new ParamMap<>();
            map.put("collection", object);
            if (object instanceof List) {
                map.put("list", object);
            }
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        } else if (object != null && object.getClass().isArray()) {
            ParamMap<Object> map = new ParamMap<>();
            map.put("array", object);
            Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
            return map;
        }
        return object;
    }

}
