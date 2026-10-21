package io.homekotlin.ha

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.ConcurrentHashMap

class HaStateStore {
    private val states = ConcurrentHashMap<String, HaState>()
    private val _events = MutableSharedFlow<StateChangeEvent>(extraBufferCapacity = 1000)
    val events: SharedFlow<StateChangeEvent> = _events.asSharedFlow()

    operator fun get(entityId: CharSequence): HaState? = states[entityId.toString()]

    fun getState(entityId: CharSequence): HaState? = states[entityId.toString()]

    fun getString(entityId: CharSequence): String? = states[entityId.toString()]?.state

    fun getNumber(entityId: CharSequence): Double? = states[entityId.toString()]?.doubleValue()

    fun getBoolean(entityId: CharSequence): Boolean? = states[entityId.toString()]?.booleanValue()

    fun isState(entityId: CharSequence, expectedState: String): Boolean {
        return states[entityId.toString()]?.state?.equals(expectedState, ignoreCase = true) == true
    }

    fun allStates(): Map<String, HaState> = HashMap(states)

    fun filterByDomain(domain: String): List<HaState> {
        val prefix = "$domain."
        return states.values.filter { it.entityId.startsWith(prefix) }
    }

    fun search(query: String): List<HaState> {
        val q = query.lowercase().trim()
        if (q.isEmpty()) return states.values.toList().sortedBy { it.entityId }
        return states.values.filter {
            it.entityId.lowercase().contains(q) ||
            it.friendlyName.lowercase().contains(q)
        }.sortedBy { it.entityId }
    }

    suspend fun updateState(newState: HaState): StateChangeEvent {
        val oldState = states.put(newState.entityId, newState)
        val event = StateChangeEvent(
            entityId = newState.entityId,
            oldState = oldState,
            newState = newState
        )
        _events.emit(event)
        return event
    }

    fun bulkUpdate(newStates: List<HaState>) {
        for (state in newStates) {
            states[state.entityId] = state
        }
    }

    fun count(): Int = states.size
}
