Alternate Lastpass Keyboard for Android
========
An alternate keyboard for lastpass which instantly switches back to your default keyboard after entering the username/password.

Expected to be used with the 'switch to other input method' button on the stock android keyboard, or equivalent, for a single-button password entry which doesn't require you to manually switch back to a usable keyboard afterwards.

Building
--------
1. Install [lastpass-java](https://github.com/nhinds/lastpass-java)
2. Install lastpass-android-xerces

        $ cd lastpass-android-xerces
        $ mvn install

3. Install the [Android SDK](http://developer.android.com/sdk/index.html) and API version 17 (4.2.2)
4. Ensure ANDROID\_HOME is set to the location of your Android SDK

        $ export ANDROID_HOME=/path/to/android_sdk_linux

5. Deploy the android SDK for 4.2.2\_r2 using [mosabua's maven android sdk deployer](https://github.com/mosabua/maven-android-sdk-deployer)

        $ git clone https://github.com/mosabua/maven-android-sdk-deployer.git
        $ cd maven-android-sdk-deployer
        $ mvn clean install -N
        $ cd platforms
        $ mvn clean install -N
        $ cd android-17
        $ mvn clean install -Dplatform.android.groupid=com.google.android

6. Build the .apk and deploy it to a connected android device (or AVD)

        $ cd lastpass-android
        $ mvn package
        $ mvn android:deploy
