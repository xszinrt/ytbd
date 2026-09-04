package com.arabi.ytdownloader.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.arabi.ytdownloader.data.AppDatabase
import com.arabi.ytdownloader.data.ExtractionResult
import com.arabi.ytdownloader.data.PlaylistRepository
import kotlinx.coroutines.launch

sealed class ExtractUiState {
    object Idle : ExtractUiState()
    object Loading : ExtractUiState()
    data class Success(val result: ExtractionResult, val alreadyQueuedIds: Set<String>) : ExtractUiState()
    data class Error(val message: String) : ExtractUiState()
}

class PlaylistExtractorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlaylistRepository()
    private val dao = AppDatabase.getInstance(application).videoDao()

    private val _uiState = MutableLiveData<ExtractUiState>(ExtractUiState.Idle)
    val uiState: LiveData<ExtractUiState> = _uiState

    fun extract(url: String) {
        _uiState.value = ExtractUiState.Loading
        viewModelScope.launch {
            val result = repository.extract(url)
            result.fold(
                onSuccess = { extraction ->
                    val ids = when (extraction) {
                        is ExtractionResult.Playlist -> extraction.entries.map { it.id }
                        is ExtractionResult.SingleVideo -> listOf(extraction.entry.id)
                    }
                    val existing = dao.findExistingIds(ids).toSet()
                    _uiState.value = ExtractUiState.Success(extraction, existing)
                },
                onFailure = { e ->
                    _uiState.value = ExtractUiState.Error(e.message ?: "فشل استخراج المعلومات")
                }
            )
        }
    }

    fun reset() {
        _uiState.value = ExtractUiState.Idle
    }
}
