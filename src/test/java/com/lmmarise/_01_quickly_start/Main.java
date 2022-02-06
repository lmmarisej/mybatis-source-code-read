package com.lmmarise._01_quickly_start;

import com.lmmarise._01_quickly_start.pojo.Person;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author lmmarise.j@gmail.com
 * @since 2022/2/6 4:08 PM
 */
public class Main {
    public static void main(String[] args) throws IOException {
        String resource = "com/lmmarise/_01_quickly_start/mybatis-config.xml";     // 配置文件中不仅包含了Mybatis配置，还包含了接口的实现规则mapper
        InputStream inputStream = Resources.getResourceAsStream(resource);  // 加载配置文件
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);    // 根据配置创建sqlSessionFactory
        SqlSession sqlSession = sqlSessionFactory.openSession();    // 创建sqlSession
        // sqlSession执行配置文件中SQL，提交事物。statement代表namespace+对应的sql语句名称
        Person user = sqlSession.selectOne("com.lmmarise._01_quickly_start.mapper.PersonMapper.selectById", 1);
        System.out.println(user);
    }
}
