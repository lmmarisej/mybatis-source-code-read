package com.lmmarise._04_proxy;

public final class SubjectProxyFactory {

    public static SubjectProxy createProxy(Subject subject) {
        return new SubjectProxy(subject);
    }

}
