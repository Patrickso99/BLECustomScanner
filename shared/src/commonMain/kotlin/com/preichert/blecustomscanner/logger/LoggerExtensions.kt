package com.preichert.blecustomscanner.logger

import co.touchlab.kermit.Logger
import kotlin.reflect.KClass

fun Logger.withTag(clazz: KClass<*>): Logger {
    return withTag(clazz.simpleName ?: "UnknownClass")
}
