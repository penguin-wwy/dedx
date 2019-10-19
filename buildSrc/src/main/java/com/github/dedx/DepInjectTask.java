package com.github.dedx;

import com.google.common.flogger.FluentLogger;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskAction;

import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public abstract class DepInjectTask extends DefaultTask {

    static {
        Logger root = Logger.getLogger("");
        root.addHandler(new ConsoleHandler());
        root.setLevel(Level.INFO);
    }

    private static final FluentLogger logger = FluentLogger.forEnclosingClass();
    public boolean enable = false;
    public FileCollection classFileCollection = null;

    @TaskAction
    public void depInjectAction() {
        logger.atInfo().log("dep inject action [%s]...", getLanguageName());
        if (enable) {
            runAction();
        }
        logger.atInfo().log("dep inject action finish");
    }

    protected abstract void runAction();

    protected abstract String getLanguageName();
}
