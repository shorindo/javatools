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

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

import com.shorindo.tools.TermInfo;
import com.shorindo.tools.ToolsLogger;
import com.shorindo.tools.PEGCombinator.Node;

/**
 * 
 */
public class TermInfoTest {
    private final static ToolsLogger LOG = ToolsLogger.getLogger(TermInfoTest.class);

    private InputStream createStream(String text) {
        return new ByteArrayInputStream(text.getBytes());
    }

    @Test
    public void testComment1() throws Exception {
        TermInfo termInfo = new TermInfo();
        termInfo.parse(createStream("#comment\n"));
    }

    @Test
    public void testComment2() throws Exception {
        TermInfo termInfo = new TermInfo();
        termInfo.parse(createStream("#comment\n#comment\n"));
    }

    @Test
    public void testStart1() throws Exception {
        TermInfo termInfo = new TermInfo();
        Node node = termInfo.parse(createStream("xterm-color|xterm color,am,cols#80,bl=^G\n"));
        LOG.info(node.getSource());
        assertEquals("xterm-color|xterm color,am,cols#80,bl=^G\n", node.getSource());
    }

    @Test
    public void testStart2() throws Exception {
        TermInfo termInfo = new TermInfo();
        Node node = termInfo.parse(createStream("xterm-color|xterm color,am,cols#80,bl=^G,\n"));
        LOG.info(node.getSource());
        assertEquals("xterm-color|xterm color,am,cols#80,bl=^G,\n", node.getSource());
    }

    @Test
    public void testContinue1() throws Exception {
        String source = "xterm-color,\n\tam,\n cols#80,\n   bl=^G\n";
        TermInfo termInfo = new TermInfo();
        Node node = termInfo.parse(createStream(source));
        LOG.info(node.getSource());
        assertEquals(source, node.getSource());
    }

    @Test
    public void testXterm() throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("terminfo");
        TermInfo termInfo = new TermInfo();
        Node node = termInfo.parse(is);
//        LOG.info(node.getSource());
        nodeWalk(node);
    }

    private void nodeWalk(Node node) {
        switch (node.getName()) {
        case "CAP_ID":
            LOG.info("CAP_ID=" + node.getSource());
            break;
        case "VAR_NAME":
            LOG.info("\tVAR_NAME=" + node.getSource());
            break;
//        case "VAR_BOOL":
//            LOG.info("\tVAR_BOOL=" + node.getSource());
//            break;
//        case "VAR_NUMBER":
//            LOG.info("\tVAR_NUMBER=" + node.getSource());
//            break;
//        case "VAR_STRING":
//            LOG.info("\tVAR_STRING=" + node.getSource());
//            break;
        default:
        }

        for (int i = 0; i < node.length(); i++) {
            nodeWalk(node.get(i));
        }
    }
}
