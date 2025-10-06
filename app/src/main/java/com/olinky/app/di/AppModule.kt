package com.olinky.app.di

import android.content.Context
import com.olinky.core.gadget.GadgetConfig
import com.olinky.data.ImageRepository
import com.olinky.data.OnboardingRepository
import com.olinky.feature.pxe.PxeController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOnboardingRepository(
        @ApplicationContext context: Context
    ): OnboardingRepository = OnboardingRepository(context)

    @Provides
    @Singleton
    fun provideImageRepository(): ImageRepository = ImageRepository()

    @Provides
    @Singleton
    fun provideGadgetConfig(): GadgetConfig = GadgetConfig()

    @Provides
    @Singleton
    fun providePxeController(): PxeController = PxeController()
}
