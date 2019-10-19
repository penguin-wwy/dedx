package com.dedx.utils.annotation

import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DepClass (
        val klass: KClass<out Any>,
        val method: String = "<clinit>",
        val input: Boolean)