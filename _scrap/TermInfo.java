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
    public static final String SPACE_CLASS = " \\t";
    private BacktrackInputStream bis;

    /*
     * 
     */
    public TermInfo() {
        // terminfo := line*
        rule("TERMINFO").define(
                rule$ZeroOrMore(
                        rule("LINE")));
        
        // line := comment | empty | start | continue
        rule("LINE").define(
                rule$Choice(
                        rule("COMMENT"),
                        rule("EMPTY"),
                        rule("START"),
                        rule("CONTINUE")));

        // comment := '#' [^LF]* LF
        rule("COMMENT").define(
                rule$Literal(SHARP),
                rule$ZeroOrMore(
                        rule$Class("^\n")),
                rule$Literal(LF));
        
        // empty := LF
        rule("EMPTY").define(
                rule$Literal(LF));

        // start := capname definition LF
        rule("START").define(
                rule("CAP_NAME"),
                rule("DEFINITION"),
                rule$Literal(LF));

        // continue := space+ definition LF
        rule("CONTINUE").define(
                rule("WS+"),
                rule("DEFINITION"),
                rule$Literal(LF));

        // definition := (',' / capvalue)*
        rule("DEFINITION").define(
                rule$ZeroOrMore(
                        rule$Choice(
                                rule$Literal(COMMA),
                                rule("CAP_VALUE"))));

        // capname := capid ('|' capdesc)?
        rule("CAP_NAME").define(
                rule("CAP_ID"),
                rule$Optional(
                        rule$Literal(VERTICAL_BAR),
                        rule("CAP_DESC")));

        rule("CAP_ID").define(
                rule$OneOrMore(
                        rule$Class("^ \t\b\r\n\\|,")));

        rule("CAP_DESC").define(
                rule$OneOrMore(
                        rule$Class("^\n,")));

        rule("CAP_VALUE").define(
                rule$Choice(
                        rule("VAR_NUMBER"),
                        rule("VAR_STRING"),
                        rule("VAR_BOOL")));

        rule("VAR_NUMBER").define(
                rule("VAR_NAME"),
                rule$Literal("#"),
                rule("VAR_NUMBER_VALUE").define(
                        rule$OneOrMore(
                                rule$Class("0-9"))));

        rule("VAR_STRING").define(
                rule("VAR_NAME"),
                rule$Literal("="),
                rule("VAR_STRING_VALUE").define(
                        rule$OneOrMore(
                                rule$Class("^,"))));

        rule("VAR_BOOL").define(
                rule("VAR_NAME"),
                rule$Optional(
                        rule$Literal("@")));

        rule("VAR_NAME").define(
                rule$OneOrMore(
                        rule$Class("0-9a-zA-Z")));

        // ws+ ;= space+
        rule("WS+").define(
                rule$OneOrMore(
                        rule$Class(SPACE_CLASS)));

        // ws* ;= space+
        rule("WS*").define(
                rule$ZeroOrMore(
                        rule$Class(SPACE_CLASS)));
    }

    /**
     *
     */
    public Node parse(InputStream is) throws UnmatchException, IOException {
        bis = new BacktrackInputStream(is);
        return rule("TERMINFO").accept(bis);
    }

}
