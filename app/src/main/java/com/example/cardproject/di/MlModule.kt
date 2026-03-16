package com.example.cardproject.di

import android.content.Context
import com.example.cardproject.ml.MLSpacedRepetitionCalculator
import com.example.cardproject.ml.TensorFlowLiteModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object MlModule {

    @Provides
    @Singleton
    fun provideTensorFlowLiteModel(
        @ApplicationContext context: Context
    ): TensorFlowLiteModel {
        return TensorFlowLiteModel(context)
    }

    @Provides
    @Singleton
    fun provideMLCalculator(
        tfliteModel: TensorFlowLiteModel,
        reviewLogRepository: com.example.cardproject.database.repository.ReviewLogRepository
    ): MLSpacedRepetitionCalculator {
        return MLSpacedRepetitionCalculator(tfliteModel, reviewLogRepository)
    }
}