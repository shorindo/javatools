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

/**
 * 
 */
public class TerminfoTest {
    private final static Logger LOG = Logger.getLogger(TerminfoTest.class);

    @Test
    public void testComment1() throws Exception {
        Terminfo terminfo = Terminfo.compile("#comment\n");
    }

    @Test
    public void testComment2() throws Exception {
        Terminfo terminfo = Terminfo.compile("#comment\n#comment\n");
    }

    @Test
    public void testStart1() throws Exception {
        Terminfo termInfo = Terminfo.compile("xterm-color|xterm color,am,cols#80,bl=^G\n");
        //assertEquals("xterm-color|xterm color,am,cols#80,bl=^G\n", node.getSource());
    }

    @Test
    public void testStart2() throws Exception {
        Terminfo termInfo = Terminfo.compile("xterm-color|xterm color,am,cols#80,bl=^G,\n");
        //assertEquals("xterm-color|xterm color,am,cols#80,bl=^G,\n", node.getSource());
    }

    @Test
    public void testContinue1() throws Exception {
        String source = "xterm-color,\n\tam,\n cols#80,\n   bl=^G\n";
        Terminfo terminfo = Terminfo.compile(source);
        //assertEquals(source, node.getSource());
    }

    @Test
    public void testXterm() throws Exception {
        String info =
            "#       Reconstructed via infocmp from file: /usr/share/terminfo/78/xterm\n" +
            "xterm|xterm terminal emulator (X Window System),\n" +
            "  am, km, mir, msgr, xenl,\n" +
            "  cols#80, it#8, lines#24,\n" +
            "  acsc=``aaffggiijjkkllmmnnooppqqrrssttuuvvwwxxyyzz{{||}}~~,\n" +
            "  bel=^G, bold=\\E[1m, clear=\\E[H\\E[2J, cr=^M,\n" +
            "  csr=\\E[%i%p1%d;%p2%dr, cub=\\E[%p1%dD, cub1=^H,\n" +
            "  cud=\\E[%p1%dB, cud1=^J, cuf=\\E[%p1%dC, cuf1=\\E[C,\n" +
            "  cup=\\E[%i%p1%d;%p2%dH, cuu=\\E[%p1%dA, cuu1=\\E[A,\n" +
            "  dch=\\E[%p1%dP, dch1=\\E[P, dl=\\E[%p1%dM, dl1=\\E[M, ed=\\E[J,\n" +
            "  el=\\E[K, enacs=\\E)0, home=\\E[H, ht=^I, hts=\\EH, il=\\E[%p1%dL,\n" +
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
            "  u6=\\E[%i%d;%dR, u7=\\E[6n, u8=\\E[?1;2c, u9=\\E[c,\n";
        Terminfo terminfo = Terminfo.compile(info);
    }

}
