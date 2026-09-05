package com.pingwei.lengkubao.ui.config

import android.app.Application
import androidx.lifecycle.*
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.dao.LocationDao
import com.pingwei.lengkubao.data.db.entity.Location
import com.pingwei.lengkubao.utils.ConfigDeleteResult
import com.pingwei.lengkubao.utils.ConfigDeleteService
import com.pingwei.lengkubao.utils.SyncTrigger
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocationConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val locationDao: LocationDao
    private val appContext = application.applicationContext
    private val _locations = MutableStateFlow<List<Location>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val locations: StateFlow<List<Location>> = _locations.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        locationDao = AppDatabase.getInstance(application).locationDao()
        loadLocations()
    }

    private fun loadLocations() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                locationDao.getAllLocations()
                    .collect { locations ->
                        _locations.value = locations
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                _isLoading.value = false
            }
        }
    }

    suspend fun countLocationBillRefs(locationId: Long): Int {
        return ConfigDeleteService.countLocationBillRefs(appContext, locationId)
    }

    suspend fun addLocation(
        locationName: String,
        description: String = "",
        capacity: Int = 0,
    ): Boolean {
        val trimmedName = locationName.trim()
        if (trimmedName.isBlank()) return false
        if (locationDao.countByLocationName(trimmedName) > 0) return false

        val location = Location(
            locationName = trimmedName,
            description = description,
            capacity = capacity,
            enabled = true,
        )

        val id = locationDao.insert(location)
        SyncTrigger.triggerLocationSync(appContext, id)
        return true
    }

    suspend fun updateLocation(location: Location) {
        locationDao.update(location.copy(syncStatus = 0))
        SyncTrigger.triggerLocationSync(appContext, location.id)
    }

    suspend fun deleteLocation(location: Location): ConfigDeleteResult {
        val result = ConfigDeleteService.deleteLocation(appContext, location)
        when (result) {
            is ConfigDeleteResult.PhysicallyDeleted ->
                _locations.value = _locations.value.filter { it.id != location.id }
            is ConfigDeleteResult.DisabledDueToReferences ->
                _locations.value = _locations.value.map {
                    if (it.id == location.id) it.copy(enabled = false) else it
                }
            else -> {}
        }
        return result
    }

    suspend fun toggleLocationEnabled(location: Location) {
        val newEnabled = !location.enabled
        locationDao.updateEnabledStatus(location.id, newEnabled)
        SyncTrigger.triggerLocationSync(appContext, location.id)
    }
}

class LocationConfigViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LocationConfigViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
