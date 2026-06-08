// ui/config/LocationConfigViewModel.kt (无Hilt版本)
package com.pingwei.lengkubao.ui.config

import android.app.Application
import androidx.lifecycle.*
import com.pingwei.lengkubao.data.db.AppDatabase
import com.pingwei.lengkubao.data.db.dao.LocationDao
import com.pingwei.lengkubao.data.db.entity.Location
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LocationConfigViewModel(application: Application) : AndroidViewModel(application) {

    private val locationDao: LocationDao
    private val _searchText = MutableStateFlow("")
    private val _locations = MutableStateFlow<List<Location>>(emptyList())
    private val _searchResults = MutableStateFlow<List<Location>>(emptyList())
    private val _isLoading = MutableStateFlow(false)

    val searchText: StateFlow<String> = _searchText.asStateFlow()
    val locations: StateFlow<List<Location>> = _locations.asStateFlow()
    val searchResults: StateFlow<List<Location>> = _searchResults.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        locationDao = AppDatabase.getInstance(application).locationDao()
        loadLocations()
        setupSearch()
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

    private fun setupSearch() {
        viewModelScope.launch {
            _searchText
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.isBlank()) {
                        _searchResults.value = emptyList()
                    } else {
                        locationDao.searchLocations(query)
                            .collect { results ->
                                _searchResults.value = results
                            }
                    }
                }
        }
    }

    fun searchLocations(query: String) {
        _searchText.value = query
    }

    fun clearSearch() {
        _searchText.value = ""
    }

    suspend fun addLocation(
        locationNo: String,
        locationName: String,
        description: String = "",
        capacity: Int = 0
    ): Boolean {
        val exists = locationDao.countByLocationNo(locationNo) > 0
        if (exists) {
            return false
        }

        val location = Location(
            locationNo = locationNo,
            locationName = locationName,
            description = description,
            capacity = capacity,
            enabled = true
        )

        locationDao.insert(location)
        return true
    }

    suspend fun updateLocation(location: Location) {
        locationDao.update(location)
    }

    suspend fun deleteLocation(location: Location) {
        locationDao.delete(location)
    }

    suspend fun toggleLocationEnabled(location: Location) {
        locationDao.updateEnabledStatus(location.id, !location.enabled)
    }


}

// ViewModel Factory
class LocationConfigViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LocationConfigViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LocationConfigViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}