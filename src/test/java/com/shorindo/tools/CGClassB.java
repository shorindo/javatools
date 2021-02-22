package com.shorindo.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CGClassB extends CGClassA {
    public void methodB1() {
        List<CGClassC> classes = new ArrayList<CGClassC>();
        classes.add(new CGClassC());
        classes
            .stream()
            .filter(e -> {
                e.methodC1();
                return true;
            })
            .collect(Collectors.toList());
//        new CGClassC().methodC2();
//        new CGClassC() {
//            public void methodC1() {
//                super.methodC1();
//            }
//        }.methodC1();
        methodB2("foo");
    }

    public String methodB2(String s) {
        return s;
    }

    @Override
    void methodAbstract() {
    }
}
