package com.damagdpixl.svita.ui.avatar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.damagdpixl.svita.core.designsystem.AvatarBodyType
import com.damagdpixl.svita.core.designsystem.AvatarSkinTone
import com.damagdpixl.svita.data.AvatarPrefs
import com.damagdpixl.svita.data.SvitaGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** State of the Settings -> Avatar screen. */
data class AvatarState(
    val loading: Boolean = true,
    val body: AvatarBodyType = AvatarBodyType.REGULAR,
    val tone: AvatarSkinTone = AvatarSkinTone.LIGHT,
)

/**
 * Behind Settings -> Avatar: loads the persisted paper-doll configuration
 * (keys `avatar.body` / `avatar.tone`), keeps the picker state, and writes
 * every change straight back to the settings repository.
 */
class AvatarViewModel(private val graph: SvitaGraph.Graph) : ViewModel() {

    private val settings = graph.repos.settings

    private val _state = MutableStateFlow(AvatarState())
    val state: StateFlow<AvatarState> = _state

    /**
     * Once true, the initial load result is discarded: a picker change made
     * before the (asynchronous) load completes must never be clobbered by
     * the stale stored values.
     */
    private var userEdited = false

    init {
        viewModelScope.launch {
            val body = AvatarBodyType.fromId(settings.getString(AvatarPrefs.AVATAR_BODY))
            val tone = AvatarSkinTone.fromId(settings.getString(AvatarPrefs.AVATAR_TONE))
            if (!userEdited) {
                _state.value = AvatarState(loading = false, body = body, tone = tone)
            }
        }
    }

    fun setBody(body: AvatarBodyType) {
        userEdited = true
        _state.value = _state.value.copy(loading = false, body = body)
        viewModelScope.launch { settings.putString(AvatarPrefs.AVATAR_BODY, body.id) }
    }

    fun setTone(tone: AvatarSkinTone) {
        userEdited = true
        _state.value = _state.value.copy(loading = false, tone = tone)
        viewModelScope.launch { settings.putString(AvatarPrefs.AVATAR_TONE, tone.id) }
    }
}
