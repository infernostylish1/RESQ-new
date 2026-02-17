package com.resq.app.di

import android.content.Context
import com.resq.app.data.dao.DisasterDao
import com.resq.app.data.dao.DisasterInfoDao
import com.resq.app.data.dao.DisasterPreparationDao
import com.resq.app.data.dao.DisasterTipDao
import com.resq.app.data.dao.EmergencyContactDao
import com.resq.app.data.dao.FirstAidCategoryDao
import com.resq.app.data.dao.FirstAidContentDao
import com.resq.app.data.dao.UserProfileDao
import com.resq.app.data.dao.UserSettingsDao
import com.resq.app.data.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Hilt module that provides database-related dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
        scope: CoroutineScope
    ): AppDatabase {
        return AppDatabase.getDatabase(context, scope)
    }

    @Provides
    @Singleton
    fun provideUserProfileDao(database: AppDatabase): UserProfileDao {
        return database.userProfileDao()
    }

    @Provides
    @Singleton
    fun provideUserSettingsDao(database: AppDatabase): UserSettingsDao {
        return database.userSettingsDao()
    }

    @Provides
    @Singleton
    fun provideDisasterDao(database: AppDatabase): DisasterDao {
        return database.disasterDao()
    }

    @Provides
    @Singleton
    fun provideDisasterInfoDao(database: AppDatabase): DisasterInfoDao {
        return database.disasterInfoDao()
    }

    @Provides
    @Singleton
    fun provideDisasterPreparationDao(database: AppDatabase): DisasterPreparationDao {
        return database.disasterPreparationDao()
    }

    @Provides
    @Singleton
    fun provideFirstAidCategoryDao(database: AppDatabase): FirstAidCategoryDao {
        return database.firstAidCategoryDao()
    }

    @Provides
    @Singleton
    fun provideFirstAidContentDao(database: AppDatabase): FirstAidContentDao {
        return database.firstAidContentDao()
    }

    @Provides
    @Singleton
    fun provideDisasterTipDao(database: AppDatabase): DisasterTipDao {
        return database.disasterTipDao()
    }

    @Provides
    @Singleton
    fun provideEmergencyContactDao(database: AppDatabase): EmergencyContactDao {
        return database.emergencyContactDao()
    }
}