# Prism Grove

Prism Grove is a standalone Android app for on-device chat with PrismML Bonsai GGUF models.

## What is in this repo

- `app/`: Jetpack Compose Android client
- `runtime/`: JNI bridge and native runtime wrapper
- `third_party/prism-llama.cpp`: pinned PrismML `llama.cpp` fork for 1-bit Bonsai support

## Model presets

The bundled presets target the 1-bit Bonsai GGUF releases:

- Bonsai 1.7B: about 237 MB
- Bonsai 4B: about 546 MB
- Bonsai 8B: about 1.08 GiB / 1.15 GB

The 8B preset is the intended "large" on-device option and should stay near a 1.15 GB download footprint.

## Local setup

1. Install Android Studio or the Android SDK, NDK `27.2.12479018`, CMake `3.22.1`, and Java 17.
2. Initialize the pinned PrismML runtime fork:

```bash
git submodule update --init --recursive
```

3. Create your own `local.properties` if your SDK is not auto-detected:

```properties
sdk.dir=/path/to/Android/Sdk
```

4. Build the APK:

```bash
./gradlew assembleDebug
```

## GitHub Actions

`.github/workflows/android-apk.yml` builds the app on pushes and pull requests and uploads both debug and release APK artifacts.
