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

import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;

/**
 * 
 */
public class TerminalSSH {

    public static void main(String[] args) {
        Terminal terminal = new Terminal("UTF-8", 80, 25);
        try{
            SshClient client = SshClient.setUpDefaultClient();
            String user = "user";
            String host = "host";
            String passwd = "password";
            int port = 22;
            Long timeout = 10000L;
            client.start();
            ClientSession session = client.connect(user, host, port).verify(timeout).getSession();
            session.addPasswordIdentity(passwd);
            session.auth().verify(timeout);
            ChannelShell shell = session.createShellChannel();
            PipedInputStream tin = new PipedInputStream();
            PipedOutputStream tout = new PipedOutputStream(tin);
            terminal.setOut(tout);
            shell.setIn(tin);
            PipedInputStream sin = new PipedInputStream();
            PipedOutputStream sout = new PipedOutputStream(sin);
            terminal.setIn(sin);
            shell.setOut(sout);
            shell.setErr(sout);
            shell.open();
        } catch(Exception e){
            System.out.println(e);
        }
        terminal.start();
    }
}
