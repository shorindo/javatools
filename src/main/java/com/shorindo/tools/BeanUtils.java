/*
 * Copyright 2016 Shorindo, Inc.
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

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 
 */
public class BeanUtils {
    private static final Logger LOG = Logger.getLogger(BeanUtils.class);
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("([a-zA-Z])([a-z0-9]*)");
    private static final Pattern SNAKE_PATTERN = Pattern.compile("_*([^_])([^_]*)");
    private static final Pattern CAMEL_PATTERN = Pattern.compile("(.[^A-Z0-9]*)");
    private static final Pattern BEAN_PATTERN = Pattern.compile("\\.?([^\\.\\[\\s]+)(\\[([^\\]]+)\\])?");
    private static final String DOCS_9999 = "";
    private static final String DOCS_0004 = "";
    private static final String DOCS_5005 = "";
    private static final String DOCS_5007 = "";

    public static <T> T copy(Object source, Class<T> clazz) {
        T dest = null;
        try {
            dest = clazz.newInstance();
            for (Method setterMethod : clazz.getMethods()) {
                if (!setterMethod.getName().startsWith("set") ||
                        setterMethod.getParameterCount() != 1) {
                    continue;
                }
                String getterMethodName = "get" + setterMethod.getName().substring(3);
                try {
                    Method getterMethod = source.getClass().getMethod(getterMethodName);
                    if (getterMethod.getReturnType().isAssignableFrom(setterMethod.getParameterTypes()[0])) {
                        setterMethod.invoke(dest, getterMethod.invoke(source));
                    }
                } catch (Exception e) {
                    LOG.error(DOCS_9999, e.getMessage());
                }
            }
        } catch (InstantiationException e) {
            LOG.error(DOCS_9999, e);
        } catch (IllegalAccessException e) {
            LOG.error(DOCS_9999, e);
        }
        return dest;
    }

    /**
     * 
     * @param name
     * @return
     */
    public static String snake2camel(String name) {
        return snake2camel(name, true);
    }

    /**
     * 
     * @param name
     * @param upcaseFirst
     * @return
     */
    public static String snake2camel(String name, boolean upcaseFirst) {
        Matcher m = SNAKE_PATTERN.matcher(name);
        StringBuilder sb = new StringBuilder();
        int start = 0;
        boolean first = true;
        while (m.find(start)) {
            if (first) {
                sb.append(upcaseFirst ?
                        m.group(1).toUpperCase() :
                        m.group(1).toLowerCase());
                first = false;
            } else {
                sb.append(m.group(1).toUpperCase());
            }
            String rest = m.group(2);
            if (rest != null) {
                sb.append(rest.toLowerCase());
            }
            start = m.end();
        }
        return sb.toString();
    }

    public static String camel2snake(String name) {
        Matcher m = CAMEL_PATTERN.matcher(name);
        StringBuilder sb = new StringBuilder();
        int start = 0;
        boolean first = true;
        while (m.find(start)) {
            if (first) {
                sb.append(m.group(1).toUpperCase());
                first = false;
            } else {
                sb.append("_" + m.group(1).toUpperCase());
            }
            start = m.end();
        }
        return sb.toString();
    }

    /**
     * 
     * @param prefix
     * @param propertyName
     * @return
     */
    private static String createMethodName(String prefix, String propertyName) {
        String methodName = prefix;
        Matcher matcher = PROPERTY_PATTERN.matcher(propertyName);
        int start = 0;
        while (matcher.find(start)) {
            methodName +=
                    matcher.group(1).toUpperCase() +
                    matcher.group(2);
            start = matcher.end();
        }
        return methodName;
    }

    /**
     * 
     * @param bean
     * @param name
     * @param value
     * @throws BeanNotFoundException
     */
    public static void setValue(Object bean, String name, Object value) throws BeanNotFoundException {
        Matcher m = BEAN_PATTERN.matcher(name);
        int start = 0, end = 0;

        while (m.find(start)) {
            if (m.start() != start) {
                throw new BeanNotFoundException(DOCS_5007 + ":" + name);
            }

            // 最後の項目はset
            if (m.end() == name.length()) {
                if (m.group(3) == null) {
                    setProperty(bean, m.group(1), value);
                } else {
                    bean = getProperty(bean, m.group(1));
                    setProperty(bean, m.group(3), value);
                }
            } else {
                bean = getProperty(bean, m.group(1));
                if (bean == null && end != name.length()) {
                    throw new BeanNotFoundException(DOCS_5005 + ":" + name);
                }
                if (m.group(3) != null) {
                    bean = getProperty(bean, m.group(3));
                    if (bean == null && end != name.length()) {
                        throw new BeanNotFoundException(DOCS_5005 + ":" + name);
                    }
                }
            }
            start = end = m.end();
        }

        if (start != end) {
            throw new BeanNotFoundException(DOCS_5007 + ":" + name);
        }
    }

    /**
     * 
     * @param value
     * @return
     */
    private static boolean isNumeric(String value) {
        return value == null ?
                false :
                (value.matches("^\\d+$") ? true : false);
    }

    /**
     * 
     * @param bean
     * @param name
     * @return
     * @throws BeanNotFoundException
     */
    public static Object getValue(Object bean, String name) throws BeanNotFoundException {
        Matcher m = BEAN_PATTERN.matcher(name);
        int start = 0, end = 0;

        while (m.find(start)) {
            if (m.start() != start) {
                throw new BeanNotFoundException(DOCS_5007 + ":" + name);
            }

            bean = getProperty(bean, m.group(1));
            if (bean == null && end != name.length()) {
                throw new BeanNotFoundException(DOCS_5005 + ":" + name);
            }
            if (m.group(3) != null) {
                bean = getProperty(bean, m.group(3));
                if (bean == null && end != name.length()) {
                    throw new BeanNotFoundException(DOCS_5005 + ":" + name);
                }
            }
            start = end = m.end();
        }

        if (start != end) {
            throw new BeanNotFoundException(DOCS_5007 + ":" + name);
        }

        return bean;
    }

    /**
     * 
     * @param bean
     * @param name
     * @param defaultValue
     * @return
     */
    public static Object getValue(Object bean, String name, Object defaultValue) {
        try {
            return getValue(bean, name);
        } catch (BeanNotFoundException e) {
            LOG.info(DOCS_0004, name, defaultValue);
            return defaultValue;
        }
    }

    /**
     * 
     * @param bean
     * @param name
     * @return
     * @throws BeanNotFoundException
     */
    public static byte getValueAsByte(Object bean, String name) throws BeanNotFoundException {
        return ((Byte)getValue(bean, name)).byteValue();
    }

    /**
     * 
     * @param bean
     * @param name
     * @return
     * @throws BeanNotFoundException
     */
    public static short getValueAsShort(Object bean, String name) throws BeanNotFoundException {
        return ((Short)getValue(bean, name)).shortValue();
    }

    /**
     * 
     * @param bean
     * @param name
     * @return
     * @throws BeanNotFoundException
     */
    public static int getValueAsInt(Object bean, String name) throws BeanNotFoundException {
        return ((Integer)getValue(bean, name)).intValue();
    }

    /**
     * 
     * @param bean
     * @param name
     * @return
     * @throws BeanNotFoundException
     */
    public static long getValueAsLong(Object bean, String name) throws BeanNotFoundException {
        return ((Long)getValue(bean, name)).longValue();
    }

    /**
     * 
     * @param bean
     * @param name
     * @param value
     * @throws BeanNotFoundException
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void setProperty(Object bean, String name, Object value)
            throws BeanNotFoundException {
        if (bean instanceof Map) {
            ((Map)bean).put(name, value);
        } else if (bean.getClass().isArray() && isNumeric(name)) {
            Array.set(bean, Integer.valueOf(name), value);
        } else if (List.class.isAssignableFrom(bean.getClass())) {
            ((List)bean).add(Integer.valueOf(name), value);
        } else {
            String setterName = createMethodName("set", name);

            if (value == null) {
                for (Method method : bean.getClass().getMethods()) {
                    if (setterName.equals(method.getName()) &&
                            method.getParameterCount() == 1 &&
                            !method.getParameters()[0].getType().isPrimitive()) {
                        try {
                            method.invoke(bean, null);
                        } catch (IllegalAccessException e) {
                            throw new BeanNotFoundException(DOCS_5005 + ":" + name, e);
                        } catch (IllegalArgumentException e) {
                            throw new BeanNotFoundException(DOCS_5005 + ":" + name, e);
                        } catch (InvocationTargetException e) {
                            throw new BeanNotFoundException(DOCS_5005 + ":" + name, e);
                        }
                    }
                }
                return;
            }

            Class<?> targetType = value.getClass();
            try {
                Method method = bean.getClass().getMethod(setterName, targetType);
                method.invoke(bean, value);
            } catch (Exception e) {
                if (Boolean.class.isAssignableFrom(targetType)) {
                    targetType = boolean.class;
                } else if (Byte.class.isAssignableFrom(targetType)) {
                    targetType = byte.class;
                } else if (Short.class.isAssignableFrom(targetType)) {
                    targetType = short.class;
                } else if (Integer.class.isAssignableFrom(targetType)) {
                    targetType = int.class;
                } else if (Long.class.isAssignableFrom(targetType)) {
                    targetType = long.class;
                } else if (Float.class.isAssignableFrom(targetType)) {
                    targetType = float.class;
                } else if (Double.class.isAssignableFrom(targetType)) {
                    targetType = double.class;
                }
                try {
                    Method method = bean.getClass().getMethod(setterName, targetType);
                    method.invoke(bean, value);
                } catch (Exception ex) {
                    throw new BeanNotFoundException(DOCS_5005 + ":" + name, ex);
                }
            }
        }
    }

    /**
     * 
     * @param bean
     * @param name
     * @return
     * @throws BeanNotFoundException
     */
    @SuppressWarnings("rawtypes")
    public static Object getProperty(Object bean, String name) throws BeanNotFoundException {
        if (bean instanceof Map) {
            Object result = ((Map<?,?>)bean).get(name);
            return result;
        } else if (bean.getClass().isArray() && isNumeric(name)) {
            return Array.get(bean, Integer.valueOf(name));
        } else if (List.class.isAssignableFrom(bean.getClass())) {
            return ((List)bean).get(Integer.valueOf(name));
        } else {
            try {
                String getterName = createMethodName("get", name);
                Method method = bean.getClass().getMethod(getterName);
                return method.invoke(bean);
            } catch (Exception e) {
                try {
                    Field field = bean.getClass().getDeclaredField(name);
                    if (!field.isAccessible()) field.setAccessible(true);
                    return field.get(bean);
                } catch (Exception ex) {
                    throw new BeanNotFoundException(DOCS_5005 + ":" + name, ex);
                }
            }
        }
    }

    public boolean hasProperty(Object bean, String name) {
        if (bean instanceof Map) {
            return ((Map<?,?>)bean).containsKey(name);
        } else if (bean.getClass().isArray() && isNumeric(name)) {
            return Array.getLength(bean) > Integer.valueOf(name);
        } else if (List.class.isAssignableFrom(bean.getClass())) {
            return ((List<?>)bean).size() > Integer.valueOf(name);
        } else {
            try {
                String getterName = createMethodName("get", name);
                bean.getClass().getMethod(getterName);
                return true;
            } catch (Exception e) {
                try {
                    bean.getClass().getDeclaredField(name);
                    return true;
                } catch (Exception ex) {
                    return false;
                }
            }
        }
    }

    /**
     * 
     */
    public static class BeanNotFoundException extends Exception {
        private static final long serialVersionUID = 1L;

        public BeanNotFoundException() {
            super();
        }

        public BeanNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }

        public BeanNotFoundException(String message) {
            super(message);
        }

        public BeanNotFoundException(Throwable cause) {
            super(cause);
        }
    }
}
