package com.shorindo.tools;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shorindo.tools.Termcap.StateMachine;

public class TermcapTest {
	private static final Logger LOG = Logger.getLogger(TermcapTest.class);

	@Test
	public void test() {
		StateMachine machine = new StateMachine();

		machine.dig(new int[] { '\r' });
		machine.dig(new int[] { '\n' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, 'D' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, 'C' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, 'A' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, 'L' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, 'H' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, 'J' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, 'K' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, ';', 0x1FFFF, 'r' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, ';', 0x1FFFF, 'H' });
		machine.dig(new int[] { 0x1b, '[', 0x1FFFF, 'H', 0x1b, '[', '2', 'J' });
		machine.dig(new int[] { 0x1b, '[', '1', 'm' });
		machine.dig(new int[] { 0x1b, '[', 'm' });
		
		System.out.print(machine.toString());
	}

}
