!function(e,t){"object"==typeof exports&&"object"==typeof module?module.exports=t():"function"==typeof define&&define.amd?define([],t):"object"==typeof exports?exports.composeApp=t():e.composeApp=t()}(this,(()=>(()=>{"use strict";var e,t,r,o,n,a,i={598:(e,t,r)=>{r.a(e,(async(e,o)=>{try{r.r(t),r.d(t,{default:()=>e});var n=r(178);const e=(await(0,n._)()).exports;o()}catch(e){o(e)}}),1)},178:(e,t,r)=>{async function o(e={},t=!0){const o=new WeakMap,n=e["./skiko.mjs"]??await r.e(366).then(r.bind(r,366)),a=e["js-yaml"]??await r.e(524).then(r.bind(r,524)),i={"kotlin.captureStackTrace":()=>(new Error).stack,"kotlin.wasm.internal.throwJsError":(e,t,r)=>{const o=new Error;throw o.message=e,o.name=t,o.stack=r,o},"kotlin.wasm.internal.stringLength":e=>e.length,"kotlin.wasm.internal.jsExportStringToWasm":(e,t,r,o)=>{const n=new Uint16Array(c.memory.buffer,o,r);let a=0,i=t;for(;a<r;)n.set([e.charCodeAt(i)],a),i++,a++},"kotlin.wasm.internal.importStringFromWasm":(e,t,r)=>{const o=new Uint16Array(c.memory.buffer,e,t),n=String.fromCharCode.apply(null,o);return null==r?n:r+n},"kotlin.wasm.internal.externrefToInt":e=>Number(e),"kotlin.wasm.internal.getJsEmptyString":()=>"","kotlin.wasm.internal.externrefToString":e=>String(e),"kotlin.wasm.internal.externrefEquals":(e,t)=>e===t,"kotlin.wasm.internal.externrefHashCode":(()=>{const e=new DataView(new ArrayBuffer(8)),t=new WeakMap;return r=>{if(null==r)return 0;switch(typeof r){case"object":case"function":return function(e){const r=t.get(e);if(void 0===r){const r=4294967296,o=Math.random()*r|0;return t.set(e,o),o}return r}(r);case"number":return function(t){return(0|t)===t?0|t:(e.setFloat64(0,t,!0),(31*e.getInt32(0,!0)|0)+e.getInt32(4,!0)|0)}(r);case"boolean":return r?1231:1237;default:return function(e){for(var t=0,r=0;r<e.length;r++)t=31*t+e.charCodeAt(r)|0;return t}(String(r))}}})(),"kotlin.wasm.internal.isNullish":e=>null==e,"kotlin.wasm.internal.externrefToBoolean":e=>Boolean(e),"kotlin.wasm.internal.externrefToFloat":e=>Number(e),"kotlin.wasm.internal.getJsTrue":()=>!0,"kotlin.wasm.internal.getJsFalse":()=>!1,"kotlin.wasm.internal.tryGetOrSetExternrefBox_$external_fun":(e,t)=>function(e,t){if("object"!=typeof e)return t;const r=o.get(e);return void 0!==r?r:(o.set(e,t),t)}(e,t),"kotlin.js.jsCatch":e=>{let t=null;try{e()}catch(e){t=e}return t},"kotlin.js.__convertKotlinClosureToJsClosure_(()->Unit)":e=>()=>c["__callFunction_(()->Unit)"](e),"kotlin.js.jsThrow":e=>{throw e},"kotlin.io.printError":e=>console.error(e),"kotlin.io.printlnImpl":e=>console.log(e),"kotlin.js.jsArrayGet":(e,t)=>e[t],"kotlin.js.jsArraySet":(e,t,r)=>{e[t]=r},"kotlin.js.JsArray_$external_fun":()=>new Array,"kotlin.js.length_$external_prop_getter":e=>e.length,"kotlin.js.JsNumber_$external_class_instanceof":e=>"number"==typeof e,"kotlin.js.__convertKotlinClosureToJsClosure_((Js?)->Js?)":e=>t=>c["__callFunction_((Js?)->Js?)"](e,t),"kotlin.js.then_$external_fun":(e,t,r)=>e.then(t,r),"kotlin.js.__convertKotlinClosureToJsClosure_((Js)->Js?)":e=>t=>c["__callFunction_((Js)->Js?)"](e,t),"kotlin.random.initialSeed":()=>Math.random()*Math.pow(2,32)|0,"kotlinx.browser.window_$external_prop_getter":()=>window,"kotlinx.browser.document_$external_prop_getter":()=>document,"org.w3c.dom.length_$external_prop_getter":e=>e.length,"org.khronos.webgl.byteLength_$external_prop_getter":e=>e.byteLength,"org.khronos.webgl.Int8Array_$external_fun":(e,t,r,o,n)=>new Int8Array(e,o?void 0:t,n?void 0:r),"org.khronos.webgl.length_$external_prop_getter":e=>e.length,"org.w3c.dom.css.height_$external_prop_setter":(e,t)=>e.height=t,"org.w3c.dom.css.width_$external_prop_setter":(e,t)=>e.width=t,"org.w3c.dom.css.style_$external_prop_getter":e=>e.style,"org.w3c.dom.encryptedmedia.__convertKotlinClosureToJsClosure_((Js)->Unit)":e=>t=>c["__callFunction_((Js)->Unit)"](e,t),"org.w3c.dom.events.timeStamp_$external_prop_getter":e=>e.timeStamp,"org.w3c.dom.events.preventDefault_$external_fun":e=>e.preventDefault(),"org.w3c.dom.events.Event_$external_class_instanceof":e=>e instanceof Event,"org.w3c.dom.events.addEventListener_$external_fun":(e,t,r,o)=>e.addEventListener(t,r,o),"org.w3c.dom.events.addEventListener_$external_fun_1":(e,t,r)=>e.addEventListener(t,r),"org.w3c.dom.events.ctrlKey_$external_prop_getter":e=>e.ctrlKey,"org.w3c.dom.events.shiftKey_$external_prop_getter":e=>e.shiftKey,"org.w3c.dom.events.altKey_$external_prop_getter":e=>e.altKey,"org.w3c.dom.events.metaKey_$external_prop_getter":e=>e.metaKey,"org.w3c.dom.events.button_$external_prop_getter":e=>e.button,"org.w3c.dom.events.offsetX_$external_prop_getter":e=>e.offsetX,"org.w3c.dom.events.offsetY_$external_prop_getter":e=>e.offsetY,"org.w3c.dom.events.MouseEvent_$external_class_instanceof":e=>e instanceof MouseEvent,"org.w3c.dom.events.key_$external_prop_getter":e=>e.key,"org.w3c.dom.events.location_$external_prop_getter":e=>e.location,"org.w3c.dom.events.ctrlKey_$external_prop_getter_1":e=>e.ctrlKey,"org.w3c.dom.events.shiftKey_$external_prop_getter_1":e=>e.shiftKey,"org.w3c.dom.events.altKey_$external_prop_getter_1":e=>e.altKey,"org.w3c.dom.events.metaKey_$external_prop_getter_1":e=>e.metaKey,"org.w3c.dom.events.keyCode_$external_prop_getter":e=>e.keyCode,"org.w3c.dom.events.DOM_KEY_LOCATION_RIGHT_$external_prop_getter":e=>e.DOM_KEY_LOCATION_RIGHT,"org.w3c.dom.events.Companion_$external_object_getInstance":()=>KeyboardEvent,"org.w3c.dom.events.KeyboardEvent_$external_class_instanceof":e=>e instanceof KeyboardEvent,"org.w3c.dom.events.deltaX_$external_prop_getter":e=>e.deltaX,"org.w3c.dom.events.deltaY_$external_prop_getter":e=>e.deltaY,"org.w3c.dom.events.WheelEvent_$external_class_instanceof":e=>e instanceof WheelEvent,"org.w3c.dom.navigator_$external_prop_getter":e=>e.navigator,"org.w3c.dom.devicePixelRatio_$external_prop_getter":e=>e.devicePixelRatio,"org.w3c.dom.requestAnimationFrame_$external_fun":(e,t)=>e.requestAnimationFrame(t),"org.w3c.dom.__convertKotlinClosureToJsClosure_((Double)->Unit)":e=>t=>c["__callFunction_((Double)->Unit)"](e,t),"org.w3c.dom.matchMedia_$external_fun":(e,t)=>e.matchMedia(t),"org.w3c.dom.accept_$external_prop_setter":(e,t)=>e.accept=t,"org.w3c.dom.files_$external_prop_getter":e=>e.files,"org.w3c.dom.type_$external_prop_setter":(e,t)=>e.type=t,"org.w3c.dom.HTMLInputElement_$external_class_instanceof":e=>e instanceof HTMLInputElement,"org.w3c.dom.namespaceURI_$external_prop_getter":e=>e.namespaceURI,"org.w3c.dom.localName_$external_prop_getter":e=>e.localName,"org.w3c.dom.clientWidth_$external_prop_getter":e=>e.clientWidth,"org.w3c.dom.clientHeight_$external_prop_getter":e=>e.clientHeight,"org.w3c.dom.getAttribute_$external_fun":(e,t)=>e.getAttribute(t),"org.w3c.dom.getAttributeNS_$external_fun":(e,t,r)=>e.getAttributeNS(t,r),"org.w3c.dom.setAttribute_$external_fun":(e,t,r)=>e.setAttribute(t,r),"org.w3c.dom.getElementsByTagName_$external_fun":(e,t)=>e.getElementsByTagName(t),"org.w3c.dom.Element_$external_class_instanceof":e=>e instanceof Element,"org.w3c.dom.documentElement_$external_prop_getter":e=>e.documentElement,"org.w3c.dom.body_$external_prop_getter":e=>e.body,"org.w3c.dom.head_$external_prop_getter":e=>e.head,"org.w3c.dom.createElement_$external_fun":(e,t,r,o)=>e.createElement(t,o?void 0:r),"org.w3c.dom.createTextNode_$external_fun":(e,t)=>e.createTextNode(t),"org.w3c.dom.getElementById_$external_fun":(e,t)=>e.getElementById(t),"org.w3c.dom.download_$external_prop_setter":(e,t)=>e.download=t,"org.w3c.dom.HTMLAnchorElement_$external_class_instanceof":e=>e instanceof HTMLAnchorElement,"org.w3c.dom.nodeName_$external_prop_getter":e=>e.nodeName,"org.w3c.dom.parentElement_$external_prop_getter":e=>e.parentElement,"org.w3c.dom.childNodes_$external_prop_getter":e=>e.childNodes,"org.w3c.dom.textContent_$external_prop_setter":(e,t)=>e.textContent=t,"org.w3c.dom.cloneNode_$external_fun":(e,t,r)=>e.cloneNode(r?void 0:t),"org.w3c.dom.lookupPrefix_$external_fun":(e,t)=>e.lookupPrefix(t),"org.w3c.dom.appendChild_$external_fun":(e,t)=>e.appendChild(t),"org.w3c.dom.replaceChild_$external_fun":(e,t,r)=>e.replaceChild(t,r),"org.w3c.dom.removeChild_$external_fun":(e,t)=>e.removeChild(t),"org.w3c.dom.click_$external_fun":e=>e.click(),"org.w3c.dom.onchange_$external_prop_setter":(e,t)=>e.onchange=t,"org.w3c.dom.clearTimeout_$external_fun":(e,t,r)=>e.clearTimeout(r?void 0:t),"org.w3c.dom.fetch_$external_fun":(e,t,r,o)=>e.fetch(t,o?void 0:r),"org.w3c.dom.userAgent_$external_prop_getter":e=>e.userAgent,"org.w3c.dom.language_$external_prop_getter":e=>e.language,"org.w3c.dom.item_$external_fun":(e,t)=>e.item(t),"org.w3c.dom.item_$external_fun_1":(e,t)=>e.item(t),"org.w3c.dom.href_$external_prop_setter":(e,t)=>e.href=t,"org.w3c.dom.HTMLTitleElement_$external_class_instanceof":e=>e instanceof HTMLTitleElement,"org.w3c.dom.type_$external_prop_setter_1":(e,t)=>e.type=t,"org.w3c.dom.HTMLStyleElement_$external_class_instanceof":e=>e instanceof HTMLStyleElement,"org.w3c.dom.width_$external_prop_getter":e=>e.width,"org.w3c.dom.width_$external_prop_setter":(e,t)=>e.width=t,"org.w3c.dom.height_$external_prop_getter":e=>e.height,"org.w3c.dom.height_$external_prop_setter":(e,t)=>e.height=t,"org.w3c.dom.HTMLCanvasElement_$external_class_instanceof":e=>e instanceof HTMLCanvasElement,"org.w3c.dom.parsing.DOMParser_$external_fun":()=>new DOMParser,"org.w3c.dom.parsing.parseFromString_$external_fun":(e,t,r)=>e.parseFromString(t,r),"org.w3c.dom.url.searchParams_$external_prop_getter":e=>e.searchParams,"org.w3c.dom.url.createObjectURL_$external_fun":(e,t)=>e.createObjectURL(t),"org.w3c.dom.url.revokeObjectURL_$external_fun":(e,t)=>e.revokeObjectURL(t),"org.w3c.dom.url.Companion_$external_object_getInstance":()=>URL,"org.w3c.dom.url.get_$external_fun":(e,t)=>e.get(t),"org.w3c.fetch.ok_$external_prop_getter":e=>e.ok,"org.w3c.fetch.arrayBuffer_$external_fun":e=>e.arrayBuffer(),"org.w3c.files.BlobPropertyBag_js_code":e=>({type:e}),"org.w3c.files.getMethodImplForFileList":(e,t)=>e[t],"org.w3c.files.File_$external_class_instanceof":e=>e instanceof File,"org.w3c.files.FileReader_$external_fun":()=>new FileReader,"org.w3c.files.result_$external_prop_getter":e=>e.result,"org.w3c.files.onload_$external_prop_setter":(e,t)=>e.onload=t,"org.w3c.files.readAsText_$external_fun":(e,t,r,o)=>e.readAsText(t,o?void 0:r),"org.w3c.files.Blob_$external_fun":(e,t,r,o)=>new Blob(r?void 0:e,o?void 0:t),"org.w3c.performance.performance_$external_prop_getter":e=>e.performance,"org.w3c.performance.now_$external_fun":e=>e.now(),"kotlinx.coroutines.tryGetProcess":()=>"undefined"!=typeof process&&"function"==typeof process.nextTick?process:null,"kotlinx.coroutines.tryGetWindow":()=>"undefined"!=typeof window&&null!=window&&"function"==typeof window.addEventListener?window:null,"kotlinx.coroutines.nextTick_$external_fun":(e,t)=>e.nextTick(t),"kotlinx.coroutines.error_$external_fun":(e,t)=>e.error(t),"kotlinx.coroutines.console_$external_prop_getter":()=>console,"kotlinx.coroutines.createScheduleMessagePoster":e=>()=>Promise.resolve(0).then(e),"kotlinx.coroutines.__callJsClosure_(()->Unit)":e=>e(),"kotlinx.coroutines.createRescheduleMessagePoster":e=>()=>e.postMessage("dispatchCoroutine","*"),"kotlinx.coroutines.subscribeToWindowMessages":(e,t)=>{e.addEventListener("message",(r=>{r.source==e&&"dispatchCoroutine"==r.data&&(r.stopPropagation(),t())}),!0)},"kotlinx.coroutines.setTimeout":(e,t,r)=>e.setTimeout(t,r),"kotlinx.coroutines.clearTimeout":e=>{"undefined"!=typeof clearTimeout&&clearTimeout(e)},"kotlinx.coroutines.setTimeout_$external_fun":(e,t)=>setTimeout(e,t),"org.jetbrains.skiko.getNavigatorInfo":()=>navigator.userAgentData?navigator.userAgentData.platform:navigator.platform,"org.jetbrains.skia.impl.FinalizationRegistry_$external_fun":e=>new FinalizationRegistry(e),"org.jetbrains.skia.impl.register_$external_fun":(e,t,r)=>e.register(t,r),"org.jetbrains.skia.impl.unregister_$external_fun":(e,t)=>e.unregister(t),"org.jetbrains.skia.impl._releaseLocalCallbackScope_$external_fun":()=>n._releaseLocalCallbackScope(),"org.jetbrains.skiko.wasm.createContext_$external_fun":(e,t,r)=>e.createContext(t,r),"org.jetbrains.skiko.wasm.makeContextCurrent_$external_fun":(e,t)=>e.makeContextCurrent(t),"org.jetbrains.skiko.wasm.GL_$external_object_getInstance":()=>n.GL,"org.jetbrains.skiko.wasm.createDefaultContextAttributes":()=>({alpha:1,depth:1,stencil:8,antialias:0,premultipliedAlpha:1,preserveDrawingBuffer:0,preferLowPowerToHighPerformance:0,failIfMajorPerformanceCaveat:0,enableExtensionsByDefault:1,explicitSwapControl:0,renderViaOffscreenBackBuffer:0,majorVersion:2}),"androidx.compose.ui.text.intl.parseLanguageTagToIntlLocale":e=>new Intl.Locale(e),"androidx.compose.ui.text.intl.language_$external_prop_getter":e=>e.language,"androidx.compose.ui.text.intl.baseName_$external_prop_getter":e=>e.baseName,"androidx.compose.ui.text.intl.getUserPreferredLanguagesAsArray":()=>window.navigator.languages,"androidx.compose.ui.window.setCursor":(e,t)=>document.getElementById(e).style.cursor=t,"org.jetbrains.compose.resources.jsExportInt8ArrayToWasm":(e,t,r)=>{new Int8Array(c.memory.buffer,r,t).set(e)},"data.io.isCircleObject":e=>"x"in e,"data.io.isClusterObject":e=>"circles"in e,"data.io.jsonStringify":e=>JSON.stringify(e),"data.io.name_$external_prop_getter":e=>e.name,"data.io.backgroundColor_$external_prop_getter":e=>e.backgroundColor,"data.io.bestCenterX_$external_prop_getter":e=>e.bestCenterX,"data.io.bestCenterY_$external_prop_getter":e=>e.bestCenterY,"data.io.shape_$external_prop_getter":e=>e.shape,"data.io.drawTrace_$external_prop_getter":e=>e.drawTrace,"data.io.content_$external_prop_getter":e=>e.content,"data.io.index_$external_prop_getter":e=>e.index,"data.io.x_$external_prop_getter":e=>e.x,"data.io.y_$external_prop_getter":e=>e.y,"data.io.radius_$external_prop_getter":e=>e.radius,"data.io.visible_$external_prop_getter":e=>e.visible,"data.io.filled_$external_prop_getter":e=>e.filled,"data.io.fillColor_$external_prop_getter":e=>e.fillColor,"data.io.borderColor_$external_prop_getter":e=>e.borderColor,"data.io.rule_$external_prop_getter":e=>e.rule,"data.io.indices_$external_prop_getter":e=>e.indices,"data.io.circles_$external_prop_getter":e=>e.circles,"data.io.parts_$external_prop_getter":e=>e.parts,"data.io.filled_$external_prop_getter_1":e=>e.filled,"data.io.rule_$external_prop_getter_1":e=>e.rule,"data.io.x_$external_prop_getter_1":e=>e.x,"data.io.y_$external_prop_getter_1":e=>e.y,"data.io.radius_$external_prop_getter_1":e=>e.radius,"data.io.insides_$external_prop_getter":e=>e.insides,"data.io.outsides_$external_prop_getter":e=>e.outsides,"data.io.fillColor_$external_prop_getter_1":e=>e.fillColor,"data.io.borderColor_$external_prop_getter_1":e=>e.borderColor,"data.io.loadYaml_$external_fun":e=>a.load(e),getCurrentURL:()=>new URL(window.location.href),checkIfUsingMobileOrTablet:()=>{let e=!1;var t;return t=window.navigator.userAgent||window.navigator.vendor||window.opera,(/(android|bb\d+|meego).+mobile|avantgo|bada\/|blackberry|blazer|compal|elaine|fennec|hiptop|iemobile|ip(hone|od)|iris|kindle|lge |maemo|midp|mmp|mobile.+firefox|netfront|opera m(ob|in)i|palm( os)?|phone|p(ixi|re)\/|plucker|pocket|psp|series(4|6)0|symbian|treo|up\.(browser|link)|vodafone|wap|windows ce|xda|xiino|android|ipad|playbook|silk/i.test(t)||/1207|6310|6590|3gso|4thp|50[1-6]i|770s|802s|a wa|abac|ac(er|oo|s\-)|ai(ko|rn)|al(av|ca|co)|amoi|an(ex|ny|yw)|aptu|ar(ch|go)|as(te|us)|attw|au(di|\-m|r |s )|avan|be(ck|ll|nq)|bi(lb|rd)|bl(ac|az)|br(e|v)w|bumb|bw\-(n|u)|c55\/|capi|ccwa|cdm\-|cell|chtm|cldc|cmd\-|co(mp|nd)|craw|da(it|ll|ng)|dbte|dc\-s|devi|dica|dmob|do(c|p)o|ds(12|\-d)|el(49|ai)|em(l2|ul)|er(ic|k0)|esl8|ez([4-7]0|os|wa|ze)|fetc|fly(\-|_)|g1 u|g560|gene|gf\-5|g\-mo|go(\.w|od)|gr(ad|un)|haie|hcit|hd\-(m|p|t)|hei\-|hi(pt|ta)|hp( i|ip)|hs\-c|ht(c(\-| |_|a|g|p|s|t)|tp)|hu(aw|tc)|i\-(20|go|ma)|i230|iac( |\-|\/)|ibro|idea|ig01|ikom|im1k|inno|ipaq|iris|ja(t|v)a|jbro|jemu|jigs|kddi|keji|kgt( |\/)|klon|kpt |kwc\-|kyo(c|k)|le(no|xi)|lg( g|\/(k|l|u)|50|54|\-[a-w])|libw|lynx|m1\-w|m3ga|m50\/|ma(te|ui|xo)|mc(01|21|ca)|m\-cr|me(rc|ri)|mi(o8|oa|ts)|mmef|mo(01|02|bi|de|do|t(\-| |o|v)|zz)|mt(50|p1|v )|mwbp|mywa|n10[0-2]|n20[2-3]|n30(0|2)|n50(0|2|5)|n7(0(0|1)|10)|ne((c|m)\-|on|tf|wf|wg|wt)|nok(6|i)|nzph|o2im|op(ti|wv)|oran|owg1|p800|pan(a|d|t)|pdxg|pg(13|\-([1-8]|c))|phil|pire|pl(ay|uc)|pn\-2|po(ck|rt|se)|prox|psio|pt\-g|qa\-a|qc(07|12|21|32|60|\-[2-7]|i\-)|qtek|r380|r600|raks|rim9|ro(ve|zo)|s55\/|sa(ge|ma|mm|ms|ny|va)|sc(01|h\-|oo|p\-)|sdk\/|se(c(\-|0|1)|47|mc|nd|ri)|sgh\-|shar|sie(\-|m)|sk\-0|sl(45|id)|sm(al|ar|b3|it|t5)|so(ft|ny)|sp(01|h\-|v\-|v )|sy(01|mb)|t2(18|50)|t6(00|10|18)|ta(gt|lk)|tcl\-|tdg\-|tel(i|m)|tim\-|t\-mo|to(pl|sh)|ts(70|m\-|m3|m5)|tx\-9|up(\.b|g1|si)|utst|v400|v750|veri|vi(rg|te)|vk(40|5[0-3]|\-v)|vm40|voda|vulc|vx(52|53|60|61|70|80|81|83|85|98)|w3c(\-| )|webc|whit|wi(g |nc|nw)|wmlb|wonu|x700|yas\-|your|zeto|zte\-/i.test(t.substr(0,4)))&&(e=!0),e},checkIfAndroid:()=>"Android"==window.navigator.userAgentData.platform};let l,s,c;const _="undefined"!=typeof process&&"node"===process.release.name,p=!_&&("undefined"!=typeof d8||"undefined"!=typeof inIon||"undefined"!=typeof jscOptions),d=!_&&!p&&"undefined"!=typeof window;if(!_&&!p&&!d)throw"Supported JS engine not detected";const m="./composeApp.wasm",g={js_code:i,"./skiko.mjs":e["./skiko.mjs"]??await r.e(366).then(r.bind(r,366))};try{if(_){s=(await import("node:module")).default.createRequire("file:///home/runner/work/Dodeclusters/Dodeclusters/build/js/packages/composeApp/kotlin/composeApp.uninstantiated.mjs");const e=s("fs"),t=s("path"),r=s("url").fileURLToPath("file:///home/runner/work/Dodeclusters/Dodeclusters/build/js/packages/composeApp/kotlin/composeApp.uninstantiated.mjs"),o=t.dirname(r),n=e.readFileSync(t.resolve(o,m)),a=new WebAssembly.Module(n);l=new WebAssembly.Instance(a,g)}if(p){const e=read(m,"binary"),t=new WebAssembly.Module(e);l=new WebAssembly.Instance(t,g)}d&&(l=(await WebAssembly.instantiateStreaming(fetch(m),g)).instance)}catch(e){if(e instanceof WebAssembly.CompileError){let e="Please make sure that your runtime environment supports the latest version of Wasm GC and Exception-Handling proposals.\nFor more information, see https://kotl.in/wasm-help\n";if(d)console.error(e);else{const t="\n"+e;"undefined"!=typeof console&&void 0!==console.log?console.log(t):print(t)}}throw e}return c=l.exports,t&&c._initialize(),{instance:l,exports:c}}r.d(t,{_:()=>o})}},l={};function s(e){var t=l[e];if(void 0!==t)return t.exports;var r=l[e]={exports:{}};return i[e](r,r.exports,s),r.exports}return s.m=i,e="function"==typeof Symbol?Symbol("webpack queues"):"__webpack_queues__",t="function"==typeof Symbol?Symbol("webpack exports"):"__webpack_exports__",r="function"==typeof Symbol?Symbol("webpack error"):"__webpack_error__",o=e=>{e&&!e.d&&(e.d=1,e.forEach((e=>e.r--)),e.forEach((e=>e.r--?e.r++:e())))},s.a=(n,a,i)=>{var l;i&&((l=[]).d=1);var s,c,_,p=new Set,d=n.exports,m=new Promise(((e,t)=>{_=t,c=e}));m[t]=d,m[e]=e=>(l&&e(l),p.forEach(e),m.catch((e=>{}))),n.exports=m,a((n=>{var a;s=(n=>n.map((n=>{if(null!==n&&"object"==typeof n){if(n[e])return n;if(n.then){var a=[];a.d=0,n.then((e=>{i[t]=e,o(a)}),(e=>{i[r]=e,o(a)}));var i={};return i[e]=e=>e(a),i}}var l={};return l[e]=e=>{},l[t]=n,l})))(n);var i=()=>s.map((e=>{if(e[r])throw e[r];return e[t]})),c=new Promise((t=>{(a=()=>t(i)).r=0;var r=e=>e!==l&&!p.has(e)&&(p.add(e),e&&!e.d&&(a.r++,e.push(a)));s.map((t=>t[e](r)))}));return a.r?c:i()}),(e=>(e?_(m[r]=e):c(d),o(l)))),l&&(l.d=0)},s.d=(e,t)=>{for(var r in t)s.o(t,r)&&!s.o(e,r)&&Object.defineProperty(e,r,{enumerable:!0,get:t[r]})},s.f={},s.e=e=>Promise.all(Object.keys(s.f).reduce(((t,r)=>(s.f[r](e,t),t)),[])),s.u=e=>e+".js",s.g=function(){if("object"==typeof globalThis)return globalThis;try{return this||new Function("return this")()}catch(e){if("object"==typeof window)return window}}(),s.o=(e,t)=>Object.prototype.hasOwnProperty.call(e,t),n={},a="composeApp:",s.l=(e,t,r,o)=>{if(n[e])n[e].push(t);else{var i,l;if(void 0!==r)for(var c=document.getElementsByTagName("script"),_=0;_<c.length;_++){var p=c[_];if(p.getAttribute("src")==e||p.getAttribute("data-webpack")==a+r){i=p;break}}i||(l=!0,(i=document.createElement("script")).charset="utf-8",i.timeout=120,s.nc&&i.setAttribute("nonce",s.nc),i.setAttribute("data-webpack",a+r),i.src=e),n[e]=[t];var d=(t,r)=>{i.onerror=i.onload=null,clearTimeout(m);var o=n[e];if(delete n[e],i.parentNode&&i.parentNode.removeChild(i),o&&o.forEach((e=>e(r))),t)return t(r)},m=setTimeout(d.bind(null,void 0,{type:"timeout",target:i}),12e4);i.onerror=d.bind(null,i.onerror),i.onload=d.bind(null,i.onload),l&&document.head.appendChild(i)}},s.r=e=>{"undefined"!=typeof Symbol&&Symbol.toStringTag&&Object.defineProperty(e,Symbol.toStringTag,{value:"Module"}),Object.defineProperty(e,"__esModule",{value:!0})},(()=>{var e;s.g.importScripts&&(e=s.g.location+"");var t=s.g.document;if(!e&&t&&(t.currentScript&&(e=t.currentScript.src),!e)){var r=t.getElementsByTagName("script");if(r.length)for(var o=r.length-1;o>-1&&!e;)e=r[o--].src}if(!e)throw new Error("Automatic publicPath is not supported in this browser");e=e.replace(/#.*$/,"").replace(/\?.*$/,"").replace(/\/[^\/]+$/,"/"),s.p=e})(),(()=>{s.b=document.baseURI||self.location.href;var e={179:0};s.f.j=(t,r)=>{var o=s.o(e,t)?e[t]:void 0;if(0!==o)if(o)r.push(o[2]);else{var n=new Promise(((r,n)=>o=e[t]=[r,n]));r.push(o[2]=n);var a=s.p+s.u(t),i=new Error;s.l(a,(r=>{if(s.o(e,t)&&(0!==(o=e[t])&&(e[t]=void 0),o)){var n=r&&("load"===r.type?"missing":r.type),a=r&&r.target&&r.target.src;i.message="Loading chunk "+t+" failed.\n("+n+": "+a+")",i.name="ChunkLoadError",i.type=n,i.request=a,o[1](i)}}),"chunk-"+t,t)}};var t=(t,r)=>{var o,n,[a,i,l]=r,c=0;if(a.some((t=>0!==e[t]))){for(o in i)s.o(i,o)&&(s.m[o]=i[o]);l&&l(s)}for(t&&t(r);c<a.length;c++)n=a[c],s.o(e,n)&&e[n]&&e[n][0](),e[n]=0},r=this.webpackChunkcomposeApp=this.webpackChunkcomposeApp||[];r.forEach(t.bind(null,0)),r.push=t.bind(null,r.push.bind(r))})(),s(598)})()));
//# sourceMappingURL=composeApp.js.map