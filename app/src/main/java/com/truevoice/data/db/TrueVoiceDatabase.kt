/*
 * True Voice: Real-Time On-Device AI Voice Clone & Scam Defense
 * SQLite Local Database Helper for Call Records, Forensics & Biometrics
 */

package com.truevoice.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.kitsumed.shizucallrecorder.utils.AppLogger
import com.truevoice.forensics.ForensicCallRecord
import com.truevoice.forensics.ForensicTimelinePoint
import com.truevoice.ml.RiskLevel
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TrueVoiceDatabase(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "truevoice.db"
        const val DATABASE_VERSION = 1

        // Table Calls
        const val TABLE_CALLS = "calls"
        const val COL_CALL_ID = "call_id"
        const val COL_CALLER_NUMBER = "caller_number"
        const val COL_CALLER_NAME = "caller_name"
        const val COL_START_TIME_MS = "start_time_ms"
        const val COL_END_TIME_MS = "end_time_ms"
        const val COL_DURATION_SECONDS = "duration_seconds"
        const val COL_FINAL_VERDICT = "final_verdict"
        const val COL_PEAK_SCORE = "peak_synthetic_score"
        const val COL_AVG_SCORE = "average_synthetic_score"
        const val COL_TOTAL_WINDOWS = "total_windows_analyzed"
        const val COL_VOCODER_CUTOFF = "vocoder_cutoff_detected"
        const val COL_COERCION_DETECTED = "coercion_detected"
        const val COL_TRIGGERED_KEYWORDS = "triggered_keywords"

        // Table Timeline Points
        const val TABLE_TIMELINE = "timeline_points"
        const val COL_TIMELINE_ID = "id"
        const val COL_FK_CALL_ID = "call_id"
        const val COL_TIMESTAMP_MS = "timestamp_ms"
        const val COL_OFFSET_SECONDS = "offset_seconds"
        const val COL_SYNTHETIC_SCORE = "synthetic_score"
        const val COL_RISK_LEVEL = "risk_level"
        const val COL_IS_VOICED = "is_voiced"
        const val COL_SPECTRAL_CUTOFF = "spectral_cutoff_detected"
        const val COL_TRIGGERED_KEYWORD = "triggered_keyword"

        // Table Enrolled Speakers
        const val TABLE_SPEAKERS = "enrolled_speakers"
        const val COL_CONTACT_ID = "contact_id"
        const val COL_CONTACT_NAME = "contact_name"
        const val COL_CONTACT_PHONE = "phone_number"
        const val COL_EMBEDDING_BLOB = "embedding_blob"
        const val COL_SAMPLE_COUNT = "sample_count"
        const val COL_LAST_UPDATED_MS = "last_updated_ms"

        @Volatile
        private var INSTANCE: TrueVoiceDatabase? = null

        fun getInstance(context: Context): TrueVoiceDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TrueVoiceDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        AppLogger.i("[TrueVoice DB] Creating True Voice database tables...")
        
        db.execSQL(
            """
            CREATE TABLE $TABLE_CALLS (
                $COL_CALL_ID TEXT PRIMARY KEY,
                $COL_CALLER_NUMBER TEXT NOT NULL,
                $COL_CALLER_NAME TEXT,
                $COL_START_TIME_MS INTEGER NOT NULL,
                $COL_END_TIME_MS INTEGER NOT NULL,
                $COL_DURATION_SECONDS INTEGER NOT NULL,
                $COL_FINAL_VERDICT TEXT NOT NULL,
                $COL_PEAK_SCORE REAL NOT NULL,
                $COL_AVG_SCORE REAL NOT NULL,
                $COL_TOTAL_WINDOWS INTEGER NOT NULL,
                $COL_VOCODER_CUTOFF INTEGER NOT NULL,
                $COL_COERCION_DETECTED INTEGER NOT NULL,
                $COL_TRIGGERED_KEYWORDS TEXT
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_TIMELINE (
                $COL_TIMELINE_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_FK_CALL_ID TEXT NOT NULL,
                $COL_TIMESTAMP_MS INTEGER NOT NULL,
                $COL_OFFSET_SECONDS REAL NOT NULL,
                $COL_SYNTHETIC_SCORE REAL NOT NULL,
                $COL_RISK_LEVEL TEXT NOT NULL,
                $COL_IS_VOICED INTEGER NOT NULL,
                $COL_SPECTRAL_CUTOFF INTEGER NOT NULL,
                $COL_TRIGGERED_KEYWORD TEXT,
                FOREIGN KEY($COL_FK_CALL_ID) REFERENCES $TABLE_CALLS($COL_CALL_ID) ON DELETE CASCADE
            );
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_SPEAKERS (
                $COL_CONTACT_ID TEXT PRIMARY KEY,
                $COL_CONTACT_NAME TEXT NOT NULL,
                $COL_CONTACT_PHONE TEXT NOT NULL,
                $COL_EMBEDDING_BLOB BLOB NOT NULL,
                $COL_SAMPLE_COUNT INTEGER NOT NULL,
                $COL_LAST_UPDATED_MS INTEGER NOT NULL
            );
            """.trimIndent()
        )

        db.execSQL("CREATE INDEX idx_timeline_call_id ON $TABLE_TIMELINE($COL_FK_CALL_ID);")
        db.execSQL("CREATE INDEX idx_speakers_phone ON $TABLE_SPEAKERS($COL_CONTACT_PHONE);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_TIMELINE;")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_CALLS;")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SPEAKERS;")
        onCreate(db)
    }

    // ==========================================
    // Call Records Persistence
    // ==========================================

    fun insertCallRecord(record: ForensicCallRecord) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val values = ContentValues().apply {
                put(COL_CALL_ID, record.callId)
                put(COL_CALLER_NUMBER, record.callerNumber)
                put(COL_CALLER_NAME, record.callerName)
                put(COL_START_TIME_MS, record.startTimeMs)
                put(COL_END_TIME_MS, record.endTimeMs)
                put(COL_DURATION_SECONDS, record.durationSeconds)
                put(COL_FINAL_VERDICT, record.finalVerdict.name)
                put(COL_PEAK_SCORE, record.peakSyntheticScore)
                put(COL_AVG_SCORE, record.averageSyntheticScore)
                put(COL_TOTAL_WINDOWS, record.totalWindowsAnalyzed)
                put(COL_VOCODER_CUTOFF, if (record.vocoderCutoffDetected) 1 else 0)
                put(COL_COERCION_DETECTED, if (record.coercionDetected) 1 else 0)
                put(COL_TRIGGERED_KEYWORDS, record.triggeredKeywords.joinToString(","))
            }
            db.insertWithOnConflict(TABLE_CALLS, null, values, SQLiteDatabase.CONFLICT_REPLACE)

            for (pt in record.timeline) {
                val ptVal = ContentValues().apply {
                    put(COL_FK_CALL_ID, record.callId)
                    put(COL_TIMESTAMP_MS, pt.timestampMs)
                    put(COL_OFFSET_SECONDS, pt.offsetSeconds)
                    put(COL_SYNTHETIC_SCORE, pt.syntheticScore)
                    put(COL_RISK_LEVEL, pt.riskLevel.name)
                    put(COL_IS_VOICED, if (pt.isVoiced) 1 else 0)
                    put(COL_SPECTRAL_CUTOFF, if (pt.spectralCutoffDetected) 1 else 0)
                    put(COL_TRIGGERED_KEYWORD, pt.triggeredKeyword)
                }
                db.insert(TABLE_TIMELINE, null, ptVal)
            }
            db.setTransactionSuccessful()
            AppLogger.d("[TrueVoice DB] Persisted call ${record.callId} with ${record.timeline.size} timeline points")
        } catch (e: Exception) {
            AppLogger.e("[TrueVoice DB] Failed to insert call record", e)
        } finally {
            db.endTransaction()
        }
    }

    fun getAllCallRecords(): List<ForensicCallRecord> {
        val records = mutableListOf<ForensicCallRecord>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_CALLS,
            null,
            null,
            null,
            null,
            null,
            "$COL_START_TIME_MS DESC"
        )

        cursor.use { c ->
            while (c.moveToNext()) {
                val callId = c.getString(c.getColumnIndexOrThrow(COL_CALL_ID))
                val callerNumber = c.getString(c.getColumnIndexOrThrow(COL_CALLER_NUMBER))
                val callerName = c.getString(c.getColumnIndexOrThrow(COL_CALLER_NAME))
                val startTimeMs = c.getLong(c.getColumnIndexOrThrow(COL_START_TIME_MS))
                val endTimeMs = c.getLong(c.getColumnIndexOrThrow(COL_END_TIME_MS))
                val durationSeconds = c.getInt(c.getColumnIndexOrThrow(COL_DURATION_SECONDS))
                val finalVerdictStr = c.getString(c.getColumnIndexOrThrow(COL_FINAL_VERDICT))
                val finalVerdict = runCatching { RiskLevel.valueOf(finalVerdictStr) }.getOrDefault(RiskLevel.INCONCLUSIVE)
                val peakScore = c.getFloat(c.getColumnIndexOrThrow(COL_PEAK_SCORE))
                val avgScore = c.getFloat(c.getColumnIndexOrThrow(COL_AVG_SCORE))
                val totalWindows = c.getInt(c.getColumnIndexOrThrow(COL_TOTAL_WINDOWS))
                val vocoder = c.getInt(c.getColumnIndexOrThrow(COL_VOCODER_CUTOFF)) == 1
                val coercion = c.getInt(c.getColumnIndexOrThrow(COL_COERCION_DETECTED)) == 1
                val keywordsStr = c.getString(c.getColumnIndexOrThrow(COL_TRIGGERED_KEYWORDS)) ?: ""
                val keywords = if (keywordsStr.isNotBlank()) keywordsStr.split(",") else emptyList()

                val points = getTimelinePointsForCall(callId)

                records.add(
                    ForensicCallRecord(
                        callId = callId,
                        startTimeMs = startTimeMs,
                        endTimeMs = endTimeMs,
                        durationSeconds = durationSeconds,
                        callerNumber = callerNumber,
                        callerName = callerName,
                        finalVerdict = finalVerdict,
                        peakSyntheticScore = peakScore,
                        averageSyntheticScore = avgScore,
                        totalWindowsAnalyzed = totalWindows,
                        vocoderCutoffDetected = vocoder,
                        coercionDetected = coercion,
                        triggeredKeywords = keywords,
                        timeline = points
                    )
                )
            }
        }
        return records
    }

    fun getTimelinePointsForCall(callId: String): List<ForensicTimelinePoint> {
        val points = mutableListOf<ForensicTimelinePoint>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_TIMELINE,
            null,
            "$COL_FK_CALL_ID = ?",
            arrayOf(callId),
            null,
            null,
            "$COL_OFFSET_SECONDS ASC"
        )
        cursor.use { c ->
            while (c.moveToNext()) {
                val timestampMs = c.getLong(c.getColumnIndexOrThrow(COL_TIMESTAMP_MS))
                val offset = c.getFloat(c.getColumnIndexOrThrow(COL_OFFSET_SECONDS))
                val score = c.getFloat(c.getColumnIndexOrThrow(COL_SYNTHETIC_SCORE))
                val riskLevelStr = c.getString(c.getColumnIndexOrThrow(COL_RISK_LEVEL))
                val riskLevel = runCatching { RiskLevel.valueOf(riskLevelStr) }.getOrDefault(RiskLevel.INCONCLUSIVE)
                val isVoiced = c.getInt(c.getColumnIndexOrThrow(COL_IS_VOICED)) == 1
                val cutoff = c.getInt(c.getColumnIndexOrThrow(COL_SPECTRAL_CUTOFF)) == 1
                val keyword = c.getString(c.getColumnIndexOrThrow(COL_TRIGGERED_KEYWORD))

                points.add(
                    ForensicTimelinePoint(
                        timestampMs = timestampMs,
                        offsetSeconds = offset,
                        syntheticScore = score,
                        riskLevel = riskLevel,
                        isVoiced = isVoiced,
                        spectralCutoffDetected = cutoff,
                        triggeredKeyword = keyword
                    )
                )
            }
        }
        return points
    }

    // ==========================================
    // Speaker Biometrics Persistence
    // ==========================================

    data class EnrolledSpeaker(
        val contactId: String,
        val contactName: String,
        val phoneNumber: String,
        val embedding: FloatArray,
        val sampleCount: Int,
        val lastUpdatedMs: Long
    )

    fun saveSpeakerEmbedding(speaker: EnrolledSpeaker) {
        val db = writableDatabase
        val byteBuffer = ByteBuffer.allocate(speaker.embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        for (f in speaker.embedding) {
            byteBuffer.putFloat(f)
        }

        val values = ContentValues().apply {
            put(COL_CONTACT_ID, speaker.contactId)
            put(COL_CONTACT_NAME, speaker.contactName)
            put(COL_CONTACT_PHONE, speaker.phoneNumber)
            put(COL_EMBEDDING_BLOB, byteBuffer.array())
            put(COL_SAMPLE_COUNT, speaker.sampleCount)
            put(COL_LAST_UPDATED_MS, speaker.lastUpdatedMs)
        }
        db.insertWithOnConflict(TABLE_SPEAKERS, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        AppLogger.i("[TrueVoice DB] Saved voice embedding for speaker ${speaker.contactName} (${speaker.phoneNumber})")
    }

    fun getSpeakerByPhone(phoneNumber: String): EnrolledSpeaker? {
        val db = readableDatabase
        val cleanPhone = phoneNumber.filter { it.isDigit() }
        val searchPhone = if (cleanPhone.length >= 10) cleanPhone.takeLast(10) else cleanPhone

        val cursor = db.query(
            TABLE_SPEAKERS,
            null,
            "$COL_CONTACT_PHONE LIKE ?",
            arrayOf("%$searchPhone"),
            null,
            null,
            null,
            "1"
        )
        cursor.use { c ->
            if (c.moveToFirst()) {
                val contactId = c.getString(c.getColumnIndexOrThrow(COL_CONTACT_ID))
                val name = c.getString(c.getColumnIndexOrThrow(COL_CONTACT_NAME))
                val phone = c.getString(c.getColumnIndexOrThrow(COL_CONTACT_PHONE))
                val blob = c.getBlob(c.getColumnIndexOrThrow(COL_EMBEDDING_BLOB))
                val count = c.getInt(c.getColumnIndexOrThrow(COL_SAMPLE_COUNT))
                val updated = c.getLong(c.getColumnIndexOrThrow(COL_LAST_UPDATED_MS))

                val floatBuffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val embedding = FloatArray(floatBuffer.remaining())
                floatBuffer.get(embedding)

                return EnrolledSpeaker(contactId, name, phone, embedding, count, updated)
            }
        }
        return null
    }

    fun getAllEnrolledSpeakers(): List<EnrolledSpeaker> {
        val list = mutableListOf<EnrolledSpeaker>()
        val db = readableDatabase
        val cursor = db.query(TABLE_SPEAKERS, null, null, null, null, null, "$COL_CONTACT_NAME ASC")
        cursor.use { c ->
            while (c.moveToNext()) {
                val contactId = c.getString(c.getColumnIndexOrThrow(COL_CONTACT_ID))
                val name = c.getString(c.getColumnIndexOrThrow(COL_CONTACT_NAME))
                val phone = c.getString(c.getColumnIndexOrThrow(COL_CONTACT_PHONE))
                val blob = c.getBlob(c.getColumnIndexOrThrow(COL_EMBEDDING_BLOB))
                val count = c.getInt(c.getColumnIndexOrThrow(COL_SAMPLE_COUNT))
                val updated = c.getLong(c.getColumnIndexOrThrow(COL_LAST_UPDATED_MS))

                val floatBuffer = ByteBuffer.wrap(blob).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                val embedding = FloatArray(floatBuffer.remaining())
                floatBuffer.get(embedding)

                list.add(EnrolledSpeaker(contactId, name, phone, embedding, count, updated))
            }
        }
        return list
    }
}
