package com.github.dedx

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection

class DepInjectPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def extension = project.extensions.create('depInject', DepInjectPluginExtension)

        def sourceSetsContainer = project.properties['sourceSets']
        def mainSourceSets = sourceSetsContainer.findByName('main')
        FileCollection outputs = mainSourceSets.output

        project.task('depInjectJava', type: JavaDepInjectTask) {
            enable = extension.enable
            classFileCollection = outputs
        }

        project.task('depInjectKotlin', type: KotlinDepInjectTask) {
            enable = extension.enable
            classFileCollection = outputs
        }

        project.tasks.getByName('compileJava').finalizedBy('depInjectJava')
        project.tasks.getByName('compileKotlin').finalizedBy('depInjectKotlin')
    }
}
