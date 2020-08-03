package com.shorindo.tools;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;

import org.junit.Test;

public class TerminalTest {
	private static final Logger LOG = Logger.getLogger(TerminalTest.class);

	@Test
	public void testHalf() throws Exception {
		assertTerm("abc", "abc\n\n\n");
		assertTerm("abcdefghij", "abcdefghij\n\n\n");
		assertTerm("abcdefghijk", "abcdefghij\nk\n\n");
		assertTerm("abcdefghijklmnopqrstuvwxyz0123",
		     "abcdefghij\nklmnopqrst\nuvwxyz0123\n");
		assertTerm("abcdefghijklmnopqrstuvwxyz01234",
		     "klmnopqrst\nuvwxyz0123\n4\n");
	}

	@Test
	public void testFull() throws Exception {
		assertTerm("あいう", "あいう\n\n\n");
		assertTerm("あいうえお", "あいうえお\n\n\n");
		assertTerm("あいうえおか", "あいうえお\nか\n\n");
		assertTerm("あいうえおかきくけこさしすせそ",
				"あいうえお\nかきくけこ\nさしすせそ\n");
		assertTerm("あいうえおかきくけこさしすせそた",
				"かきくけこ\nさしすせそ\nた\n");
	}
	
	private void assertTerm(String input, String expect) {
		Terminal terminal = new Terminal("UTF-8", 10, 3);
		try {
			byte[] b = input.getBytes("UTF-8");
			OutputStream os = terminal.getOutputStream();
			os.write(b);
			Thread.sleep(10);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			terminal.close();
		}
		char[][] buffer = terminal.getBuffer();
		StringBuffer sb = new StringBuffer();
		for (int row = 0; row < buffer.length; row++) {
			char[] line = buffer[row];
			for (int col = 0; col < line.length; col++) {
				if (line[col] > 0)
					sb.append(line[col]);
			}
			sb.append("\n");
		}
		assertEquals(expect, sb.toString());
	}

}
