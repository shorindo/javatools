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

import org.springframework.context.annotation.Bean;

/**
 * 
 */
public class BeanConfig {
    @Bean
    public X x() {
        return null;
    }

    // タイプが同じで名前が違う
    @Bean
    public X y() {
        return null;
    }

    // 名前が同じでタイプが違う
    @Bean(name = "x")
    public Z z() {
        return null;
    }
    
    public @interface MyAnnot {

    }

    public static interface X {

        @Deprecated
        public void methodX1();
        public void methodX2();

    }

    public static abstract class Y implements X {

        public Y() {
        }

        @Override
        public void methodX1() {
        }

        @Override
        public void methodX2() {
        }

        public void methodY1() {
        }

        public void methodY2() {
        }
    }

    public static class Z extends Y {

        public Z() {
        }

        public void methodZ1() {
        }

        @MyAnnot
        public void methodZ2() {
        }
    }
}
