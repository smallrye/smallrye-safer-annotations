package io.smallrye.safer.annotations.test;

import java.util.List;

public class Invalid {
    @AccessorAnnotation
    public Integer notGetter() {
        return 1;
    }

    @AccessorAnnotation
    public void getFail() {
    }

    @AccessorAnnotation
    public int getFail(int i) {
        return 1;
    }

    @AccessorAnnotation
    public void notSetter(int i) {
    }

    @AccessorAnnotation
    public int setFail(int i) {
        return 1;
    }

    @AccessorAnnotation
    public void setFail2(int i, int j) {
    }

    @MethodAnnotation
    public int method() {
        return 1;
    }

    @MethodAnnotation
    public void method2(List<String> ls) {
    }

    @MethodAnnotation
    public void method3(List<Integer> ls, String s) {
    }

    // this is supposed to be its standard definition, but it should be overridden
    @OverriddenMethodAnnotation
    public void method4(Integer s) {
    }
}
