# Android APK (experimental)

This packages VectorCraft's existing touch-enabled WebAssembly app inside an Android WebView. The
APK bundles the compiled site, so the editor launches and works offline; this is a WebView wrapper,
not a separate native Android/Rust UI. It uses WebGL2 for device compatibility and Android's
system file picker for opening and saving files.

## Build locally

Install Rust (the workspace requires the version in `Cargo.toml`), Java 17, Android SDK platform
35 and build tools 35.0.0, Gradle 8.11.1, and Trunk. Add the WebAssembly target if needed and make
sure Gradle reports version 8.11.1:

```sh
rustup target add wasm32-unknown-unknown
sdkmanager "platforms;android-35" "build-tools;35.0.0"
cargo install trunk --locked
gradle --version
packaging/android/build.sh
```

The script builds `apps/vectorcraft-web`, then packages `dist/web` into the APK. The installable,
debug-signed APK is written to:

```text
dist/android/vectorcraft-android-debug.apk
```

The `Android APK` GitHub Actions workflow also builds the APK and uploads it as the artifact
`vectorcraft-android-apk` for manual, push, and pull-request runs. Debug APKs can be installed on a
device after allowing installation from the chosen file manager/browser.

## Mobile behavior and limits

- Android 6.0 (API 23) or newer; Android System WebView must support WebGL2.
- The web assets and their local storage are bundled/on-device. The APK declares no Internet
  permission and blocks web requests outside its local asset origin.
- File → Open/Place uses Android's document picker. Save/Export opens Android's Save As picker.
  Files are written only to the destination the user chooses.
- This is an experimental mobile packaging of the web UI. Some desktop-oriented tools and browser
  features (for example screen colour sampling and browser printing) are not available in Android
  WebView. A pen's advanced hardware features depend on Android WebView's Pointer Events support.
- The build uses the project's original app icon and the version in the root `Cargo.toml`.

Build the web app with `CRAFT_FONTS_DIR` set to a craft-fonts checkout to bundle the optional
additional web fonts too; without it, the APK still builds and runs, but the optional downloaded
Arabic faces are absent.
