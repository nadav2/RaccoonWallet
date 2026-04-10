package io.raccoonwallet.app.feature.vault.receive

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import io.raccoonwallet.app.core.transport.qr.QrEncoder
import io.raccoonwallet.app.deps
import io.raccoonwallet.app.nav.VaultReceive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReceiveViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val publicStore = application.deps.publicStore
    private val qrEncoder = QrEncoder()

    private val accountIndex: Int = savedStateHandle.toRoute<VaultReceive>().accountIndex

    private val _address = MutableStateFlow("Loading...")
    val address: StateFlow<String> = _address.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    init {
        viewModelScope.launch {
            val addr = publicStore.getAccounts()
                .getOrNull(accountIndex)?.address ?: "No address"
            _address.value = addr

            if (addr != "No address") {
                _qrBitmap.value = withContext(Dispatchers.Default) {
                    qrEncoder.encodeAddressToBitmap(addr)
                }
            }
        }
    }
}
