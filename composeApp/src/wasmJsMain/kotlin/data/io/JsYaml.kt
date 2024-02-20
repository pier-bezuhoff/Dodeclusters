@file:JsModule("js-yaml")

package data.io

// NOTE: js-yaml is imported by default, check yarn.lock
// reference: https://github.com/nodeca/js-yaml
/** cast output as an `external interface` of an appropriate shape */
@JsName("load")
external fun loadYaml(source: String, settings: YamlSettings?): JsAny

external fun Type(tag: String, params: TypeParams?): JsAny

external interface TypeParams : JsAny {
    var kind: String
}

external object Schema : JsAny {
    fun create(types: JsArray<JsAny>): Schema
}

external interface YamlSettings : JsAny {
    var schema: JsAny
}

@JsName("dump")
external fun dumpYaml(obj: JsAny): String