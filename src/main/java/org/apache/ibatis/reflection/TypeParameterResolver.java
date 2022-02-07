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

import java.lang.reflect.*;
import java.util.Arrays;

/**
 * @author Iwao AVE!
 * <p>
 * 当存在复杂的继承关系以及泛型定义时，提供静态方法用于解析指定类中的字段、方法返回值或方法参数的类型。
 * <p>
 * TypeVariable：泛型的类型变量，指的是List< T>、Map< K,V>中的T，K，V等值。
 * ParameterizedType：参数化类型，即泛型；例如：List<T>、Map<K,V>等带有参数化的对象。
 * GenericArrayType：泛型数组，描述的是形如：A< T>[]或T[]类型变量和原始类型。
 * WildcardType：通配符表达式，或泛型表达式，并不是Java类型中的一种，表示的仅仅是类似 ? extends T、? super K这样的通配符表达式。
 * Class：类的抽象，即对“类”做描述：比如类有修饰、字段、方法等属性，有获得该类的所有方法、所有公有方法等方法。
 * Java 引入泛型擦除的原因是避免因为引入泛型而导致运行时创建不必要的类。那我们其实就可以通过定义类的方式，在类信息中保留泛型信息，从而在运行时获得这些泛型信息。
 * 简而言之，Java 的泛型擦除是有范围的，即类定义中的泛型是不会被擦除的。
 * <p>
 * 综上：List<T ? entends>[]：这里的List就是ParameterizedType，T就是TypeVariable，T ? entends就是WildcardType
 * （注意，WildcardType不是Java类型，而是一个表达式），整个List<T ? entends>[]就是GenericArrayType
 */
public class TypeParameterResolver {

    /**
     * Resolve field type.
     *
     * 根据泛型类型的实现类，获取泛型字段的具体类型。
     *
     * @param field   the field
     * @param srcType the src type
     * @return The field type as {@link Type}. If it has type parameters in the declaration,<br>
     * they will be resolved to the actual runtime {@link Type}s.
     */
    public static Type resolveFieldType(Field field, Type srcType) {
        Type fieldType = field.getGenericType();        // 字段声明的类型
        Class<?> declaringClass = field.getDeclaringClass();        // 获取字段定义所在类的Class对象
        return resolveType(fieldType, srcType, declaringClass);
    }

    /**
     * Resolve return type.
     *
     * @param method  the method
     * @param srcType the src type
     * @return The return type of the method as {@link Type}. If it has type parameters in the declaration,<br>
     * they will be resolved to the actual runtime {@link Type}s.
     */
    public static Type resolveReturnType(Method method, Type srcType) {
        Type returnType = method.getGenericReturnType();
        Class<?> declaringClass = method.getDeclaringClass();
        return resolveType(returnType, srcType, declaringClass);
    }

    /**
     * Resolve param types.
     *
     * @param method  the method
     * @param srcType the src type
     * @return The parameter types of the method as an array of {@link Type}s. If they have type parameters in the
     * declaration,<br>
     * they will be resolved to the actual runtime {@link Type}s.
     */
    public static Type[] resolveParamTypes(Method method, Type srcType) {
        Type[] paramTypes = method.getGenericParameterTypes();
        Class<?> declaringClass = method.getDeclaringClass();
        Type[] result = new Type[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            result[i] = resolveType(paramTypes[i], srcType, declaringClass);
        }
        return result;
    }

    /**
     * 根据第一个参数类型（字段、方法返回值或方法参数的类型），选择合适的方法进行解析。
     *
     * @param srcType        查找该字段。
     * @param declaringClass 该字段或方法所在的类。
     */
    private static Type resolveType(Type type, Type srcType, Class<?> declaringClass) {
        if (type instanceof TypeVariable) {
            // 解析TypeVariable类型
            return resolveTypeVar((TypeVariable<?>) type, srcType, declaringClass);
        } else if (type instanceof ParameterizedType) {
            return resolveParameterizedType((ParameterizedType) type, srcType, declaringClass);
        } else if (type instanceof GenericArrayType) {
            return resolveGenericArrayType((GenericArrayType) type, srcType, declaringClass);
        } else {
            return type;    // class类型
        }
    }

    /**
     * 解析GenericArrayType类型的变量。
     *
     * @param genericArrayType 具体的字段。
     * @param srcType          实现类。
     * @param declaringClass   字段定义的类（可能是接口类型）。
     */
    private static Type resolveGenericArrayType(GenericArrayType genericArrayType, Type srcType, Class<?> declaringClass) {
        Type componentType = genericArrayType.getGenericComponentType();        // 数组元素类型，即List<String>[] 中的 List<String>
        Type resolvedComponentType = null;
        if (componentType instanceof TypeVariable) {
            resolvedComponentType = resolveTypeVar((TypeVariable<?>) componentType, srcType, declaringClass);
        } else if (componentType instanceof GenericArrayType) {
            resolvedComponentType = resolveGenericArrayType((GenericArrayType) componentType, srcType, declaringClass);
        } else if (componentType instanceof ParameterizedType) {
            resolvedComponentType = resolveParameterizedType((ParameterizedType) componentType, srcType, declaringClass);
        }
        if (resolvedComponentType instanceof Class) {
            return Array.newInstance((Class<?>) resolvedComponentType, 0).getClass();
        } else {
            return new GenericArrayTypeImpl(resolvedComponentType);
        }
    }

    /**
     * 假设传入的是：
     * - parameterizedType = Map<K, V>
     * - TypeText.SubA<Long>
     * - ClassA
     *
     * @param parameterizedType 待解析的parameterizedType类型。
     * @param srcType           解析操作的起始类型。
     * @param declaringClass    定义该字段或方法所在的类的Class对象。
     */
    private static ParameterizedType resolveParameterizedType(ParameterizedType parameterizedType, Type srcType, Class<?> declaringClass) {
        Class<?> rawType = (Class<?>) parameterizedType.getRawType();       // 获取原始类型Map对应的Class对象
        Type[] typeArgs = parameterizedType.getActualTypeArguments();       // 类型变量为K，V
        Type[] args = new Type[typeArgs.length];        // 保存解析后的真实类型
        for (int i = 0; i < typeArgs.length; i++) {     // 解析K，V
            if (typeArgs[i] instanceof TypeVariable) {      // 类型变量
                args[i] = resolveTypeVar((TypeVariable<?>) typeArgs[i], srcType, declaringClass);
            } else if (typeArgs[i] instanceof ParameterizedType) {  // 嵌套了ParameterizedType
                args[i] = resolveParameterizedType((ParameterizedType) typeArgs[i], srcType, declaringClass);
            } else if (typeArgs[i] instanceof WildcardType) {       // 嵌套了WildcardType
                args[i] = resolveWildcardType((WildcardType) typeArgs[i], srcType, declaringClass);
            } else {
                args[i] = typeArgs[i];
            }
        }
        return new ParameterizedTypeImpl(rawType, null, args);      // 解析结果封装为ParameterizedType
    }

    private static Type resolveWildcardType(WildcardType wildcardType, Type srcType, Class<?> declaringClass) {
        Type[] lowerBounds = resolveWildcardTypeBounds(wildcardType.getLowerBounds(), srcType, declaringClass);
        Type[] upperBounds = resolveWildcardTypeBounds(wildcardType.getUpperBounds(), srcType, declaringClass);
        return new WildcardTypeImpl(lowerBounds, upperBounds);
    }

    private static Type[] resolveWildcardTypeBounds(Type[] bounds, Type srcType, Class<?> declaringClass) {
        Type[] result = new Type[bounds.length];
        for (int i = 0; i < bounds.length; i++) {
            if (bounds[i] instanceof TypeVariable) {
                result[i] = resolveTypeVar((TypeVariable<?>) bounds[i], srcType, declaringClass);
            } else if (bounds[i] instanceof ParameterizedType) {
                result[i] = resolveParameterizedType((ParameterizedType) bounds[i], srcType, declaringClass);
            } else if (bounds[i] instanceof WildcardType) {
                result[i] = resolveWildcardType((WildcardType) bounds[i], srcType, declaringClass);
            } else {
                result[i] = bounds[i];
            }
        }
        return result;
    }

    private static Type resolveTypeVar(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass) {
        Type result;
        Class<?> clazz;
        if (srcType instanceof Class) {
            clazz = (Class<?>) srcType;
        } else if (srcType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) srcType;
            clazz = (Class<?>) parameterizedType.getRawType();      // 获取List<T>的List部分
        } else {
            throw new IllegalArgumentException("The 2nd arg must be Class or ParameterizedType, but was: " + srcType.getClass());
        }

        if (clazz == declaringClass) {      // 如果是继承则不相等
            Type[] bounds = typeVar.getBounds();    // 获取上界
            if (bounds.length > 0) {
                return bounds[0];
            }
            return Object.class;
        }

        Type superclass = clazz.getGenericSuperclass();     // 获取声明的父类类型 => List<T>
        result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superclass);       // 扫描父类递归处理
        if (result != null) {
            return result;
        }

        Type[] superInterfaces = clazz.getGenericInterfaces();      // 取得接口
        for (Type superInterface : superInterfaces) {
            result = scanSuperTypes(typeVar, srcType, declaringClass, clazz, superInterface);
            if (result != null) {
                return result;
            }
        }
        return Object.class;        // 都没解析成功
    }

    /**
     * 递归整个继承结构，并完成类型变量的解析。
     *
     * @param declaringClass ClassA<K, V>
     * @param typeVar        T
     * @param srcType        SubClassA<Long>
     * @param clazz          A.class
     * @param superclass     Class<T>
     */
    private static Type scanSuperTypes(TypeVariable<?> typeVar, Type srcType, Class<?> declaringClass, Class<?> clazz, Type superclass) {
        // 父类是带有参数化的类型
        if (superclass instanceof ParameterizedType) {      // 是A<T>
            ParameterizedType parentAsType = (ParameterizedType) superclass;
            Class<?> parentAsClass = (Class<?>) parentAsType.getRawType();      // 获取原始类型，A<T> => A
            TypeVariable<?>[] parentTypeVars = parentAsClass.getTypeParameters();   // 获取泛型名，A<T> => T
            if (srcType instanceof ParameterizedType) {    // 原始类型也是A<T>
                parentAsType = translateParentTypeVars((ParameterizedType) srcType, clazz, parentAsType);
            }
            if (declaringClass == parentAsClass) {
                for (int i = 0; i < parentTypeVars.length; i++) {
                    if (typeVar.equals(parentTypeVars[i])) {
                        return parentAsType.getActualTypeArguments()[i];  // 获取泛型第n个通配符表达式WildcardType，<? extends java.lang.String, ?>  第一个=> ? extends java.lang.String
                    }
                }
            }
            if (declaringClass.isAssignableFrom(parentAsClass)) {
                return resolveTypeVar(typeVar, parentAsType, declaringClass);   // 继续解析父类，直到解析到定义该字段的类
            }
        }
        // 父类是Class，并且声明的类型继承了该类型
        else if (superclass instanceof Class && declaringClass.isAssignableFrom((Class<?>) superclass)) {
            return resolveTypeVar(typeVar, superclass, declaringClass);
        }
        return null;
    }

    private static ParameterizedType translateParentTypeVars(ParameterizedType srcType, Class<?> srcClass, ParameterizedType parentType) {
        Type[] parentTypeArgs = parentType.getActualTypeArguments();
        Type[] srcTypeArgs = srcType.getActualTypeArguments();
        TypeVariable<?>[] srcTypeVars = srcClass.getTypeParameters();
        Type[] newParentArgs = new Type[parentTypeArgs.length];
        boolean noChange = true;
        for (int i = 0; i < parentTypeArgs.length; i++) {
            if (parentTypeArgs[i] instanceof TypeVariable) {
                for (int j = 0; j < srcTypeVars.length; j++) {
                    if (srcTypeVars[j].equals(parentTypeArgs[i])) {
                        noChange = false;
                        newParentArgs[i] = srcTypeArgs[j];
                    }
                }
            } else {
                newParentArgs[i] = parentTypeArgs[i];
            }
        }
        return noChange ? parentType : new ParameterizedTypeImpl((Class<?>) parentType.getRawType(), null, newParentArgs);
    }

    private TypeParameterResolver() {
        super();
    }

    static class ParameterizedTypeImpl implements ParameterizedType {
        private Class<?> rawType;

        private Type ownerType;

        private Type[] actualTypeArguments;

        public ParameterizedTypeImpl(Class<?> rawType, Type ownerType, Type[] actualTypeArguments) {
            super();
            this.rawType = rawType;
            this.ownerType = ownerType;
            this.actualTypeArguments = actualTypeArguments;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return actualTypeArguments;
        }

        @Override
        public Type getOwnerType() {
            return ownerType;
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public String toString() {
            return "ParameterizedTypeImpl [rawType=" + rawType + ", ownerType=" + ownerType + ", actualTypeArguments=" + Arrays.toString(actualTypeArguments) + "]";
        }
    }

    static class WildcardTypeImpl implements WildcardType {
        private Type[] lowerBounds;

        private Type[] upperBounds;

        WildcardTypeImpl(Type[] lowerBounds, Type[] upperBounds) {
            super();
            this.lowerBounds = lowerBounds;
            this.upperBounds = upperBounds;
        }

        @Override
        public Type[] getLowerBounds() {
            return lowerBounds;
        }

        @Override
        public Type[] getUpperBounds() {
            return upperBounds;
        }
    }

    static class GenericArrayTypeImpl implements GenericArrayType {
        private Type genericComponentType;

        GenericArrayTypeImpl(Type genericComponentType) {
            super();
            this.genericComponentType = genericComponentType;
        }

        @Override
        public Type getGenericComponentType() {
            return genericComponentType;
        }
    }
}
