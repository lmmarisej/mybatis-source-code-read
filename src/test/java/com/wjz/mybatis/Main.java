package com.wjz.mybatis;

import com.wjz.mybatis.ongl.User;
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
        String resource = "mybatis-config.xml";
        InputStream inputStream = Resources.getResourceAsStream(resource);  // 加载配置文件
        SqlSessionFactory sqlSessionFactory = new SqlSessionFactoryBuilder().build(inputStream);    // 根据配置创建sqlSessionFactory
        SqlSession sqlSession = sqlSessionFactory.openSession();    // 创建sqlSession
        User user = sqlSession.selectOne("com.zzh.mybatis.mapping.userMapper.getUser", 1);  // sqlSession执行配置文件中SQL，提交事物
        System.out.println(user);
    }
}
