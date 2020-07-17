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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Frame;
import java.awt.TextArea;
import java.awt.TextField;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;

import javax.swing.BoxLayout;

import org.mozilla.javascript.tools.shell.Main;

/**
 * 
 */
public class RhinoShell extends WindowAdapter {
    private static final String PROMPT = "rhino> ";
    private TextArea textArea;
    private TextField textField;
    private InputStream in;
    private PrintStream out;
    private PipedOutputStream pos;

    public static void main(String args[]) {
        if (args.length > 0) {
            Main.main(args);
        } else {
            new RhinoShell().start();
        }
    }
    
    public void start() {
        new Thread() {
            @Override
            public void run() {
                Main.setIn(getIn());
                Main.setOut(getOut());
                Main.setErr(getOut());
                Main.main(new String[]{});
            }
        }.start();

        textArea = new TextArea(24, 80);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setEditable(false);
        textField = new TextField();
        textField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        textField.addKeyListener(new KeyListener() {
            private History history = new History();

            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                //System.out.println(e.getKeyCode());
                switch (e.getKeyCode()) {
                case KeyEvent.VK_ENTER:
                    try {
                        history.add(textField.getText());
                        history.last();
                        String line = textField.getText() + "\n";
                        textArea.append(PROMPT + line);
                        pos.write(line.getBytes());
                        textField.setText("");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                    break;
                case KeyEvent.VK_L:
                    if ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
                        textArea.setText("");
                    }
                    break;
                case KeyEvent.VK_U:
                    if ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
                        textField.setCaretPosition(0);
                        textField.setText("");
                    }
                    break;
                case KeyEvent.VK_A:
                    if ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
                        textField.setCaretPosition(0);
                        e.consume();
                    }
                    break;
                case KeyEvent.VK_E:
                    if ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
                        textField.setCaretPosition(textField.getText().length());
                    }
                    break;
                case KeyEvent.VK_P:
                    if ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
                        textField.setText(history.back(textField.getText()));
                        textField.setCaretPosition(textField.getText().length());
                    }
                    break;
                case KeyEvent.VK_N:
                    if ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0) {
                        textField.setText(history.forward(textField.getText()));
                        textField.setCaretPosition(textField.getText().length());
                    }
                    break;
                case KeyEvent.VK_UP:
                    textField.setText(history.back(textField.getText()));
                    textField.setCaretPosition(textField.getText().length());
                    break;
                case KeyEvent.VK_DOWN:
                    textField.setText(history.forward(textField.getText()));
                    textField.setCaretPosition(textField.getText().length());
                    break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
            
        });
        Frame frame = new Frame();
        frame.addWindowListener(this);
        frame.setLayout(new BoxLayout(frame, BoxLayout.Y_AXIS));
        frame.add(textArea);
        frame.add(textField);
        frame.pack();
        frame.setVisible(true);
        textField.requestFocus();
    }
    
    public void windowClosing(WindowEvent e) {
        System.exit(0);
    }
    
    public void print(String line) {
        textArea.append(line + "\n");
    }
    
    public InputStream getIn() {
        if (in == null) {
            try {
                pos = new PipedOutputStream();
                in = new PipedInputStream(pos);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return in;
    }
    
    public PrintStream getOut() {
        if (out == null) {
            out = new PrintStream(new OutputStream() {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                @Override
                public void write(int b) throws IOException {
                    if ( b == '\n') {
                        String line = new String(buffer.toByteArray())
                            .replaceAll("[\r\n]", "")
                            .replaceAll("js> ", "");
                        if (!"".equals(line)) {
                            print(line);
                        }
                        buffer.reset();
                    } else {
                        buffer.write(b);
                    }
                }
            });
        }
        return out;
    }

    public class History extends ArrayList<String> {
        private static final long serialVersionUID = -8719279781968316379L;
        private int curr = 0;

        @Override
        public boolean add(String e) {
            boolean b = super.add(e);
            curr = size();
            return b;
        }

        public String back(String text) {
            if (curr > 0) {
                return get(--curr);
            } else {
                return text;
            }
        }

        public String forward(String text) {
            if (curr < size() - 1) {
                return get(++curr);
            } else if (curr == size() - 1) {
                curr++;
                return "";
            } else {
                return "";
            }
        }
        
        public void last() {
            curr = size();
        }
    }
}
