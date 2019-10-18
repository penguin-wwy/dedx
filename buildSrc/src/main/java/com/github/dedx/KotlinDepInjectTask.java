package com.github.dedx;

public class KotlinDepInjectTask extends DepInjectTask {

    private final String languageName = "kotlin";

    @Override
    protected void runAction() {

    }

    @Override
    public String getLanguageName() {
        return languageName;
    }
}
