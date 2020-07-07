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

import org.junit.Test;

import com.shorindo.tools.ToolsLogger;
import com.shorindo.tools.VirtualCharacter;
import com.shorindo.tools.VirtualTerminal;

/**
 * 
 */
public class VirtualTerminalTest {
    private static final ToolsLogger LOG = ToolsLogger.getLogger(VirtualTerminalTest.class);

    @Test
    public void testConstructor() {
        new VirtualTerminal(24, 80);
    }

    @Test
    public void testScroll() {
        VirtualTerminal terminal = new VirtualTerminal(24, 80);
        for (int row = 0; row < 24; row++) {
            terminal.moveTo(row, 0);
            String num = Integer.toString(row);
            for (int col = 0; col < num.length(); col++) {
                terminal.addChar(num.charAt(col));
            }
        }
        dump(terminal);
        terminal.scroll(1);
        dump(terminal);
        terminal.scroll(-2);
        dump(terminal);
    }

    @Test
    public void testMoveTo() {
        VirtualTerminal terminal = new VirtualTerminal(24, 80);
        terminal.moveTo(23, 79);
    }

    @Test
    public void testAddChar() {
        String seeds = "0123456789";
        VirtualTerminal terminal = new VirtualTerminal(24, 80);
        for (int i = 0; i < 2000; i++) {
            terminal.addChar(seeds.charAt(i % 10));
        }
        dump(terminal);
    }

    @Test
    public void testClear() {
        String seeds = "0123456789";
        VirtualTerminal terminal = new VirtualTerminal(24, 80);
        for (int i = 0; i < 2000; i++) {
            terminal.addChar(seeds.charAt(i % 10));
        }
        terminal.clear();
        dump(terminal);
    }

    public void dump(VirtualTerminal terminal) {
        StringBuffer sb = new StringBuffer();
        for (int row = 0; row < 24; row++) {
            for (int col = 0; col < 80; col++) {
                VirtualCharacter ch = terminal.getChar(row, col);
                if (ch == null) ch = new VirtualCharacter(' ');
                sb.append(ch);
            }
            sb.append('\n');
        }
        LOG.debug(sb.toString());
    }
}
