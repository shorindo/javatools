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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ファイルシステムを走査し、ファイルを見つける
 */
public class FileFinder {
    private static final Logger LOG = Logger.getLogger(FileFinder.class);
    private FileVisitor visitor;

    /**
     * 
     * @param visitor
     */
    public FileFinder(FileVisitor visitor) {
        this.visitor = visitor;
    }

    /**
     * 
     * @param files
     * @return
     */
    public List<File> find(File[] files) {
        List<File> fileList = new ArrayList<File>();
        for (File file : files) {
            fileList.addAll(find(file));
        }
        sort(fileList);
        return fileList;
    }

    /**
     * 
     * @param file
     * @return
     */
    public List<File> find(File file) {
        List<File> fileList = new ArrayList<File>();
        OUT:if (file.isFile()) {
            if (!visitor.visit(file)) {
                break OUT;
            }
            fileList.add(file);
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            sort(files);
            fileList.addAll(find(files));
        } else {
            LOG.warn("unknown type : " + file);
        }
        sort(fileList);
        return fileList;
    }

    private static final int ORDER = 1;
    private void sort(File[] files) {
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return ORDER * o1.getAbsolutePath().compareTo(o2.getAbsolutePath());
            }
        });
    }

    private void sort(List<File> fileList) {
        Collections.sort(fileList, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                return ORDER * o1.getAbsolutePath().compareTo(o2.getAbsolutePath());
            }
        });
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
