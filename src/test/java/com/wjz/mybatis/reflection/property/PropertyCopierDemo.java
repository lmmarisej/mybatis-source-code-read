package com.wjz.mybatis.reflection.property;

import com.wjz.mybatis.reflection.bridge.Child;
import org.apache.ibatis.reflection.property.PropertyCopier;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PropertyCopierDemo {

    @Test
    public void copyBeanProperties() {
        Child child = new Child();
        child.setName("iss002");
        Child child2 = new Child();
        PropertyCopier.copyBeanProperties(Child.class, child, child2);
        assertEquals("iss002", child2.getName());
    }

}
