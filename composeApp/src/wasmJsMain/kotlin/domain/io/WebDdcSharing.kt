@file:OptIn(ExperimentalWasmJsInterop::class)

package domain.io

import SearchParamKeys
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import get
import getStringProperty
import jsonStringify
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.url.URL
import org.w3c.fetch.Response
import kotlin.js.Promise

// MAYBE: persist it
private fun generateDk(): Promise<JsString> = js(
    """
        window.crypto.subtle.digest("SHA-256", new TextEncoder().encode(window.location.origin + window.location.pathname))
            .then(buffer => encodeURIComponent(btoa(String.fromCharCode(...new Uint8Array(buffer)))))
            
    """
)

private fun generateDk_(): Promise<JsString> = js(
    """
        Promise.resolve("")
    """
)

@Suppress("unused")
private fun fetchPost(url: String, content: String): Promise<Response?> = js(
    """
        fetch(url, {
            redirect: "follow",
            method: "POST",
            headers: { "Content-Type": "text/plain; charset=UTF-8" },
            body: content,
        })
    """
)

private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbwMjpJfLFnUvrMCMlAA4DwiH-3PwW0bEc9PxE1oD7srYhmS8WPb43mY2JzIDi_K3D0J/exec"

const val SHARE_PERMISSION_KEY = "ddc-share-perm"
const val USER_ID_KEY = "ddc-user-id"

private fun setUrlSearchParam(key: String, value: String) {
    val newUrl = URL(window.location.href)
    newUrl.searchParams.set(key, value)
    window.history.pushState(null, "", newUrl.href)
}

object WebDdcSharing : DdcSharing {
    override var shared: SharedIdAndOwnedStatus? by mutableStateOf(null)

    override suspend fun fetchSharedDdc(sharedId: SharedId): DdcContentAndOwnedStatus? {
        try {
            val dk = generateDk().await<JsString>().toString()
            val userId = localStorage.getItem(USER_ID_KEY)
            val promise = if (userId != null) {
                window.fetch("$ENDPOINT?doko=$dk&user_id=$userId&id=$sharedId")
            } else {
                window.fetch("$ENDPOINT?doko=$dk&id=$sharedId")
            }
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return null
            val json = response.json().await<JsAny?>()
            if (json == null)
                return null
            val content = json.getStringProperty("content")
            if (content == null) {
                println("no .content in ${jsonStringify(json)}")
                return null
            }
            val owned = (json["owned"] as? JsBoolean)?.toBoolean() ?: false
            setUrlSearchParam(SearchParamKeys.SHARED_ID, sharedId)
            return Pair(content, owned)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun registerUser(): UserId? {
        try {
            val dk = generateDk().await<JsString>().toString()
            val promise = window.fetch("$ENDPOINT?doko=$dk&register=1")
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return null
            val json = response.json().await<JsAny?>()
            if (json == null)
                return null
            val userId = json.getStringProperty("user")
            if (userId == null) {
                println("no .user in ${jsonStringify(json)}")
                return null
            }
            return userId
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun testSharePermission(): Boolean =
        localStorage.getItem(SHARE_PERMISSION_KEY) != null &&
        localStorage.getItem(USER_ID_KEY) != null

    override suspend fun shareNewDdc(content: DdcContent): SharedId? {
        try {
            val dk = generateDk().await<JsString>().toString()
            val pk = localStorage.getItem(SHARE_PERMISSION_KEY)
            val userId = localStorage.getItem(USER_ID_KEY)
            if (pk == null || userId == null)
                return null
            val url = "$ENDPOINT?doko=$dk&pk=${pk.take(16)}${content.length}${pk.drop(16)}&user_id=$userId"
            val promise = fetchPost(url, content)
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return null
            val json = response.json().await<JsAny?>()
            if (json == null)
                return null
            val sharedId = json.getStringProperty("id")
            if (sharedId == null) {
                println("no .id in ${jsonStringify(json)}")
                return null
            }
            println("shared successfully -> $sharedId")
            setUrlSearchParam(SearchParamKeys.SHARED_ID, sharedId)
            return sharedId
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun overwriteSharedDdc(sharedId: SharedId, content: DdcContent): SharedId? {
        try {
            val dk = generateDk().await<JsString>().toString()
            val pk = localStorage.getItem(SHARE_PERMISSION_KEY)
            val userId = localStorage.getItem(USER_ID_KEY)
            if (pk == null || userId == null)
                return null
            val url = "$ENDPOINT?doko=$dk&pk=${pk.take(16)}${content.length}${pk.drop(16)}&user_id=$userId&id=$sharedId"
            val promise = fetchPost(url, content)
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return null
            val json = response.json().await<JsAny?>()
            if (json == null)
                return null
            val sharedId = json.getStringProperty("id")
            if (sharedId == null) {
                println("no .id in ${jsonStringify(json)}")
                return null
            }
            println("overwritten shared successfully -> $sharedId")
            setUrlSearchParam(SearchParamKeys.SHARED_ID, sharedId)
            return sharedId
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override fun formatLink(sharedId: SharedId): String {
        val link = URL(window.location.origin + window.location.pathname)
        link.searchParams.set(SearchParamKeys.SHARED_ID, sharedId)
        return link.href
    }
}

