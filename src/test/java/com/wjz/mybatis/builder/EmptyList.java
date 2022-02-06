package com.wjz.mybatis.builder;

import com.wjz.mybatis.pojo.Person;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class EmptyList {

    @Test
    public void empty() {
        List<Person> persons = Collections.<Person>emptyList();
        Assert.assertEquals(0, persons.size());
    }
}
