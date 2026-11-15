package com.focusguard.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val isCompletedToday: Boolean
)

@Entity(tableName = "relapse_reflections")
data class RelapseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val streakLostDays: Int,
    val trigger: String,
    val emotionalState: String,
    val tomorrowActionPlan: String
)

@Entity(tableName = "heartfulness_journal")
data class HeartfulnessEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val practice: String,
    val durationMinutes: Int,
    val innerCondition: String,
    val notes: String
)

@Dao
interface FocusGuardDao {
    @Query("SELECT * FROM habits")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabits(habits: List<HabitEntity>)

    @Query("SELECT * FROM relapse_reflections ORDER BY timestamp DESC")
    fun getRelapses(): Flow<List<RelapseEntity>>

    @Insert
    suspend fun insertRelapse(relapse: RelapseEntity)

    @Query("SELECT * FROM heartfulness_journal ORDER BY timestamp DESC")
    fun getHeartfulnessEntries(): Flow<List<HeartfulnessEntryEntity>>

    @Insert
    suspend fun insertHeartfulnessEntry(entry: HeartfulnessEntryEntity)
}

@Database(
    entities = [HabitEntity::class, RelapseEntity::class, HeartfulnessEntryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun focusGuardDao(): FocusGuardDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_guard_offline.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

object FocusGuardPrefs {
    private const val PREF_NAME = "focus_guard_prefs"

    fun getPrefs(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isProtectionActive(context: Context): Boolean =
        getPrefs(context).getBoolean("master_protection_active", true)

    fun setProtectionActive(context: Context, active: Boolean) {
        getPrefs(context).edit().putBoolean("master_protection_active", active).apply()
    }

    fun getFamilyDnsIp(context: Context): String =
        getPrefs(context).getString("family_dns_ip", "94.140.14.15") ?: "94.140.14.15"

    fun getFamilyDnsHost(context: Context): String =
        getPrefs(context).getString("family_dns_host", "family.adguard-dns.com") ?: "family.adguard-dns.com"

    fun setFamilyDns(context: Context, host: String, ip: String) {
        getPrefs(context).edit()
            .putString("family_dns_host", host)
            .putString("family_dns_ip", ip)
            .apply()
    }

    fun getStreakDays(context: Context): Int =
        getPrefs(context).getInt("streak_days", 1)

    fun getUrgesManaged(context: Context): Int =
        getPrefs(context).getInt("urges_managed", 0)

    fun incrementUrgesManaged(context: Context): Int {
        val next = getUrgesManaged(context) + 1
        getPrefs(context).edit().putInt("urges_managed", next).apply()
        return next
    }

    fun getBlockedCount(context: Context): Int =
        getPrefs(context).getInt("blocked_count", 0)

    fun incrementBlockedCount(context: Context): Int {
        val next = getBlockedCount(context) + 1
        getPrefs(context).edit().putInt("blocked_count", next).apply()
        return next
    }

    fun getLifeGoal(context: Context): String =
        getPrefs(context).getString("life_goal", "Achieve self-mastery, mental purity, and fulfill my family's hopes.") ?: ""

    fun getCareerGoal(context: Context): String =
        getPrefs(context).getString("career_goal", "Excel in my studies and career with undivided focus.") ?: ""

    fun getFamilyExpectations(context: Context): String =
        getPrefs(context).getString("family_expectations", "My family believes in me and sacrificed for my future.") ?: ""

    fun getMotivationalMessage(context: Context): String =
        getPrefs(context).getString("motivational_message", "A few minutes of temporary stimulation will never compare to lifelong self-respect.") ?: ""

    fun saveGoals(context: Context, lifeGoal: String, careerGoal: String, family: String, message: String) {
        getPrefs(context).edit()
            .putString("life_goal", lifeGoal)
            .putString("career_goal", careerGoal)
            .putString("family_expectations", family)
            .putString("motivational_message", message)
            .putBoolean("onboarding_done", true)
            .apply()
    }
}
