// com/resq/app/di/SearchModule.kt
package com.resq.app.di

import com.resq.app.data.repository.DisasterRepository
import com.resq.app.data.repository.DisasterTipRepository
import com.resq.app.data.repository.EmergencyContactRepository
import com.resq.app.data.repository.FirstAidRepository
import com.resq.app.data.repository.GlobalSearchRepository
import com.resq.app.util.SearchHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SearchModule {

    @Provides
    @Singleton
    fun provideGlobalSearchRepository(
        disasterRepository: DisasterRepository,
        firstAidRepository: FirstAidRepository,
        disasterTipRepository: DisasterTipRepository,
        emergencyContactRepository: EmergencyContactRepository,
        searchHelper: SearchHelper
    ): GlobalSearchRepository = GlobalSearchRepository(
        disasterRepository       = disasterRepository,
        firstAidRepository       = firstAidRepository,
        disasterTipRepository    = disasterTipRepository,
        emergencyContactRepository = emergencyContactRepository,
        searchHelper             = searchHelper
    )
}