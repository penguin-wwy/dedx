package com.github.dedx;

import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;

import java.io.File;
import java.util.*;

public class JavaDepInjectTask extends DepInjectTask {

    private final String languageName = "java";
    private static final FluentLogger logger = FluentLogger.forEnclosingClass();

    @Override
    protected void runAction() {
        Set<File> classFiles = Sets.newHashSet();
        classFileCollection.getFiles().forEach((buildDir) -> {
            if (buildDir.getAbsolutePath().endsWith(languageName + File.separator + "main")) {
                File[] files = buildDir.listFiles((File file, String s) -> s.endsWith(".class"));
                if (files != null) classFiles.addAll(Arrays.asList(files));
            }
        });
        injectFiles(classFiles);
    }

    @Override
    public String getLanguageName() {
        return languageName;
    }

    private void injectFiles(Collection<File> classFiles) {

    }
}
