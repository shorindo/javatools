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

import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 
 */
public class LoggerImpl extends Logger {
    private Class<?> clazz;
    private String className;
    
    protected LoggerImpl(Class<?> clazz) {
        this.clazz = clazz;
        className = clazz.getSimpleName();
    }

    private void log(Level level, String message, Object... params) {
        if (getLevel().getPriority() > level.getPriority()) {
            return;
        }
        String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date());
        if (message == null) {
            message = "";
        }
        if (params.length > 0) {
            MessageFormat format = new MessageFormat(message);
            System.out.println(now + " [" + level + "] " + className + " - " + format.format(params));
        } else {
            System.out.println(now + " [" + level + "] " + className + " - " + message);
        }
    }

    private void log(Level level, String message, Throwable th, Object... params) {
        log(level, message, params);
        th.printStackTrace(System.out);
    }

    @Override
    public void error(String message) {
        log(Level.ERROR, message);
    }

    @Override
    public void error(String message, Object... params) {
        log(Level.ERROR, message, params);
    }

    @Override
    public void error(String message, Throwable th) {
        log(Level.ERROR, message, th);
    }

    @Override
    public void error(String message, Throwable th, Object... params) {
        log(Level.ERROR, message, th, params);
    }

    @Override
    public void warn(String message) {
        log(Level.WARN, message);
    }

    @Override
    public void warn(String message, Object... params) {
        log(Level.WARN, message, params);
    }

    @Override
    public void warn(String message, Throwable th) {
        log(Level.WARN, message, th);
    }

    @Override
    public void warn(String message, Throwable th, Object... params) {
        log(Level.WARN, message, th, params);
    }

    @Override
    public void info(String message) {
        log(Level.INFO, message);
    }

    @Override
    public void info(String message, Object... params) {
        log(Level.INFO, message, params);
    }

    @Override
    public void info(String message, Throwable th) {
        log(Level.INFO, message, th);
    }

    @Override
    public void info(String message, Throwable th, Object... params) {
        log(Level.INFO, message, th, params);
    }

    @Override
    public void debug(String message) {
        log(Level.DEBUG, message);
    }

    @Override
    public void debug(String message, Object... params) {
        log(Level.DEBUG, message, params);
    }

    @Override
    public void debug(String message, Throwable th) {
        log(Level.DEBUG, message, th);
    }

    @Override
    public void debug(String message, Throwable th, Object... params) {
        log(Level.DEBUG, message, th, params);
    }

    @Override
    public void trace(String message) {
        log(Level.TRACE, message);
    }

    @Override
    public void trace(String message, Object... params) {
        log(Level.TRACE, message, params);
    }

    @Override
    public void trace(String message, Throwable th) {
        log(Level.TRACE, message, th);
    }

    @Override
    public void trace(String message, Throwable th, Object... params) {
        log(Level.TRACE, message, th, params);
    }

}
