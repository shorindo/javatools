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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 
 */
public class FileFinder {
    private static final Logger LOG = Logger.getLogger(FileFinder.class);
    private FileVisitor visitor;

    public FileFinder(FileVisitor visitor) {
        this.visitor = visitor;
    }

    public List<File> find(File[] files) {
        List<File> fileList = new ArrayList<File>();
        for (File file : files) {
            fileList.addAll(find(file));
        }
        return fileList;
    }

    public List<File> find(File file) {
        List<File> fileList = new ArrayList<File>();
        OUT:if (file.isFile()) {
            if (!visitor.visit(file)) {
                break OUT;
            }
            fileList.add(file);
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            Arrays.sort(files);
            fileList.addAll(find(files));
        } else {
            LOG.warn("unknown type : " + file);
        }
        return fileList;
    }

    public static void main(String[] args) {
        Set<File> dirSet = new HashSet<File>();
        FileFinder finder = new FileFinder(new FileVisitor() {
            @Override
            public boolean visit(File file) {
                String name = file.getName();
                if (name.endsWith(".java")) {
                    File parent = file.getParentFile();
                    if (!dirSet.contains(parent)) {
                        LOG.debug(parent.getAbsolutePath() + ":");
                        dirSet.add(parent);
                    }
                    LOG.debug("\t" + name);
                    return true;
                } else {
                    return false;
                }
            }
        });
        try {
            finder.find(new File(".").getCanonicalFile());
        } catch (IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }
}
