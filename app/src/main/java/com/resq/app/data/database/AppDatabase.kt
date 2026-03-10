package com.resq.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.resq.app.data.dao.DisasterDao
import com.resq.app.data.dao.DisasterInfoDao
import com.resq.app.data.dao.DisasterPreparationDao
import com.resq.app.data.dao.DisasterTipDao
import com.resq.app.data.dao.EmergencyContactDao
import com.resq.app.data.dao.FirstAidCategoryDao
import com.resq.app.data.dao.FirstAidContentDao
import com.resq.app.data.dao.UserProfileDao
import com.resq.app.data.dao.UserSettingsDao
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.data.entity.DisasterInfoEntity
import com.resq.app.data.entity.DisasterPreparationEntity
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.data.entity.FirstAidCategoryEntity
import com.resq.app.data.entity.FirstAidContentEntity
import com.resq.app.data.entity.UserProfileEntity
import com.resq.app.data.entity.UserSettingsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Room database for the RESQ application.
 * Contains all entities and provides DAOs for data access.
 */
@Database(
    entities = [
        UserProfileEntity::class,
        UserSettingsEntity::class,
        DisasterEntity::class,
        DisasterInfoEntity::class,
        DisasterPreparationEntity::class,
        FirstAidCategoryEntity::class,
        FirstAidContentEntity::class,
        DisasterTipEntity::class,
        EmergencyContactEntity::class
    ],
    version = 4,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userProfileDao(): UserProfileDao
    abstract fun userSettingsDao(): UserSettingsDao
    abstract fun disasterDao(): DisasterDao
    abstract fun disasterInfoDao(): DisasterInfoDao
    abstract fun disasterPreparationDao(): DisasterPreparationDao
    abstract fun firstAidCategoryDao(): FirstAidCategoryDao
    abstract fun firstAidContentDao(): FirstAidContentDao
    abstract fun disasterTipDao(): DisasterTipDao
    abstract fun emergencyContactDao(): EmergencyContactDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resq_database"
                )
                    .addCallback(DatabaseCallback(scope))
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDatabaseWithoutCallback(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "resq_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : Callback() {

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    populateDatabase(database)
                }
            }
        }

        suspend fun populateDatabase(database: AppDatabase) {
            // Initialize default user profile
            database.userProfileDao().insertProfile(UserProfileEntity())

            // Initialize default settings
            database.userSettingsDao().insertSettings(UserSettingsEntity())

            // Populate disasters
            val disasters = PreloadedDisasterData.getDisasters()
            database.disasterDao().insertAllDisasters(disasters)

            // Populate disaster info
            val disasterInfo = PreloadedDisasterData.getDisasterInfo()
            database.disasterInfoDao().insertAllInfo(disasterInfo)

            // Populate disaster preparation
            val preparations = PreloadedDisasterData.getDisasterPreparation()
            database.disasterPreparationDao().insertAllPreparation(preparations)

            // Populate disaster tips
            val tips = PreloadedDisasterData.getDisasterTips()
            database.disasterTipDao().insertTips(tips)

            // Populate first aid categories
            val firstAidCategories = PreloadedFirstAidData.getCategories()
            database.firstAidCategoryDao().insertAllCategories(firstAidCategories)

            // Populate first aid content
            val firstAidContent = PreloadedFirstAidData.getContent()
            database.firstAidContentDao().insertAllContent(firstAidContent)

            // Populate emergency contacts
            val contacts = PreloadedContactData.getContacts()
            database.emergencyContactDao().insertAllContacts(contacts)
        }
    }
}