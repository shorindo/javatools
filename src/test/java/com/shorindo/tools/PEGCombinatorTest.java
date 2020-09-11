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

import static org.junit.Assert.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import com.shorindo.tools.Logger.Level;
import com.shorindo.tools.PEGCombinator.PEGContext;
import com.shorindo.tools.PEGCombinator.PEGException;
import com.shorindo.tools.PEGCombinator.PEGNode;
import com.shorindo.tools.PEGCombinator.Rule;
import com.shorindo.tools.PEGCombinator.RuleTypes;

/**
 * 
 */
public class PEGCombinatorTest {

    /**
     * EXPR := MULT (('+' / '-') MULT)*
     * MULT := NUM (('*' / '/') NUM)*
     */
    @Test
    public void testCalc() throws Exception {
        Calculator calc = new Calculator();
        assertEquals(3,  calc.calculate("1+2"));
        assertEquals(-1, calc.calculate("2-3"));
        assertEquals(12, calc.calculate("3*4"));
        assertEquals(3,  calc.calculate("9/3"));
        assertEquals(7,  calc.calculate("1+2*3-4/5"));
        assertEquals(15, calc.calculate("5+4*3-2/1"));
    }
    
    public static class Calculator {
        private static PEGCombinator PEG = new PEGCombinator();
        private static Rule rule;
        static {
            rule = PEG.define(CalcTypes.EXPR,
                PEG.rule(CalcTypes.MULT),
                PEG.rule$ZeroOrMore(
                    PEG.rule$Choice(
                        PEG.rule$Literal("+"),
                        PEG.rule$Literal("-")),
                    PEG.rule(CalcTypes.MULT)))
                .action($$ -> {
                    int a = (int)$$.get(0).getValue();
                    for (int i = 0; i < $$.get(1).length(); i++) {
                        PEGNode child = $$.get(1).get(i);
                        int b = (int)child.get(1).getValue();
                        switch ((String)child.get(0).getValue()) {
                        case "+": a = a + b; break;
                        case "-": a = a - b; break;
                        }
                    }
                    $$.setValue(a);
                    return $$; 
                });
            PEG.define(CalcTypes.MULT,
                PEG.rule(CalcTypes.NUM),
                PEG.rule$ZeroOrMore(
                    PEG.rule$Choice(
                        PEG.rule$Literal("*"),
                        PEG.rule$Literal("/")),
                    PEG.rule(CalcTypes.NUM)))
                .action($$ -> {
                    int a = (int)$$.get(0).getValue();
                    for (int i = 0; i < $$.get(1).length(); i++) {
                        PEGNode child = $$.get(1).get(i);
                        int b = (int)child.get(1).getValue();
                        switch ((String)child.get(0).getValue()) {
                        case "*": a = a * b; break;
                        case "/": a = a / b; break;
                        }
                    }
                    $$.setValue(a);
                    return $$;
                });
            PEG.define(CalcTypes.NUM,
                PEG.rule$RegExp("[0-9]+"))
                .action($$ -> {
                    $$.setValue(Integer.parseInt($$.getSource()));
                    return $$;
                });
        }
        
        public int calculate(String expr) throws Exception {
            PEGContext ctx = PEG.createContext(expr);
            PEGNode node = rule.accept(ctx);
            if (ctx.available() > 0) {
                throw new RuntimeException("error at:" + ctx.subString(ctx.position()));
            }
            return (int)node.getValue();
        }
    }
    public enum CalcTypes implements RuleTypes {
        EXPR, MULT, NUM;
    }

    /**
     * https://www.json.org/json-ja.html
     */
    @Test
    public void testJson() throws Exception {
        Logger.setLevel(Level.TRACE);
        JsonParser parser = new JsonParser();
        System.out.println(parser.parse("{}"));
        System.out.println(parser.parse("{ \"foo\" : \"bar\" }"));
        System.out.println(parser.parse("[]"));
        System.out.println(parser.parse("[123]"));
        System.out.println(parser.parse("[ 1 ]"));
        System.out.println(parser.parse("[ 1, 2, 3 ]"));
        System.out.println(parser.parse("[ \"a\", \"b\", \"c\" ]"));
        System.out.println(parser.parse("{ \"foo\" : 123, \"bar\" : 456 }"));
        System.out.println(parser.parse("{ \"foo\" : [ 1, \"a b c\", true, false, null ] }"));
        System.out.println(parser.parse("\"あ\""));
    }

    public static class JsonParser {
        private static PEGCombinator PEG = new PEGCombinator();
        private static Rule rule;
        static {
            rule = PEG.define(JsonTypes.JSON,
                PEG.rule(JsonTypes.ELEMENT));
            PEG.define(JsonTypes.VALUE,
                PEG.rule$Choice(
                    PEG.rule(JsonTypes.OBJECT),
                    PEG.rule(JsonTypes.ARRAY),
                    PEG.rule(JsonTypes.STRING),
                    PEG.rule(JsonTypes.NUMBER),
                    PEG.rule$Literal("true"),
                    PEG.rule$Literal("false"),
                    PEG.rule$Literal("null")));
            PEG.define(JsonTypes.OBJECT,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule$Literal("{"),
                        PEG.rule(JsonTypes.WS),
                        PEG.rule$Literal("}")),
                    PEG.rule$Sequence(
                        PEG.rule$Literal("{"),
                        PEG.rule(JsonTypes.MEMBERS),
                        PEG.rule$Literal("}"))));
            PEG.define(JsonTypes.MEMBERS,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule(JsonTypes.MEMBER),
                        PEG.rule$Literal(","),
                        PEG.rule(JsonTypes.MEMBERS)),
                    PEG.rule(JsonTypes.MEMBER)));
            PEG.define(JsonTypes.MEMBER,
                PEG.rule(JsonTypes.WS),
                PEG.rule(JsonTypes.STRING),
                PEG.rule(JsonTypes.WS),
                PEG.rule$Literal(":"),
                PEG.rule(JsonTypes.ELEMENT));
            PEG.define(JsonTypes.ARRAY,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule$Literal("["),
                        PEG.rule(JsonTypes.ELEMENTS),
                        PEG.rule$Literal("]")),
                    PEG.rule$Sequence(
                        PEG.rule$Literal("["),
                        PEG.rule(JsonTypes.WS),
                        PEG.rule$Literal("]"))));
            PEG.define(JsonTypes.ELEMENTS,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule(JsonTypes.ELEMENT),
                        PEG.rule$Literal(","),
                        PEG.rule(JsonTypes.ELEMENTS)),
                    PEG.rule(JsonTypes.ELEMENT)));
            PEG.define(JsonTypes.ELEMENT,
                PEG.rule(JsonTypes.WS),
                PEG.rule(JsonTypes.VALUE),
                PEG.rule(JsonTypes.WS));
            PEG.define(JsonTypes.STRING,
                PEG.rule$Literal("\""),
                PEG.rule(JsonTypes.CHARACTERS),
                PEG.rule$Literal("\""));
            PEG.define(JsonTypes.CHARACTERS,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule(JsonTypes.CHARACTER),
                        PEG.rule(JsonTypes.CHARACTERS)),
                    PEG.rule(JsonTypes.CHARACTER),
                    PEG.rule$Literal("")))
                .action($$ -> {
                    return $$;
                });
            PEG.define(JsonTypes.CHARACTER,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule$Literal("\\"),
                        PEG.rule(JsonTypes.ESCAPE)),
                    PEG.rule$RegExp("[\\u0020-\\u0021\\u0023-\\u005b\\u005d-\\u10ffff]")))
                .action($$ -> {
                    return $$;
                });
            PEG.define(JsonTypes.ESCAPE,
                PEG.rule$Choice(
                    PEG.rule$RegExp("[\"\\/bfnrt]"),
                    PEG.rule$Sequence(
                        PEG.rule$Literal("u"),
                        PEG.rule(JsonTypes.HEX),
                        PEG.rule(JsonTypes.HEX),
                        PEG.rule(JsonTypes.HEX))));
            PEG.define(JsonTypes.HEX,
                PEG.rule$Choice(
                    PEG.rule(JsonTypes.DIGIT),
                    PEG.rule$RegExp("[A-F]"),
                    PEG.rule$RegExp("[a-f]")));
            PEG.define(JsonTypes.NUMBER,
                PEG.rule(JsonTypes.INTEGER),
                PEG.rule(JsonTypes.FRACTION),
                PEG.rule(JsonTypes.EXPONENT));
            PEG.define(JsonTypes.INTEGER,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule$Literal("-"),
                        PEG.rule(JsonTypes.ONENINE),
                        PEG.rule(JsonTypes.DIGITS)),
                    PEG.rule$Sequence(
                        PEG.rule$Literal("-"),
                        PEG.rule(JsonTypes.DIGIT)),
                    PEG.rule$Sequence(
                            PEG.rule(JsonTypes.ONENINE),
                            PEG.rule(JsonTypes.DIGITS)),
                    PEG.rule(JsonTypes.DIGIT)));
            PEG.define(JsonTypes.DIGITS,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule(JsonTypes.DIGIT),
                        PEG.rule(JsonTypes.DIGITS)),
                    PEG.rule(JsonTypes.DIGIT)));
            PEG.define(JsonTypes.DIGIT,
                PEG.rule$Choice(
                    PEG.rule$Literal("0"),
                    PEG.rule(JsonTypes.ONENINE)));
            PEG.define(JsonTypes.ONENINE,
                PEG.rule$RegExp("[1-9]"));
            PEG.define(JsonTypes.FRACTION,
                PEG.rule$Choice(
                    PEG.rule$Literal(""),
                    PEG.rule$Sequence(
                        PEG.rule$Literal("."),
                        PEG.rule(JsonTypes.DIGITS))));
            PEG.define(JsonTypes.EXPONENT,
                PEG.rule$RegExp("(|E|e)"));
            PEG.define(JsonTypes.SIGN,
                PEG.rule$RegExp("(|\\+|\\-)"));
            PEG.define(JsonTypes.WS,
                PEG.rule$Choice(
                    PEG.rule$Sequence(
                        PEG.rule$Literal(" "),
                        PEG.rule(JsonTypes.WS)),
                    PEG.rule$Sequence(
                        PEG.rule$Literal("\r"),
                        PEG.rule(JsonTypes.WS)),
                    PEG.rule$Sequence(
                        PEG.rule$Literal("\n"),
                        PEG.rule(JsonTypes.WS)),
                    PEG.rule$Sequence(
                        PEG.rule$Literal("\t"),
                        PEG.rule(JsonTypes.WS)),
                    PEG.rule$Literal("")));
        }
        
        public PEGNode parse(String source) throws Exception {
            PEGContext ctx = PEG.createContext(source);
            PEGNode node = rule.accept(ctx);
            if (ctx.available() > 0) {
                throw new RuntimeException("error at:" + ctx.subString(ctx.position()));
            }
            return node;
        }
    }

    public enum JsonTypes implements RuleTypes {
        JSON, VALUE, OBJECT, MEMBERS, MEMBER, ARRAY, ELEMENT, ELEMENTS, STRING,
        CHARACTERS, CHARACTER, ESCAPE, HEX, NUMBER, INTEGER, DIGITS, DIGIT,
        ONENINE, FRACTION, EXPONENT, SIGN, WS;
    }

    @Test
    public void testFullWidth() {
        System.out.println(new String("\u10ffff"));
        Pattern p = Pattern.compile("[\u0020-\uffff]+");
        Matcher m = p.matcher("あ");
        assertTrue(m.matches());
    }
}
