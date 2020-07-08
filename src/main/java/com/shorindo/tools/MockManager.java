/*
 * Copyright 2020 Shorindo, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.shorindo.tools;

import java.io.UnsupportedEncodingException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.Modifier;
import javassist.NotFoundException;

/**
 * 
 */
public class MockManager {
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        CtClass.main(null);
        ClassPool clPool = ClassPool.getDefault();
    }

    public static <T>T mock(Class<T> clazz) {
        try {
            CtClass.main(null);
            ClassPool classPool = ClassPool.getDefault();
            CtClass ctClass = classPool.get(clazz.getName());
            for (CtMethod ctMethod : ctClass.getDeclaredMethods()) {
                String access = "";
                if ((Modifier.PUBLIC & ctMethod.getModifiers()) != 0) {
                    access = "public";
                } else if ((Modifier.PROTECTED & ctMethod.getModifiers()) != 0) {
                    access = "protected";
                } else {
                    access = "private";
                }
                if ((Modifier.STATIC & ctMethod.getModifiers()) != 0) {
                    access += " static";
                }
                System.out.println(access + " " + ctMethod.getReturnType().getName() + " " + ctMethod.getLongName());
            }
            
            CtClass componentCC = classPool.get(clazz.getName());
            CtClass proxyCC = classPool.makeClass(clazz.getName() + "$$Proxy", componentCC);
            Class proxyClazz = proxyCC.toClass();
            return (T)proxyClazz.newInstance();
        } catch (NotFoundException e) {
            e.printStackTrace();
        } catch (CannotCompileException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static <T>T mock(Class<T> clazz, Object methodMock) {
        return null;
    }

    protected static void addHook(String signature, Object mock) {
        
    }

    public static boolean hasHook(String signature) {
        return false;
    }

    public static Object doHook(String signature) {
        return null;
    }

    public static String hash(String in) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            StringBuffer sb = new StringBuffer();
            for (byte b : md5.digest(in.getBytes("UTF-8"))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
    
    public void withoutReturn() {
        String sign = hash("public void com.shorindo.tools.MockManager.withoutReturn()");
        if (hasHook(sign)) {
            doHook(sign);
            return;
        }
        System.out.println("withoutReturn");
    }
    
    public boolean withReturn(String param) {
        String sign = hash("public boolean com.shorindo.tools.MockManager.withReturn(java.lang.String)");
        if (hasHook(sign)) {
            return (Boolean)doHook(sign);
        }
        System.out.println("withReturn");
        return false;
    }
    
    public static void main(String[] args) {
        try {
            Method method = MockManager.class.getDeclaredMethod("withReturn", String.class);
            System.out.println(method);
            System.out.println(hash(method.toString()));
            
            MockManager manager = MockManager.mock(MockManager.class);
            boolean b = manager.withReturn(null);
            System.out.println(b);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }
}
