package com.example.dictionaryapp.data

import android.provider.BaseColumns

/**
 * A contract class defines the table name and column names for a single table representing
 * definitions of a english word.
 *
 * (A companion class, known as a contract class)
 */
object MyContract {

    object Entry : BaseColumns {
        // Table name
        const val TABLE_NAME = "definitions"

        // Column names
        const val COLUMN_NAME_WORD = "word"
        const val COLUMN_NAME_PRONUNCIATION = "pronunciation"
        const val COLUMN_NAME_TYPE = "type"
        const val COLUMN_NAME_DEFINITION = "definition"
        const val COLUMN_NAME_EXAMPLE = "example"
    }
}