package com.lmmarise._02_reflect;

import com.lmmarise._02_reflect.bridge.Child;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;

/**
 * 实例创建工厂
 *
 * @author iss002
 */
public class ObjectFactoryDemo {

    public static void main(String[] args) {
        ObjectFactory factory = new DefaultObjectFactory();
        Child child = factory.create(Child.class);
        child.setName("iss002");
        System.out.println(child.getName());
    }
}
