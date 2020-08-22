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

import java.util.HashMap;
import java.util.Map;

/**
 * 
 */
public abstract class Logger {
    private static Map<Class<?>, Logger> loggerMap = new HashMap<>();
    private static Level level = Level.INFO;

    public static Logger getLogger(Class<?> clazz) {
        Logger logger = loggerMap.get(clazz);
        if (logger == null) {
            logger = new LoggerImpl(clazz);
            loggerMap.put(clazz, logger);
        }
        return logger;
    }
    
    public static void setLevel(Level level) {
        Logger.level = level;
    }
    public static Level getLevel() {
        return Logger.level;
    }
    public abstract void error(String message);
    public abstract void error(String message, Object...params);
    public abstract void error(String message, Throwable th);
    public abstract void error(String message, Throwable th, Object...params);
    public abstract void warn(String message);
    public abstract void warn(String message, Object...params);
    public abstract void warn(String message, Throwable th);
    public abstract void warn(String message, Throwable th, Object...params);
    public abstract void info(String message);
    public abstract void info(String message, Object...params);
    public abstract void info(String message, Throwable th);
    public abstract void info(String message, Throwable th, Object...params);
    public abstract void debug(String message);
    public abstract void debug(String message, Object...params);
    public abstract void debug(String message, Throwable th);
    public abstract void debug(String message, Throwable th, Object...params);
    public abstract void trace(String message);
    public abstract void trace(String message, Object...params);
    public abstract void trace(String message, Throwable th);
    public abstract void trace(String message, Throwable th, Object...params);
    
    public static enum Level {
        TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4);

        private int priority;

        private Level(int priority) {
            this.priority = priority;
        }
        public int getPriority() {
            return priority;
        }
    }
}
