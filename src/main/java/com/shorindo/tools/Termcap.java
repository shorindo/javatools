package com.shorindo.tools;

public class Termcap {
	private static final Logger LOG = Logger.getLogger(Termcap.class);
	private static final PEGCombinator TC = new PEGCombinator();
	static {
		//TC.define(Types.ENTRY, rules);
	}
	
	private static enum Types {
		ENTRY
	}
}
