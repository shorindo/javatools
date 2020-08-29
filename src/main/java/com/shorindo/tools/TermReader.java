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
public class TermReader extends Reader {
    private Reader reader;
    private List<Character> buffer;
    private int position = 0;
    private State start;
    private State current;
    private List<ScreenEventListener> listeners;

    protected TermReader(Reader in) {
        reader = in;
        buffer = new ArrayList<>();
        listeners = new ArrayList<>();
        start = new State(0);
    }

    public void addScreenEventListener(ScreenEventListener listener) {
        listeners.add(listener);
    }

    @Override
    public int read() throws IOException {
        if (position < buffer.size() - 1) {
            return buffer.get(position++);
        } else {
            int c = reader.read();
            if (c != -1) {
                buffer.add((char)c);
                position++;
            }
            return c;
        }
    }

    @Override
    public int read(char[] cbuf, int off, int len) throws IOException {
        int c;
        int count = 0;
        while ((c = read()) != -1 && count < len) {
            cbuf[off++] = (char)c;
            count++;
        }
        return count;
    }

    @Override
    public void close() throws IOException {
        buffer.clear();
        reader.close();
    }
    
    public int markStart() {
        buffer.clear();
        return buffer.size();
    }

    public void markEnd() {
        buffer.clear();
    }

    public void reset() {
        buffer.clear();
        position = 0;
    }

    public int mark() {
        return position;
    }

    public void rewind(int position) {
        if (position < 0) {
            this.position = 0;
        } else if (position >= buffer.size()) {
            this.position = buffer.size() - 1;
        } else {
            this.position = position;
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
         */
        public void walk(PushbackReader r) throws IOException, UnmatchException {
            r.mark(10);
            int c = r.read();
            State next = null;
            if (targetMap.size() == 0) {
                // 終端
            } else if ('0' <= c && c <= '9' && targetMap.containsKey(Terminfo.PARAM_D)) {
                next = targetMap.get(Terminfo.PARAM_D);
            } else if (targetMap.containsKey(c)) {
                next = targetMap.get(c);
            } else {
                throw new UnmatchException();
            }
            try {
                next.walk(r);
            } catch (UnmatchException e) {
                r.unread(c);
            }
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
