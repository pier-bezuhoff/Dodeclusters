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
import kotlin.random.Random
import kotlin.random.nextULong

// MAYBE: persist it
private fun generateDk(): Promise<JsString> = js(
    """
        window.crypto.subtle.digest("SHA-256", new TextEncoder().encode(window.location.origin + window.location.pathname))
            .then(buffer => encodeURIComponent(btoa(String.fromCharCode(...new Uint8Array(buffer)))))
            
    """
)

private fun _generateDk(): Promise<JsString> = js(""" Promise.resolve("")""")

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

// NOTE: new kotlin version may change seeding algo (but likely will not)
private val PK_RANDOM = Random(0x12c161086L)

private fun nextPk(): ULong {
    return PK_RANDOM.nextULong(10_000_000UL .. 99_999_999UL)
}

private val PK = "${nextPk()}${nextPk()}${nextPk()}${nextPk()}"

private const val ENDPOINT = "https://script.google.com/macros/s/AKfycbw7hmxreFXv_b2D_XzDHRpZ3iopD1J5jTI2ef66RIQpM2WJ2LWgTNjsmy5lmFrhBDNp/exec"

private fun setUrlSearchParam(key: String, value: String) {
    val newUrl = URL(window.location.href)
    newUrl.searchParams.set(key, value)
    window.history.pushState(null, "", newUrl.href)
}

object WebDdcSharing : DdcSharing {
    override var shared: SharedIdAndOwnedStatus? by mutableStateOf(null)

    private val NO_RESPONSE: Result<Nothing> = Result.failure(Error("null or unsuccessful response"))
    private val NO_RESPONSE_JSON: Result<Nothing> = Result.failure(Error("null response.json()"))

    override fun formatLink(sharedId: SharedId): String {
        val link = URL(window.location.origin + window.location.pathname)
        link.searchParams.set(SearchParamKeys.SHARED_ID, sharedId)
        return link.href
    }

    override suspend fun fetchSharedDdc(sharedId: SharedId): Result<DdcContentAndOwnedStatus> {
        try {
            val dk = generateDk().await<JsString>().toString()
            val userId = localStorage.getItem(LocalStorageKeys.USER_ID)
            val promise = if (userId != null) {
                window.fetch("$ENDPOINT?doko=$dk&user_id=$userId&id=$sharedId")
            } else {
                window.fetch("$ENDPOINT?doko=$dk&id=$sharedId")
            }
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return NO_RESPONSE
            val json = response.json().await<JsAny?>() ?: return NO_RESPONSE_JSON
            val content = json.getStringProperty("content")
            if (content == null) {
                println("no .content in ${jsonStringify(json)}")
                return Result.failure(Error("response.json() has no .content"))
            }
            val owned = (json["owned"] as? JsBoolean)?.toBoolean() ?: false
            setUrlSearchParam(SearchParamKeys.SHARED_ID, sharedId)
            return Result.success(Pair(content, owned))
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override suspend fun registerUser(): Result<UserId> {
        try {
            val dk = generateDk().await<JsString>().toString()
            val promise = window.fetch("$ENDPOINT?doko=$dk&register=1")
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return NO_RESPONSE
            val json = response.json().await<JsAny?>() ?: return NO_RESPONSE_JSON
            val userId = json.getStringProperty("user")
            if (userId == null) {
                println("no .user in ${jsonStringify(json)}")
                return Result.failure(Error("response.json() has no .user"))
            }
            return Result.success(userId)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override fun testSharePermission(): Boolean =
//        localStorage.getItem(SHARE_PERMISSION_KEY) != null &&
        localStorage.getItem(LocalStorageKeys.USER_ID) != null

    override suspend fun shareNewDdc(content: DdcContent): Result<SharedId> {
        try {
            val dk = generateDk().await<JsString>().toString()
            val pk = PK //localStorage.getItem(SHARE_PERMISSION_KEY)
            val userId = localStorage.getItem(LocalStorageKeys.USER_ID)
            if (pk == null || userId == null)
                return Result.failure(Error("null pk or userId"))
            val url = "$ENDPOINT?doko=$dk&pk=${pk.take(16)}${content.length}${pk.drop(16)}&user_id=$userId"
            val promise = fetchPost(url, content)
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return NO_RESPONSE
            val json = response.json().await<JsAny?>() ?: return NO_RESPONSE_JSON
            val sharedId = json.getStringProperty("id")
            if (sharedId == null) {
                println("no .id in ${jsonStringify(json)}")
                return Result.failure(Error("response.json() has no .id"))
            }
            println("shared successfully -> $sharedId")
            setUrlSearchParam(SearchParamKeys.SHARED_ID, sharedId)
            return Result.success(sharedId)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }

    override suspend fun overwriteSharedDdc(sharedId: SharedId, content: DdcContent): Result<SharedId> {
        try {
            val dk = generateDk().await<JsString>().toString()
            val pk = PK //localStorage.getItem(SHARE_PERMISSION_KEY)
            val userId = localStorage.getItem(LocalStorageKeys.USER_ID)
            if (pk == null || userId == null)
                return Result.failure(Error("null pk or userId"))
            val url = "$ENDPOINT?doko=$dk&pk=${pk.take(16)}${content.length}${pk.drop(16)}&user_id=$userId&id=$sharedId"
            val promise = fetchPost(url, content)
            val response = promise.await<Response?>()
            if (response?.ok != true)
                return NO_RESPONSE
            val json = response.json().await<JsAny?>() ?: return NO_RESPONSE_JSON
            val sharedId = json.getStringProperty("id")
            if (sharedId == null) {
                println("no .id in ${jsonStringify(json)}")
                return Result.failure(Error("response.json() has no .id"))
            }
            println("overwritten shared successfully -> $sharedId")
            setUrlSearchParam(SearchParamKeys.SHARED_ID, sharedId)
            return Result.success(sharedId)
        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure(e)
        }
    }
}

