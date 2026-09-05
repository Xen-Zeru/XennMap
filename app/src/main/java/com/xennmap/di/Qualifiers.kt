package com.xennmap.di

import javax.inject.Qualifier

/** Coroutine scope that lives as long as the application process. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
