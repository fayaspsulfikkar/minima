package com.minima.domain.usecase

import com.minima.domain.model.AppInfo
import com.minima.domain.repository.AppRepository
import javax.inject.Inject

/**
 * Use case to retrieve the list of installed applications.
 */
class GetInstalledAppsUseCase @Inject constructor(
    private val repository: AppRepository
) {
    operator fun invoke(): List<AppInfo> {
        return repository.getInstalledApps()
    }
}
