Alternate Lastpass Keyboard for Android
========
An alternate keyboard for lastpass which instantly switches back to your default keyboard after entering the username/password.

Expected to be used with the 'switch to other input method' button on the stock android keyboard, or equivalent, for a single-button password entry which doesn't require you to manually switch back to a usable keyboard afterwards.

Building
--------
1. Install [lastpass-java](https://github.com/nhinds/lastpass-java)
2. Install the [Android SDK](http://developer.android.com/sdk/index.html) and API version 16 (4.1.x)
3. Ensure ANDROID\_HOME is set to the location of your Android SDK

        $ export ANDROID_HOME=/path/to/android_sdk_linux

4. Build the .apk and deploy it to a connected android device (or AVD)

        $ cd lastpass-android
        $ mvn package
        $ mvn android:deploy
