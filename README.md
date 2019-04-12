# Triton Digital® Mobile SDK for Android

The Triton Digital® Mobile SDK is designed to help you play your radio station or display on-demand advertisements very easily in your own application. There are two (2) versions of the mobile SDK; one for Android and one for iOS. This is the Android version.
 
The Triton Digital® Mobile SDK includes a [ZIP file that contains the API reference](https://userguides.tritondigital.com/spc/moband/zip_file.htm) and a sample application that is ready to compile showing the most common uses of the SDK.

The Triton Digital® Mobile SDK adds about 240 kb to the size of your Android mobile application. This may vary with updates, and according to the parameters that you use.
 
The main features of the SDK include:

- Play Triton Digital® streams (including HLS mounts);
- Receive meta-data synchronized with the streams;
- Receive Now Playing History information; and
- Advertising:
    - Sync banners
    - On-demand audio and video interstitial ads
    - Support for VAST and DAAST formats

For complete documentation on using the Triton Digital® Mobile SDK for Android, visit our [online documentation](https://userguides.tritondigital.com/spc/moband/).

## Getting Started

The following instructions will get a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

&ensp; [Android Studio](https://developer.android.com/studio/)<br>
&ensp; [Java OpenJDK](https://openjdk.java.net/)

### Installing

Install Android Studio (and an emulator if you want to use that instead of a physical phone).

Fork and clone the Triton Digital® Mobile SDK for Android project on GitHub

## Running the tests

In your terminal, navigate to where you forked or cloned the SDK:

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## Built With

[Android Studio](https://developer.android.com/studio/)

## Contributing

If you wish to contribute to this project, please see the [CONTRIBUTING.md](CONTRIBUTING.md) file for details.

## Versioning

We use an internal versioning system. All accepted contributions will be versioned under this versioning scheme.

## License

This project is licensed under the Apache 2.0 License - see the [LICENSE.md](LICENSE.md) file for details.
