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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 
 */
public class Logger {
    private static Map<Class<?>, Logger> loggerMap = new HashMap<>();
    private Class<?> clazz;
    private String clazzName;
    private Level level;

    public synchronized static Logger getLogger(Class<?> clazz) {
        Logger logger = loggerMap.get(clazz);
        if (logger == null) {
            logger = new Logger(clazz);
            loggerMap.put(clazz, logger);
        }
        return logger;
    }

    private Logger(Class<?> clazz) {
        this.clazz = clazz;
        this.clazzName = clazz.getSimpleName();
    }

    public void trace(String message) {
        log(Level.TRACE, message);
    }

    public void debug(String message) {
        log(Level.DEBUG, message);
    }
    
    public void info(String message) {
        info(message, null);
    }

    public void info(String message, Throwable t) {
        log(Level.INFO, message, t);
    }

    public void warn(String message) {
        warn(message, null);
    }

    public void warn(String message, Throwable t) {
        log(Level.WARN, message);
    }

    public void error(String message) {
        error(message, null);
    }

    public void error(String message, Throwable t) {
        log(Level.ERROR, message, t);
    }

    public void log(Level level, String message) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String now = format.format(new Date());
        System.out.println(now + " [" + level.name() + "] " + clazzName + " - "+ message);
    }

    public void log(Level level, String message, Throwable t) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String now = format.format(new Date());
        System.out.println(now + " [" + level.name() + "] " + clazzName + " - "+ message);
        if (t != null) t.printStackTrace();
    }

    public static enum Level {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR;
    }
}
