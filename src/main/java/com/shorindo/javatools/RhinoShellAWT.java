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
package com.shorindo.javatools;

import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
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

import javax.swing.BoxLayout;

import org.mozilla.javascript.tools.shell.Main;

/**
 * 
 */
public class RhinoShellAWT extends WindowAdapter {
    private static final String PROMPT = "rhino> ";
    private TextArea textArea;
    private TextField textField;
    private InputStream in;
    private PrintStream out;
    private PipedOutputStream pos;

    public static void main(String args[]) {
        new RhinoShellAWT().start();
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
        textField = new TextField();
        textField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textField.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                //System.out.println(e.getKeyCode());
                switch (e.getKeyCode()) {
                case 10:
                    try {
                        String line = textField.getText() + "\n";
                        textArea.append(PROMPT + line);
                        pos.write(line.getBytes());
                        textField.setText("");
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
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

}