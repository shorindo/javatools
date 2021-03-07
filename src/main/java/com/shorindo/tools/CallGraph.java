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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

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
    private Set<String> includeSet;
    private Set<String> excludeSet;
    private PrintWriter nodeWriter;
    private PrintWriter edgeWriter;

    public static void main(String[] args) {
        try {
            CallGraph graph = new CallGraph();
            graph.create(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public CallGraph() {
        cp = new ClassPool(false);
        includeSet = new LinkedHashSet<>();
        excludeSet = new LinkedHashSet<>();
    }

    protected void create(String[] args) throws Exception {
        //LOG.info("create(" + paths + ")");
        List<File> fileList = new ArrayList<>();
        Stack<String> stack = new Stack<>();

        for (int i = args.length - 1; i >= 0; i--) {
            String arg = args[i];
            switch (arg) {
            case "--classpath":
                fileList.addAll(addClassPath(stack.pop()));
                break;
            case "--includes":
                addInclude(stack.pop());
                break;
            case "--excludes":
                addExclude(stack.pop());
                break;
            case "--nodes":
                setNodeFile(stack.pop());
                break;
            case "--edges":
                setEdgeFile(stack.pop());
                break;
            default:
                stack.add(arg);
            }
        }

        // システムおよび自身のパスは最後にしないと重複するとおかしくなる
        cp.appendSystemPath();
        for (File file : fileList) {
            walkFile(file);
        }
        nodeWriter.close();
        edgeWriter.close();
    }

    protected List<File> addClassPath(String fileName) throws NotFoundException {
        List<File> fileList = new ArrayList<>();
        if (fileName.endsWith("*")) {
            File file = new File(fileName.substring(0, fileName.length() - 2));
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    if (child.getName().endsWith(".jar")) {
                        //LOG.info("addClassPath(" + child.getAbsolutePath() + ")");
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

    protected List<File> findClass(File file) {
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

    protected void addInclude(String pattern) {
        includeSet.add(pattern);
    }

    protected void addExclude(String pattern) {
        excludeSet.add(pattern);
    }

    protected void setNodeFile(String fileName) throws IOException {
        nodeWriter = new PrintWriter(new FileWriter(fileName));
        nodeWriter.println("key:ID,methodName,shortName,longName,className,modifier,label:LABEL");
    }

    protected void setEdgeFile(String fileName) throws IOException {
        edgeWriter = new PrintWriter(new FileWriter(fileName));
        edgeWriter.println(":START_ID,:END_ID,:TYPE,callerName,calleeName,order");
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

    private GraphData walkJar(File file) throws NotFoundException {
        LOG.info("walkJar(" + file.getName() + ")");
        GraphData graphJar = new GraphData();
        try (JarFile jarFile = new JarFile(file)) {
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String clazzName = path2clazz(entry.getName());
                    if (!filter(clazzName)) {
                        continue;
                    }
                    CtClass cc = cp.get(clazzName);
                    GraphData graph = analyzeClass(cc);
                    for (MethodData method : graph.getMethodList()) {
                        //printNode(method.getFullName());
                        printNode(method);
                    }
                    for (CallData call : graph.getCallList().values()) {
                        printEdge(call);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return graphJar;
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
        LOG.info("walkClass(" + classFile.getName() + ")");
        try (DataInputStream is = new DataInputStream(new FileInputStream(classFile))) {
            ClassFile cf = new ClassFile(is);
            CtClass cc = cp.get(cf.getName());
            if (!filter(cc.getName())) {
                return;
            }
            //walkClass(cc);
            GraphData graph = analyzeClass(cc);
            for (MethodData method : graph.getMethodList()) {
                //LOG.info("walkClass -> " + method.getShortName());
                //printNode(method.getFullName());
                printNode(method);
            }
            for (CallData call : graph.getCallList().values()) {
                printEdge(call);
            }
        }
    }

    protected GraphData analyzeClass(CtClass cc) throws NotFoundException {
        GraphData graph = new GraphData();

        // 対象でないクラスを除外
        if (cc.getName().equals("java.lang.Object")
                || cc.getName().equals("java.lang.Class")) {
            return graph;
        }

        //LOG.info("analyzeClass(" + cc.getName() + ")");
        BootstrapMethod[] bsms = null;
        for (AttributeInfo attr : cc.getClassFile().getAttributes()) {
            if (attr instanceof BootstrapMethodsAttribute) {
                BootstrapMethodsAttribute bsma = (BootstrapMethodsAttribute)attr;
                bsms = bsma.getMethods();
            }
        }

        // このクラスで定義されたメソッドの解析
        for (CtMethod method : cc.getDeclaredMethods()) {
            try {
                String callerName = getMethodName(method); 
                graph.addMethod(method.getModifiers(), callerName);
                Map<String, CallData> callMap = findCall(bsms, method);
                int count = 0;
                for (Entry<String,CallData> entry : callMap.entrySet()) {
                    String calleeName = entry.getValue().getCalleeName();
                    if (!filter(calleeName)) {
                        continue;
                    }
                    graph.addCall(callerName, calleeName, RelationType.CALL, ++count);
                }
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }

        // スーパークラスのメソッド継承
        try {
            CtClass superClass = cc.getSuperclass();
            GraphData superData = analyzeClass(superClass);
            for (MethodData methodData : superData.getMethodList()) {
                // このクラスで上書きされたメソッドは継承されない
                Optional<MethodData> optMethod = graph.getMethodList()
                        .stream()
                        .filter(e -> {
                            String thisName = methodData.getFullName().replaceAll("^.*?#(.*)$", "$1");
                            String thatName = e.getFullName().replaceAll("^.*?#(.*)$", "$1");
                            return thisName.equals(thatName);
                        })
                        .findFirst();
                if (optMethod.isPresent()) {
                    if (Modifier.isAbstract(methodData.getModifiers())) {
                        String callerName = methodData.getFullName();
                        String calleeName = cc.getName() + "#" + methodData.getFullName().replaceAll("^.*?#(.*)$", "$1");
                        if (filter(methodData.getClassName())) {
                            graph.addCall(callerName, calleeName, RelationType.IMPLEMENT, 1);
                        }
                    }
                } else if (!methodData.isPrivate()) {
                    String callerName = cc.getName() + "#" + methodData.getFullName().replaceAll("^.*?#(.*)$", "$1");
                    String calleeName = methodData.getFullName();
                    graph.addMethod(methodData.getModifiers(), callerName);
                    if (filter(methodData.getClassName())) {
                        graph.addCall(callerName, calleeName, RelationType.INHERIT, 1);
                    }
                }
            }
        } catch (NotFoundException e) {
            LOG.error(e.getMessage(), e);
        }

        // インターフェースとの関連付け
        for (CtClass itfc : cc.getInterfaces()) {
            GraphData itfcGraph = analyzeClass(itfc);
            for (MethodData itfcMethod : itfcGraph.getMethodList()) {
                if (!filter(itfcMethod.getClassName())) {
                    continue;
                }
                String callerName = itfcMethod.getFullName();
                String calleeName = cc.getName() + "#" + itfcMethod.getFullName().replaceAll("^.*?#(.*)$", "$1");
                boolean b = graph.getMethodList()
                    .stream()
                    .filter(m -> {
                        //LOG.info(m + " -> " + calleeName);
                        return m.equals(calleeName);
                    })
                    .findFirst()
                    .isPresent();
                if (!b) {
                    graph.addMethod(itfcMethod.getModifiers(), calleeName);
                }
                if (filter(itfcMethod.getClassName())) {
                    graph.addCall(callerName, calleeName, RelationType.IMPLEMENT, 1);
                }
            }
        }

        return graph;
    }

    private String getMethodName(CtMethod method) {
        return method.getDeclaringClass().getName() + "#" + method.getName() + method.getSignature();
    }

    private void printNode(MethodData method) {
        // key:ID,methodName,shortName,longName,className,modifier,label:LABEL
        nodeWriter.println(
                method.getHash()
                + "," + method.getMethodName()
                + "," + method.getShortName()
                + "," + method.getFullName()
                + "," + method.getClassName()
                + "," + method.getModifiers()
                + "," + method.getShortName());
    }

//    private void printNode(String methodName) {
//        //LOG.info("printNode(" + methodName + ")");
//        String shortName = methodName.replaceAll("^.*?\\.([^\\.]+#[^\\.\\(]+).*$", "$1");
//        String longName = methodName;
//        String className = methodName.replaceAll("^(.*?)#.*$", "$1");
//        String mName = methodName.replaceAll("^.*?#([^\\.\\(]+).*$", "$1");
//        nodeWriter.println(
//                HASH(methodName) + 
//                "," + mName + 
//                "," + shortName + 
//                "," + longName + 
//                "," + className + 
//                "," + shortName);
//    }

    private void printEdge(CallData callData) {
        //LOG.info("printEdge(" + type.name() + ":" + callerName + " -> " + calleeName + ")");
        String callerKey = HASH(callData.getCallerName());
        String calleeKey = HASH(callData.getCalleeName());
        edgeWriter.println(callerKey +
                    "," + calleeKey +
                    "," + callData.getType().name() +
                    "," + SHORTEN(callData.getCallerName()) +
                    "," + SHORTEN(callData.getCalleeName()) +
                    "," + callData.getOrder());
    }

    private static String HASH(String s) throws RuntimeException {
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
     * @throws NotFoundException 
     */
    private Map<String,CallData> findCall(BootstrapMethod[] bsms, CtMethod cm) throws BadBytecode, NotFoundException {
        Map<String,CallData> callMap = new LinkedHashMap<>();
        MethodInfo info = cm.getMethodInfo2();
        ConstPool pool = info.getConstPool();
        CodeAttribute code = info.getCodeAttribute();

        if (code == null) {
            return callMap;
        }

        //LOG.info("CALLER:" + cm.getName());
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
            case Opcode.NEW:
                {
                    int index = i.u16bitAt(pos + 1);
                    String className = pool.getClassInfo(index);
                    if (filter(className)) { 
                        LOG.info("NEW " + className);
                    }
                }
                break;

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

                    if (filter(className) && "<init>".equals(methodName)) {
                        String calleeName = className + "#" + methodName + desc;
                        LOG.info("new " + calleeName);
                    }
                    if (filter(className) && !"<init>".equals(methodName)) {
                        String calleeName = className + "#" + methodName + desc;
                        calleeSet.add(calleeName);
                    }
                }
                break;
            }
        }

        int count = 0;
        for (String calleeName : calleeSet) {
            //LOG.info("\tCALL:" + calleeName);
            //printEdge(callerName, calleeName, RelationType.CALL);
            CallData call = new CallData(callerName, calleeName, RelationType.CALL, ++count);
            callMap.put(call.getHash(), call);
        }

        return callMap;
    }

    private static String SHORTEN(String methodName) {
        return methodName.replaceAll("^.*?([^\\.]+#[^\\(]+).*$", "$1");
    }

    public static class GraphData {
        private Map<String,MethodData> methodMap;
        private Map<String,CallData> callSet;

        public GraphData() {
            methodMap = new LinkedHashMap<>();
            callSet = new LinkedHashMap<>();
        }

        public List<MethodData> getMethodList() {
            return new ArrayList<>(methodMap.values());
        }

        public void addMethod(CtMethod method) {
            addMethod(method.getModifiers(), method.getDeclaringClass().getName() + "#" + method.getName() + method.getSignature());
        }

        public void addMethod(int modifiers, String methodName) {
            MethodData methodData = new MethodData(modifiers, methodName);
            methodMap.put(methodData.getHash(), methodData);
        }

        public Map<String, CallData> getCallList() {
            return callSet;
        }

        public void addCall(String callerName, String calleeName, RelationType type, int order) {
            CallData callData = new CallData(callerName, calleeName, type, order);
            callSet.put(callData.getHash(), callData);
        }

    }

    public static class MethodData {
        private int modifiers;
        private String fullName;

        public MethodData(int modifiers, String fullName) {
            this.modifiers = modifiers;
            this.fullName = fullName;
        }
        public String getFullName() {
            return fullName;
        }
        public String getShortName() {
            return fullName.replaceAll("^.*?([^\\.]+#[^\\(]+).*$", "$1");
        }
        public String getClassName() {
            return fullName.replaceAll("^([^#]+).*$", "$1");
        }
        public String getMethodName() {
            return fullName.replaceAll("^[^#]+#([^\\(]+).*$", "$1");
        }
        public int getModifiers() {
            return modifiers;
        }
        public String getHash() {
            return HASH(fullName);
        }
        public boolean isPrivate() {
            return Modifier.isPrivate(modifiers);
        }
        public String toString() {
            return getFullName();
        }
    }

    public static class CallData {
        private String callerName;
        private String calleeName;
        private RelationType type;
        private int order;
        private String key;

        public CallData(String callerName, String calleeName, RelationType type, int order) {
            this.callerName = callerName;
            this.calleeName = calleeName;
            this.type = type;
            this.order = order;
            this.key = HASH(HASH(callerName) + HASH(calleeName) + HASH(type.name()));
        }

        public String getHash() {
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

        public int getOrder() {
            return order;
        }

        public String toString() {
            return type.name() +
                    "," +
                    SHORTEN(callerName) +
                    "," +
                    SHORTEN(calleeName) +
                    "," +
                    order;
        }
    }

    public enum RelationType {
        CALL, IMPLEMENT, INHERIT, OVERRIDE;
    }
}

