package com.preichert.blecustomscanner.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

actual fun getDatabaseBuilder(context: Any?): RoomDatabase.Builder<AppDatabase> {
    val appContext = context as Context
    val dbFile = appContext.getDatabasePath("ble_scanner.db")
    return Room.databaseBuilder<AppDatabase>(
        context = appContext,
        name = dbFile.absolutePath
    )
}

// Handled by Room compiler
// actual object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
