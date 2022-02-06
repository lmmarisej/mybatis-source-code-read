package com.wjz.mybatis.ongl;

import org.junit.Assert;
import org.junit.Test;

import java.util.StringTokenizer;

public class StringTokenizerTest {

    @Test
    public void split() {
        String str = "hello|world|123";
        String delim = "|";
        StringTokenizer tokenier = new StringTokenizer(str, delim, false);
        Assert.assertEquals(tokenier.countTokens(), 3);

        while (tokenier.hasMoreTokens()) {
            System.out.println(tokenier.nextToken());
        }
    }

    @Test
    public void insert() {
        StringBuilder sql = new StringBuilder();
        sql.append(" * from f_order ");
        sql.insert(0, " select");
        sql.insert(sql.length(), "where f_order_code = ? ");
        System.out.println(sql.toString());
    }

}
