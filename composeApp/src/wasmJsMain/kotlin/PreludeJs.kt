@file:OptIn(ExperimentalWasmJsInterop::class)

operator fun <T : JsAny> JsAny.get(property: String): T? =
    getObjectProperty<T>(this, property)

@Suppress("unused")
fun <T : JsAny> getObjectProperty(obj: JsAny, property: String): T? =
    js("obj[property]")

@Suppress("unused")
fun jsonStringify(obj: JsAny): String =
    js("JSON.stringify(obj)")


