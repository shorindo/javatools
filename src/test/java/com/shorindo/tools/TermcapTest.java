package com.shorindo.tools;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shorindo.tools.Termcap.StateMachine;

public class TermcapTest {
    private static final Logger LOG = Logger.getLogger(TermcapTest.class);

    @Test
    public void test() {
        StateMachine machine = new StateMachine();

        machine.define("kd", new int[] { '\n' });
        machine.define("AL", new int[] { 0x1b, '[', 0x1FFFF, 'L' });
        machine.define("DC", new int[] { 0x1b, '[', 0x1FFFF, 'P' });
        machine.define("DL", new int[] { 0x1b, '[', 0x1FFFF, 'M' });
        machine.define("DO", new int[] { 0x1b, '[', 0x1FFFF, 'B' });
        machine.define("LE", new int[] { 0x1b, '[', 0x1FFFF, 'D' });
        machine.define("RI", new int[] { 0x1b, '[', 0x1FFFF, 'C' });
        machine.define("UP", new int[] { 0x1b, '[', 0x1FFFF, 'A' });
        machine.define("ae", new int[] { CTRL('O') });
        machine.define("al", new int[] { 0x1b, '[', 'L' });
        machine.define("as", new int[] { CTRL('N') });
        machine.define("bl", new int[] { CTRL('G') });
        machine.define("cd", new int[] { 0x1b, '[', 'J' });
        machine.define("ce", new int[] { 0x1b, '[', 'K' });
        machine.define("cl", new int[] { 0x1b, '[', 'H', 0x1b, '[', '2', 'J' });
        machine.define("cm", new int[] { 0x1b, '[', 0x1FFFF, ';', 0x1FFFF, 'H' });
        machine.define("cr", new int[] { '\r' });
        machine.define("cs", new int[] { 0x1b, '[', 0x1FFFF, ';', 0x1FFFF, 'r' });
        machine.define("ct", new int[] { 0x1b, '[', '3', 'g' });
        machine.define("dc", new int[] { 0x1b, '[', 'P' });
        machine.define("dl", new int[] { 0x1b, '[', 'M' });
        machine.define("eA", new int[] { 0x1b, '[', ')', '0' });
        machine.define("ei", new int[] { 0x1b, '[', '4', 'l' });
        machine.define("ho", new int[] { 0x1b, '[', 'H' });
        machine.define("im", new int[] { 0x1b, '[', '4', 'h' });
        machine.define("le", new int[] { CTRL('H') });
        machine.define("md", new int[] { 0x1b, '[', '1', 'm' });
        machine.define("me", new int[] { 0x1b, '[', 'm' });
        machine.define("ml", new int[] { 0x1b, 'l' });
        machine.define("mr", new int[] { 0x1b, '[', '7', 'm' });
        machine.define("mu", new int[] { 0x1b, 'm' });
        machine.define("nd", new int[] { 0x1b, '[', 'C' });
        machine.define("nw", new int[] { '\r', '\n' });
        machine.define("rc", new int[] { 0x1b, '8' });
        machine.define("rs", new int[] { 0x1b, '[', 'm', 0x1b, '?', '7', 'h', 0x1b, '[', '4', 'l', 0x1b, '>', 0x1b, '7', 0x1b, '[', 'r', 0x1b, '[', '1', ';', '3', ';', '4', ';', '6', 'l', 0x1b, '8' });
        machine.define("sc", new int[] { 0x1b, '7' });
        machine.define("se", new int[] { 0x1b, '[', 'm' });
        machine.define("sf", new int[] { '\n' });
        machine.define("so", new int[] { 0x1b, '[', '7', 'm' });
        machine.define("sr", new int[] { 0x1b, 'M' });
        machine.define("ta", new int[] { CTRL('I') });
        machine.define("te", new int[] { 0x1b, '[', '2', 'J', 0x1b, '[', '?', '4', '7', 'l', 0x1b, 'B' });
        machine.define("ti", new int[] { 0x1b, '7', 0x1b, '[', '?', '4', '7', 'h' });
        machine.define("ue", new int[] { 0x1b, '[', 'm' });
        machine.define("up", new int[] { 0x1b, '[', 'A' });
        machine.define("us", new int[] { 0x1b, '[', '4', 'm' });

        System.out.print(machine.toString());
        
        machine.write('A');
        machine.write('\n');
        machine.write(0x1b);
        machine.write('[');
        machine.write('M');
        machine.write(0x1b);
        machine.write('X');
        machine.write(0x1b);
        machine.write('[');
        machine.write('1');
        machine.write('2');
        machine.write('L');
        machine.write(0x1b);
        machine.write('[');
        machine.write('1');
        machine.write(';');
        machine.write('2');
        machine.write('r');
        
        System.out.println("");
    }

    private static int CTRL(char c) {
        return c - 64;
    }
}
