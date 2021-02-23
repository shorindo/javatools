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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Scriptable;

import javassist.CannotCompileException;
import javassist.ClassPool;
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
import javassist.bytecode.MethodInfo;
import javassist.bytecode.Opcode;

/**
 * UNDER CONSTRUCTION
 */
public class CallGraph {
    private static Logger LOG = Logger.getLogger(CallGraph.class);
    private ClassPool cp;
    private Set<String> pathSet;
    private Set<String> includeSet;
    private Set<String> excludeSet;
    private PrintWriter nodeWriter;
    private PrintWriter edgeWriter;

    public static void main(String[] args) {
        try {
            CallGraph graph = new CallGraph();
            graph.addPath("C:\\Users\\kazm\\git\\shorindocs\\shorindocs-web\\target\\docs\\WEB-INF\\classes"); 
            graph.addPath("C:\\Users\\kazm\\git\\shorindocs\\shorindocs-web\\target\\docs\\WEB-INF\\lib\\*");
            graph.addInclude("com.shorindo.");
            //graph.addInclude("java.lang.");
            //graph.addInclude("java.util.function.");
            //graph.addExclude("java.");
            //graph.addExclude("org.");
            graph.setNodeFile("target/nodes.csv");
            graph.setEdgeFile("target/edges.csv");
            graph.create();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public CallGraph() {
        cp = ClassPool.getDefault();
        pathSet = new LinkedHashSet<>();
        includeSet = new LinkedHashSet<>();
        excludeSet = new LinkedHashSet<>();
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

    private void create() throws Exception {
        //LOG.info("create(" + paths + ")");
        List<File> fileList = new ArrayList<>();
        for (String path : pathSet) {
            fileList.addAll(addClassPath(path));
        }
        for (File file : fileList) {
            walkFile(file);
        }
        nodeWriter.close();
        edgeWriter.close();
    }

    private List<File> addClassPath(String fileName) throws NotFoundException {
        List<File> fileList = new ArrayList<>();
        if (fileName.endsWith("*")) {
            File file = new File(fileName.substring(0, fileName.length() - 2));
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    if (child.getName().endsWith(".jar")) {
                        LOG.info("addClassPath(" + child.getAbsolutePath() + ")");
                        cp.appendClassPath(child.getAbsolutePath());
                        fileList.add(child);
                    }
                }
            } else {
                throw new NotFoundException(file.getName() + " is not directory.");
            }
        } else {
            LOG.info("addClassPath(" + fileName + ")");
            cp.appendClassPath(fileName);
            fileList.addAll(findClass(new File(fileName)));
        }
        return fileList;
    }

    private List<File> findClass(File file) {
        List<File> fileList = new ArrayList<>();
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                fileList.addAll(findClass(child));
            }
        } else if (file.getName().endsWith(".class")) {
            fileList.add(file);
        }
        return fileList;
    }

    public void addPath(String path) {
        pathSet.add(path);
    }

    public void addInclude(String pattern) {
        includeSet.add(pattern);
    }

    public void addExclude(String pattern) {
        excludeSet.add(pattern);
    }

    public void setNodeFile(String fileName) throws IOException {
        nodeWriter = new PrintWriter(new FileWriter(fileName));
        nodeWriter.println(":ID,shortName,longName,className,methodName");
    }

    public void setEdgeFile(String fileName) throws IOException {
        edgeWriter = new PrintWriter(new FileWriter(fileName));
        edgeWriter.println(":START_ID,:END_ID,:TYPE");
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
        //cp.appendClassPath(file.getAbsolutePath());
        try (JarFile jarFile = new JarFile(file)) {
            //cp.appendClassPath(file.getAbsolutePath());
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String clazzName = path2clazz(entry.getName());
                    if (!filter(clazzName)) {
                        continue;
                    }
                    //LOG.info("walkJar:class=" + clazzName);
                    CtClass cc = cp.get(clazzName);
                    walkClass(cc);
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

    protected void walkClass(File classFile) throws FileNotFoundException, IOException, NotFoundException, CannotCompileException, BadBytecode {
        //LOG.info(classFile.getName());
        try (DataInputStream is = new DataInputStream(new FileInputStream(classFile))) {
            ClassFile cf = new ClassFile(is);
            CtClass cc = cp.get(cf.getName());
            if (!filter(cc.getName())) {
                return;
            }
            walkClass(cc);
        }
    }

    protected void walkClass(CtClass cc) throws CannotCompileException, NotFoundException, BadBytecode {
        BootstrapMethod[] bsms = null;
        for (AttributeInfo attr : cc.getClassFile().getAttributes()) {
            if (attr instanceof BootstrapMethodsAttribute) {
                BootstrapMethodsAttribute bsma = (BootstrapMethodsAttribute)attr;
                bsms = bsma.getMethods();
            }
        }

        Map<String,CtMethod> methodMap = findMethods(cc);
        for (Entry<String,CtMethod> entry : methodMap.entrySet()) {
            try {
                printNode(cc.getName() + "#" + entry.getKey());
                findCall(bsms, entry.getValue());
            } catch (Exception e) {
                LOG.error("walkClass:" + cc.getName() + "#" + entry.getKey());
                e.printStackTrace();
            }
        }
    }

    protected Map<String,CtMethod> findMethods(CtClass cc) throws CannotCompileException, NotFoundException, BadBytecode {
        Map<String,CtMethod> methodMap = new TreeMap<>();

        if (!filter(cc.getName())) {
            return methodMap;
        }

        //String className = (cc.isInterface() ? "interface " : "class ") + cc.getName();
        //Set<String> methodSet = new TreeSet<>();
        for (CtMethod method : cc.getDeclaredMethods()) {
            String methodName = method.getName() + method.getSignature();
            methodMap.put(methodName, method);
        }

        for (CtClass itfc : cc.getInterfaces()) {
            for (String methodName : walkInterface(itfc)) {
                String callerName = itfc.getName() + "#" + methodName;
                String calleeName = cc.getName() + "#" + methodName;
                printEdge(callerName, calleeName, RelationType.IMPLEMENT);
            }
        }

        for (String methodName : walkSuper(cc.getSuperclass())) {
            //LOG.info(className + "#" + methodName);
            //methodSet.add(className + "#" + methodName);
            String name = methodName.replaceAll("(.*?)\\(.*$", "$1");
            String desc = methodName.replaceAll(".*?(\\(.*)$", "$1");
            try {
                CtMethod method = cc.getMethod(name, desc);
                methodMap.put(methodName, method);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return methodMap;
    }

    private Set<String> walkSuper(CtClass superClass) throws CannotCompileException, NotFoundException {
        Set<String> methodSet = new TreeSet<>();
        if (!superClass.isPrimitive()
                && !superClass.getName().equals("java.lang.Object")
                && !superClass.getName().equals("java.lang.Class")) {
            BootstrapMethod[] bsms = null;
            for (AttributeInfo attr : superClass.getClassFile().getAttributes()) {
                if (attr instanceof BootstrapMethodsAttribute) {
                    BootstrapMethodsAttribute bsma = (BootstrapMethodsAttribute)attr;
                    bsms = bsma.getMethods();
                }
            }

            for (CtMethod method : superClass.getDeclaredMethods()) {
                if (!Modifier.isPrivate(method.getModifiers())) {
                    String methodName = method.getName() + method.getSignature();
                    methodSet.add(methodName);
                    try {
                        printNode(method.getDeclaringClass() + "#" + method.getLongName() + method.getSignature());
                        findCall(bsms, method);
                    } catch (BadBytecode e) {
                        LOG.error(e.getMessage(), e);
                    }
                }
            }
            methodSet.addAll(walkSuper(superClass.getSuperclass()));
        }
        return methodSet;
    }

    private void printNode(String methodName) {
        //LOG.info("printNode(" + methodName + ")");
        String shortName = methodName.replaceAll("^.*?\\.([^\\.]+#[^\\.\\(]+).*$", "$1");
        String longName = methodName;
        String className = methodName.replaceAll("^(.*?)#.*$", "$1");
        String mName = methodName.replaceAll("^.*?#([^\\.\\(]+).*$", "$1");
        nodeWriter.println(hash(methodName) + "," + shortName + "," + longName + "," + className + "," + mName);
    }

    private void printEdge(String callerName, String calleeName, RelationType type) {
        //LOG.info("printEdge(" + type.name() + ":" + callerName + " -> " + calleeName + ")");
        String callerKey = hash(callerName);
        String calleeKey = hash(calleeName);
        edgeWriter.println(callerKey + "," + calleeKey + "," + type.name());
    }

    private static String hash(String s) throws RuntimeException {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            StringBuffer sb = new StringBuffer();
            for (byte b : md5.digest(s.getBytes("UTF-8"))) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
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

    /**
     * 参考：https://hidekatsu-izuno.hatenablog.com/entry/2020/06/08/235553
     */
    private Map<String,CallData> findCall(BootstrapMethod[] bsms, CtMethod cm) throws BadBytecode {
        Map<String,CallData> callMap = new LinkedHashMap<>();
        MethodInfo info = cm.getMethodInfo2();
        ConstPool pool = info.getConstPool();
        CodeAttribute code = info.getCodeAttribute();

        if (code == null) {
            return callMap;
        }

        String callerName = cm.getDeclaringClass().getName() + "#" + cm.getName() + cm.getSignature();
        Set<String> calleeSet = new LinkedHashSet<>();
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

                if (!className.startsWith("java.") && !"<init>".equals(methodName)) {
                    String calleeName = className + "#" + methodName + desc;
                    if (!calleeSet.contains(calleeName)) {
                        calleeSet.add(calleeName);
                    }
                }
                break;
            }
        }

        for (String calleeName : calleeSet) {
            printEdge(callerName, calleeName, RelationType.CALL);
            CallData call = new CallData(callerName, calleeName, RelationType.CALL);
            callMap.put(call.getKey(), call);
        }

        return callMap;
    }

    public static class GraphData {
        private Map<String,CtMethod> methodSet;
        private Map<String,CallData> callSet;

        public GraphData() {
            methodSet = new LinkedHashMap<>();
            callSet = new LinkedHashMap<>();
        }

        public Map<String, CtMethod> getMethodSet() {
            return methodSet;
        }

        public void addMethod(CtMethod method) {
            String key = hash(method.getDeclaringClass().getName() + "#" + method.getLongName() + method.getSignature());
            methodSet.put(key, method);
        }

        public Map<String, CallData> getCallSet() {
            return callSet;
        }

        public void addCall(Map<String, CallData> callSet) {
            this.callSet = callSet;
        }
    }

    public static class CallData {
        private String callerName;
        private String calleeName;
        private RelationType type;
        private String key;

        public CallData(String callerName, String calleeName, RelationType type) {
            this.callerName = callerName;
            this.calleeName = calleeName;
            this.type = type;
            this.key = hash(hash(callerName) + hash(calleeName) + hash(type.name()));
        }

        public String getKey() {
            return key;
        }

        public String getCallerName() {
            return callerName;
        }

        public String getCalleeName() {
            return calleeName;
        }

        public RelationType getType() {
            return type;
        }
    }

    public enum RelationType {
        CALL, IMPLEMENT, INHERIT, OVERRIDE;
    }
}

