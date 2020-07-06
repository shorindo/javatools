/*
 * Copyright 2017-2019 Shorindo, Inc.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PEGによる構文解析器を生成する
 */
public class PEGCombinator {
    private static final ToolsLogger LOG = ToolsLogger.getLogger(PEGCombinator.class);
    private static final UnmatchException UNMATCH = new UnmatchException();
    private Map<String,Rule> ruleMap;

    /**
     * 
     */
    public PEGCombinator() {
        ruleMap = new HashMap<String,Rule>();
    }

    protected Rule rule(final String ruleName) {
        if (!ruleMap.containsKey(ruleName)) {
            ruleMap.put(ruleName, new Rule(ruleName) {
                @Override
                public Node accept(BacktrackInputStream is) throws IOException,
                        UnmatchException {
                    Node $$ = new Node(ruleName);
                    $$.setName(ruleName);
                    for (Rule rule : childRules) {
                        $$.add(rule.accept(is));
                    }
                    LOG.debug("rule[" + ruleName + "] : accept");
                    return $$;
                }
            });
        }
        return ruleMap.get(ruleName);
    }

    protected Rule rule$Any() {
        return new Rule("rule$Any") {
            @Override
            public Node accept(BacktrackInputStream is) throws IOException, UnmatchException {
                int c = is.read();
                if (c == -1) {
                    throw UNMATCH;
                } else {
                    LOG.trace("rule$Any() : accept <= " + (char)c);
                    Node $$ = new Node("any");
                    $$.setValue((char)c);
                    return $$;
                }
            }
        };
    }

// FIXME 子ルールが不一致のとき、入力が巻き戻されてしまう
//    protected Rule rule$Not(final Rule rule) {
//        return new Rule("rule$Not") {
//            @Override
//            public Node accept(BacktrackInputStream is) throws IOException, UnmatchException {
//                int pos = is.position();
//                try {
//                    rule.accept(is);
//                } catch (UnmatchException e) {
//                    LOG.debug("rule$Not() <= " + rule);
//                    return new Node("not");
//                }
//                is.reset(pos);
//                throw UNMATCH;
//            }
//        };
//    }

    protected Rule rule$Literal(final String literal) {
        return new Rule("rule$Literal") {
            @Override
            public Node accept(BacktrackInputStream is) throws IOException, UnmatchException {
                int mark = is.position();
                for (int i = 0; i < literal.length(); i++) {
                    char c = literal.charAt(i);
                    int r = is.read();
                    LOG.debug("rule$Literal('" + format(literal) + "') <= " + (char)r);
                    if (c != r) {
                        is.reset(mark);
                        throw UNMATCH;
                    }
                }
                LOG.debug("rule$Literal('" + format(literal) + "') : accept");
                Node $$ = new Node("literal");
                $$.setValue(literal);
                return $$;
            }
        };
    }
    
    protected Rule rule$Class(final String charClass) {
        return new Rule("rule$Class") {
            Pattern pattern = Pattern.compile("[" + charClass + "]");

            @Override
            public Node accept(BacktrackInputStream is) throws IOException,
                    UnmatchException {
                int curr = is.position();
                int c = is.read();
                if (c == -1) {
                    throw UNMATCH;
                }
                Matcher m = pattern.matcher(String.valueOf((char)c));
                if (m.matches()) {
                    Node $$ = new Node("class");
                    $$.setValue((char)c);
                    LOG.debug("rule$Class('" + format(charClass) + "') : accept <= " + (char)c);
                    return $$;
                } else {
                    is.reset(curr);
                    throw UNMATCH;
                }
            }
        };
    }
    protected Rule rule$ZeroOrMore(final Rule...rules) {
        return new Rule("rule$ZeroOrMore") {
            @Override
            public Node accept(BacktrackInputStream is) throws IOException,
                    UnmatchException {
                Node $$ = new Node("*");
                while (true) {
                    int curr = is.position();
                    try {
                        for (Rule child : rules) {
                            $$.add(child.accept(is));
                        }
                    } catch (UnmatchException e) {
                        is.reset(curr);
                        break;
                    }
                }
                LOG.trace("rule$ZeroOrMore[" + toString(rules) + "] : accept");
                return $$;
            }
        };
    }
    protected Rule rule$OneOrMore(final Rule...rules) {
        return new Rule("rule$OneOrMore") {
            @Override
            public Node accept(BacktrackInputStream is) throws IOException,
                    UnmatchException {
                int count = 0;
                Node $$ = new Node("+");
                while (true) {
                    int curr = is.position();
                    try {
                        for (Rule child : rules) {
                            $$.add(child.accept(is));
                        }
                        count++;
                    } catch (UnmatchException e) {
                        is.reset(curr);
                        break;
                    }
                }
                if (count > 0) {
                    LOG.trace("rule$ZeroOrMore[" + toString(rules) + " : accept");
                    return $$;
                } else {
                    throw UNMATCH;
                }
            }
        };
    }
    protected Rule rule$Sequence(final Rule...rules) {
        return new Rule("rule$Sequence") {
            @Override
            public Node accept(BacktrackInputStream is) throws IOException,
                    UnmatchException {
                //LOG.trace(toString());
                int curr = is.position();
                Node $$ = new Node("sequence");
                try {
                    for (Rule child : rules) {
                        Node $n = (Node)child.accept(is);
                        $$.add($n);
                    }
                } catch (UnmatchException e) {
                    is.reset(curr);
                    throw e;
                }
                LOG.trace("rule$Sequence <= " + toString(rules));
                return $$;
            }
        };
    }
    
    protected Rule rule$Choice(final Rule...rules) {
        return new Rule("rule$Choice") {
            @Override
            public Node accept(BacktrackInputStream is) throws IOException,
                    UnmatchException {
                //LOG.trace(toString());
                int curr = is.position();
                for (Rule child : rules) {
                    try {
                        LOG.debug("rule$Choice(" + toString(child) + ")");
                        return child.accept(is);
                    } catch (UnmatchException e) {
                        is.reset(curr);
                    }
                }
                throw UNMATCH;
            }
        };
    }

    protected Rule rule$Optional(final Rule...rules) {
        return new Rule("rule$Optional") {
            @Override
            public Node accept(BacktrackInputStream is) throws IOException,
                    UnmatchException {
//                for (Rule child : rules) {
//                    childRules.add(child);
//                }

                int curr = is.position();
                Node $$ = new Node("optional");
                try {
                    for (Rule child : rules) {
                        $$.add(child.accept(is));
                    }
                    LOG.trace("rule$Optional() <= " + toString(rules));
                } catch (UnmatchException e) {
                    $$.clear();
                    is.reset(curr);
                }
                return $$;
            }
        };
    }

    protected String format(String s) {
        return s
                .replaceAll("\\\\", "\\\\")
                .replaceAll("\r", "\\\\r")
                .replaceAll("\n", "\\\\n")
                .replaceAll("\t", "\\\\t");
        
    }

    @SuppressWarnings("serial")
    public static class UnmatchException extends Exception {
        public UnmatchException() {}
        public UnmatchException(String msg) { super(msg); }
        public UnmatchException(Exception e) { super(e); }
    }
    
    /**
     * 
     */
    public static abstract class Rule {
        protected String ruleName;
        protected List<Rule> childRules = new ArrayList<Rule>();

        public abstract Node accept(BacktrackInputStream is)
                throws IOException, UnmatchException;

        public Rule(String ruleName) {
            this.ruleName = ruleName;
        }

        public Rule define(Rule...rules) {
            for (Rule child : rules) {
                childRules.add(child);
            }
            return this;
        }

        public Rule get(int i) {
            return childRules.get(i);
        }

        public String toString(Rule...rules) {
            StringBuffer sb = new StringBuffer(this.ruleName);
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
            return toString(childRules.toArray(new Rule[]{}));
        }
    }

    /**
     * 
     */
    public static class Node {
        private String name;
        private Object value;
        private boolean empty = false;
        private List<Node> childList = new ArrayList<Node>();

        public Node(String name) {
            this.name = name;
        }
        public String getName() {
            return name;
        }
        public void setName(String name) {
            this.name = name;
        }
        public Object getValue() {
            return value;
        }
        public void setValue(Object value) {
            this.value = value;
        }
        public boolean isEmpty() {
            return empty;
        }
        public void setEmpty(boolean empty) {
            this.empty = empty;
        }
        public void add(Node child) {
            childList.add(child);
        }
        public Node get(int i) {
            return childList.get(i);
        }
        public int length() {
            return childList.size();
        }
        public void clear() {
            childList.clear();
        }
        public String getSource() {
            StringBuilder sb = new StringBuilder();
            if (value != null) {
                sb.append(String.valueOf(getValue()));
            }
            for (Node child : childList) {
                sb.append(child.getSource());
            }
            return sb.toString();
        }
        public String toString() {
            if (this.isEmpty()) {
                return "";
            }
            StringBuffer sb = new StringBuffer();
            sb.append("(");
            sb.append(getName());
            for (Node child : childList) {
                sb.append(" " + child.toString());
            }
            sb.append(")");
            return sb.toString();
        }
    }

    /**
     * 
     */
    public static class BacktrackInputStream extends InputStream {
        private InputStream is;
        private List<Byte> buffer;
        private int position = 0;

        /**
         * @param is
         */
        public BacktrackInputStream(InputStream is) {
            this.is = is;
            this.buffer = new ArrayList<Byte>();
        }
        public void reset(int position) {
            this.position = position;
        }
        public int position() {
            return position;
        }
        public void clear() {
            position = 0;
            buffer = new ArrayList<Byte>();
        }
        @Override
        public int read() throws IOException {
            int c = -1;
            if (buffer.size() == position) {
                c = is.read();
                if (c != -1) {
                    //bos.write(c);
                    buffer.add((byte)c);
                    position++;
                }
            } else {
                c = buffer.get(position++);
            }
            return c;
        }
        @Override
        public int available() throws IOException {
            return buffer.size() - position;
        }
    }
}