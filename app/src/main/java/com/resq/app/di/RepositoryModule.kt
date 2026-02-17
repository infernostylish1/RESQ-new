package com.resq.app.di

import com.resq.app.data.dao.DisasterDao
import com.resq.app.data.dao.DisasterInfoDao
import com.resq.app.data.dao.DisasterPreparationDao
import com.resq.app.data.dao.DisasterTipDao
import com.resq.app.data.dao.EmergencyContactDao
import com.resq.app.data.dao.FirstAidCategoryDao
import com.resq.app.data.dao.FirstAidContentDao
import com.resq.app.data.dao.UserProfileDao
import com.resq.app.data.dao.UserSettingsDao
import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.DisasterTipRepository
import com.resq.app.data.repository.EmergencyContactRepository
import com.resq.app.data.repository.FirstAidRepository
import com.resq.app.data.repository.UserProfileRepository
import com.resq.app.data.repository.UserSettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module that provides repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideUserProfileRepository(
        userProfileDao: UserProfileDao
    ): UserProfileRepository {
        return UserProfileRepository(userProfileDao)
    }

    @Provides
    @Singleton
    fun provideUserSettingsRepository(
        userSettingsDao: UserSettingsDao
    ): UserSettingsRepository {
        return UserSettingsRepository(userSettingsDao)
    }

    @Provides
    @Singleton
    fun provideDisasterRepository(
        disasterDao: DisasterDao,
        disasterInfoDao: DisasterInfoDao,
        disasterPreparationDao: DisasterPreparationDao
    ): DisasterRepository {
        return DisasterRepository(disasterDao, disasterInfoDao, disasterPreparationDao)
    }

    @Provides
    @Singleton
    fun provideFirstAidRepository(
        firstAidCategoryDao: FirstAidCategoryDao,
        firstAidContentDao: FirstAidContentDao
    ): FirstAidRepository {
        return FirstAidRepository(firstAidCategoryDao, firstAidContentDao)
    }

    @Provides
    @Singleton
    fun provideEmergencyContactRepository(
        emergencyContactDao: EmergencyContactDao
    ): EmergencyContactRepository {
        return EmergencyContactRepository(emergencyContactDao)
    }

    @Provides
    @Singleton
    fun provideDisasterTipRepository(
        disasterTipDao: DisasterTipDao
    ): DisasterTipRepository {
        return DisasterTipRepository(disasterTipDao)
    }
}