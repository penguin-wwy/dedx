package com.github.dedx;

public class JavaDepInjectTask extends DepInjectTask {

    private final String languageName = "java";

    @Override
    protected void runAction() {

    }

    @Override
    public String getLanguageName() {
        return languageName;
    }
}
