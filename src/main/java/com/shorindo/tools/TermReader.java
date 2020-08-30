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

import java.io.FilterReader;
import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.shorindo.tools.Terminal.Edge;
import com.shorindo.tools.Terminal.ScreenEventListener;

/**
 * 
 */
public class TermReader {
    private BacktrackReader reader;
    private State start;
    private List<ScreenEventListener> listeners;

    protected TermReader(Reader in) {
        reader = new BacktrackReader(in);
        listeners = new ArrayList<>();
        start = new State(0);
    }

    public void addScreenEventListener(ScreenEventListener listener) {
        listeners.add(listener);
    }

    public int read() throws IOException {
        //while (start.accept(reader));
        return reader.read();
    }

    public static class BacktrackReader extends FilterReader {
        private List<Integer> buffer;
        private int pos = -1;

        public BacktrackReader(Reader in) {
            super(in);
            buffer = new ArrayList<>();
        }

        @Override
        public int read() throws IOException {
            if (pos == -1) {
                return super.read();
            } else if (pos < buffer.size()) {
                return buffer.get(pos++);
            } else {
                int c = super.read();
                buffer.add(c);
                pos = buffer.size();
                return c;
            }
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            int count = 0;
            for (int i = off; i < len; i++) {
                int c = read();
                if (c == -1) {
                    break;
                } else {
                    cbuf[i] = (char)c;
                    count++;
                }
            }
            return count;
        }

        public int mark() {
            return pos = buffer.size();
        }

        public void rewind(int pos) {
            if (this.pos == -1) {
                return;
            } else if (pos < 0) {
                pos = 0;
            } else if (pos < this.pos) {
                this.pos = pos;
            }
        }

        public void reset() {
            pos = -1;
            buffer.clear();
        }
    }

    /**
     * 
     */
    public class State {
        private int id;
        private Set<String> actions;
        private Map<Integer,State> targetMap;

        public State(int id) {
            this.id = id;
            this.actions = new HashSet<>();
            this.targetMap = new HashMap<>();
        }

        public int getId() {
            return id;
        }

        public void addAction(String action) {
            this.actions.add(action);
        }

        public Set<String> getActions() {
            return actions;
        }

        /*
         * 一直線にたどり着くパターン/分岐のあるパターン
         * 重複のあるパターン
         * 途中で一致しないパターン
         * 最初から一致しないパターン
         * 
         * (0) - (1) + (2)
         *           + (3) + (4)
         *                 + (5)
         */
        public boolean accept(BacktrackReader r) throws IOException {
            int c = r.read();
            if (targetMap.size() == 0) {
                doAction();
                return true;
            } else if ('0' <= c && c <= '9' && targetMap.containsKey(Terminfo.PARAM_D)) {
                boolean b = targetMap.get(Terminfo.PARAM_D).accept(r);
                return b;
            } else if (targetMap.containsKey(c)) {
                boolean b = targetMap.get(c).accept(r);
                return b;
            } else {
                return false;
            }
        }

        private void doAction() {
        }

        public String toString() {
            return String.valueOf(id);
        }
    }

    public static class Edge {
        private State source;
        private State target;
        private int event;

        public Edge(State source, State target) {
            this.source = source;
            this.target = target;
        }

        public State getSource() {
            return source;
        }

        public State getTarget() {
            return target;
        }

        public void setEvent(int event) {
            this.event = event;
        }

        public int getEvent() {
            return event;
        }

        public String toString() {
            return "[" + source + ", " + target + "]";
        }
    }

    public class UnmatchException extends Exception {
        private static final long serialVersionUID = 1L;
    }
}
