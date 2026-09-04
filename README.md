# Beyond V12 Android — Camera2 live receiver prototype

V12 moves camera capture from Termux:API into a native Android Camera2 stream.

## Current state
- Native Android Camera2 capture.
- Continuous YUV_420_888 frames.
- No Termux:API dependency.
- Portrait receiver UI.
- Camera autofocus and auto-exposure enabled.
- Capture pipeline is intentionally separated from the proven BYN8/V8-V11 protocol.

## Build
Open this directory in Android Studio and let Gradle sync. Build/install the `app` module.

## Important
This is the V12 Camera2 foundation, not yet the final optical decoder. The next layer should port the proven NumPy finder/homography/grid decoder to Android (Kotlin/Java), then port the Reed-Solomon and SHA-256 reconstruction state machine. Keeping those layers separate makes debugging much easier.
