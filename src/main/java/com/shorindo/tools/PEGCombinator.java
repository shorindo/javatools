/*
 * Copyright 2017-2020 Shorindo, Inc.
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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PEGによる構文解析器を生成する
 */
public class PEGCombinator {
    private static Logger LOG = Logger.getLogger(PEGCombinator.class);
    private Map<RuleTypes,Rule> ruleMap;
    private Stack<Rule> ruleStack;

    /**
     * 
     */
    public PEGCombinator() {
        ruleMap = new HashMap<>();
        ruleStack = new Stack<>();
    }

    public PEGContext createContext(String text) {
        return new PEGContext(text);
    }

    public Rule define(RuleTypes ruleType, Rule...rules) {
        Rule rule = new Rule(ruleType) {

            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                PEGNode $$ = new PEGNode(ctx, ruleType);
                int curr = ctx.position();
                ruleStack.push(this);
                try {
                    $$.setType(ruleType);
                    for (Rule rule : rules) {
                        PEGNode childNode = rule.accept(ctx);
                        $$.add(childNode);
                    }
                    String sub = ctx.subString(curr, ctx.position);
                    $$.setSource(sub);
                    StringBuffer trace = new StringBuffer();
                    String prefix = "";
                    for (int i = 0; i < ruleStack.size(); i++) {
                        trace.append(prefix + ruleStack.get(i).getType());
                        prefix = "->";
                    }
                    LOG.trace("rule({0})[{1}] accept <- {2} : {3}",
                        ruleType, curr, $$.getSource(), trace.toString());
                    return ctx.success(this, action.apply($$), curr, ctx.position());
                } catch (UnmatchException e) {
                    ctx.failure(curr, this);
//                    LOG.trace("rule({0})[{1}] deny <- {2}",
//                        ruleType, curr, ctx.subString(curr));
                    throw e;
                } finally {
                    ruleStack.pop();
                }
            }

            public String toString(int depth, Set<RuleTypes> visited) {
                if (visited.contains(getType())) {
                    return indent(depth) + getType().name() + "\n";
                } else {
                    visited.add(getType());
                    StringBuffer sb = new StringBuffer(indent(depth) + "(" + ruleType + "\n");
                    for (Rule child : rules) {
                        if (this != child)
                            sb.append(child.toString(depth + 1, visited));
                    }
                    sb.append(indent(depth) + ")\n");
                    return sb.toString();
                }
            }
            
            public String toString() {
                Set<RuleTypes> visited = new HashSet<>();
                return toString(0, visited);
            }
        };
        ruleMap.put(ruleType, rule);
        return rule;
    }

    public Rule rule(final RuleTypes ruleType) {
        Rule rule = new Rule(ruleType) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                Rule rule = ruleMap.get(ruleType);
                if (rule == null) {
                    throw new PEGException(ruleType + " is not defined.");
                }
                try {
                    PEGNode $$ = rule.accept(ctx);
                    return action.apply($$);
                } catch (UnmatchException e) {
                    throw e;
                }
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                Rule rule = ruleMap.get(ruleType);
                if (rule == null) {
                    throw new RuntimeException(ruleType + " is not defined.");
                }
                return rule.toString(depth + 1, visited);
            }
            
            public String toString() {
                Set<RuleTypes> visited = new HashSet<>();
                return toString(0, visited);
            }
        };
        return rule;
    }

    public Rule rule$Any() {
        Rule rule = new Rule(Types.PEG_ANY) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                int curr = ctx.position();
                int c = ctx.read();
                if (c == -1) {
                    ctx.failure(curr, this);
                    //LOG.trace("rule$Any()[{0}] deny <- {1}", curr, (char)c);
                    throw new UnmatchException();
                } else {
                    PEGNode $$ = new PEGNode(ctx, Types.PEG_ANY);
                    $$.setSource(String.valueOf((char)c));
                    $$.setValue(String.valueOf((char)c));
                    LOG.trace("rule$Any()[{0}] accept <- {1}", curr, (char)c);
                    return ctx.success(this, action.apply($$), curr, ctx.position());
                }
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                return indent(depth) + ".\n";
            }
        };
        return rule;
    }

    public Rule rule$And(final Rule rule) {
        return rule$And(0, rule);
    }

    public Rule rule$And(int preceeding, final Rule childRule) {
        Rule rule = new Rule(Types.PEG_AND) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                int curr = ctx.position();
                int next = curr + preceeding;
                try {
                    if (next < 0 || preceeding > ctx.available()) {
                        throw new UnmatchException();
                    }
                    childRule.accept(ctx);
                    PEGNode $$ = new PEGNode(ctx, Types.PEG_AND);
                    $$.setSource("");
                    LOG.trace("rule$And()[{0}] accept <- {1}", curr, childRule.getType());
                    return ctx.success(this, action.apply($$), curr, ctx.position());
                } catch (UnmatchException e) {
                    ctx.failure(curr, this);
                    //LOG.trace("rule$And()[{0}] deny <- {1}", curr, childRule);
                    throw e;
                } finally {
                    ctx.reset(curr);
                }
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                return "&" + childRule.toString(depth, visited);
            }
        };
        return rule;
    }

    public Rule rule$Not(final Rule rule) {
        return rule$Not(0, rule);
    }

    public Rule rule$Not(int preceeding, final Rule childRule) {
        Rule rule = new Rule(Types.PEG_NOT) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                int curr = ctx.position();
                int next = curr + preceeding;
                try {
                    if (next < 0 || preceeding > ctx.available()) {
                        throw new UnmatchException();
                    }
                    ctx.reset(curr + preceeding);
                    childRule.accept(ctx);
                } catch (UnmatchException e) {
                    PEGNode $$ = new PEGNode(ctx, Types.PEG_NOT);
                    $$.setSource("");
                    LOG.trace("rule$Not()[{0}] accept <- {1}", curr, childRule.getType());
                    return ctx.success(this, action.apply($$), curr, ctx.position());
                } finally {
                    ctx.reset(curr);
                }
                ctx.failure(curr, this);
                //LOG.trace("rule$Not()[{0}] deny <- {1}", curr, childRule);
                throw new UnmatchException();
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                return indent(depth) + "(!\n" + childRule.toString(depth + 1, visited) + indent(depth) + ")\n";
            }
        };
        return rule;
    }

        
    public Rule rule$Literal(final String literal) {
        return rule$Literal(literal, false);
    }

    public Rule rule$Literal(final String literal, boolean ignoreCase) {
        Rule rule = new Rule(Types.PEG_LITERAL) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                int curr = ctx.position();
                for (int i = 0; i < literal.length(); i++) {
                    char c = ignoreCase
                        ? Character.toLowerCase(literal.charAt(i))
                        : literal.charAt(i);
                    int r = ignoreCase
                        ? Character.toLowerCase(ctx.read())
                        : ctx.read();
                    if (c != r) {
                        //LOG.trace("rule$Literal()[{0}] deny <- {1}", curr, escape(literal));
                        ctx.reset(curr);
                        ctx.failure(curr, this);
                        throw new UnmatchException();
                    }
                }
                PEGNode $$ = new PEGNode(ctx, Types.PEG_LITERAL);
                $$.setSource(literal);
                $$.setValue(literal);
                LOG.trace("rule$Literal()[{0}] accept <- {1}", curr, escape(literal));
                return ctx.success(this, action.apply($$), curr, ctx.position());
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                return indent(depth) + "'" + escape(literal) + "'\n";
            }
        };
        return rule;
    }
    
    public Rule rule$Class(final String charClass) {
        Rule rule = new Rule(Types.PEG_CLASS) {
            Pattern pattern = Pattern.compile("[" + charClass + "]");

            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                int curr = ctx.position();
                int c = ctx.read();
                if (c == -1) {
                    //LOG.trace("rule$Class({0})[{1}] deny <- {2}",
                    //    escape(charClass), curr, escape(String.valueOf((char)c)));
                    ctx.failure(curr, this);
                    ctx.reset(curr);
                    throw new UnmatchException();
                }
                Matcher m = pattern.matcher(String.valueOf((char)c));
                if (m.matches()) {
                    PEGNode $$ = new PEGNode(ctx, Types.PEG_CLASS);
                    $$.setSource(String.valueOf((char)c));
                    $$.setValue(String.valueOf((char)c));
                    LOG.trace("rule$Class({0})[{1}] accept <- {2}",
                        escape(charClass), curr, escape(String.valueOf((char)c)));
                    return ctx.success(this, action.apply($$), curr, ctx.position());
                } else {
                    ctx.reset(curr);
                    throw new UnmatchException();
                }
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                return indent(depth) + "[" + escape(charClass) + "]\n";
            }
        };
        return rule;
    }

    public Rule rule$RegExp(final String regexp) {
        Rule rule = new Rule(Types.PEG_REGEXP) {
            Pattern pattern = Pattern.compile("^" + regexp);

            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                int start = ctx.position();
                Matcher matcher = pattern.matcher(ctx.source.substring(start));
                if (matcher.find()) {
                    PEGNode $$ = new PEGNode(ctx, Types.PEG_REGEXP);
                    String source = matcher.group();
                    ctx.reset(start + source.length());
                    $$.setSource(source);
                    $$.setValue(source);
                    PEGNode $i = new PEGNode(ctx, Types.PEG_REGEXP);
                    $i.setValue(matcher.group(0));
                    $$.add($i);
                    for (int i = 0; i < matcher.groupCount(); i++) {
                        $i = new PEGNode(ctx, Types.PEG_REGEXP);
                        $i.setValue(matcher.group(i + 1));
                        $$.add($i);
                    }
                    LOG.trace("rule$RegExp({0})[{1}] accept <- {2}",
                        escape(regexp), start, escape(source));
                    return ctx.success(this, action.apply($$), start, ctx.position());
                } else {
                    ctx.failure(start, this);
                    ctx.reset(start);
                    throw new UnmatchException();
                }
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                return indent(depth) + "/" + escape(regexp) + "/\n";
            }
        };
        return rule;
    }

    public Rule rule$ZeroOrMore(final Rule...rules) {
        Rule rule = new Rule(Types.PEG_ZERO_OR_MORE) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                PEGNode $$ = new PEGNode(ctx, Types.PEG_ZERO_OR_MORE);
                int start = ctx.position();
                while (true) {
                    PEGNode seq = new PEGNode(ctx, Types.PEG_SEQUENCE);
                    int curr = ctx.position();
                    try {
                        for (Rule child : rules) {
                            seq.add(child.accept(ctx));
                        }
                    } catch (UnmatchException e) {
                        //LOG.trace("rule$ZeroOrMore[{0}] deny", start);
                        ctx.failure(start, this);
                        ctx.reset(curr);
                        break;
                    } finally {
                        if (seq.length() == rules.length) {
                            $$.add(seq);
                        }
                    }
                }
                $$.setSource(ctx.subString(start, ctx.position()));
                LOG.trace("rule$ZeroOrMore[{0}] accept <- ''{1}''", start, $$.getSource());
                return ctx.success(this, action.apply($$), start, ctx.position());
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                StringBuffer sb = new StringBuffer();
                sb.append(indent(depth) + "(*\n");
                for (int i = 0; i < rules.length; i++) {
                    sb.append(rules[i].toString(depth + 1, visited));
                }
                sb.append(indent(depth) + ")\n");
                return sb.toString();
            }
        };
        return rule;
    }

    public Rule rule$OneOrMore(final Rule...rules) {
        Rule rule = new Rule(Types.PEG_ONE_OR_MORE) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                int count = 0;
                PEGNode $$ = new PEGNode(ctx, Types.PEG_ONE_OR_MORE);
                int start = ctx.position();
                while (true) {
                    PEGNode seq = new PEGNode(ctx, Types.PEG_SEQUENCE);
                    int curr = ctx.position();
                    try {
                        for (Rule child : rules) {
                            seq.add(child.accept(ctx));
                        }
                        count++;
                    } catch (UnmatchException e) {
                        ctx.reset(curr);
                        break;
                    } finally {
                        if (seq.length() == rules.length) {
                            $$.add(seq);
                        }
                    }
                }
                if (count > 0) {
                    $$.setSource(ctx.subString(start, ctx.position()));
                    LOG.trace("rule$OneOrMore[{0}] accept <- ''{1}''", start, $$.getSource());
                    return ctx.success(this, action.apply($$), start, ctx.position());
                } else {
                    //LOG.trace("rule$OneOrMore[{0}] deny", start);
                    ctx.failure(start, this);
                    throw new UnmatchException();
                }
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                StringBuffer sb = new StringBuffer();
                if (rules.length == 1) {
                    sb.append(rules[0].toString(depth + 1, visited));
                } else {
                    String sep = "";
                    sb.append(indent(depth) + "(\n");
                    for (int i = 0; i < rules.length; i++) {
                        sb.append(sep + rules[i].toString(depth + 1, visited));
                        sep = ", ";
                    }
                    sb.append(indent(depth) + ")\n");
                }
                return sb.toString();
            }
        };
        return rule;
    }
    
    public Rule rule$Sequence(final Rule...rules) {
        Rule rule = new Rule(Types.PEG_SEQUENCE) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                //LOG.trace(toString());
                int curr = ctx.position();
                PEGNode $$ = new PEGNode(ctx, Types.PEG_SEQUENCE);
                try {
                    for (Rule child : rules) {
                        PEGNode $n = (PEGNode)child.accept(ctx);
                        $$.add($n);
                    }
                } catch (UnmatchException e) {
                    //LOG.trace("rule$Sequence deny <- " + $$.getSource());
                    ctx.failure(curr, this);
                    ctx.reset(curr);
                    throw e;
                }
                $$.setSource(ctx.subString(curr, ctx.position()));
                LOG.trace("rule$Sequence accept <- " + $$.getSource());
                return ctx.success(this, action.apply($$), curr, ctx.position());
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                StringBuffer sb = new StringBuffer();
                if (rules.length == 1) {
                    sb.append(rules[0].toString(depth, visited));
                } else {
                    sb.append(indent(depth) + "(=\n");
                    for (Rule child : rules) {
                        sb.append(child.toString(depth + 1, visited));
                    }
                    sb.append(indent(depth) + ")\n");
                }
                return sb.toString();
            }
        };
        return rule;
    }
    
    public Rule rule$Choice(final Rule...rules) {
        Rule rule = new Rule(Types.PEG_CHOICE) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }
                //LOG.trace(toString());
                int curr = ctx.position();
                for (Rule child : childRules) {
                    try {
                        PEGNode $$ = child.accept(ctx);
                        $$.setSource(ctx.subString(curr, ctx.position()));
                        LOG.trace("rule$Choice()[{0}] accept", curr);
                        return ctx.success(this, action.apply($$), curr, ctx.position());
                    } catch (UnmatchException e) {
                        ctx.reset(curr);
                    }
                }
                //LOG.trace("rule$Choice()[{0}] deny", curr);
                ctx.failure(curr, this);
                throw new UnmatchException();
            }
            
            public String toString(int depth, Set<RuleTypes> visited) {
                StringBuffer sb = new StringBuffer(indent(depth) + "(/\n");
                String sep = indent(depth + 1);
                for (Rule child : childRules) {
                    sb.append(child.toString(depth + 1, visited));
                    //sep = "\n," + indent(depth + 1);
                }
                sb.append(indent(depth) + ")\n");
                return sb.toString();
            }
        };
        for (Rule child : rules) {
            rule.add(child);
        }
        return rule;
    }

    public Rule rule$Optional(final Rule...rules) {
        Rule rule = new Rule(Types.PEG_OPTIONAL) {
            @Override
            public PEGNode accept(PEGContext ctx) throws PEGException {
                if (ctx.hasMemo(this)) {
                    return ctx.getMemo(this);
                }

                int curr = ctx.position();
                PEGNode $$ = new PEGNode(ctx, Types.PEG_OPTIONAL);
                try {
                    for (Rule child : childRules) {
                        $$.add(child.accept(ctx));
                    }
                    //LOG.trace("rule$Optional() <= " + toString(rules));
                } catch (UnmatchException e) {
                    //LOG.trace("rule$Optional deny <- " + $$.getSource());
                    ctx.failure(curr, this);
                    $$.clear();
                    ctx.reset(curr);
                }
                $$.setSource(ctx.subString(curr, ctx.position()));
                LOG.trace("rule$Optional accept <- " + $$.getSource());
                return ctx.success(this, action.apply($$), curr, ctx.position());
            }

            @Override
            public String toString(int depth, Set<RuleTypes> visited) {
                StringBuffer sb = new StringBuffer(indent(depth) + "(?\n");
                for (Rule child : childRules) {
                    sb.append(" " + child.toString(depth + 1, visited));
                }
                sb.append(indent(depth) + ")\n");
                return sb.toString();
            }
        };
        for (Rule child : rules) {
            rule.add(child);
        }
        return rule;
    }

    @SuppressWarnings("serial")
    public static class PEGException extends Exception {
        public PEGException() {}
        public PEGException(String msg) { super(msg); }
        public PEGException(Exception e) { super(e); }
    }

    public static class UnmatchException extends PEGException {
        private static final long serialVersionUID = -5428867116035040421L;
        public UnmatchException() {}
        public UnmatchException(String msg) { super(msg); }
        public UnmatchException(Exception e) { super(e); }
    }
    
    /**
     * 
     */
    public static abstract class Rule {
        public RuleTypes type;
        protected Function<PEGNode,PEGNode> action;
        protected List<Rule> childRules = new ArrayList<Rule>();

        public Rule(RuleTypes type) {
            this.type = type;
            this.action = new Function<PEGNode,PEGNode>() {
                @Override
                public PEGNode apply(PEGNode $$) {
                    return $$;
                }
            };
        }

        public abstract PEGNode accept(PEGContext ctx)
                throws PEGException;

        public abstract String toString(int depth, Set<RuleTypes> visited);

        public RuleTypes getType() {
            return type;
        }

        public Rule add(Rule child) {
            childRules.add(child);
            return this;
        }

        public Rule get(int i) {
            return childRules.get(i);
        }
        
        public Rule action(Function<PEGNode,PEGNode> action) {
            this.action = action;
            return this;
        }

        public String toString(Rule...rules) {
            StringBuffer sb = new StringBuffer(type.name());
            String sep = "";
            sb.append("[");
            for (Rule rule : rules) {
                sb.append(sep + rule);
                sep = ",";
            }
            sb.append("]");
            return sb.toString();
        }

        public String toString() {
            StringBuffer sb = new StringBuffer("(" + type.name());
            for (Rule child : childRules) {
                sb.append(" " + child.toString());
            }
            sb.append(")");
            return sb.toString();
        }
    }

    private static String escape(String text) {
        return text
            .replaceAll("\\\\", "\\\\\\\\")
            .replaceAll("\t", "\\\\t")
            .replaceAll("\r", "\\\\r")
            .replaceAll("\n", "\\\\n");
    }
    
    private static String indent(int depth) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    /**
     * 
     */
    public static class PEGNode {
        private PEGContext ctx;
        private RuleTypes type;
        private String source;
        private String value;
        private boolean empty = false;
        private List<PEGNode> childList = new ArrayList<PEGNode>();

        public PEGNode(PEGContext ctx, RuleTypes type) {
            this.ctx = ctx;
            this.type = type;
        }
        public RuleTypes getType() {
            return type;
        }
        public void setType(RuleTypes type) {
            this.type = type;
        }
        public String getValue() {
            return value;
        }
        public void setValue(String value) {
            this.value = value;
        }
//        public boolean isEmpty() {
//            return empty;
//        }
//        public void setEmpty(boolean empty) {
//            this.empty = empty;
//        }
        public void add(PEGNode child) {
            childList.add(child);
        }
        public PEGNode get(int i) {
            return childList.get(i);
        }
        public int length() {
            return childList.size();
        }
        public void clear() {
            childList.clear();
        }
        public PEGContext getContext() {
            return ctx;
        }
        public void setSource(String source) {
            this.source = source;
        }
        public String getSource() {
            return source;
        }
        public PEGNode pack() {
            if (this.getValue() != null) {
                return this;
            } else {
                StringBuffer sb = new StringBuffer();
                for (int i = 0; i < this.length(); i++) {
                    PEGNode $i = this.get(i);
                    if ($i.getValue() == null) {
                        $i.pack();
                    }
                    if ($i.getValue() != null) {
                        sb.append($i.getValue());
                    }
                }
                this.setValue(sb.toString());
                return this;
            }
        }
        public String toString() {
            if (value == null && childList.size() == 0) {
                return "";
            }
            StringBuffer sb = new StringBuffer();
            sb.append("(");
            switch (getType().name()) {
            case "PEG_ANY": sb.append("."); break;
            case "PEG_ZERO_OR_MORE": sb.append("*"); break;
            case "PEG_ONE_OR_MORE": sb.append("+"); break;
            case "PEG_CHOICE": sb.append("/"); break;
            case "PEG_SEQUENCE": sb.append("="); break;
            case "PEG_AND": sb.append("&"); break;
            case "PEG_NOT": sb.append("!"); break;
            case "PEG_CLASS": sb.append("[" + escape(getValue()) + "]"); break;
            case "PEG_REGEXP": sb.append("/" + escape(getValue()) + "/"); break;
            case "PEG_LITERAL": sb.append("'" + getValue() + "'"); break;
            default:
                sb.append(getType().name());
            }
            for (PEGNode child : childList) {
                sb.append(" " + child.toString());
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * 
     */
    public static class PEGContext {
        private String source;
        private int position = 0;
        private boolean useMemo = true;
        private Map<String,String> attrMap;
        private Map<RuleTypes,Statistics> statsMap;
        private Map<Rule,Map<Integer,Memo>> memoMap;

        protected PEGContext(String text) {
            attrMap = new HashMap<>();
            statsMap = new HashMap<>();
            memoMap = new HashMap<>();
            source = text;
        }

        public PEGContext createContext(String text) {
            return new PEGContext(text);
        }

        public void reset(int position) {
            this.position = position;
        }

        public int position() {
            return position;
        }

        public void clear() {
            position = 0;
        }

        public int read() {
            if (available() > 0) {
                return source.charAt(position++);
            } else {
                return -1;
            }
        }

        public int available() {
            return source.length() - position;
        }
        
        public void setAttr(String key, String value) {
            attrMap.put(key, value);
        }
        
        public String getAttr(String key) {
            return attrMap.get(key);
        }
        
        /**
         * 統計情報を取得する
         */
        public Map<RuleTypes,Statistics> getStatistics() {
            return statsMap;
        }

        /**
         * 成功した回数をカウントし、結果をメモる
         */
        public PEGNode success(Rule rule, PEGNode $$, int start, int end) {
            if ($$ == null) throw new RuntimeException("$$ is null");
            Statistics stat = statsMap.get(rule.getType());
            if (stat == null) {
                stat = new Statistics();
                statsMap.put(rule.getType(), stat);
            }
            stat.success();

            if (useMemo) {
                Map<Integer,Memo> memo = memoMap.get(rule);
                if (memo == null) {
                    memo = new HashMap<>();
                    memoMap.put(rule, memo);
                }
                memo.put(start, new Memo(start, end, $$));
            }
            return $$;
        }
        
        /**
         * 失敗した回数をカウントし、結果をメモる
         */
        public void failure(int position, Rule rule) {
            Statistics stat = statsMap.get(rule.getType());
            if (stat == null) {
                stat = new Statistics();
                statsMap.put(rule.getType(), stat);
            }
            stat.failure();
            
            if (useMemo) {
                Map<Integer,Memo> memo = memoMap.get(rule);
                if (memo == null) {
                    memo = new HashMap<>();
                    memoMap.put(rule, memo);
                }
                memo.put(position, null);
            }
        }

        public boolean hasMemo(Rule rule) {
            if (useMemo) {
                Map<Integer,Memo> memo = memoMap.get(rule);
                if (memo == null) {
                    return false;
                }
                if (memo.containsKey(position())) {
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        }
        
        public PEGNode getMemo(Rule rule) throws UnmatchException {
            Map<Integer,Memo> memoIndex = memoMap.get(rule);
            //LOG.debug("getMemo(" + rule + ") -> " + memo);
            if (memoIndex != null) {
                Memo memo = memoIndex.get(position());
                //LOG.debug("getMemo(" + rule + ", " + position() + ") -> " + $$);
                if (memo != null) {
                    reset(memo.getEnd());
                    return memo.getNode();
                } else {
                    throw new UnmatchException();
                }
            }
            throw new RuntimeException("memo has no " + rule + " at " + position());
        }

        public String subString(int start) {
            return source.substring(start);
        }

        public String subString(int start, int end) {
            return source.substring(start, end);
        }
    }
    
    protected static class Memo {
        private int start;
        private int end;
        private PEGNode node;
        
        public Memo(int start, int end, PEGNode node) {
            this.start = start;
            this.end = end;
            this.node = node;
        }
        public int getStart() {
            return start;
        }
        public int getEnd() {
            return end;
        }
        public PEGNode getNode() {
            return node;
        }
    }

    public static class Statistics {
        private AtomicInteger success;
        private AtomicInteger failure;

        public Statistics() {
            success = new AtomicInteger();
            failure = new AtomicInteger();
        }
        public void success() {
            success.incrementAndGet();
        }
        
        public void failure() {
            failure.incrementAndGet();
        }
        
        public int getSuccess() {
            return success.get();
        }
        
        public int getFailure() {
            return failure.get();
        }
    }

    private enum Types implements RuleTypes {
        PEG_ANY, PEG_AND, PEG_NOT, PEG_LITERAL, PEG_CLASS, PEG_REGEXP,
        PEG_ZERO_OR_MORE, PEG_ONE_OR_MORE, PEG_SEQUENCE, PEG_CHOICE, PEG_OPTIONAL;
    }

    public interface RuleTypes {
        public String name();
    }
    
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ 
        ElementType.METHOD 
    })
    public @interface RuleName {
        
    }
}
