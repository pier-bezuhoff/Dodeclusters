@file:OptIn(ExperimentalWasmJsInterop::class)

operator fun JsAny.get(property: String): JsAny? =
    getObjectProperty<JsAny>(this, property)

fun JsAny.getStringProperty(property: String): String? =
    getObjectProperty<JsString>(this, property)?.toString()

@Suppress("unused")
fun <T : JsAny> getObjectProperty(obj: JsAny, property: String): T? =
    js("obj[property]")

@Suppress("unused")
fun jsonStringify(obj: JsAny): String =
    js("JSON.stringify(obj)")


