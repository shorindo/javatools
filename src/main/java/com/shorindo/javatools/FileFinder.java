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
import java.util.ArrayList;
import java.util.List;

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
            fileList.addAll(find(file.listFiles()));
        } else {
            
        }
        return fileList;
    }
}
