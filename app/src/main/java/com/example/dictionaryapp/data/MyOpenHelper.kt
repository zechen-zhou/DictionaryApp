package com.example.dictionaryapp.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.BaseColumns

class MyOpenHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL(SQL_CREATE_ENTRIES)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        // This database is only a cache for online data, so its upgrade policy is
        // to simply to discard the data and start over
        db?.execSQL(SQL_DELETE_ENTRIES)
        onCreate(db)
    }

    override fun onDowngrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }

    companion object {
        const val DATABASE_NAME = "TheDatabase.db"

        // If you change the database schema, you must increment the database version.
        // the version of the database (starting at 1)
        const val DATABASE_VERSION = 1

        // SQL syntax that create and delete a table

        // SQLite doesn't have VARCHAR for declaring string columns
        // Instead you use the TEXT keyword to declare a String column
        private const val SQL_CREATE_ENTRIES =
            "CREATE TABLE ${MyContract.Entry.TABLE_NAME} (" +
                    "${BaseColumns._ID} INTEGER PRIMARY KEY," + // The id increase automatically
                    "${MyContract.Entry.COLUMN_NAME_WORD} TEXT," +
                    "${MyContract.Entry.COLUMN_NAME_PRONUNCIATION} TEXT," +
                    "${MyContract.Entry.COLUMN_NAME_TYPE} TEXT," +
                    "${MyContract.Entry.COLUMN_NAME_DEFINITION} TEXT," +
                    "${MyContract.Entry.COLUMN_NAME_EXAMPLE} TEXT)"

        private const val SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS ${MyContract.Entry.TABLE_NAME}"
    }
}