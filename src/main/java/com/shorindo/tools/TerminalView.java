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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * 
 */
public class TerminalView extends WindowAdapter implements ComponentListener {
    private static final ToolsLogger LOG = ToolsLogger.getLogger(TerminalView.class);
    private final VirtualTerminal vt = new VirtualTerminal(24, 80);

    public static void main(String args[]) {
        new TerminalView().start();
    }

    private void start() {
        Frame frame = new Frame();
        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 16);
        frame.setFont(font);
        FontMetrics fm = frame.getFontMetrics(font);
        int cw = fm.charWidth('W');
        int ch = fm.getHeight();
        frame.setSize(cw * 80 , ch * 24);
        frame.addWindowListener(this);
        frame.add(new CustomPaintComponent(vt));
        frame.addKeyListener(new KeyListener() {
                @Override
                public void keyTyped(KeyEvent e) {
                    LOG.debug(e.toString());
                    if (e.isControlDown()) {
                        LOG.debug("keyCode=" + e.getKeyCode());
                        switch(e.getKeyCode()) {
                        case 'l':
                            vt.clear();
                            break;
                        default:
                        }
                    } else {
                        vt.addChar(e.getKeyChar());
                    }
                    frame.repaint();
                }
                @Override
                public void keyPressed(KeyEvent e) {
                    LOG.debug(e.toString());
                    if (e.isControlDown()) {
                        //LOG.debug("keyCode=" + e.getKeyCode());
                        switch(e.getKeyCode()) {
                        case 76:
                            vt.clear();
                            break;
                        default:
                        }
                    } else {
                        vt.addChar(e.getKeyChar());
                    }
                    frame.repaint();
                }
                @Override
                public void keyReleased(KeyEvent e) {
                }
            }
        );
        frame.addComponentListener(this);
        frame.setVisible(true);
    }

    public void windowClosing(WindowEvent e) {
        System.exit(0);
    }


    /* (non-Javadoc)
     * @see java.awt.event.ComponentListener#componentResized(java.awt.event.ComponentEvent)
     */
    @Override
    public void componentResized(ComponentEvent e) {
        Dimension d = e.getComponent().getSize();
//        Font font = new Font(Font.MONOSPACED, Font.PLAIN, 16);
//        FontMetrics fm = e.getComponent().getFontMetrics(font);
//        int cw = fm.charWidth('W');
//        int ch = fm.getHeight();
//        int rowSize = (int)(d.getWidth() / cw);
//        int colSize = (int)(d.getHeight() / ch);
//        vt.resize(rowSize, colSize);
//        LOG.debug("[" + rowSize + ", " + colSize + "]");
    }

    /* (non-Javadoc)
     * @see java.awt.event.ComponentListener#componentMoved(java.awt.event.ComponentEvent)
     */
    @Override
    public void componentMoved(ComponentEvent e) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see java.awt.event.ComponentListener#componentShown(java.awt.event.ComponentEvent)
     */
    @Override
    public void componentShown(ComponentEvent e) {
        // TODO Auto-generated method stub
        
    }

    /* (non-Javadoc)
     * @see java.awt.event.ComponentListener#componentHidden(java.awt.event.ComponentEvent)
     */
    @Override
    public void componentHidden(ComponentEvent e) {
        // TODO Auto-generated method stub
        
    }

    /**
     * 
     */
    @SuppressWarnings("serial")
    public static class CustomPaintComponent extends Component {
        private VirtualTerminal term;

        /**
         * 
         * @param term
         */
        public CustomPaintComponent(VirtualTerminal term) {
            super();
            this.term = term;
            String seeds = "0123456789abcdeABCDE";
            for (int i = 0; i < 2000 - 1; i++) {
                term.addChar(seeds.charAt(i % seeds.length()));
            }
        }

        /**
         * 
         */
        public void paint(Graphics g) {
            Dimension size = getSize();
            Image back = createImage(size.width, size.height);
            Graphics buffer = back.getGraphics();

            Font font = new Font(Font.MONOSPACED, Font.PLAIN, 16);
            FontMetrics fm = getFontMetrics(font);
            int cw = fm.charWidth('W');
            int ch = fm.getHeight();
            buffer.setFont(font);

            for (int row = 0; row < 24; row++) {
                for(int col = 0; col < 80; col++) {
                    VirtualCharacter vch = term.getChar(row, col);
                    if (vch != null) {
                        char[] data = { vch.getChar() };
                        int x = cw * col;
                        int y = ch * (row + 1);
                        buffer.drawChars(data, 0, 1, x, y);
                    }
                }
            }

            g.drawImage(back, 0, 0, this);
        }
    }

}
