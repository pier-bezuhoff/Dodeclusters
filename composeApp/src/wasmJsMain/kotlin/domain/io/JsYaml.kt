@file:JsModule("js-yaml")
@file:OptIn(ExperimentalWasmJsInterop::class)

package domain.io

// NOTE: js-yaml is imported by default, check yarn.lock
// reference: https://github.com/nodeca/js-yaml
/** cast output as an `external interface` of an appropriate shape */
@JsName("load")
external fun loadYaml(source: String): JsAny

@JsName("dump")
external fun dumpYaml(obj: JsAny): String