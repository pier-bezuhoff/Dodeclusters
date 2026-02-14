@file:OptIn(ExperimentalWasmJsInterop::class)

package domain.io

import SearchParamKeys
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.browser.localStorage
import kotlinx.browser.window
import kotlinx.coroutines.await
import org.w3c.dom.url.URL
import org.w3c.fetch.Response
import kotlin.js.Promise

private fun generateDk(): Promise<JsString> = js(
    """
        window.crypto.subtle.digest("SHA-256", new TextEncoder().encode(window.location.origin + window.location.pathname))
            .then(buffer => encodeURIComponent(btoa(String.fromCharCode(...new Uint8Array(buffer)))))
            
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

@Suppress("unused")
private fun <T : JsAny> getObjectProperty(obj: JsAny, property: String): T? =
    js("obj[property]")

private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbzuiUE8QtvIrFBwyeYMYWL7FUVMiuMzm_R4iTsLDkXWmqTcvACNUyapsOvGLa858m4_/exec"

const val SHARE_PERMISSION_KEY = "ddc-share-perm"
const val USER_ID_KEY = "ddc-user-id"

private const val UNAUTHORIZED_RESPONSE = "Unauthorized"

private fun setUrlSearchParam(key: String, value: String) {
    val newUrl = URL(window.location.href)
    newUrl.searchParams.set(key, value)
    window.history.pushState(null, "", newUrl.href)
}

object WebDdcSharing : DdcSharing {
    override var shared: SharedIdAndOwnedStatus? by mutableStateOf(null)

    internal var tmpDk = ""

    override suspend fun fetchSharedDdc(sharedId: SharedId): DdcContentAndOwnedStatus? {
        try {
            var dk = generateDk().await<JsString>().toString()
            dk = tmpDk
            val userId = localStorage.getItem(USER_ID_KEY)
            val promise = if (userId != null) {
                window.fetch("$ENDPOINT?doko=$dk&user_id=$userId&id=$sharedId")
            } else {
                window.fetch("$ENDPOINT?doko=$dk&id=$sharedId")
            }
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return null
            val text = response.clone().text().await<JsString?>()?.toString()
            if (text == UNAUTHORIZED_RESPONSE || text.isNullOrBlank())
                return null
            val json = response.json().await<JsAny?>()
            if (json == null)
                return null
            val content = getObjectProperty<JsString>(json, "content")?.toString()
            if (content == null)
                return null
            val owned = getObjectProperty<JsBoolean>(json, "owned")?.toBoolean() ?: false
            setUrlSearchParam(SearchParamKeys.SHARED_ID, sharedId)
            return Pair(content, owned)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun registerUser(): UserId? {
        try {
            var dk = generateDk().await<JsString>().toString()
            dk = tmpDk
            val promise = window.fetch("$ENDPOINT?doko=$dk&register=1")
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return null
            val text = response.text().await<JsString?>()?.toString()
            if (text == UNAUTHORIZED_RESPONSE || text.isNullOrBlank())
                return null
            return text
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
            var dk = generateDk().await<JsString>().toString()
            dk = tmpDk
            val pk = localStorage.getItem(SHARE_PERMISSION_KEY)
            val userId = localStorage.getItem(USER_ID_KEY)
            if (pk == null || userId == null)
                return null
            val url = "$ENDPOINT?doko=$dk&pk=${pk.take(16)}${content.length}${pk.drop(16)}&user_id=$userId"
//            println("fetch-post $url")
            val promise = fetchPost(url, content)
            val response = promise.await<Response?>()
//            println("response = $response")
            if (response?.ok != true)
                return null
            val text = response.text().await<JsString?>()?.toString()
            if (text == UNAUTHORIZED_RESPONSE || text.isNullOrBlank())
                return null
            // FIX: when json error is returned we cannot detect it...
            println("shared successfully -> $text")
            setUrlSearchParam(SearchParamKeys.SHARED_ID, text)
            return text
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    override suspend fun overwriteSharedDdc(sharedId: SharedId, content: DdcContent): SharedId? {
        try {
            var dk = generateDk().await<JsString>().toString()
            dk = tmpDk
            val pk = localStorage.getItem(SHARE_PERMISSION_KEY)
            val userId = localStorage.getItem(USER_ID_KEY)
            if (pk == null || userId == null)
                return null
            val url = "$ENDPOINT?doko=$dk&pk=${pk.take(16)}${content.length}${pk.drop(16)}&user_id=$userId&id=$sharedId"
            val promise = fetchPost(url, content)
            val response = promise.await<Response?>()
            println("response = $response")
            if (response?.ok != true)
                return null
            val text = response.text().await<JsString?>()?.toString()
            println("response.text() = $text")
            if (text == UNAUTHORIZED_RESPONSE || text.isNullOrBlank())
                return null
            // FIX: when json error is returned we cannot detect it...
            println("overwritten shared successfully -> $text")
            setUrlSearchParam(SearchParamKeys.SHARED_ID, text)
            return text
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

