package com.shorindo.tools;

import static org.junit.Assert.*;

import org.junit.AfterClass;
import org.junit.Test;

public class CallGraphTest {

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Test
    public void test() {
        CallGraph.main(new String[] {
                "src/test/resources/callgraph.js"
        });
    }

}
