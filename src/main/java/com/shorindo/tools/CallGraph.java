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
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.AttributeInfo;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.BootstrapMethodsAttribute;
import javassist.bytecode.BootstrapMethodsAttribute.BootstrapMethod;
import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.ConstPool;
import javassist.bytecode.Descriptor;
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;
import javassist.expr.Cast;
import javassist.expr.ConstructorCall;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.Handler;
import javassist.expr.Instanceof;
import javassist.expr.MethodCall;
import javassist.expr.NewArray;
import javassist.expr.NewExpr;

/**
 * UNDER CONSTRUCTION
 */
public class CallGraph {
    private static Logger LOG = Logger.getLogger(CallGraph.class);
    private ClassPool cp;
    private Set<String> includeSet;
    private Set<String> excludeSet;

    public static void main(String[] args) {
        CallGraph callgraph = new CallGraph();
        //callgraph.execute(new File(args[0]));

        try {
            CallGraph graph = new CallGraph();
            graph.addInclude("com.shorindo.tools.CG");
            graph.addInclude("java.lang.");
            graph.addInclude("java.util.function.");
            //graph.addExclude("java.");
            graph.create(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public CallGraph() {
        cp = ClassPool.getDefault();
        includeSet = new HashSet<>();
        excludeSet = new HashSet<>();
    }

    protected void execute(File configFile) {
        Context ctx = Context.enter();
        try {
            Reader reader = new FileReader(configFile);
            Scriptable scope = ctx.initStandardObjects();
            scope.put("callgraph", scope, this);
            ctx.evaluateReader(scope, reader, configFile.getName(), 1, null);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
        } finally {
            Context.exit();
        }
    }

    private void create(String[] paths) throws Exception {
        LOG.info("create(" + paths + ")");
        for (String path : paths) {
            cp.appendClassPath(path);
            walkFile(new File(path));
        }
    }

    public void addInclude(String pattern) {
        includeSet.add(pattern);
    }

    public void addExclude(String pattern) {
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

    private void walkClass(File classFile) throws FileNotFoundException, IOException, NotFoundException, CannotCompileException, BadBytecode {
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

    private void walkMethod(CtClass cc) throws CannotCompileException, NotFoundException, BadBytecode {
        if (!filter(cc.getName())) {
            return;
        }

        String className = (cc.isInterface() ? "interface " : "class ") + cc.getName();
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

        BootstrapMethod[] bsms = null;
        for (AttributeInfo attr : cc.getClassFile().getAttributes()) {
            if (attr instanceof BootstrapMethodsAttribute) {
                BootstrapMethodsAttribute bsma = (BootstrapMethodsAttribute)attr;
                bsms = bsma.getMethods();
            }
        }

        for (Entry<String,CtMethod> entry : methodMap.entrySet()) {
            findMethod(bsms, entry.getValue());
        }

//        for (Entry<String,CtMethod> entry : methodMap.entrySet()) {
//            CtMethod method = entry.getValue();
//            Set<String> calleeSet = new TreeSet<>();
//
//            method.instrument(new ExprEditor() {
//                @Override
//                public void edit(MethodCall m) throws CannotCompileException {
//                    //LOG.info("edit(" + m.getMethodName() + ")");
//                    if (filter(m.getClassName())) {
//                        String methodName = m.getClassName() + "." + m.getMethodName() + m.getSignature();
//                        if (!calleeSet.contains(methodName)) {
//                            //LOG.info("\tCALL:" + methodName);
//                            //methodSet.add(m.getMethodName());
//                            calleeSet.add(methodName);
//                        }
//                    }
//                    super.edit(m);
//                }
//            });
//
//            int start = method.getMethodInfo().getLineNumber(Integer.MIN_VALUE);
//            int end = method.getMethodInfo().getLineNumber(Integer.MAX_VALUE);
//            LOG.info("[" + start + "," + end + "] " + className + "#" + entry.getKey());
//            for (String callee : calleeSet) {
//                LOG.info("\tCALL:" + callee);
//            }
//        }
//
//        applyLambda(cc);
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

    private boolean isLambda(CtMethod cm) {
        return cm.getName().startsWith("lambda$");
    }

    private void applyLambda(CtClass cc) {
        List<CtMethod> callerList = Arrays.asList(cc.getDeclaredMethods());
        Arrays.asList(cc.getDeclaredMethods())
            .stream()
            .filter(callee -> {
                return callee.getName().startsWith("lambda$");
            })
            .forEach(callee -> {
                int mid = callee.getMethodInfo().getLineNumber(Integer.MIN_VALUE);
                callerList.stream()
                    .filter(caller -> {
                        int start = caller.getMethodInfo().getLineNumber(Integer.MIN_VALUE);
                        int end = caller.getMethodInfo().getLineNumber(Integer.MAX_VALUE);
                        return start <= mid && mid <= end;
                    })
                    .findFirst()
                    .ifPresent(caller -> {
                        String callerName = caller.getDeclaringClass().getName() + "#" + caller.getName() + caller.getSignature();
                        String calleeName = callee.getDeclaringClass().getName() + "#" + callee.getName() + callee.getSignature();
                        output(callerName, calleeName, RelationType.CALL);
                    });
            });
//        for (CtMethod dm : cc.getDeclaredMethods()) {
//            if (isLambda(dm)) {
//                int start = dm.getMethodInfo().getLineNumber(Integer.MIN_VALUE);
//                for (CtMethod mm : cc.getDeclaredMethods()) {
//                    if (mm.getMethodInfo().getLineNumber(Integer.MIN_VALUE) <= start
//                            && start <= mm.getMethodInfo().getLineNumber(Integer.MAX_VALUE)) {
//                        LOG.info(mm.getLongName() + " -> " + dm.getLongName());
//                        break;
//                    }
//                }
//            }
//        }
    }

    private void output(String callerName, String calleeName, RelationType type) {
        //LOG.info("output(" + type.name() + ":" + callerName + " -> " + calleeName + ")");
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

    private void findMethods(CtClass cc) throws BadBytecode {
        BootstrapMethod[] bsms = null;
        for (AttributeInfo attr : cc.getClassFile().getAttributes()) {
            if (attr instanceof BootstrapMethodsAttribute) {
                BootstrapMethodsAttribute bsma = (BootstrapMethodsAttribute)attr;
                bsms = bsma.getMethods();
            }
        }

        for (CtMethod cm : cc.getMethods()) {
            if (!cm.getDeclaringClass().getName().startsWith("java.")) {
                findMethod(bsms, cm);
            }
        }
    }

    private void findMethod(BootstrapMethod[] bsms, CtMethod cb) throws BadBytecode {
        //for (CtMethod cb : cc.getDeclaredMethods()) {
            MethodInfo info = cb.getMethodInfo2();
            ConstPool pool = info.getConstPool();
            CodeAttribute code = info.getCodeAttribute();

            if (code == null)
                return;

            //System.out.println(cb.getLongName());
            System.out.println(cb.getDeclaringClass().getName() + "#" + cb.getName() + cb.getSignature());

            CodeIterator i = code.iterator();
            while (i.hasNext()) {
                int pos = i.next();
                int opecode = i.byteAt(pos);
                int dynIndex = -1;
                if (opecode == Opcode.INVOKEDYNAMIC) {
                    int index = i.u16bitAt(pos + 1);
                    int bootstrap = pool.getInvokeDynamicBootstrap(index);
                    BootstrapMethod bm = bsms[bootstrap];

                    for (int arg : bm.arguments) {
                        int tag = pool.getTag(arg);
                        if (tag == ConstPool.CONST_MethodHandle) {
                            switch (pool.getMethodHandleKind(arg)) {
                                case ConstPool.REF_invokeVirtual:
                                    opecode = Opcode.INVOKEVIRTUAL;
                                    dynIndex = pool.getMethodHandleIndex(arg);
                                    break;
                                case ConstPool.REF_invokeSpecial:
                                    opecode = Opcode.INVOKESPECIAL;
                                    dynIndex = pool.getMethodHandleIndex(arg);
                                    break;
                                case ConstPool.REF_invokeStatic:
                                    opecode = Opcode.INVOKESTATIC;
                                    dynIndex = pool.getMethodHandleIndex(arg);
                                    break;
                                case ConstPool.REF_invokeInterface:
                                    opecode = Opcode.INVOKEINTERFACE;
                                    dynIndex = pool.getMethodHandleIndex(arg);
                                    break;
                            }
                            break;
                        }
                    }
                }

                switch (opecode) {
                case Opcode.INVOKEVIRTUAL:
                case Opcode.INVOKESPECIAL:
                case Opcode.INVOKESTATIC:
                case Opcode.INVOKEINTERFACE:
                {
                    int index = dynIndex == -1 ? i.u16bitAt(pos + 1) : dynIndex;
                    String className;
                    if (opecode == Opcode.INVOKEINTERFACE) {
                        className = pool.getInterfaceMethodrefClassName(index);
                    } else {
                        className = pool.getMethodrefClassName(index);
                    }

                    String methodName;
                    if (opecode == Opcode.INVOKEINTERFACE) {
                        methodName = pool.getInterfaceMethodrefName(index);
                    } else {
                        methodName = pool.getMethodrefName(index);
                    }

                    String desc;
                    if (opecode == Opcode.INVOKEINTERFACE) {
                        desc = pool.getInterfaceMethodrefType(index);
                    } else {
                        desc = pool.getMethodrefType(index);
                    }

                    String ret = null;
                    List<String> params = new ArrayList<String>();
                    if (desc.charAt(0) == '(') {
                        int start = 1;
                        boolean inClass = false;
                        for (int j = start; j < desc.length(); j++) {
                            char c = desc.charAt(j);
                            if (inClass) {
                                if (c == ';') {
                                    params.add(Descriptor.toClassName(desc.substring(start, j + 1)));
                                    start = j + 1;
                                    inClass = false;
                                }
                            } else if (c == ')') {
                                ret = Descriptor.toClassName(desc.substring(j + 1));
                                break;
                            } else if (c == 'L') {
                                inClass = true;
                            } else if (c != '[') {
                                params.add(Descriptor.toClassName(desc.substring(start, j + 1)));
                                start = j + 1;
                            }
                        }
                    } else {
                        ret = Descriptor.toClassName(desc);
                    }

                    if (!className.startsWith("java.") && !"<init>".equals(methodName)) {
                        System.out.print("\tCALL:");
                        System.out.print(className);
                        System.out.print('#');
                        System.out.print(methodName);
                        System.out.println(desc);
                    }
                    break;
                }
            }
        }
    }
}

