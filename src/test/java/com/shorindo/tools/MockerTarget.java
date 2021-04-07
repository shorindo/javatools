package com.shorindo.tools;

public class MockerTarget {
	public static void main(String[] args) {
		System.out.println(new MockerTarget().methodA(args[0]));
		System.out.println(new MockerTarget().methodB(args[0]));
	}

	public String methodA(String a) {
		return "methodA(" + a + ")";
	}

	public String methodA(String a, String b) {
		return "methodA(" + a + ", " + b + ")";
	}

	public String methodB(String a) {
		return "methodB(" + a + ")";
	}
}
