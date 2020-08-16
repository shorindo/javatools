package com.shorindo.tools;

import static org.junit.Assert.*;

import org.junit.Test;

import com.shorindo.tools.Terminal.Termcap;

public class TermcapTest {
    private static final Logger LOG = Logger.getLogger(TermcapTest.class);

    @Test
    public void test() {
        Termcap machine = new Termcap();
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
        machine.write('3');
        machine.write(';');
        machine.write('4');
        machine.write('5');
        machine.write('H');
        machine.write(0x1b);
        machine.write('[');
        machine.write('1');
        machine.write('m');
        
        System.out.println("");
    }

    private static int CTRL(char c) {
        return c - 64;
    }
}
