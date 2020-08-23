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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;

import com.shorindo.tools.Logger.Level;

/**
 * 
 */
public class TerminalSSH {
	private static final Logger LOG = Logger.getLogger(TerminalSSH.class);

    public static void main(String[] args) {
        Logger.setLevel(Level.DEBUG);
        Terminal terminal = new Terminal("UTF-8", 80, 25);
        terminal.open();
        String user = prompt(terminal, "login: ", true);
        String pass = prompt(terminal, "password:", false);
        ssh(terminal, user, pass);
    }
    
    private static String prompt(Terminal terminal, String text, boolean echo) {
        LOG.debug("prompt(" + text + ")");
        try {
            PipedInputStream sin = new PipedInputStream();
            PipedOutputStream sout = new PipedOutputStream(sin);
            sout.write(text.getBytes());
            PipedInputStream kin = new PipedInputStream();
            PipedOutputStream kout = new PipedOutputStream(kin);
            terminal.connect(sin, kout);
            int c;
            StringBuffer sb = new StringBuffer();
            while ((c = kin.read()) != '\n') {
                if (echo) sout.write((byte)c);
                else sout.write('*');
                sout.flush();
                sb.append((char)c);
            }
            sout.write('\r');
            sout.write('\n');
            sout.flush();
            Thread.sleep(100);
            sin.close();
            sout.close();
            kin.close();
            kout.close();
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
    
    private static void ssh(Terminal terminal, String server, String pass) {
        try{
            SshClient client = SshClient.setUpDefaultClient();
            Pattern p = Pattern.compile("(.+?)@(.+)(:(\\d+))");
            Matcher m = p.matcher(server);
            if (!m.matches()) {
                LOG.error("invalid server:" + server);
                return;
            }
            String host = m.group(2);
            String user = m.group(1);
            String passwd = pass;
            int port = m.groupCount() > 2 ? Integer.parseInt(m.group(4)) : 22;
            Long timeout = 10000L;
            client.start();
            ClientSession session = client.connect(user, host, port).verify(timeout).getSession();
            session.addPasswordIdentity(passwd);
            session.auth().verify(timeout);
            ChannelShell shell = session.createShellChannel();
            shell.setPtyType("xterm");
            shell.setPtyLines(25);
            PipedInputStream tin = new PipedInputStream();
            PipedOutputStream tout = new PipedOutputStream(tin);
            shell.setIn(tin);
            PipedInputStream sin = new PipedInputStream();
            PipedOutputStream sout = new PipedOutputStream(sin);
            terminal.connect(sin, tout);
            shell.setOut(sout);
            shell.setErr(sout);
            shell.open();
        } catch(Exception e){
            e.printStackTrace();
        }
    }
}
