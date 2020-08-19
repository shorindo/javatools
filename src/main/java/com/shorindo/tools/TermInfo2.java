package com.shorindo.tools;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TermInfo2 {
    public static TermInfo2 compile(String source) {
        return null;
    }

    private String name;
    private List<Capability> capabilities;

    private TermInfo2(String name) {
        this.name = name;
        this.capabilities = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    private void addCapability(Capability cap) {
        capabilities.add(cap);
    }

    public List<Capability> getCapabilities() {
        return Collections.unmodifiableList(capabilities);
    }

    public static class Capability {
        private String name;
        private Object data;

        public Capability(String name, Object data) {
            this.name = name;
            this.data = data;
        }

        public String getName() {
            return name;
        }

        public Object getData() {
            return data;
        }
    }

}
