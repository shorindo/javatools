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
package com.shorindo.javatools;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;

/**
 * 
 */
public class CallGraph {

    public static void main(String[] args) {
        for (Entry entry : System.getProperties().entrySet()) {
            System.out.println(entry.getKey() + " = " + entry.getValue());
        }
        
        CallGraph graph = new CallGraph();
        //graph.create(args);
    }

    private CallGraph() {
    }

    private void create(String[] paths) {
        for (String path : paths) {
            walkFile(new File(path));
        }
    }

    private void walkFile(File file) {
        String name = file.getName().toLowerCase();
        if (file.isDirectory()) {
            for (File child : file.listFiles()) {
                walkFile(child);
            }
        } else if (name.endsWith(".class")) {
            walkClass(file);
        } else if (name.endsWith(".jar")) {
            walkJar(file);
        } else if (name.endsWith(".java")) {
            
        }
    }

    private void walkJar(File file) {
        try (JarFile jarFile = new JarFile(file)) {
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
                JarEntry entry = e.nextElement();
                if (entry.getName().endsWith(".class")) {
                    try {
                        ClassParser parser = new ClassParser(file.getAbsolutePath(), entry.getName());
                        JavaClass javaClass = parser.parse();
                        System.out.println("class " + javaClass.getClassName());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void walkClass(File classFile) {
        try {
            ClassParser parser = new ClassParser(classFile.getAbsolutePath());
            JavaClass javaClass = parser.parse();
            javaClass.getAccessFlags();
            javaClass.getAttributes();
            javaClass.getClassName();
            javaClass.getInterfaceNames();
            javaClass.getMethods();
            javaClass.getModifiers();
            javaClass.getSuperclassName();
            System.out.println("class " + javaClass.getClassName());
            for (Method method : javaClass.getMethods()) {
                for (AnnotationEntry ae : method.getAnnotationEntries()) {
                    System.out.println(ae.getAnnotationType());
                }
            }
        } catch (IOException e) {
            
        }
    }
}
