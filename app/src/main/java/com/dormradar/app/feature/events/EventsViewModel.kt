package com.dormradar.app.feature.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dormradar.app.core.data.EventFilter
import com.dormradar.app.core.data.EventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 事件时间线（阶段 3：Room 流水 + 筛选，docs/06 阶段 3 任务 3） */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EventsViewModel @Inject constructor(
    private val repo: EventRepository,
) : ViewModel() {

    private val _filter = MutableStateFlow(EventFilter.ALL)
    val filter: StateFlow<EventFilter> = _filter.asStateFlow()

    val events: StateFlow<List<com.dormradar.app.core.data.room.RadarEventEntity>> =
        _filter.flatMapLatest { repo.observe(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: EventFilter) {
        _filter.value = filter
    }

    fun clearAll() {
        viewModelScope.launch { repo.clearAll() }
    }
}
