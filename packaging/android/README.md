# Android APK (experimental)

This packages VectorCraft's existing touch-enabled WebAssembly app inside an Android WebView. The
APK bundles the compiled site, so the editor launches and works offline; this is a WebView wrapper,
not a separate native Android/Rust UI. It uses WebGL2 for device compatibility and Android's
system file picker for opening and saving files.

## Build locally

Install Rust (the workspace requires the version in `Cargo.toml`), Java 17, Android SDK platform
36 and build tools 36.0.0, Gradle 8.13, and Trunk. Add the WebAssembly target if needed and make
sure Gradle reports version 8.13:

```sh
rustup target add wasm32-unknown-unknown
sdkmanager "platforms;android-36" "build-tools;36.0.0"
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
device after allowing installation from the chosen file manager/browser. A debug APK is for testing;
it is not a Play Store upload.

## Play Store App Bundle

Google Play expects a signed Android App Bundle (`.aab`), not this debug APK. The project targets
Android API 36 and includes a manual `Play Store AAB` workflow that creates a signed bundle using
your private upload key. After these changes are merged to `main`, run that workflow from `main`; it
does **not** publish to Google Play automatically.

### Prepare a private upload key

Create an upload key once and keep a secure offline backup. If you use Play App Signing, Google
holds the app-signing key while this key signs uploads:

```sh
keytool -genkeypair -v -keystore vectorcraft-upload-key.jks \
  -keyalg RSA -keysize 2048 -validity 10000 -alias vectorcraft-upload
```

Never commit the keystore or put its passwords in chat. For the GitHub workflow, add these under
**Repository Settings → Secrets and variables → Actions**:

- `ANDROID_KEYSTORE_BASE64`: base64 encoding of the `.jks` file (`base64 -w 0` on Linux; on macOS,
  `base64 < vectorcraft-upload-key.jks | tr -d '\n'`).
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password.
- `ANDROID_KEY_ALIAS`: `vectorcraft-upload` (or the alias you chose).
- `ANDROID_KEY_PASSWORD`: the key password.

Then go to **Actions → Play Store AAB → Run workflow**. Download the `vectorcraft-playstore-aab`
artifact and upload its `.aab` to your app in Play Console. Locally, with `ANDROID_KEYSTORE_PATH`,
`ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD` set in the environment,
run `packaging/android/build-play.sh`; it writes `dist/android/vectorcraft-<version>-play.aab`.

Before submission, confirm you control the Play Console app entry and the package ID
`ai.storyteller.vectorcraft`, and complete the store listing, privacy policy, data-safety and other
Play Console declarations. Publishing and account verification must be done by the Play Console
account owner. Uninstall the debug APK before installing a Play-distributed version signed with a
different certificate.

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
