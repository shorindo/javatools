/*
 * Copyright 2018 Shorindo, Inc.
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

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.ClassFile;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

/**
 * UNDER CONSTRUCTION
 */
public class CallGraph {
    private static Logger LOG = Logger.getLogger(CallGraph.class);
    private ClassPool cp;
    private Map<String,ClassInfo> classMap;
    private Set<String> includeSet;
    private Set<String> excludeSet;

    public static void main(String[] args) {
//        for (Entry<Object,Object> entry : System.getProperties().entrySet()) {
//            LOG.info("props:" + entry.getKey() + " = " + entry.getValue());
//        }

        try {
            CallGraph graph = new CallGraph();
            graph.addInclude("com.shorindo.");
            graph.addExclude("java.");
            graph.create(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CallGraph() {
        cp = ClassPool.getDefault();
        classMap = new TreeMap<>();
        includeSet = new HashSet<>();
        excludeSet = new HashSet<>();
    }

    private void create(String[] paths) throws Exception {
        LOG.info("create(" + paths + ")");
        for (String path : paths) {
            cp.appendClassPath(path);
            walkFile(new File(path));
        }
    }

    private void addInclude(String pattern) {
        includeSet.add(pattern);
    }

    private void addExclude(String pattern) {
        excludeSet.add(pattern);
    }

    private void walkFile(File file) throws Exception {
        //LOG.info("walkFile(" + file.getName() + ")");
        String name = file.getName();
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                walkFile(child);
            }
        } else if (name.endsWith(".class")) {
            walkClass(file);
        } else if (name.endsWith(".jar")) {
            walkJar(file);
        }
    }

    private void walkJar(File file) throws NotFoundException {
        LOG.info("walkJar(" + file.getName() + ")");
        cp.appendClassPath(file.getAbsolutePath());
        try (JarFile jarFile = new JarFile(file)) {
            //cp.appendClassPath(file.getAbsolutePath());
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String clazzName = path2clazz(entry.getName());
                    if (!filter(clazzName)) {
                        continue;
                    }
                    LOG.info("walkJar:class=" + clazzName);
                    CtClass cc = cp.get(clazzName);
                    walkMethod(cc);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String path2clazz(String path) {
        return path.replaceAll("/", ".")
                .replaceAll("\\.class$", "");
    }

    private boolean filter(String className) {
        if (!excludeSet.isEmpty()) {
            boolean result = excludeSet.stream()
                .filter(p -> {
                    return className.startsWith(p);
                })
                .findFirst()
                .isPresent();
            if (result) {
                return false;
            }
        }
        if (!includeSet.isEmpty()) {
            return includeSet.stream()
                .filter(p -> {
                    return className.startsWith(p);
                })
                .findFirst()
                .isPresent();
        } else {
            return true;
        }
    }

    private void walkClass(File classFile) throws FileNotFoundException, IOException, NotFoundException, CannotCompileException {
        //LOG.info(classFile.getName());
        try (DataInputStream is = new DataInputStream(new FileInputStream(classFile))) {
            ClassFile cf = new ClassFile(is);
            CtClass cc = cp.get(cf.getName());
            if (!filter(cc.getName())) {
                return;
            }
            walkMethod(cc);
        }
    }

    private void walkMethod(CtClass cc) throws CannotCompileException, NotFoundException {
        if (!filter(cc.getName())) {
            return;
        }
        String className = (cc.isInterface() ? "interface " : "class ") + cc.getName();
        ClassInfo info = new ClassInfo(className);
        classMap.put(className, info);
        Set<String> methodSet = new TreeSet<>();
        Map<String,CtMethod> methodMap = new TreeMap<>();
        for (CtMethod method : cc.getDeclaredMethods()) {
            String methodName = method.getName() + method.getSignature();
            methodMap.put(methodName, method);
        }

        for (CtClass itfc : cc.getInterfaces()) {
            walkInterface(itfc);
        }

        for (String methodName : walkSuper(cc.getSuperclass())) {
            //LOG.info(className + "#" + methodName);
            methodSet.add(className + "#" + methodName);
            String name = methodName.replaceAll("(.*?)\\(.*$", "$1");
            String desc = methodName.replaceAll(".*?(\\(.*)$", "$1");
            try {
                CtMethod method = cc.getMethod(name, desc);
                methodMap.put(methodName, method);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Set<String> calleeSet = new TreeSet<>();
        for (Entry<String,CtMethod> entry : methodMap.entrySet()) {
            CtMethod method = entry.getValue();
            method.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    if (filter(m.getClassName())) {
                        String methodName = m.getClassName() + "." + m.getMethodName() + m.getSignature();
                        if (!calleeSet.contains(methodName)) {
                            LOG.info("\tCALL:" + methodName);
                            //methodSet.add(m.getMethodName());
                            calleeSet.add(methodName);
                        }
                    }
                    super.edit(m);
                }
            });

            LOG.info(className + "#" + entry.getKey());
            for (String callee : calleeSet) {
                LOG.info("\tCALL:" + callee);
            }
        }

    }

    private Set<String> walkSuper(CtClass superClass) throws CannotCompileException, NotFoundException {
        Set<String> methodSet = new TreeSet<>();
        if (!superClass.isPrimitive()
                && !superClass.getName().equals("java.lang.Object")
                && !superClass.getName().equals("java.lang.Class")) {
            for (CtMethod method : superClass.getDeclaredMethods()) {
                if (!Modifier.isPrivate(method.getModifiers())) {
                    String methodName = method.getName() + method.getSignature();
                    methodSet.add(methodName);
                }
            }
            methodSet.addAll(walkSuper(superClass.getSuperclass()));
        }
        return methodSet;
    }

    private Set<String> walkInterface(CtClass itfc) {
        //LOG.info("\timplements " + cc.getName());
        Set<String> methodSet = new TreeSet<>();
        for (CtMethod method : itfc.getDeclaredMethods()) {
            //LOG.info(method.getLongName());
            String methodName = method.getName() + method.getSignature();
            methodSet.add(methodName);
        }
        return methodSet;
    }

    public enum RelationType {
        CALL, IMPLEMENT, INHERIT, OVERRIDE;
    }

    public static class ClassInfo {
        private String name;
        private Map<String,MethodInfo> methodMap;
        private boolean fixed = false;

        public ClassInfo(String name) {
            this.name = name;
            this.methodMap = new TreeMap<>();
        }
        public boolean isFixed() {
            return fixed;
        }
        public void setFixed(boolean fixed) {
            this.fixed = fixed;
        }
        public String getName() {
            return name;
        }
        public void addMethodInfo(MethodInfo info) {
            methodMap.put(info.getName(), info);
        }
        public Map<String, MethodInfo> getMethodMap() {
            return methodMap;
        }
    }

    public static class MethodInfo {
        private String name;
        private boolean fixed = false;

        public MethodInfo (String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        public boolean isFixed() {
            return fixed;
        }
        public void setFixed(boolean fixed) {
            this.fixed = fixed;
        }

    }
}
