package com.github.dedx;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Set;

public abstract class DepInjectTask extends DefaultTask {

    public boolean enable = false;
    public Set<File> classFiles = null;

    @TaskAction
    public void depInjectAction() {
        System.out.println(String.format("dep inject action [%s]...", getLanguageName()));
        runAction();
        System.out.println("dep inject action finish");
    }

    protected abstract void runAction();

    protected abstract String getLanguageName();
}
