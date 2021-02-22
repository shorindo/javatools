package com.shorindo.tools;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntSupplier;

public class CGClassC {
    public void methodC1() {
        System.out.println("methodC1()");
    }

    public void methodC2() {
        List<D> list = Arrays.asList(
                new D("foo"),
                new D("bar"),
                new D("baz")
                );
        list.stream()
            .filter(d -> {
                d.methodD1();
                return true;
            })
            .forEach(D::methodD2);
    }

    private static void println(String value) {
        System.out.println(value);
    }

    public static class D {
        private String s;

        public D(String s) {
            this.s = s;
        }

        public void methodD1() {
        }

        public void methodD2() {
        }
    }
}
