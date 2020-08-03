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

import java.io.ByteArrayInputStream;

import org.junit.Test;

import com.shorindo.tools.PEGCombinator;
import com.shorindo.tools.PEGCombinator.PEGContext;
import com.shorindo.tools.PEGCombinator.PEGNode;
import com.shorindo.tools.PEGCombinator.UnmatchException;

/**
 * 
 */
public class PEGCombinatorTest {

//    private BacktrackInputStream createStream(String text) {
//        return new BacktrackInputStream(new ByteArrayInputStream(text.getBytes()));
//    }

    @Test
    public void testAny() throws Exception {
        PEGCombinator comb = new PEGCombinator();
        PEGContext ctx = comb.createContext("abc");
        PEGNode node = comb
            .define("ANY", comb.rule$Any())
            .accept(ctx);
        assertEquals("a", node.getSource());
    }

    @Test
    public void testAnyUnmatch() throws Exception {
        PEGCombinator comb = new PEGCombinator();
        BacktrackInputStream bis = createStream("");
        try {
            comb.rule("ANY")
                .define(comb.rule$Any())
                .accept(bis);
            fail("expect unmatch, but match.");
        } catch (UnmatchException e) {
            
        }
    }

    @Test
    public void testLiteral() throws Exception {
        PEGCombinator comb = new PEGCombinator();
        BacktrackInputStream bis = createStream("abc");
        Node node = comb.rule("LITERAL")
            .define(comb.rule$Literal("abc"))
            .accept(bis);
        assertEquals("abc", node.getSource());
    }

    @Test
    public void testLiteralUnmatch() throws Exception {
        PEGCombinator comb = new PEGCombinator();
        BacktrackInputStream bis = createStream("abd");
        try {
            Node node = comb.rule("LITERAL")
                    .define(comb.rule$Literal("abc"))
                    .accept(bis);
            fail("match");
        } catch (UnmatchException e) {
        }
    }
}
