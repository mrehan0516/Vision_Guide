package com.example.data.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.Collections

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_VERSION = 1
        private const val DATABASE_NAME = "VisionPilot.db"
        
        // Activity Log Table
        private const val TABLE_ACTIVITY = "activity_log"
        private const val COLUMN_ID = "id"
        private const val COLUMN_COMMAND = "command"
        private const val COLUMN_ACTION_TAKEN = "action_taken"
        private const val COLUMN_RESULT = "result"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_SUCCESS = "success"

        // Quick Commands Table
        private const val TABLE_QUICK_COMMANDS = "quick_commands"
        private const val COLUMN_QC_ID = "id"
        private const val COLUMN_QC_COMMAND = "command"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createActivityTable = ("CREATE TABLE " + TABLE_ACTIVITY + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_COMMAND + " TEXT,"
                + COLUMN_ACTION_TAKEN + " TEXT,"
                + COLUMN_RESULT + " TEXT,"
                + COLUMN_TIMESTAMP + " INTEGER,"
                + COLUMN_SUCCESS + " INTEGER" + ")")
        db.execSQL(createActivityTable)

        val createQuickCommandsTable = ("CREATE TABLE " + TABLE_QUICK_COMMANDS + "("
                + COLUMN_QC_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_QC_COMMAND + " TEXT" + ")")
        db.execSQL(createQuickCommandsTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ACTIVITY")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_QUICK_COMMANDS")
        onCreate(db)
    }

    fun insertActivity(command: String, actionTaken: String, result: String, success: Boolean) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_COMMAND, command)
        values.put(COLUMN_ACTION_TAKEN, actionTaken)
        values.put(COLUMN_RESULT, result)
        values.put(COLUMN_TIMESTAMP, System.currentTimeMillis())
        values.put(COLUMN_SUCCESS, if (success) 1 else 0)
        db.insert(TABLE_ACTIVITY, null, values)
        db.close()
    }

    fun getAllActivities(): List<ActivityLogEntry> {
        val activityList = ArrayList<ActivityLogEntry>()
        val selectQuery = "SELECT * FROM $TABLE_ACTIVITY ORDER BY $COLUMN_TIMESTAMP DESC"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID))
                val command = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_COMMAND))
                val action = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ACTION_TAKEN))
                val result = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_RESULT))
                val timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                val success = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_SUCCESS)) == 1
                
                activityList.add(ActivityLogEntry(id, command, action, result, timestamp, success))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return activityList
    }

    fun clearAll() {
        val db = this.writableDatabase
        db.execSQL("DELETE FROM $TABLE_ACTIVITY")
        db.close()
    }

    fun insertQuickCommand(command: String) {
        val db = this.writableDatabase
        val values = ContentValues()
        values.put(COLUMN_QC_COMMAND, command)
        db.insert(TABLE_QUICK_COMMANDS, null, values)
        
        // Remove older entries if count maxes out
        db.execSQL(
            "DELETE FROM $TABLE_QUICK_COMMANDS WHERE $COLUMN_QC_ID NOT IN " +
            "(SELECT $COLUMN_QC_ID FROM $TABLE_QUICK_COMMANDS ORDER BY $COLUMN_QC_ID DESC LIMIT 5)"
        )
        db.close()
    }

    fun getTopFiveCommands(): List<QuickCommandEntry> {
        val commandList = ArrayList<QuickCommandEntry>()
        val selectQuery = "SELECT * FROM $TABLE_QUICK_COMMANDS ORDER BY $COLUMN_QC_ID DESC LIMIT 5"
        val db = this.readableDatabase
        val cursor = db.rawQuery(selectQuery, null)

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_QC_ID))
                val command = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_QC_COMMAND))
                commandList.add(QuickCommandEntry(id, command))
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return commandList
    }

    fun deleteQuickCommand(id: Int) {
        val db = this.writableDatabase
        db.delete(TABLE_QUICK_COMMANDS, "$COLUMN_QC_ID=?", arrayOf(id.toString()))
        db.close()
    }
}

data class ActivityLogEntry(
    val id: Int,
    val command: String,
    val actionTaken: String,
    val result: String,
    val timestamp: Long,
    val success: Boolean
)

data class QuickCommandEntry(
    val id: Int,
    val command: String
)
