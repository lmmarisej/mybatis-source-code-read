package com.wjz.mybatis.parsing;

import org.apache.ibatis.io.Resources;
import org.apache.ibatis.parsing.PropertyParser;

import java.util.Properties;

/**
 * <b>Token解析</b>
 *
 * @author iss002
 */
public class TokenParser {

    public static void main(String[] args) {
        try {
            String resource = "configuration.properties";
            Properties variables = Resources.getResourceAsProperties(resource);
            String value = PropertyParser.parse("${driver}", variables);
            System.out.println(value);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
