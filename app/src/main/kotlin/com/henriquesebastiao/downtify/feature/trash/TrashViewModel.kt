package com.henriquesebastiao.downtify.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.henriquesebastiao.downtify.core.data.library.LibraryTrashRepository
import com.henriquesebastiao.downtify.core.data.library.TrashFailure
import com.henriquesebastiao.downtify.core.data.sync.SyncCoordinator
import com.henriquesebastiao.downtify.core.model.Track
import com.henriquesebastiao.downtify.core.model.TrashItem
import com.henriquesebastiao.downtify.core.model.TrashPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrashUiState(
    val open: Boolean = false,
    val preview: TrashPreview? = null,
    val preparationHidden: Boolean = false,
    val choosing: List<Track>? = null,
    val chosen: Set<String> = emptySet(),
    val items: List<TrashItem> = emptyList(),
    val selected: Set<String> = emptySet(),
    val search: String = "",
    val offset: Int = 0,
    val total: Int = 0,
    val size: Long = 0,
    val busy: Boolean = false,
    val exclusions: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: LibraryTrashRepository,
    private val sync: SyncCoordinator,
) : ViewModel() {
    private val mutable = MutableStateFlow(TrashUiState())
    val state = mutable.asStateFlow()
    private val capability = MutableStateFlow(false)
    val canManage = capability.asStateFlow()
    private var loading: Job? = null
    private var previewJob: Job? = null
    private var signature = ""

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.session.collectLatest { session ->
                capability.value = false
                mutable.value = TrashUiState()
                signature = ""
                loading?.cancel()
                previewJob?.cancel()
                if (session != null) capability.value = repository.canManage()
            }
        }
    }

    fun open() {
        if (!capability.value) return
        mutable.update { it.copy(open = true, message = null) }
        refresh()
    }

    fun close() {
        loading?.cancel()
        mutable.update { it.copy(open = false) }
    }

    fun choose(tracks: List<Track>) {
        if (capability.value) mutable.update { it.copy(choosing = tracks, chosen = emptySet(), message = null) }
    }

    fun toggleChoice(id: String) = mutable.update {
        val selected = if (id in it.chosen) {
            it.chosen - id
        } else if (it.chosen.size < 200) {
            it.chosen + id
        } else {
            it.chosen
        }
        it.copy(chosen = selected)
    }

    fun previewChoice() {
        val ids = mutable.value.chosen.toList()
        mutable.update { it.copy(choosing = null, chosen = emptySet()) }
        previewIds(ids)
    }

    fun preview(tracks: List<Track>) = previewIds(tracks.map { it.id })

    private fun previewIds(ids: List<String>) {
        if (!capability.value || ids.isEmpty()) return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            mutable.update { it.copy(busy = true, message = null, preparationHidden = false) }
            try {
                val preview = repository.preview(ids)
                mutable.update { it.copy(preview = preview) }
            } catch (error: TrashFailure) {
                fail(error)
            } finally {
                mutable.update { it.copy(busy = false) }
            }
        }
    }

    fun dismissPreview() {
        if (mutable.value.busy) return
        mutable.update { it.copy(preview = null, choosing = null, chosen = emptySet(), message = null) }
    }

    fun hidePreparation() = mutable.update { it.copy(preparationHidden = true) }

    fun confirm() {
        val token = mutable.value.preview?.confirmation ?: return
        act {
            repository.confirm(token)
            mutable.update { it.copy(preview = null, open = true, message = "pending") }
        }
    }

    fun restore(ids: List<String>) = act { repository.restore(ids) }
    fun retry(id: String) = act { repository.retry(listOf(id)) }
    fun allow(id: String) = act { repository.allowDownload(listOf(id)) }

    private fun act(block: suspend () -> Unit) {
        if (!capability.value || mutable.value.busy) return
        viewModelScope.launch {
            mutable.update { it.copy(busy = true, message = null) }
            try {
                block()
                mutable.update { it.copy(selected = emptySet()) }
                sync.requestSync()
            } catch (error: TrashFailure) {
                fail(error)
            } finally {
                mutable.update { it.copy(busy = false) }
                refresh()
            }
        }
    }

    fun refresh() {
        if (!capability.value || !mutable.value.open) return
        if (loading?.isActive == true || mutable.value.busy) return
        loading = viewModelScope.launch {
            val before = mutable.value
            try {
                val page = if (before.exclusions) {
                    repository.exclusions()
                } else {
                    repository.list(
                        before.offset,
                        before.search,
                    )
                }
                val currentSignature = page.items.joinToString { it.id + it.state }
                if (signature.isNotEmpty() && signature != currentSignature) sync.requestSync()
                signature = currentSignature
                mutable.update {
                    it.copy(
                        items = page.items,
                        total =
                            page.total.takeIf { count -> count > 0 } ?: page.items.size,
                        size = page.size,
                    )
                }
            } catch (error: TrashFailure) {
                fail(error)
            }
        }
    }

    fun search(value: String) {
        loading?.cancel()
        mutable.update { it.copy(search = value, offset = 0) }
        refresh()
    }

    fun page(delta: Int) {
        loading?.cancel()
        mutable.update { it.copy(offset = (it.offset + delta).coerceAtLeast(0), selected = emptySet()) }
        refresh()
    }

    fun toggleExclusions() {
        loading?.cancel()
        mutable.update { it.copy(exclusions = !it.exclusions, offset = 0, selected = emptySet(), items = emptyList()) }
        refresh()
    }

    fun toggle(id: String) = mutable.update {
        it.copy(selected = if (id in it.selected) it.selected - id else it.selected + id)
    }

    private fun fail(error: TrashFailure) {
        if (error.code == "admin_required") {
            capability.value = false
            mutable.value = TrashUiState(message = error.code)
        } else {
            mutable.update { it.copy(message = error.code) }
        }
    }
}
