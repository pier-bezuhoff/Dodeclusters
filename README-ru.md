# Dodeclusters ֍ Додекластеры
[![License: GPL v3](https://img.shields.io/badge/license-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
![Static Badge](https://img.shields.io/badge/status-beta-violet)
[![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/pier-bezuhoff/Dodeclusters/build.yml?branch=master&event=push)](https://github.com/pier-bezuhoff/Dodeclusters/actions)  

### Векторный редактор, построенный на окружностях   ⸽   [EN](README.md) | **RU**

![Dodeclusters](docs/02inf-art.png)

## Возможности:
♾️ Безграничный холст, бездонное приближение/отдаление, бесконечная точность<sup>\*</sup>  
⬤ Мощный инструментарий сферической геометрии: единство точек, окружностей и прямых  
💬 Простой, интуитивный дизайн с интерактивными подсказками и пояснениями  
💯 Кроссплатформенность (сайт + офлайн приложение + мобильное приложение  
🌲 Динамическое дерево взаимозависимых объектов  
📜 История операций: отменить & повторить действие; автосохранение  
🔗 Возможность быстро делиться с друзьями своими чертежами/рисунками по ссылке  
💾 Удобный и легко читаемый формат сохранения, основанный на YAML + Safe SVG экспорт  
✌ Умные жесты и комбинации клавиш  
🌘 Дневной/ночной режим (*WIP*, пока как аргументы URL `?theme=light` or `?theme=dark`)  

\*: объекты динамически связаны друг с другом, поэтому точность сохраняется при любом масштабе

Проект построен на [Compose Multiplatform](https://github.com/JetBrains/compose-multiplatform) и поддерживает:
- [x] ПК версию (Windows, Linux)
- [x] Android 7.0+ (SDK 24+)
- [x] [Сайт](https://pier-bezuhoff.github.io/Dodeclusters/) (браузерная версия на [Wasm](#Web-Wasm-compatibility))
- [ ] Мак/iPhone, если я откопаю Apple dev аккаунт, т.к. там требуют подписать программу. Пока работает только как веб-версия

## Желаемая цель проекта
Совместить интерактивный дизайн ~ Geogebra  
с редактором векторной графики ~ Inkscape  
и с особым родом анимаций ~ [Дробно-линейные преобразования Мёбиуса](https://ru.wikipedia.org/wiki/%D0%94%D1%80%D0%BE%D0%B1%D0%BD%D0%BE-%D0%BB%D0%B8%D0%BD%D0%B5%D0%B9%D0%BD%D0%BE%D0%B5_%D0%BF%D1%80%D0%B5%D0%BE%D0%B1%D1%80%D0%B0%D0%B7%D0%BE%D0%B2%D0%B0%D0%BD%D0%B8%D0%B5_%D0%BA%D0%BE%D0%BC%D0%BF%D0%BB%D0%B5%D0%BA%D1%81%D0%BD%D0%BE%D0%B9_%D0%BF%D0%BB%D0%BE%D1%81%D0%BA%D0%BE%D1%81%D1%82%D0%B8) и возможно [геометрией сфер Ли](https://en.wikipedia.org/wiki/Lie_sphere_geometry)  

> [!NOTE]
> Этот проект на бета-стадии, могут возникать баги и ошибки при смене версий. Если вы их встретили, прошу описать в Issues на Гитхабе

## Установка и запуск
[Веб-приложение размещено здесь](https://pier-bezuhoff.github.io/Dodeclusters/) из ветки `github-pages`.  
Файлы для запуска на Windows, Linux и Android вы найдёте здесь, [прикреплённые к последней версии программприкреплённые к последней версии программы](https://github.com/pier-bezuhoff/Dodeclusters/releases/latest) или [на Гугл диске](https://drive.google.com/drive/folders/1abGxbUhnnr4mGyZERKv4ePH--us66Wd4?usp=sharing).

### Windows
1. Из [последнего релиза](https://github.com/pier-bezuhoff/Dodeclusters/releases/latest) перейдите в `assets` и скачайте `windows.zip`
2. Разархивируйте его в папку
3. Внутри неё, в папке `com.pierbezuhoff.dodeclusters` вы найдёте `com.pierbezuhoff.dodeclusters.exe`
4. Запустите этот экзешник для офлайн версии (он также содержит нужную JVM)

### Linux
1. Из [последнего релиза](https://github.com/pier-bezuhoff/Dodeclusters/releases/latest) перейдите в `assets` и скачайте `linux.zip`
2. Разархивируйте его в папку
3. Внутри неё, в папке `com.pierbezuhoff.dodeclusters/bin` вы найдёте ELF `com.pierbezuhoff.dodeclusters`
4. `chmod +x` его в терминале и далее вы можете запускать программу с помощью `./com.pierbezuhoff.dodeclusters` (содержит JVM)

### Android
Скачайте и установите APK файл из ассетов [последнего релиза](https://github.com/pier-bezuhoff/Dodeclusters/releases/latest).

> [!NOTE]
> Офлайн приложение скорее всего несколко отстаёт от веб-приложения

## Быстрые сочетания клавиш
- `Ctrl + A`: quickly select/deselect everything
- `Delete`, `Backspace`: delete selected objects
- `Ctrl + V`: duplicate selected objects
- `Ctrl + +`, `Ctrl + =`; `Ctrl + -`; mouse wheel: enlarge/shrink selected objects or zoom in/out
- `Ctrl + Z`: undo ↶
- `Ctrl + Y`: redo ↷
- `Esc`: cancel any ongoing constructions
- `O`: open existing file
- `S`: save file
- `M`: go to Drag mode (~ **M**ove)
- `L`: go to Mu**L**tiselect mode
- `R`: go to **R**egions mode
- `T`: open **T**ransform tools
- `C`: open **C**reate tools
- `Enter`: confirm & conclude current action


## Build and run via Gradle

> [!IMPORTANT]
> Requires JDK 17 (later versions may work too)

### Run desktop app
`./gradlew :composeApp:run`  
### Run web app (dev)
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun`  

### Package for Windows/macOS/Linux
Build platform-dependent package (e.g. run thru Github Actions using corresponding OS):  
`./gradlew composeApp:createDistributable`  
output directory: `composeApp/build/compose/binaries/main/app/`  
Same + use ProGuard to minify:  
`./gradlew composeApp:createReleaseDistributable`  
output directory: `composeApp/build/compose/binaries/main-release/app/`  

Individually:  
- `./gradlew packageReleaseMsi`  
- `./gradlew packageReleaseDmg` + notarize/register (requires Apple dev acc)  
- `./gradlew packageReleaseDeb`  
output directory: `composeApp/build/compose/binaries/main-release/app/`  

### Package for web browser 
`./gradlew wasmJsBrowserDistribution`  
output directory: `composeApp/build/dist/wasmJs/productionExecutable/`  

#### Package development version for web
`./gradlew wasmJsBrowserDevelopmentExecutableDistribution`  
output directory: `composeApp/build/dist/wasmJs/developmentExecutable/`  

### Generate debug .apk for Android
`./gradlew assembleDebug`  
output directory: `composeApp/build/outputs/apk/debug/`


## Web Wasm compatibility

To run applications built with Kotlin/Wasm in a browser, you need a browser supporting [wasm garbage collection feature](https://github.com/WebAssembly/gc):  
- For **Chrome** and **Chromium-based** browsers (Edge, Brave etc.), it **should just work** since version 119.
- For **Firefox** 120+ it **should just work**.
- For **Safari** it should work starting from version 18.2.
> [!NOTE]
> Safari 18.2 is available for iOS 18.2, iPadOS 18.2, visionOS 2.2, macOS 15.2, macOS Sonoma, and macOS Ventura. On iOS and iPadOS, Safari 18.2 is bundled with the operating system. To get it, update your device to version 18.2 or later. [Safari release notes](https://developer.apple.com/documentation/safari-release-notes/safari-18_2-release-notes#Overview)
- [Relevant Wasm compatibility table.](https://webassembly.org/features/#table-row-gc)

For more information see https://kotl.in/wasm_help/.
