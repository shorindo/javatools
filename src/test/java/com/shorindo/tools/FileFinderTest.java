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
package com.shorindo.tools;

import static org.junit.Assert.*;

import java.io.File;
import java.util.List;

import org.junit.Test;

import com.shorindo.tools.FileFinder;
import com.shorindo.tools.FileVisitor;
import com.shorindo.tools.ToolsLogger;

/**
 * 
 */
public class FileFinderTest {
    private static ToolsLogger LOG = ToolsLogger.getLogger(FileFinderTest.class);
    private static String PATH_NAME = FileFinderTest.class.getName()
            .replaceAll("\\.", "/") + ".class";

    @Test
    public void testThis() {
        FileFinder finder = new FileFinder(new FileVisitorImpl());
        List<File> fileList = finder.find(new File("."));
        assertTrue(fileList.get(0).toURI().toString().endsWith(PATH_NAME));
    }

//    @Test
//    public void testClassVisitor() {
//        FileFinder finder = new FileFinder();
//        finder.add(new ClassVisitor());
//        List<File> fileList = finder.walk(new File("."));
//        for (File file : fileList) {
//            LOG.info(file.getName());
//        }
//        assertTrue(fileList.size() > 0);
//    }
//
//    @Test
//    public void testJarVisitor() {
//        FileFinder finder = new FileFinder();
//        finder.add(new JarVisitor());
//        List<File> fileList = finder.walk(new File(".."));
//        for (File file : fileList) {
//            LOG.info(file.getName());
//        }
//        assertTrue(fileList.size() > 0);
//    }

    public static class FileVisitorImpl implements FileVisitor {

        @Override
        public boolean visit(File file) {
            if (file.toURI().toString().endsWith(PATH_NAME)) {
                System.out.println(file.toURI());
                return true;
            } else {
                return false;
            }
        }

    }
}
