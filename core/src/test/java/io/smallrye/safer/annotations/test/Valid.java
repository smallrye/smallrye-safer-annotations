package io.smallrye.safer.annotations.test;

import java.util.List;

public class Valid {
    @AccessorAnnotation
    public int getI() {
        return 1;
    }

    @AccessorAnnotation
    public void setI(int i) {
    }

    @MethodAnnotation
    public void method() {
    }

    @MethodAnnotation
    public List<Integer> method2(Integer i, List<Integer> i2) {
        return null;
    }

    @MethodAnnotation
    public String method3(Integer i) {
        return null;
    }
}
