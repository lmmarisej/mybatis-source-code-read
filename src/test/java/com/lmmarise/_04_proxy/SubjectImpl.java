package com.lmmarise._04_proxy;

public class SubjectImpl implements Subject {

    @Override
    public void operation() {
        System.out.println("具体实现类");
    }

}
