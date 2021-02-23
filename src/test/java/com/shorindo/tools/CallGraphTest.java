package com.shorindo.tools;

import static org.junit.Assert.*;

import java.util.Map;
import java.util.Map.Entry;

import org.junit.Test;

import javassist.ClassPool;
import javassist.CtMethod;

public class CallGraphTest {
    private CallGraph graph = new CallGraph();
    private ClassPool cp = ClassPool.getDefault();

    private void assertClass(Class<?> clazz) throws Exception {
        graph.walkClass(cp.get(clazz.getName()));
    }

    /*
     * メソッド呼び出し
     */
    @Test
    public void testC1() throws Exception {
        assertClass(C1.class);
    }

    public static class C1 {
        public void c1() {
            c2();
            new C2().c2();
        }
        public void c2() {
        }
    }

    public static class C2 {
        public void c2() {
        }
    }

    /*
     * インターフェースの実装
     */
    @Test
    public void testI1() throws Exception {
        assertClass(C3.class);
    }

    public static interface I1 {
        public String i1(String s);
    }

    public static class C3 implements I1 {
        @Override
        public String i1(String s) {
            return null;
        }

        public void c3() {
            i1("c3");
        }
    }

    /*
     * インターフェース呼び出し
     */
    @Test
    public void testC4() throws Exception {
        assertClass(C4.class);
    }

    public static class C4 {
        public void c4() {
            I1 i1 = new C3();
            i1.i1("C3");
        }
    }

    /*
     * 継承メソッド呼び出し
     */
    @Test
    public void testC5() throws Exception {
        assertClass(C5.class);
    }

    public static class C5 {
        public void c5() {
        }
    }

    public static class C6 extends C5 {
    }

    /*
     * 抽象クラス継承
     */
    @Test
    public void testA1() throws Exception {
        assertClass(C5.class);
    }

    public static abstract class A1 {
        public abstract void a1();
        public void a2() {
        }
    }

    public static class C7 extends A1 {
        @Override
        public void a1() {
        }
    }
}
