package com.shorindo.tools;

import java.io.FilterOutputStream;
import java.io.FilterWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Termcap {
    private static final Logger LOG = Logger.getLogger(Termcap.class);
    private static final PEGCombinator TC = new PEGCombinator();
    private static final int NUMERIC = 0x1FFFF;
    private static final int OTHER = 0x8FFFF;
    static {
        // TC.define(Types.ENTRY, rules);
    }

    private InputStream is;
    private State init = new State();
    private State curr = init;

    public Termcap(InputStream is) {
        this.is = is;
    }

    public void define(String capability, int[] seq) {
        init.add(capability, seq);
    }

    public int read() throws IOException {
        int c;
        while ((c = is.read()) > 0) {
            try {
                curr = curr.accept((char) c);
            } catch (ParseException e) {
                curr = init;
                return c;
            }
        }
        return 0;
    }

    Set<State> visited;

    public void print() {
        visited = new HashSet<>();
        print(init, 0);
    }

    private void print(State curr, int level) {
        // visited.add(curr);
        StringBuffer indent = new StringBuffer();
        for (int i = 0; i < level; i++)
            indent.append("    ");
        for (Map.Entry<Character, State> entry : curr.stateMap.entrySet()) {
            char key = entry.getKey();
            State state = entry.getValue();
            indent.append(key + "->");
            System.out.println(indent.toString());
            if (!visited.contains(state)) {
                visited.add(state);
                print(state, level + 1);
            }
        }
    }

    private static enum Types {
        ENTRY
    }

    private static class TermcapWriter extends FilterWriter {

        public TermcapWriter(Writer out) {
            super(out);
        }

        @Override
        public void write(int b) throws IOException {
            super.write(b);
        }
        
    }

    private static class State {
        private static final String NUMCHAR = "0123456789";
        private Map<Character, State> stateMap = new HashMap<>();
        private String capability;
        private boolean term = false;

        public State accept(char c) throws ParseException {
            State next = stateMap.get(c);
            if (next != null) {
                return next;
            } else {
                throw new ParseException();
            }
        }

        public void action() {
        }

        public boolean isTerm() {
            return term;
        }

        public void add(String capability, int[] seq) {
            if (seq.length == 0) {
                return;
            } else if (seq.length == 1) {
                this.capability = capability;
                this.term = term;
            }
            int c = seq[0];
            switch (c) {
            case NUMERIC:
                for (int i = 0; i < NUMCHAR.length(); i++) {
                    char n = NUMCHAR.charAt(i);
                    if (!stateMap.containsKey(n)) {
                        stateMap.put(n, this);
                    }
                    this.add(capability, Arrays.copyOfRange(seq, 1, seq.length));
                }
                break;
            default:
                if (!stateMap.containsKey((char) c)) {
                    State next = new State();
                    stateMap.put((char) c, next);
                    next.add(capability, Arrays.copyOfRange(seq, 1, seq.length));
                }
            }
        }
    }

    private static class ParseException extends Exception {
    }

    public static class StateMachine {
        private static final String NUMCHAR = "0123456789";
        private List<Node> nodes;
        private List<Edge> edges;
        private Node start;
        private Node curr;

        public StateMachine() {
            nodes = new ArrayList<>();
            edges = new ArrayList<>();
            start = new Node(0);
            curr = start;
            nodes.add(start);
        }

        public void define(String action, int[] seq) {
            dig(start, action, seq);
        }

        private void dig(Node source, String action, int[] seq) {
            // LOG.debug("dig(" + source.getId() + ")");
            if (seq.length == 0) {
                source.setAction(action);
                return;
            }
            int event = seq[0];
            Optional<Edge> optEdge = edges.stream().filter(e -> {
                return e.getSource() == source && e.getEvent() == event;
            }).findFirst();
            if (optEdge.isPresent()) {
                dig(optEdge.get().getTarget(), action,
                    Arrays.copyOfRange(seq, 1, seq.length));
            } else {
                Node target = new Node(nodes.size());
                nodes.add(target);
                Edge edge = new Edge(source, target);
                edge.setEvent(event);
                edges.add(edge);
                dig(target, action, Arrays.copyOfRange(seq, 1, seq.length));

                if (event == NUMERIC) {
                    edge = new Edge(target, target);
                    edge.setEvent(event);
                    edges.add(edge);
                }
            }
        }

        public List<List<Node>> findPath(Node source, Set<Edge> visited) {
            // LOG.debug("findPath(" + source.getId() + ")");
            List<List<Node>> result = new ArrayList<>();
            List<Edge> sources = edges.stream().filter(e -> {
                return e.getSource() == source;
            }).collect(Collectors.toList());
            if (sources.size() == 0) {
                List<Node> nodeList = new ArrayList<>();
                nodeList.add(source);
                result.add(nodeList);
                return result;
            }
            for (Edge edge : sources) {
                if (visited.contains(edge)) {
                    continue;
                } else if (edge.getSource() == edge.getTarget()) {
                    continue;
                } else {
                    visited.add(edge);
                }
                List<List<Node>> pathList = findPath(edge.getTarget(), visited);
                for (List<Node> list : pathList) {
                    list.add(0, source);
                }
                result.addAll(pathList);
            }
            return result;
        }

        List<Integer> buffer = new ArrayList<>();
        public void write(int c) {
            buffer.add(c);
            Optional<Edge> optEdge = edges.stream()
                .filter(e -> {
                    return e.getSource() == curr && match(e.getEvent(), c);
                })
                .findFirst();
            if (optEdge.isPresent()) {
            	if (optEdge.get().getEvent() == 0x1FFFF) {
                    numbuffer.add(c);
            	} else if (numbuffer.size() > 0) {
                    int r = 0;
                    for (int i : numbuffer) {
                        r = r * 10 + (i - '0');
                    }
                    params.add(r);
                    numbuffer.clear();
            	}
            	curr = optEdge.get().getTarget();
                if (curr.getAction() != null) {
                	switch (curr.getAction()) {
                	case "AL":
                		int param = params.get(0);
                		params.remove(0);
                		LOG.debug("cmd_" + curr.getAction() + "(" + param + ")");
                		break;
                	case "cs": 
                		int p1 = params.get(0);
                		int p2 = params.get(1);
                		params.remove(0);
                		params.remove(0);
                		LOG.debug("cmd_" + curr.getAction() + "(" + p1 + "," + p2 + ")");
                		break;
                	default:
                		LOG.debug("cmd_" + curr.getAction() + "()");
                	}
                    curr = start;
                    buffer.clear();
                }
            } else {
                // バッファ + c を吐き出す
                for (int b : buffer) {
                    LOG.debug("put(" + (char)b + ")");
                }
                curr = start;
                buffer.clear();
            }
        }

        private List<Integer> numbuffer = new ArrayList<>();
        private List<Integer> params = new ArrayList<>();
        private boolean match(int expect, int actual) {
            if (expect == 0x1FFFF && '0' <= actual && actual <= '9') {
                return true;
            } else {
                return expect == actual;
            }
        }

        public String toString() {
            Set<Edge> visited = new HashSet<>();
            StringBuffer sb = new StringBuffer();
            for (List<Node> path : findPath(start, visited)) {
                // LOG.debug("path=" + path);
                String sep = "";
                Node last = null;
                for (Node node : path) {
                    sb.append(sep + node.getId());
                    sep = " -> ";
                    last = node;
                }
                sb.append(" : " + last.getAction());
                sb.append("\n");
            }
            return sb.toString();
        }
    }

    public static class Node {
        private int id;
        private String action;

        public Node(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }

        public void setAction(String action) {
            this.action = action;
        }

        public String getAction() {
            return action;
        }

        public String toString() {
            return String.valueOf(id);
        }
    }

    public static class Edge {
        private Node source;
        private Node target;
        private int event;

        public Edge(Node source, Node target) {
            this.source = source;
            this.target = target;
        }

        public Node getSource() {
            return source;
        }

        public Node getTarget() {
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
}
