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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * 
 */
public class ToolsLogger {
    private Logger LOG;

    public synchronized static ToolsLogger getLogger(Class<?> clazz) {
        return new ToolsLogger(LogManager.getLogger(clazz));
    }

    private ToolsLogger(Logger logger) {
        LOG = logger;
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
        LOG.log(level.getLevel(), message);
    }

    public void log(Level level, String message, Throwable t) {
        LOG.log(level.getLevel(), message, t);

    }

    public static enum Level {
        TRACE(org.apache.logging.log4j.Level.TRACE),
        DEBUG(org.apache.logging.log4j.Level.DEBUG),
        INFO(org.apache.logging.log4j.Level.INFO),
        WARN(org.apache.logging.log4j.Level.WARN),
        ERROR(org.apache.logging.log4j.Level.ERROR);
        
        private org.apache.logging.log4j.Level log4jLevel;
        
        private Level(org.apache.logging.log4j.Level log4jLevel) {
            this.log4jLevel = log4jLevel;
        }

        public org.apache.logging.log4j.Level getLevel() {
            return log4jLevel;
        }
    }
}
