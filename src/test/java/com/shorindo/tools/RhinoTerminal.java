package com.shorindo.tools;

import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.PrintStream;

import org.mozilla.javascript.tools.shell.Main;

public class RhinoTerminal {
	public static void main(String[] args) {
		try {
			Terminal terminal = new Terminal("UTF-8", 80, 25);
			terminal.open();
		
			PipedInputStream sin = new PipedInputStream();
			PipedOutputStream sout = new PipedOutputStream(sin);
			PrintStream ps = new PrintStream(sout);
			PipedInputStream kin = new PipedInputStream();
			PipedOutputStream kout = new PipedOutputStream(kin);
			
			terminal.connect(sin, kout);
			Main.setIn(kin);
			Main.setOut(ps);
			Main.setErr(ps);

			Main.getIn();
			Main.main(new String[]{});
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
