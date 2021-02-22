package com.shorindo.tools;

public abstract class CGClassA implements CGInterface {

    public static void main(String[] args) {
        new CGClassB().methodB1();
    }

    @Override
    public void methodA() {
    }

    abstract void methodAbstract();
}
