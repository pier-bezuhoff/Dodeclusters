@file:JsModule("js-yaml")

package data.io

// NOTE: js-yaml is imported by default, check yarn.lock
// reference: https://github.com/nodeca/js-yaml
/** cast output as an `external interface` of an appropriate shape */
@JsName("load")
external fun loadYaml(source: String, settings: YamlSettings?): JsAny

external fun Type(tag: String, params: TypeParams?): JsAny

external class TypeParams : JsAny {
    val kind: JsString
}

external class Schema : JsAny {
    companion object {
        fun create(types: JsArray<JsAny>)
    }
}

external class YamlSettings : JsAny {
    val schema: JsAny
}

@JsName("dump")
external fun dumpYaml(obj: JsAny): String