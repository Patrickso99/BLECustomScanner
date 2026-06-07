package com.preichert.blecustomscanner.db

import androidx.room.RoomDatabase

expect fun getDatabaseBuilder(context: Any? = null): RoomDatabase.Builder<AppDatabase>
