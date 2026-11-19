package com.minima.domain.usecase

import com.minima.domain.repository.AppRepository
import javax.inject.Inject

/**
 * Use case to launch an application by its package name.
 */
class LaunchAppUseCase @Inject constructor(
    private val repository: AppRepository
) {
    operator fun invoke(packageName: String): Boolean {
        return repository.launchApp(packageName)
    }
}
