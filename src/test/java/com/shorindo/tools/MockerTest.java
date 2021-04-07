package com.shorindo.tools;

import static org.junit.Assert.*;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Test;

import com.shorindo.tools.HotMock.MockMethod;

public class MockerTest {

	@BeforeClass
	public static void beforeClass() {
		HotMock.mock("com.shorindo.tools.MockerTarget",
				new MockMethod("methodA", new File("src/test/resources", "mock.js")),
				new MockMethod("methodB", new File("src/test/resources", "mock.js")));
	}

	@Test
	public void test() {
		System.out.println("result = " + new com.shorindo.tools.MockerTarget().methodA("bar"));
		System.out.println("result = " + new com.shorindo.tools.MockerTarget().methodA("foo", "bar"));
		System.out.println("result = " + new com.shorindo.tools.MockerTarget().methodB("baz"));
	}
}
