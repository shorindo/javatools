/*
 * Copyright 2019 Shorindo, Inc.
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.bcel.classfile.AnnotationEntry;
import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.ElementValuePair;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;

/**
 * 
 */
public class BeanAnalyzer {
    private static final Logger LOG = Logger.getLogger(BeanAnalyzer.class);
    private static final String BEAN_TYPE = "Lorg/springframework/context/annotation/Bean;";
    private Map<String, List<BeanDefinition>> typeMap = new TreeMap<>();
    private Map<String, List<BeanDefinition>> nameMap = new TreeMap<>();

    public BeanAnalyzer() {
    }

    public void analyze(File classFile) {

        try {
            ClassParser parser = new ClassParser(classFile.getAbsolutePath());
            JavaClass javaClass = parser.parse();
            for (Method method : javaClass.getMethods()) {
                for (AnnotationEntry entry : method.getAnnotationEntries()) {
                    if (BEAN_TYPE.equals(entry.getAnnotationType())) {
                        String name = method.getName();
                        Type type = method.getReturnType();
                        for (ElementValuePair pair : entry.getElementValuePairs()) {
                            if ("name".equals(pair.getNameString())) {
                                name = pair.getValue().toString()
                                        .replaceAll("^\\{(.*)\\}$", "$1");
                            }
                        }
                        LOG.debug("type=" + type + ", name=" + name);
                        BeanDefinition def = new BeanDefinition();
                        def.setLocation(classFile)
                            .setType(type)
                            .setName(name);

                        List<BeanDefinition> nameList = nameMap.get(type.toString());
                        if (nameList == null) {
                            nameList = new ArrayList<BeanDefinition>();
                            nameMap.put(type.toString(), nameList);
                        } else {
                            LOG.warn("duplicate:" + def);
                        }
                        nameList.add(def);

                        List<BeanDefinition> typeList = typeMap.get(name);
                        if (typeList == null) {
                            typeList = new ArrayList<BeanDefinition>();
                            typeMap.put(name, typeList);
                        } else {
                            LOG.warn("duplicate:" + def);
                        }
                        typeList.add(def);
                    }
                }
            }
        } catch (ClassFormatException e) {
            LOG.warn(e.getMessage(), e);
        } catch (IOException e) {
            LOG.warn(e.getMessage(), e);
        }
    }

    public void showErrors() {
        LOG.warn("DUPLICATED NAME:");
        for (Entry<String,List<BeanAnalyzer.BeanDefinition>> entry : nameMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (BeanDefinition def : entry.getValue()) {
                    LOG.warn(def.toString());
                }
            }
        }

        LOG.warn("DUPLICATED TYPE:");
        for (Entry<String,List<BeanAnalyzer.BeanDefinition>> entry : typeMap.entrySet()) {
            if (entry.getValue().size() > 1) {
                for (BeanDefinition def : entry.getValue()) {
                    LOG.warn(def.toString());
                }
            }
        }
    }

    public static class BeanDefinition {
        private File location;
        private Type type;
        private String name;

        public File getLocation() {
            return location;
        }
        public BeanDefinition setLocation(File location) {
            this.location = location;
            return this;
        }
        public Type getType() {
            return type;
        }
        public BeanDefinition setType(Type type) {
            this.type = type;
            return this;
        }
        public String getName() {
            return name;
        }
        public BeanDefinition setName(String name) {
            this.name = name;
            return this;
        }
        public String toString() {
            return type + "\t" + name + "\t" + location;
        }
    }

    public static void main(String[] args) {
        BeanAnalyzer analyzer = new BeanAnalyzer();
        FileFinder finder = new FileFinder(new FileVisitor() {
            @Override
            public boolean visit(File file) {
                if (file.getName().toLowerCase().endsWith(".class")) {
                    //LOG.debug(file.getName());
                    analyzer.analyze(file);
                    return true;
                } else {
                    return false;
                }
            }
        });
        finder.find(new File("."));
        analyzer.showErrors();
    }
}
