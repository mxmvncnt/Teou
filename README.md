# téou

Location tracking app for Android that does not require Google Play Services.

## UnifiedPush

The app mainly works with a push system powered by [UnifiedPush](https://unifiedpush.org/).
Instead of having the app constantly open in the background, it works on a "on-demand" principle. When someone wants yo know the location of a device, the user simply opens the app, automatically,
all contacts will receive an invisible UnifiedPush notification that will tell the app to wake up and to send back the device's current information back, also via UnifiedPush

## FCM (Google Play Services)

This is still WIP

Using FCM is a bit more tricky because it requires a dedicated backend to hold the FCM keys. Otherwise, anyone could simply spam users with a bunch of notifications.
But with the backend running, the principle of the app would remain the same: users ask connected contacts for their location, and only then is it given.

## Running in Android Studio

Sync Gradle, then select **FCM** and your FCM phone, or **UnifiedPush** and
your GrapheneOS phone, and click Run. Each is a separate Android app module
with its own distribution, so switching run configurations switches the app
without changing Build Variants. Android Studio remembers the device for each
configuration. Both support the normal Run and Debug buttons.

# Adding a contact

Adding a contact is a mutual process that each party has to conduct.

In these steps the `sending` phone is the phone that will be sending its location information.

1. On the sending phone, go in `Security`, then display the QR code. You can also copy a link that you can send to the other user if they are not in the room with you or if using the camera is not viable.
2. **If you chose the link method, you can skip steps 2 and 3!**
3. On the receiving phone, in the map view, open up the `Contacts` drawer, and click the `+` button.
4. Tap on `Scan QR code`, then scan the QR code displayed on the sending phone.
5. Enter a familiar name (this name is only saved locally, so it can be whatever you like). This name can be changed at any time.
6. Keep the location sharing toggle enabled!
7. Save
8. Re-do this full process, but invert the `receiving` and `sending` phones!

Note: You may have to restart the apps for the first sync to happen

## Project name

The project's name comes from the french "t'es ou?", more formally "ou es-tu?", which means "where are you?"!

GPL-3.0-or-later. See [LICENSE](LICENSE).
