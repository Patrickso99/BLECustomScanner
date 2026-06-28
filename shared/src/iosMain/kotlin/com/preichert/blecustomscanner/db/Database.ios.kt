@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.preichert.blecustomscanner.db

import androidx.room.Room
import androidx.room.RoomDatabase

actual fun getDatabaseBuilder(context: Any?): RoomDatabase.Builder<AppDatabase> {
    val dbFilePath = documentDirectory() + "/ble_scanner.db"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFilePath,
    )
}

private fun documentDirectory(): String {
    val documentDirectory = platform.Foundation.NSFileManager.defaultManager.URLForDirectory(
        directory = platform.Foundation.NSDocumentDirectory,
        inDomain = platform.Foundation.NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(documentDirectory?.path)
}

// Handled by Room compiler
// actual object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
