@file:OptIn(ExperimentalWasmJsInterop::class)

import domain.io.DdcContent
import domain.io.DdcSharing
import domain.io.SharedId
import domain.io.UserId
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

private fun fetchSharedDdc(endpoint: String, dk: String, userId: String, sharedId: String): Promise<JsString?> = js(
    """
        fetch(endpoint + '?doko=' + dk + '&user_id=' + userId + '&id=' + sharedId)
            .then(response => response.text())
            .catch(error => console.error(error))
    """
)

private fun registerSharer(endpoint: String, dk: String): Promise<JsString?> = js(
    """
        fetch(endpoint + '?doko=' + dk + '&register=1')
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

private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbxWhHrZ2ejXB8TvhIXurkzBMKsiftyiSL82HAVrTRJRuzHqxB76VeK3K2yZqGBv-y7o/exec"

const val SHARE_PERMISSION_KEY = "ddc-share-perm"
const val USER_ID_KEY = "ddc-user-id"

private const val UNAUTHORIZED_RESPONSE = "Unauthorized"


object WebDdcSharing : DdcSharing {
    internal var tmpDk = ""

    override suspend fun fetchSharedDdc(sharedId: SharedId): DdcContent? {
        val dk0 = generateDk().await<JsString>().toString()
        val dk = tmpDk
        val userId = localStorage.getItem(USER_ID_KEY)
        val promise =
            if (userId != null)
                fetchSharedDdc(ENDPOINT, dk, userId, sharedId)
            else
                fetchSharedDdc(ENDPOINT, dk, sharedId)
        val response = promise.await<JsString?>()?.toString()
        return if (response == UNAUTHORIZED_RESPONSE || response.isNullOrBlank())
            null
        else response
    }

    override suspend fun registerSharer(): UserId? {
        val dk0 = generateDk().await<JsString>().toString()
        val dk = tmpDk
        val response = registerSharer(ENDPOINT, dk)
            .await<JsString?>()
            ?.toString()
        return if (response == null || response.isBlank() || response == UNAUTHORIZED_RESPONSE) {
            null
        } else {
            response
        }
    }

    override fun testSharePermission(): Boolean =
        localStorage.getItem(SHARE_PERMISSION_KEY) != null &&
        localStorage.getItem(USER_ID_KEY) != null

    override suspend fun shareNewDdc(content: DdcContent): SharedId? {
        val dk0 = generateDk().await<JsString>().toString()
        val dk = tmpDk
        val pk = localStorage.getItem(SHARE_PERMISSION_KEY)
        if (pk == null)
            return null
        val response = shareNewDdc(ENDPOINT, dk, pk.take(16), pk.drop(16), content)
            .await<JsString?>()
            ?.toString()
        return if (response == UNAUTHORIZED_RESPONSE || response.isNullOrBlank())
            null
        else response
    }

    override suspend fun overwriteSharedDdc(sharedId: SharedId, content: DdcContent): SharedId? {
        val dk0 = generateDk().await<JsString>().toString()
        val dk = tmpDk
        val pk = localStorage.getItem(SHARE_PERMISSION_KEY)
        if (pk == null)
            return null
        val response = overwriteSharedDdc(ENDPOINT, dk, pk.take(16), pk.drop(16), sharedId, content)
            .await<JsString?>()
            ?.toString()
        return if (response == UNAUTHORIZED_RESPONSE || response.isNullOrBlank())
            null
        else response
    }
}

