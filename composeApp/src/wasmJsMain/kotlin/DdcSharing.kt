@file:OptIn(ExperimentalWasmJsInterop::class)

import kotlinx.browser.localStorage
import kotlinx.coroutines.await
import kotlin.js.Promise

private fun generateDk(): Promise<JsString> = js(
    """
        window.crypto.subtle.digest("SHA-256", new TextEncoder().encode(window.location.origin + window.location.pathname))
            .then(buffer => encodeURIComponent(btoa(String.fromCharCode(...new Uint8Array(buffer)))))
            
    """
)

private fun fetchSharedDdc(endpoint: String, dk: String, sharedId: String): Promise<JsString?> = js(
    """
        fetch(endpoint + '?doko=' + dk + '&id=' + sharedId)
            .then(response => response.text())
            .catch(error => console.error(error))
    """
)

private fun shareNewDdc(endpoint: String, dk: String, pk1: String, pk2: String, content: String): Promise<JsString?> = js(
    """
        fetch(endpoint + '?doko=' + dk + '&pk=' + pk1 + content.length + pk2, {
            redirect: "follow",
            method: "POST",
            headers: { "Content-Type": "text/plain; charset=UTF-8" },
            body: content,
        })
            .then(response => response.text())
            .catch(error => console.error(error))
    """
)

private fun overwriteSharedDdc(endpoint: String, dk: String, pk1: String, pk2: String, sharedId: String, content: String): Promise<JsString?> = js(
    """
        fetch(endpoint + '?doko=' + dk + '&pk=' + pk1 + content.length + pk2 + '&id=' + sharedId, {
            redirect: "follow",
            method: "POST",
            headers: { "Content-Type": "text/plain; charset=UTF-8" },
            body: content,
        })
            .then(response => response.text())
            .catch(error => console.error(error))
    """
)

private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbzL1MhoEuMO8nWX_HJxte3z9wsthIWyoOdzNR74U2StuTzvF2w8etgDiw5AnKwWrr2P/exec"

const val SHARE_PERMISSION_KEY = "ddc-share-perm"

private const val UNAUTHORIZED_RESPONSE = "Unauthorized"

internal var tmpDk = ""

/** @return ddc content */
suspend fun fetchSharedDdc(sharedId: String): String? {
    val dk0 = generateDk().await<JsString>().toString()
    val dk = tmpDk
    val responseText = fetchSharedDdc(ENDPOINT, dk, sharedId).await<JsString?>()
    val content = responseText?.toString()
    return if (content == UNAUTHORIZED_RESPONSE)
        null
    else content
}

fun testSharePermission(): Boolean =
    localStorage.getItem(SHARE_PERMISSION_KEY) != null

/** @return shared id */
suspend fun shareNewDdc(content: String): String? {
    val dk0 = generateDk().await<JsString>().toString()
    val dk = tmpDk
    val pk = localStorage.getItem(SHARE_PERMISSION_KEY)
    if (pk == null) return null
    val responseText = shareNewDdc(ENDPOINT, dk, pk.take(16), pk.drop(16), content).await<JsString?>()
    val content = responseText?.toString()
    return if (content == UNAUTHORIZED_RESPONSE)
        null
    else content
}

/** @return shared id */
suspend fun overwriteSharedDdc(sharedId: String, content: String): String? {
    val dk0 = generateDk().await<JsString>().toString()
    val dk = tmpDk
    val pk = localStorage.getItem(SHARE_PERMISSION_KEY)
    if (pk == null) return null
    val responseText = overwriteSharedDdc(ENDPOINT, dk, pk.take(16), pk.drop(16), sharedId, content).await<JsString?>()
    val content = responseText?.toString()
    return if (content == UNAUTHORIZED_RESPONSE)
        null
    else content
}
