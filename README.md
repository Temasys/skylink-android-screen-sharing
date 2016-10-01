# skylink-android-screen-sharing

## About this project
This project uses Android [MediaProjection](https://developer.android.com/reference/android/media/projection/MediaProjection.html) (available from API level 21 onwards) to capture screenshots, which are sent to a remote Peer using the SkylinkConnection.[sendData](https://cdn.temasys.com.sg/skylink/skylinksdk/android/latest/doc/reference/sg/com/temasys/skylink/sdk/rtc/SkylinkConnection.html#sendData%28java.lang.String,%20byte[]%29) method.
The remote Peer receives each screenshot and render it in an ImageView.

The screensharing effect achieved here is a demonstration of a possible use case of SkylinkConnection.sendData, and is not compatible with the screensharing functionality on other Skylink SDKs, including the Skylink JS SDK's [shareScreen](https://cdn.temasys.com.sg/skylink/skylinkjs/latest/doc/classes/Skylink.html#method_shareScreen).

## Pre-requisites

The device sharing the screen needs to run API level 21 and above.

## Step by step guide for Android Studio 

1. Clone the repository
2. Import the project into Android Studio using File -> Open and select SkylinkShare folder inside the skylink-android-screen-sharing folder 
3. Obtain [developer key](http://developer.temasys.com.sg)
4. In the MainActivity, replace the APP_KEY and APP_SECRET constants with the key and secret obtained from Step 3.
 
![](receiver_device.gif?raw=true)

## Contact Us

Please feel free to reach out to us with feedback or feature requests regarding Skylink on our [support portal](http://support.temasys.com.sg)


## Other resources

Product website : https://skylink.io/android
SkylinkSDK for Android's Sample app: https://github.com/Temasys/skylink-android-sample

## Copyright and License

Copyright 2015 Temasys Communications Pte Ltd under [APACHE 2.0](http://www.apache.org/licenses/LICENSE-2.0.html) license

