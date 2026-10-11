UVCCamera
=========

library and sample to access to UVC web camera on non-rooted Android device

Copyright (c) 2014-2017 saki t_saki@serenegiant.com

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

All files in the folder are under this Apache License, Version 2.0.
Files in the jni/libjpeg, jni/libusb and jin/libuvc folders may have a different license,
see the respective files.

About this fork
=========
This is a fork of [saki4510t/UVCCamera](https://github.com/saki4510t/UVCCamera), reduced to the library and one app:
a fullscreen viewer for a UVC HDMI capture device, e.g. to use a phone as the display of a Raspberry Pi.
The other sample apps of the original repository were removed, see the original repository for them and for the
history of the library.

* `libuvccamera` is the library, `usbCameraTest0` is the viewer. It is installed as "HDMI Display".
* The viewer shows the device in landscape at 1920x1080 (MJPEG) without title bar, keeps the screen on and opens the
  device as soon as it is attached. It can be set as the default app for the device in the USB permission dialog.
* MJPEG frames are decoded straight into the buffer of the preview surface and only the newest frame is decoded,
  this gives 30fps at 1920x1080 on a Nokia 5.1.

How to build
=========
The project builds with Gradle 9.8 / Android Gradle Plugin 9.4 / NDK 28 and a JDK 17 or newer (the JDK bundled with
Android Studio works), for `armeabi-v7a` and `arm64-v8a`. The NDK is downloaded by the Android Gradle Plugin.

```
./gradlew :usbCameraTest0:assembleDebug
adb install -r usbCameraTest0/build/outputs/apk/debug/usbCameraTest0-debug.apk
```

For a signed release build add the key to `local.properties` and run `./gradlew :usbCameraTest0:assembleRelease`.

```
release.storeFile=/path/to/release.jks
release.storePassword=...
release.keyAlias=...
release.keyPassword=...
```

Android 10 and Play Protect
=========
`targetSdkVersion` has to stay at 27. On Android 10 the USB service reports a missing `CAMERA` permission for video class
devices to every app targeting API 28 or later, even when the permission is granted, so the device can never be opened.

Play Protect refuses to install apps with such an old target. Install with `adb` after turning off
`Verify apps over USB` in the developer options, it can be turned on again afterwards.
