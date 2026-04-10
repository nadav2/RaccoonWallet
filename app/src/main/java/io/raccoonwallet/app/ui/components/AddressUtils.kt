package io.raccoonwallet.app.ui.components

fun String.truncateAddress(): String {
    if (length <= 12) return this
    return "${take(6)}...${takeLast(4)}"
}
