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

import org.apache.ibatis.reflection.invoker.*;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.util.MapUtil;

import java.lang.reflect.*;
import java.text.MessageFormat;
import java.util.*;
import java.util.Map.Entry;

/**
 * This class represents a cached set of class definition information that allows for easy mapping between property names and getter/setter methods.
 *
 * 每个Reflector对象都对应一个类，Reflector中缓存了反射操作需要使用的类的元信息。
 *
 * @author Clinton Begin
 */
public class Reflector {

    private final Class<?> type;        // 对应的Class类型
    private final String[] readablePropertyNames;       // 可读属性集合，存储getter方法的属性
    private final String[] writablePropertyNames;
    private final Map<String, Invoker> setMethods = new HashMap<>();    // 记录属性相应的setter方法，key是属性名，Invoker对setter方法的封装
    private final Map<String, Invoker> getMethods = new HashMap<>();
    private final Map<String, Class<?>> setTypes = new HashMap<>();     // 记录setter方法相应的参数类型
    private final Map<String, Class<?>> getTypes = new HashMap<>();
    private Constructor<?> defaultConstructor;      // 记录默认构造方法

    private Map<String, String> caseInsensitivePropertyMap = new HashMap<>();       // 记录了所有属性名称的集合

    public Reflector(Class<?> clazz) {
        type = clazz;
        addDefaultConstructor(clazz);       // 默认构造
        Method[] classMethods = getClassMethods(clazz);     // 获取当前类（包括父类）中定义的所有方法的唯一签名以及对应的Method对象
        addGetMethods(classMethods);    // 处理getter方法
        addSetMethods(classMethods);
        addFields(clazz);       // 处理没有getter/setter的字段
        readablePropertyNames = getMethods.keySet().toArray(new String[0]);     // 默认空数组，根据getter，初始化可读属性名称集合
        writablePropertyNames = setMethods.keySet().toArray(new String[0]);
        for (String propName : readablePropertyNames) {     // 初始化caseInsensitivePropertyMap集合
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
        for (String propName : writablePropertyNames) {
            caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
        }
    }

    // 反射遍历所有构造，获取默认构造
    private void addDefaultConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        Arrays.stream(constructors).filter(constructor -> constructor.getParameterTypes().length == 0)
                .findAny().ifPresent(constructor -> this.defaultConstructor = constructor);
    }

    // 解析类中定义的getter方法
    private void addGetMethods(Method[] methods) {
        Map<String, List<Method>> conflictingGetters = new HashMap<>();
        Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 0 && PropertyNamer.isGetter(m.getName()))
                .forEach(m -> addMethodConflict(conflictingGetters, PropertyNamer.methodToProperty(m.getName()), m));
        resolveGetterConflicts(conflictingGetters);
    }

    // 处理子类覆盖的getter方法
    private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
        for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
            Method winner = null;
            String propName = entry.getKey();
            boolean isAmbiguous = false;
            for (Method candidate : entry.getValue()) {
                if (winner == null) {
                    winner = candidate;
                    continue;
                }
                Class<?> winnerType = winner.getReturnType();       // 方法返回值
                Class<?> candidateType = candidate.getReturnType();
                if (candidateType.equals(winnerType)) {
                    if (!boolean.class.equals(candidateType)) {
                        isAmbiguous = true;
                        break;
                    } else if (candidate.getName().startsWith("is")) {
                        winner = candidate;
                    }
                } else if (candidateType.isAssignableFrom(winnerType)) {
                    // OK getter type is descendant
                } else if (winnerType.isAssignableFrom(candidateType)) {
                    winner = candidate;
                } else {
                    isAmbiguous = true;
                    break;
                }
            }
            addGetMethod(propName, winner, isAmbiguous);
        }
    }

    private void addGetMethod(String name, Method method, boolean isAmbiguous) {
        MethodInvoker invoker = isAmbiguous
                ? new AmbiguousMethodInvoker(method, MessageFormat.format(
                "Illegal overloaded getter method with ambiguous type for property ''{0}'' in class ''{1}''. This breaks the JavaBeans specification and can cause unpredictable results.",
                name, method.getDeclaringClass().getName()))
                : new MethodInvoker(method);
        getMethods.put(name, invoker);  // 属性名和MethodInvoker加入
        Type returnType = TypeParameterResolver.resolveReturnType(method, type);
        getTypes.put(name, typeToClass(returnType));        // 将返回值加入
    }

    private void addSetMethods(Method[] methods) {
        Map<String, List<Method>> conflictingSetters = new HashMap<>();
        Arrays.stream(methods).filter(m -> m.getParameterTypes().length == 1 && PropertyNamer.isSetter(m.getName()))
                .forEach(m -> addMethodConflict(conflictingSetters, PropertyNamer.methodToProperty(m.getName()), m));
        resolveSetterConflicts(conflictingSetters);
    }

    private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
        if (isValidPropertyName(name)) {
            List<Method> list = MapUtil.computeIfAbsent(conflictingMethods, name, k -> new ArrayList<>());
            list.add(method);
        }
    }

    private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
        for (Entry<String, List<Method>> entry : conflictingSetters.entrySet()) {
            String propName = entry.getKey();
            List<Method> setters = entry.getValue();
            Class<?> getterType = getTypes.get(propName);
            boolean isGetterAmbiguous = getMethods.get(propName) instanceof AmbiguousMethodInvoker;
            boolean isSetterAmbiguous = false;
            Method match = null;
            for (Method setter : setters) {
                if (!isGetterAmbiguous && setter.getParameterTypes()[0].equals(getterType)) {
                    // should be the best match
                    match = setter;
                    break;
                }
                if (!isSetterAmbiguous) {
                    match = pickBetterSetter(match, setter, propName);
                    isSetterAmbiguous = match == null;
                }
            }
            if (match != null) {
                addSetMethod(propName, match);
            }
        }
    }

    private Method pickBetterSetter(Method setter1, Method setter2, String property) {
        if (setter1 == null) {
            return setter2;
        }
        Class<?> paramType1 = setter1.getParameterTypes()[0];
        Class<?> paramType2 = setter2.getParameterTypes()[0];
        if (paramType1.isAssignableFrom(paramType2)) {
            return setter2;
        } else if (paramType2.isAssignableFrom(paramType1)) {
            return setter1;
        }
        MethodInvoker invoker = new AmbiguousMethodInvoker(setter1,
                MessageFormat.format(
                        "Ambiguous setters defined for property ''{0}'' in class ''{1}'' with types ''{2}'' and ''{3}''.",
                        property, setter2.getDeclaringClass().getName(), paramType1.getName(), paramType2.getName()));
        setMethods.put(property, invoker);
        Type[] paramTypes = TypeParameterResolver.resolveParamTypes(setter1, type);
        setTypes.put(property, typeToClass(paramTypes[0]));
        return null;
    }

    private void addSetMethod(String name, Method method) {
        MethodInvoker invoker = new MethodInvoker(method);
        setMethods.put(name, invoker);
        Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
        setTypes.put(name, typeToClass(paramTypes[0]));
    }

    private Class<?> typeToClass(Type src) {
        Class<?> result = null;
        if (src instanceof Class) {
            result = (Class<?>) src;
        } else if (src instanceof ParameterizedType) {
            result = (Class<?>) ((ParameterizedType) src).getRawType();
        } else if (src instanceof GenericArrayType) {
            Type componentType = ((GenericArrayType) src).getGenericComponentType();
            if (componentType instanceof Class) {
                result = Array.newInstance((Class<?>) componentType, 0).getClass();
            } else {
                Class<?> componentClass = typeToClass(componentType);
                result = Array.newInstance(componentClass, 0).getClass();
            }
        }
        if (result == null) {
            result = Object.class;
        }
        return result;
    }

    // 处理类中定义的所有字段，将处理后的字段信息添加到对应集合
    private void addFields(Class<?> clazz) {
        Field[] fields = clazz.getDeclaredFields();     // 全部字段
        for (Field field : fields) {
            if (!setMethods.containsKey(field.getName())) {     // 没有同名的setter方法
                // issue #379 - removed the check for final because JDK 1.5 allows
                // modification of final fields through reflection (JSR-133). (JGB)
                // pr #16 - final static can only be set by the classloader
                int modifiers = field.getModifiers();
                // 只过滤final static 字段，final字段在jsr133中可以被反射修改
                if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
                    addSetField(field);
                }
            }
            if (!getMethods.containsKey(field.getName())) {
                addGetField(field);
            }
        }
        if (clazz.getSuperclass() != null) {
            addFields(clazz.getSuperclass());       // 处理父类
        }
    }

    private void addSetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            setMethods.put(field.getName(), new SetFieldInvoker(field));        // 字段封装为Invoker
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            setTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private void addGetField(Field field) {
        if (isValidPropertyName(field.getName())) {
            getMethods.put(field.getName(), new GetFieldInvoker(field));
            Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
            getTypes.put(field.getName(), typeToClass(fieldType));
        }
    }

    private boolean isValidPropertyName(String name) {
        return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
    }

    /**
     * This method returns an array containing all methods
     * declared in this class and any superclass.
     * We use this method, instead of the simpler <code>Class.getMethods()</code>,
     * because we want to look for private methods as well.
     *
     * @param clazz The class
     * @return An array containing all methods in this class
     */
    private Method[] getClassMethods(Class<?> clazz) {
        Map<String, Method> uniqueMethods = new HashMap<>();        // 记录类中全部方法的签名
        Class<?> currentClass = clazz;
        // 从子类开始向父类遍历处理
        while (currentClass != null && currentClass != Object.class) {
            addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

            // we also need to look for interface methods -
            // because the class may be abstract
            Class<?>[] interfaces = currentClass.getInterfaces();       // 记录接口中定义的方法
            for (Class<?> anInterface : interfaces) {
                addUniqueMethods(uniqueMethods, anInterface.getMethods());
            }

            currentClass = currentClass.getSuperclass();        // 获取父类
        }

        Collection<Method> methods = uniqueMethods.values();

        return methods.toArray(new Method[0]);      // 转为数组
    }

    // 为每个方法生成唯一的签名放入uniqueMethods
    private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
        for (Method currentMethod : methods) {
            if (!currentMethod.isBridge()) {
                String signature = getSignature(currentMethod);
                // check to see if the method is already known
                // if it is known, then an extended class must have
                // overridden a method
                if (!uniqueMethods.containsKey(signature)) {        // 避免父类方法覆盖子类方法
                    uniqueMethods.put(signature, currentMethod);
                }
            }
        }
    }

    private String getSignature(Method method) {
        StringBuilder sb = new StringBuilder();
        Class<?> returnType = method.getReturnType();
        if (returnType != null) {
            sb.append(returnType.getName()).append('#');
        }
        sb.append(method.getName());
        Class<?>[] parameters = method.getParameterTypes();
        for (int i = 0; i < parameters.length; i++) {
            sb.append(i == 0 ? ':' : ',').append(parameters[i].getName());
        }
        return sb.toString();
    }

    /**
     * Checks whether can control member accessible.
     *
     * @return If can control member accessible, it return {@literal true}
     * @since 3.5.0
     */
    public static boolean canControlMemberAccessible() {
        try {
            SecurityManager securityManager = System.getSecurityManager();
            if (null != securityManager) {
                securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
            }
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    /**
     * Gets the name of the class the instance provides information for.
     *
     * @return The class name
     */
    public Class<?> getType() {
        return type;
    }

    public Constructor<?> getDefaultConstructor() {
        if (defaultConstructor != null) {
            return defaultConstructor;
        } else {
            throw new ReflectionException("There is no default constructor for " + type);
        }
    }

    public boolean hasDefaultConstructor() {
        return defaultConstructor != null;
    }

    public Invoker getSetInvoker(String propertyName) {
        Invoker method = setMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    public Invoker getGetInvoker(String propertyName) {
        Invoker method = getMethods.get(propertyName);
        if (method == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return method;
    }

    /**
     * Gets the type for a property setter.
     *
     * @param propertyName - the name of the property
     * @return The Class of the property setter
     */
    public Class<?> getSetterType(String propertyName) {
        Class<?> clazz = setTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /**
     * Gets the type for a property getter.
     *
     * @param propertyName - the name of the property
     * @return The Class of the property getter
     */
    public Class<?> getGetterType(String propertyName) {
        Class<?> clazz = getTypes.get(propertyName);
        if (clazz == null) {
            throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
        }
        return clazz;
    }

    /**
     * Gets an array of the readable properties for an object.
     *
     * @return The array
     */
    public String[] getGetablePropertyNames() {
        return readablePropertyNames;
    }

    /**
     * Gets an array of the writable properties for an object.
     *
     * @return The array
     */
    public String[] getSetablePropertyNames() {
        return writablePropertyNames;
    }

    /**
     * Check to see if a class has a writable property by name.
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a writable property by the name
     */
    public boolean hasSetter(String propertyName) {
        return setMethods.containsKey(propertyName);
    }

    /**
     * Check to see if a class has a readable property by name.
     *
     * @param propertyName - the name of the property to check
     * @return True if the object has a readable property by the name
     */
    public boolean hasGetter(String propertyName) {
        return getMethods.containsKey(propertyName);
    }

    public String findPropertyName(String name) {
        return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
    }
}
