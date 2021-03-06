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

import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.regex.Pattern;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Reader;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;
import javassist.NotFoundException;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.NativeArray;
import org.mozilla.javascript.NativeObject;
import org.mozilla.javascript.ScriptableObject;

/**
 * 
 */
public class Tracer {
    private static LoaderClassPath classPath;
    private static Settings settings = new Settings();

    /**
     * Options:
     *   <config file>
     */
    public static void premain(String agentArgs, Instrumentation instrumentation) {
        CtClass.main(null);
        System.out.println("premain:" + Tracer.class.getName());
        
        try (InputStream is = new FileInputStream(agentArgs)) {
            Context ctx = Context.enter();
            ScriptableObject scope = ctx.initStandardObjects();
            scope.put("settings", scope, settings);
            Reader reader = new InputStreamReader(is, "UTF-8");
            ctx.evaluateReader(scope, reader, agentArgs, 1, null);
            settings = Settings.load(agentArgs);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ClassPool clPool = ClassPool.getDefault();

        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String className,
                    Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
                    byte[] classfileBuffer) throws IllegalClassFormatException {

                String cName = className.replaceAll("/", ".");
                if (!isTarget(cName)) {
                    return null;
                }

                try (InputStream is = new ByteArrayInputStream(classfileBuffer)) {
                    // Tomcatなど複数のクラスローダを使う場合のコンテキスクラスパスを追加する
                    if (classPath == null) {
                        classPath = new LoaderClassPath(Thread.currentThread().getContextClassLoader());
                        clPool.appendClassPath(classPath);
                    }

                    CtClass cc = clPool.makeClass(is);
                    if (cc.isEnum()) return null;
                    if (cc.isInterface()) return null;

                    // メソッドをrenameして新しいメソッドから呼び出す。
                    // →アノテーションがうまくハンドリングできないので
                    //   元のメソッドの中身を入れ替えてコピーしたのを呼び出すようにする。
                    for (CtMethod origMethod : cc.getDeclaredMethods()) {
                        String origName = origMethod.getName();
                        String newName = "_tools_tracer_" + origName;
                        CtMethod newMethod = CtNewMethod.copy(origMethod, newName, cc, null);
                        newMethod.setModifiers(Modifier.PRIVATE);

                        cc.addMethod(newMethod);
                        StringBuffer body = new StringBuffer();
                        body.append("{");
                        body.append("com.shorindo.tools.Tracer.Profile profile = com.shorindo.tools.Tracer.profileIn(\"" + origMethod.getLongName() + "\", $args);");
                        body.append("try {");
                        if (!"void".equals(newMethod.getReturnType().getName())) {
                            body.append("return ");
                        }
                        body.append(newName + "($$);\n");
                        body.append("} finally {");
                        body.append("    com.shorindo.tools.Tracer.profileOut(profile);");
                        body.append("}}");
                        origMethod.setBody(body.toString());
                    }

                    return cc.toBytecode();
                } catch (IOException | RuntimeException e) {
                    e.printStackTrace();
                } catch (CannotCompileException e) {
                    e.printStackTrace();
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }
                return null;
            }
            
            private final String OWN_NAME = Tracer.class.getName();
            private boolean isTarget(String className) {
                //System.err.println("isTarget(" + className + ")");
                if (className.equals(OWN_NAME)) {
                    return false;
                }

                for (Pattern p : settings.getExcludes()) {
                    if (p.matcher(className).matches()) {
                        return false;
                    }
                }

                for (Pattern p : settings.getIncludes()) {
                    if (p.matcher(className).matches()) {
                        return true;
                    }
                }
                
                return false;
            }

        });

        if (settings.isPrintOnExit()) {
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    try {
                        if ("text".equals(settings.getFormat())) {
                            printText();
                        } else if ("html".equals(settings.getFormat())) {
                            printHtml();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private static Map<Thread, Stack<Profile>> stackMap = new HashMap<>();
    private static Map<Thread, List<Profile>> profileMap = new LinkedHashMap<>();

    public static Profile profileIn(String name, Object[] args) {
        //System.err.println(name);
        /*
         * メソッドの引数を文字列化して入れ替える。
         * ただし、Proxyの継承クラスのインスタンスは無限の再帰呼び出しが起こるので[proxy]と表現する。
         */
        if (args.length > 0) {
            StringBuilder sb = new StringBuilder();
            String sep = "";
            for (int i = 0; i < args.length; i++) {
                if (args[i] == null) {
                    sb.append(sep + "null");
                } else if (Proxy.class.isAssignableFrom(args[i].getClass())) {
                    sb.append(sep + "[proxy]");
                } else {
                    String arg = args[i].toString();
                    arg = arg.length() > 8 ? arg.substring(0, 8) + ".." : arg.toString();
                    sb.append(sep + arg);
                }
                sep = ", ";
            }
            name = name.replaceAll("\\(.*\\)", "(" + sb.toString().replaceAll("\\$", "\\\\\\$") + ")");
        }
        Stack<Profile> stack = stackMap.get(Thread.currentThread());
        if (stack == null) {
            stack = new Stack<Profile>();
            stackMap.put(Thread.currentThread(), stack);
        }
        List<Profile> profileList = profileMap.get(Thread.currentThread());
        if (profileList == null) {
            profileList = new ArrayList<Profile>();
            profileMap.put(Thread.currentThread(), profileList);
        }

        Profile profile = new Profile(name, stack.size());
        if (stack.size() == 0) {
            profileList.add(profile);
        } else {
            Profile parent = stack.peek();
            parent.addChild(profile);
        }
        stack.push(profile);
        return profile;
    }

    public static void profileOut(Profile profile) {
        //System.out.println("out:" + profile.getName());
        Stack<Profile> stack = stackMap.get(Thread.currentThread());
        if (profile == stack.peek()) {
            stack.pop();
            profile.setNanoTime(System.nanoTime() - profile.getNanoTime());
        }
    }

    public static void printText() throws FileNotFoundException {
        PrintStream ps = System.out;
        if (settings.getOutput() != null) {
            ps = new PrintStream(new FileOutputStream(settings.getOutput()));
        }
        for (Entry<Thread,List<Profile>> entry : profileMap.entrySet()) {
            ps.println("[" + entry.getKey().getName() + "]");
            for (Profile p : entry.getValue()) {
                _printText(ps, p);
            }
        }
    }

    private static void _printText(PrintStream ps, Profile profile) {
        ps.println(indent(profile.getLevel()) + profile.getName() + ":" + ((double)profile.getNanoTime() / 1000000.0) + " msec");
        for (Profile child : profile.getChildList()) {
            _printText(ps, child);
        }
    }
    

    public static void printHtml() throws Exception {
        PrintStream ps = System.out;
        if (settings.getOutput() != null) {
            ps = new PrintStream(new FileOutputStream(settings.getOutput()));
        }
        InputStream is = Tracer.class.getClassLoader().getResourceAsStream("com/shorindo/tools/tracer.html");
        Reader reader = new InputStreamReader(is, "UTF-8");
        char[] buff = new char[2048];
        int len = 0;
        StringBuilder tempBuilder = new StringBuilder();
        while ((len = reader.read(buff)) > 0) {
            tempBuilder.append(buff, 0, len);
        }
        is.close();

        String template = tempBuilder.toString();
        StringBuilder sb = new StringBuilder();
        sb.append("<div id=\"navigator\">");
        sb.append("<div class=\"title\">Thread</div>");
        for (Thread thread : profileMap.keySet()) {
            sb.append("<a href=\"javascript:showThread('" + thread.getName() + "')\">[" + thread.getName() + "]</a>");
        }
        sb.append("</div>");
        sb.append("<div id=\"content\">");
        for (Entry<Thread,List<Profile>> entry : profileMap.entrySet()) {
            sb.append("<div id=\"" + entry.getKey().getName() + "\" class=\"thread\" style=\"display:none;\">");
            for (Profile p : entry.getValue()) {
                sb.append(_printHtml(p));
            }
            sb.append("</div>");
        }
        sb.append("</div>");
        sb.append("</body>");
        sb.append("</html>");
        ps.print(template.replace("{{content}}", sb.toString()));
    }
    
    private static String _printHtml(Profile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"trace close\"><a class=\"icon\" onclick=\"toggle(event);\"></a>" + profile.getName() + ":" + ((double)profile.getNanoTime() / 1000000.0) + " msec");
        for (Profile child : profile.getChildList()) {
            sb.append(_printHtml(child));
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    public static String toSource(CtClass cc) {
        StringBuilder sb = new StringBuilder();
        try {
            for (Object annot : cc.getAnnotations()) {
                sb.append(annot.toString() + "\n");
            }
            sb.append("class " + cc.getName() + " {\n");
            for (CtMethod method : cc.getMethods()) {
                for (Object mannot : method.getAnnotations()) {
                    sb.append("    " + mannot + "\n");
                }
                sb.append("    " + method.getName() + "();\n");
            }
            sb.append("}");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }

    public static class Profile {
        private String name;
        private int level;
        private long nanoTime;
        private List<Profile> childList;

        public Profile(String name, int level) {
            this.name = name;
            this.level = level;
            this.nanoTime = System.nanoTime();
            this.childList = new ArrayList<>();
        }

        public String getName() {
            return name;
        }

        public int getLevel() {
            return level;
        }

        public long getNanoTime() {
            return nanoTime;
        }

        public void setNanoTime(long nanoTime) {
            this.nanoTime = nanoTime;
        }

        public void addChild(Profile child) {
            this.childList.add(child);
        }

        public List<Profile> getChildList() {
            return this.childList;
        }

        public String toString() {
            return name + ":" + nanoTime;
        }
    }
    
    public static class Settings {
        private boolean printOnExit = false;
        private String format;
        private String output;
        private List<Pattern> includes = new ArrayList<>();
        private List<Pattern> excludes = new ArrayList<>();

        public static Settings load(String fileName) {
            Settings settings = new Settings();
            Context ctx = Context.enter();
            ScriptableObject scope = ctx.initStandardObjects();
            try (InputStream is = new FileInputStream(fileName)) {
                Reader reader = new InputStreamReader(is, "UTF-8");
                ctx.evaluateReader(scope, reader, fileName, 1, null);
                settings = jsToBean(scope.get("settings"), Settings.class);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return settings;
        }

        @SuppressWarnings("unchecked")
        protected static <T>T jsToBean(Object jsObject, Class<T> clazz) {
            //System.out.println("__jsToBean(" + jsObject.getClass() + ", " + clazz + ")");
            if (jsObject instanceof Boolean) {
                return (T)jsObject;
            }
            if (jsObject instanceof Integer) {
                return (T)jsObject;
            }
            if (jsObject instanceof Double) {
                return (T)jsObject;
            }
            if (jsObject instanceof String) {
                return (T)jsObject;
            }
            if (jsObject instanceof NativeArray && clazz == List.class) {
                Class<?> compType = clazz.getComponentType();
                List<Object> list = new ArrayList<>();
                for (Object item : (NativeArray)jsObject) {
                    list.add(jsToBean(item, compType));
                }
                return (T)list;
            }
            if (jsObject instanceof NativeArray && clazz.isArray()) {
                // TODO
            }
            if (jsObject instanceof NativeObject && clazz == Map.class) {
                // TODO
            }
            if (jsObject instanceof NativeObject) {
                try {
                    T bean = clazz.newInstance();
                    for (Entry<Object,Object> entry : ((NativeObject)jsObject).entrySet()) {
                        String name = entry.getKey().toString();
                        String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
                        for (Method method : bean.getClass().getDeclaredMethods()) {
                            if (setterName.equals(method.getName())
                                    && method.getParameterCount() == 1) {
                                method.invoke(bean, jsToBean(entry.getValue(), method.getParameters()[0].getType()));
                                break;
                            }
                        }
                    }
                    return bean;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        public boolean isPrintOnExit() {
            return printOnExit;
        }

        public void setPrintOnExit(boolean printOnExit) {
            this.printOnExit = printOnExit;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }

        public String getOutput() {
            return output;
        }

        public void setOutput(String output) {
            this.output = output;
        }

        public List<Pattern> getIncludes() {
            return includes;
        }

        public void setIncludes(List<String> includes) {
            for (String include : includes) {
                this.includes.add(compile(include));
            }
        }

        public List<Pattern> getExcludes() {
            return excludes;
        }

        public void setExcludes(List<String> excludes) {
            for (String exclude : excludes) {
                this.excludes.add(compile(exclude));
            }
        }
        
        private Pattern compile(String s) {
            return Pattern.compile(
                    "^" + s.replaceAll("\\.", "\\\\.")
                    .replaceAll("\\$", "\\\\\\$")
                    .replaceAll("\\*", ".*") + "$");   
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("printOnExit = " + printOnExit + "\n");
            sb.append("foormat = " + format + "\n");
            sb.append("output = " + output + "\n");
            sb.append("includes = [\n");
            for (Pattern p : includes) {
                sb.append("    " + p.pattern() + "\n");
            }
            sb.append("]\n");
            sb.append("excludes = [\n");
            for (Pattern p : excludes) {
                sb.append("    " + p.pattern() + "\n");
            }
            sb.append("]\n");
            return sb.toString();
        }
    }

}
