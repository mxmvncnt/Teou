# Location Sharing

Android peer-to-peer, on-demand location sharing using UnifiedPush.

Pair devices with QR codes in **Security**, add a contact on **Home**, and enable sharing for that peer in **Security**. A paired peer can request one location fix; the receiver checks the sender's signature and per-peer sharing setting before replying. Requests and responses are encrypted in transit.

Requires a UnifiedPush distributor on each device (for example ntfy). Push delivery is handled by the distributor; the app does not poll for requests. There is no FCM fallback yet.

Background location replies are not yet guaranteed: Android can stop the app before the asynchronous fix and reply finish. A short-lived location foreground service is needed for reliable background operation.

Build with `sh gradlew assembleFdroidDebug` or `sh gradlew assembleFdroidRelease` (JDK and Android SDK required).

GPL-3.0-or-later. See [LICENSE](LICENSE).
