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

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * 
 */
public class ClipboardWatcher extends Thread implements ClipboardOwner {
    Clipboard sysClip = Toolkit.getDefaultToolkit().getSystemClipboard();
    
    public void run() {
        Transferable trans = sysClip.getContents(this);
        regainOwnership(trans);
        System.out.println("Listening to board...");
        while(true) {}
    }
    
    public void lostOwnership(Clipboard c, Transferable t) {
        try {
            sleep(20);
            Transferable contents = sysClip.getContents(this); //EXCEPTION
            processContents(contents);
            regainOwnership(contents);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
    
    void processContents(Transferable t) {
//        for (DataFlavor flavor : t.getTransferDataFlavors()) {
//            System.out.println(flavor);
//        }
        if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            try {
                String text = (String)t.getTransferData(DataFlavor.stringFlavor);
                System.out.println(text);
            } catch (UnsupportedFlavorException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (t.isDataFlavorSupported(DataFlavor.imageFlavor)) {
            try {
                Object img = t.getTransferData(DataFlavor.imageFlavor);
                System.out.println(img.getClass());
            } catch (UnsupportedFlavorException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    void regainOwnership(Transferable t) {
        sysClip.setContents(t, this);
    }
    
    public static void main(String[] args) {
        ClipboardWatcher b = new ClipboardWatcher();
        b.start();
    }
}
