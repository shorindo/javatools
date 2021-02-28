package com.shorindo.tools;

import static org.junit.Assert.*;

import java.util.Optional;

import org.junit.BeforeClass;
import org.junit.Test;

import com.shorindo.tools.CallGraph.CallData;
import com.shorindo.tools.CallGraph.MethodData;

import javassist.ClassPool;

/*
 *
 */
public class CallGraphTest {
    private static CallGraph graph = new CallGraph();
    private ClassPool cp = ClassPool.getDefault();

    @BeforeClass
    public static void beforeClass() {
        graph.addInclude("com.shorindo.");
    }

    private void assertMethod(Class<?> clazz, String shortName) throws Exception {
        assertMethod(clazz, shortName, true);
    }

    private void assertNotMethod(Class<?> clazz, String shortName) throws Exception {
        assertMethod(clazz, shortName, false);
    }

    private void assertMethod(Class<?> clazz, String shortName, boolean b) throws Exception {
        Optional<MethodData> result = graph.analyzeClass(cp.get(clazz.getName()))
                .getMethodList()
                .stream()
                .filter(m -> {
                    //System.out.println(m.getShortName());
                    return shortName.equals(SHORTEN(m.getFullName()));
                })
                .findFirst();
        if (b && !result.isPresent()) {
            fail("not found:" + shortName);
        }
        if (!b && result.isPresent()) {
            fail("found:" + shortName);
        }
    }

    private void assertCall(Class<?> clazz, String callerName, String calleeName, String typeName) throws Exception {
        assertCall(clazz, callerName, calleeName, typeName, true);
    }

    private void assertNotCall(Class<?> clazz, String callerName, String calleeName, String typeName) throws Exception {
        assertCall(clazz, callerName, calleeName, typeName, false);
    }

    private void assertCall(Class<?> clazz, String callerName, String calleeName, String typeName, boolean b) throws Exception {
        Optional<CallData> result = graph.analyzeClass(cp.get(clazz.getName()))
            .getCallList()
            .entrySet()
            .stream()
            .map(e -> {
                return e.getValue();
            })
            .filter(call -> {
                //System.out.println(call);
                return callerName.equals(SHORTEN(call.getCallerName()))
                        && calleeName.equals(SHORTEN(call.getCalleeName()))
                        && typeName.equals(call.getType().name());
            })
            .findFirst();
        if (b && !result.isPresent()) {
            fail("not found edge:" + callerName + ", " + calleeName + ", " + typeName);
        }
        if (!b && result.isPresent()) {
            fail("found edge:" + callerName + ", " + calleeName + ", " + typeName);
        }
    }

    private String SHORTEN(String methodName) {
        return methodName.replaceAll("^.*?([^\\.\\$]+#[^\\(]+).*$", "$1");
    }

    /**
     * インターフェース
     */
    @Test
    public void test1() throws Exception {
        assertMethod(I1.class, "I1#i11");

        assertMethod(I2.class, "I2#i11");
        assertMethod(I2.class, "I2#i21");
        assertCall(I2.class, "I1#i11", "I2#i11", "IMPLEMENT");
    }

    public static interface I1 {
        public void i11();
    }

    public static interface I2 extends I1 {
        public void i21();
    }

    /**
     * 抽象クラス
     */
    @Test
    public void test2() throws Exception {
        assertMethod(A1.class, "A1#i11");
        assertMethod(A1.class, "A1#a11");
        assertMethod(A1.class, "A1#a12");

        assertMethod(A2.class, "A2#i11");
        assertMethod(A2.class, "A2#a11");
        assertMethod(A2.class, "A2#a12");
        assertMethod(A2.class, "A2#a21");
        assertCall(A2.class, "A2#a21", "A2#i11", "CALL");
    }

    public static abstract class A1 implements I1 {
        public abstract void a11();
        public void a12() {
            i11();
            a11();
            a12();
        }
    }

    public static class A2 extends A1 {
        public void i11() {
        }
        @Override
        public void a11() {
        }
        public void a21() {
            i11();
        }
    }

    /**
     * クラス
     */
    @Test
    public void test3() throws Exception {
        assertMethod(C1.class, "C1#i11");
        assertMethod(C1.class, "C1#i21");
        assertMethod(C1.class, "C1#a11");
        assertMethod(C1.class, "C1#a12");
        assertMethod(C1.class, "C1#c11");
        assertCall(C1.class, "I2#i11", "C1#i11", "IMPLEMENT");
        assertCall(C1.class, "I2#i21", "C1#i21", "IMPLEMENT");
        assertCall(C1.class, "C1#c11", "C1#i11", "CALL");
        assertCall(C1.class, "C1#c11", "C1#a11", "CALL");
        assertCall(C1.class, "C1#c11", "C1#a12", "CALL");
        assertCall(C1.class, "C1#c11", "C1#c11", "CALL");
    }

    public static class C1 extends A1 implements I2 {
        @Override
        public void i11() {
        }
        @Override
        public void i21() {
        }
        @Override
        public void a11() {
        }
        public void c11() {
            i11();
            a11();
            a12();
            c11();
        }
        public void c12() {
        }
        private void c13() {
        }
    }

    /**
     * 継承
     */
    @Test
    public void test4() throws Exception {
        assertMethod(C2.class, "C2#i11");
        assertMethod(C2.class, "C2#a11");
        assertMethod(C2.class, "C2#a12");
        assertMethod(C2.class, "C2#c11");
        assertMethod(C2.class, "C2#c12");
        assertMethod(C2.class, "C2#c21");
        assertNotMethod(C2.class, "C2#c13");
        assertCall(C2.class, "C2#c11", "C1#c11", "INHERIT");
        assertCall(C2.class, "C2#a11", "C1#a11", "INHERIT");
        assertCall(C2.class, "C2#a12", "C1#a12", "INHERIT");
        assertNotCall(C2.class, "C2#c12", "C1#c12", "INHERIT");
        assertCall(C2.class, "C2#c21", "C2#c11", "CALL");
        assertCall(C2.class, "C2#c21", "C2#c12", "CALL");
    }

    public static class C2 extends C1 {
        public void c12() {
        }
        public void c21() {
            c11();
            c12();
        }
    }
}
