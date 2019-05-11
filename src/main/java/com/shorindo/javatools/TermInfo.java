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

import java.io.IOException;
import java.io.InputStream;

/**
 * 
 */
public class TermInfo extends PEGCombinator {
    public static final String LF = "\n";
    public static final String SHARP = "#";
    public static final String COMMA = ",";
    public static final String VERTICAL_BAR = "|";
    public static final String SPACE_CLASS = "[ \\t\\b]";
    private BacktrackInputStream bis;

    /*
     * 
     */
    public TermInfo() {
        // terminfo := line*
        rule("TERMINFO").define(
                rule$ZeroOrMore(
                        rule("LINE"))
                );
        
        // line := comment | empty | start | continue
        rule("LINE").define(
                rule$Choice(
                        rule("COMMENT"),
                        rule("EMPTY"),
                        rule("START"),
                        rule("CONTINUE"))
                );

        // comment := '#' [^LF]* LF
        rule("COMMENT").define(
                rule$Literal(SHARP),
                rule$Not(
                        rule$Literal(LF)),
                rule$Literal(LF));
        
        // empty := LF
        rule("EMPTY").define(
                rule$Literal(LF));

        // start := definition LF
        rule("START").define(
                rule("DEFINITION"),
                rule$Literal(LF));

        // continue := space+ definition LF
        rule("CONTINUE").define(
                rule("WS+"),
                rule("DEFINITION"),
                rule$Literal(LF));

        // definition := capname (',' capvalue)*
        rule("DEFINITION").define(
                rule("CAPNAME"),
                rule$ZeroOrMore(
                        rule$Sequence(
                                rule$Literal(COMMA),
                                rule("CAPVALUE"))));

        // capname := capid ('|' capdesc)?
        rule("CAPNAME").define(
                rule("CAPID"),
                rule$Optional(
                        rule$Literal(VERTICAL_BAR),
                        rule("CAPDESC")));

        // TODO

        // ws+ ;= space+
        rule("WS+").define(
                rule$OneOrMore(
                        rule$Literal(SPACE_CLASS)));

        // ws* ;= space+
        rule("WS*").define(
                rule$ZeroOrMore(
                        rule$Literal(SPACE_CLASS)));
    }

    /**
     *
     */
    public Node parse(InputStream is) throws UnmatchException, IOException {
        bis = new BacktrackInputStream(is);
        return rule("TERMINFO").accept(bis);
    }

}
