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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.mozilla.javascript.tools.shell.Main;

import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.Border;
import javafx.scene.layout.Background;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.concurrent.Task;
import javafx.application.Platform;
import javafx.util.Callback;

@SuppressWarnings("restriction")
public class RhinoShellFX extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    public void start(Stage stage) throws Exception {
        Terminal terminal  = new Terminal();
        terminal.setPrefWidth(640);
        terminal.setPrefHeight(480);
        new Thread() {
            @Override
            public void run() {
                Main.setIn(terminal.getIn());
                Main.setOut(terminal.getOut());
                Main.setErr(terminal.getOut());
                Main.main(new String[]{});
            }
        }.start();
        VBox vbox = new VBox();
        vbox.getChildren().addAll(terminal);
        vbox.setAlignment(Pos.CENTER);
        stage.setTitle("RhinoShell");
        stage.setWidth(640);
        stage.setHeight(480);
        stage.setScene(new Scene(vbox));
        stage.show();
    }

    public static class Terminal extends ListView<String> {
        private InputStream in;
        private PrintStream out;
        private PipedOutputStream pos;
        private List<String> lines;

        public Terminal() {
            super();
            Terminal self = this;
            lines = new ArrayList<>();
            lines.add("rhino> ");
            ObservableList<String>lm = FXCollections.observableArrayList(lines);
            setItems(lm);
            setEditable(true);
            setCellFactory(new Callback<ListView<String>, ListCell<String>>() {
                @Override
                public ListCell<String> call(ListView<String> listView) {
                    return new EditCell(self, pos);
                }
            });
            layout();
            edit(getItems().size() - 1);
        }
        
        public void print(String line) {
            //System.out.println("print(" + line + ")");
            Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    getItems().add(getItems().size() - 1, line);
                    scrollTo(getItems().size() - 1);
                    layout();
                    edit(getItems().size() - 1);
                }
            };
            
            Task<Boolean> task = new Task<Boolean>() {
                @Override
                protected Boolean call() throws Exception {
                    Platform.runLater(runnable);
                    return true;
                }
            };
                 
            Thread t = new Thread( task );
            t.setDaemon( true );
            t.start();
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
        
        public void reset() {
            for (int i = getItems().size() - 2; i >= 0; i--) {
                getItems().remove(i);
            }
            layout();
            edit(0);
        }
        
        public static class EditCell extends ListCell<String> {
            private static final String PROMPT = "rhino> ";
            private List<String> histories;
            private TextField textField;
            private Terminal terminal;
            private PipedOutputStream pos;

            public EditCell(Terminal terminal, PipedOutputStream pos) {
                this.histories = new ArrayList<>();
                this.terminal = terminal;
                this.pos = pos;
            }

            @Override
            public void cancelEdit() {
                super.cancelEdit();
                //System.out.println("cancelEdit(" + line.getText() + ")");
                setText(getItem());
                setGraphic(null);
            }

            @Override
            public void commitEdit(String arg0) {
                super.commitEdit(arg0);
                System.out.println("commitEdit()");
            }

            @Override
            public void startEdit() {
                super.startEdit();
                //System.out.println("startEdit(" + line.getText() + ")");

                textField = new TextField(getItem());
                textField.setBorder(Border.EMPTY);
                textField.setPadding(Insets.EMPTY);
                textField.setBackground(Background.EMPTY);
                textField.setOnKeyPressed(e -> {
                    switch(e.getCode()) {
                    case L:
                        if (e.isControlDown()) {
                            terminal.reset();
                        }
                        break;
                    case LEFT:
                    case UP:
                    case DOWN:
                        break;
                    case ENTER:
                        try {
                            String line = textField.getText().replaceAll("^" + PROMPT, "") + "\n";
                            terminal.print(PROMPT + line);
                            byte[] b = line.getBytes();
                            pos.write(b, 0, b.length);
                            pos.flush();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        break;
                    default:
                        break;
                    }
                });

                setText(null);
                setGraphic(textField);
                layout();
                textField.requestFocus();
                textField.clear();
                textField.setText(PROMPT);
                textField.positionCaret(PROMPT.length());
            }

            @Override
            protected void updateItem(String paramT, boolean paramBoolean) {
                //System.out.println("updateItem(" + paramT + ")");
                super.updateItem(paramT, paramBoolean);
                setText(paramT);
            }
            
        }

    }

}