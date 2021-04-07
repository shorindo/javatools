package com.shorindo.tools;

public class MockerTarget {
	public static void main(String[] args) {
		System.out.println(new MockerTarget().methodA(args[0]));
		System.out.println(new MockerTarget().methodB(args[0]));
	}

	public String methodA(String input) {
		return "methodA - " + input;
	}

	public String methodB(String input) {
		return "methodB - " + input;
	}
}
