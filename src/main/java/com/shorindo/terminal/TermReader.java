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
package com.shorindo.terminal;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.shorindo.terminal.Terminal.ScreenEventListener;
import com.shorindo.terminal.Terminfo.Capability;
import com.shorindo.terminal.Terminfo.TerminfoException;

/**
 * 
 */
public class TermReader {
    private BacktrackReader reader;
    private State start;
    private List<ScreenEventListener> listeners;
    private List<State> states;
    private List<Edge> edges;

    protected TermReader(Reader in) {
        reader = new BacktrackReader(in);
        listeners = new ArrayList<>();
        states = new ArrayList<>();
        edges = new ArrayList<>();
        start = new State(0);
        states.add(start);
        
        try {
            Terminfo info = Terminfo.compile(
                "# Reconstructed via infocmp from file: /usr/share/terminfo/78/xterm\n" +
                "xterm|xterm terminal emulator (X Window System),\n" +
                "  am, km, mir, msgr, xenl,\n" +
                "  cols#80, it#8, lines#24,\n" +
                "  acsc=``aaffggiijjkkllmmnnooppqqrrssttuuvvwwxxyyzz{{||}}~~,\n" +
                "  bel=^G, bold=\\E[1m, clear=\\E[H\\E[2J, cr=^M,\n" +
                "  csr=\\E[%i%p1%d;%p2%dr, cub=\\E[%p1%dD, cub1=^H,\n" +
                "  cud=\\E[%p1%dB, cud1=^J, cuf=\\E[%p1%dC, cuf1=\\E[C,\n" +
                "  cup=\\E[%i%p1%d;%p2%dH, cuu=\\E[%p1%dA, cuu1=\\E[A,\n" +
                "  dch=\\E[%p1%dP, dch1=\\E[P, dl=\\E[%p1%dM, dl1=\\E[M, ed=\\E[J,\n" +
                "  el=\\E[K, enacs=\\E)0, .home=\\E[H, ht=^I, hts=\\EH, il=\\E[%p1%dL,\n" +
                "  il1=\\E[L, ind=^J,\n" +
                "  is2=\\E7\\E[r\\E[m\\E[?7h\\E[?1;3;4;6l\\E[4l\\E8\\E>, kbs=^H,\n" +
                "  kcub1=\\EOD, kcud1=\\EOB, kcuf1=\\EOC, kcuu1=\\EOA,\n" +
                "  kdch1=\\E[3~, kf1=\\E[11~, kf10=\\E[21~, kf11=\\E[23~,\n" +
                "  kf12=\\E[24~, kf13=\\E[25~, kf14=\\E[26~, kf15=\\E[28~,\n" +
                "  kf16=\\E[29~, kf17=\\E[31~, kf18=\\E[32~, kf19=\\E[33~,\n" +
                "  kf2=\\E[12~, kf20=\\E[34~, kf3=\\E[13~, kf4=\\E[14~,\n" +
                "  kf5=\\E[15~, kf6=\\E[17~, kf7=\\E[18~, kf8=\\E[19~, kf9=\\E[20~,\n" +
                "  kfnd=\\E[1~, kich1=\\E[2~, kmous=\\E[M, knp=\\E[6~, kpp=\\E[5~,\n" +
                "  kslt=\\E[4~, rc=\\E8, rev=\\E[7m, ri=\\EM, rmacs=^O,\n" +
                "  rmcup=\\E[2J\\E[?47l\\E8, rmir=\\E[4l, rmkx=\\E[?1l\\E>,\n" +
                "  rmso=\\E[m, rmul=\\E[m,\n" +
                "  rs2=\\E7\\E[r\\E8\\E[m\\E[?7h\\E[?1;3;4;6l\\E[4l\\E>, sc=\\E7,\n" +
                "  sgr0=\\E[m, smacs=^N, smcup=\\E7\\E[?47h, smir=\\E[4h,\n" +
                "  smkx=\\E[?1h\\E=, smso=\\E[7m, smul=\\E[4m, tbc=\\E[3g,\n" +
                "  u6=\\E[%i%d;%dR, u7=\\E[6n, u8=\\E[?1;2c, u9=\\E[c,\n");
            for (Capability cap : info.getCapabilities()) {
                if (cap.getType() == null) continue;
                if (cap.getData() instanceof List) {
                    List<Integer> list = ((List<Integer>)cap.getData())
                        .stream()
                        .filter(i -> {
                            switch (i) {
                            case Terminfo.PARAM_I:
                            case Terminfo.PARAM_P1:
                            case Terminfo.PARAM_P2:
                            case Terminfo.PARAM_P3:
                            case Terminfo.PARAM_P4:
                            case Terminfo.PARAM_P5:
                            case Terminfo.PARAM_P6:
                            case Terminfo.PARAM_P7:
                            case Terminfo.PARAM_P8:
                            case Terminfo.PARAM_P9:
                                return false;
                            default:
                                return true;
                            }
                        })
                        .collect(Collectors.toList());
                    int array[] = new int[list.size()];
                    for (int i = 0; i < list.size(); i++) {
                        array[i] = list.get(i);
                    }
                    define(cap.getType().getCapCode(), array);
                }
            }
        } catch (TerminfoException e) {
            e.printStackTrace();
        }

    }

    public void define(String action, int[] seq) {
        define(start, action, seq);
    }

    private void define(State source, String action, int[] seq) {
        // LOG.debug("dig(" + source.getId() + ")");
        if (seq.length == 0) {
            source.addAction(action);
            return;
        }
        int event = seq[0];
        Optional<Edge> optEdge = edges.stream().filter(e -> {
            return e.getSource() == source && e.getEvent() == event;
        }).findFirst();
        if (optEdge.isPresent()) {
            define(optEdge.get().getTarget(), action,
                Arrays.copyOfRange(seq, 1, seq.length));
        } else {
            State target = new State(states.size());
            states.add(target);
            Edge edge = new Edge(source, target);
            edge.setEvent(event);
            edges.add(edge);
            source.addEdge(edge);
            define(target, action, Arrays.copyOfRange(seq, 1, seq.length));

            if (event == Terminfo.PARAM_D) {
                edge = new Edge(target, target);
                edge.setEvent(event);
                edges.add(edge);
                target.addEdge(edge);
            }
        }
    }

    public List<List<Edge>> findPath(State source, Set<Edge> visited) {
        // LOG.debug("findPath(" + source.getId() + ")");
        List<List<Edge>> result = new ArrayList<>();
        List<Edge> edgeList = edges.stream().filter(e -> {
            return e.getSource() == source;
        }).collect(Collectors.toList());
        if (edgeList.size() == 0) {
            List<Edge> stateList = new ArrayList<>();
            result.add(stateList);
            return result;
        }
        for (Edge edge : edgeList) {
            if (visited.contains(edge)) {
                continue;
            } else if (edge.getSource() == edge.getTarget()) {
                continue;
            } else {
                visited.add(edge);
            }
            List<List<Edge>> pathList = findPath(edge.getTarget(), visited);
            for (List<Edge> list : pathList) {
                list.add(0, edge);
            }
            result.addAll(pathList);
        }
        return result;
    }

    public void addScreenEventListener(ScreenEventListener listener) {
        listeners.add(listener);
    }

    public int read() throws IOException {
        while (start.accept(reader)) {
            reader.reset();
        }
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

        public void addEdge(Edge edge) {
            edges.add(edge);
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
