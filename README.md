# Location Sharing

Android peer-to-peer, on-demand location sharing using UnifiedPush.

Expand the bottom sheet on **Home** to add or manage contacts, pair devices with QR codes in **Security**, and enable sharing for that peer in **Security**. A paired peer can request one location fix; the receiver checks the sender's signature and per-peer sharing setting before replying. Requests and responses are encrypted in transit.

**Home** shows the latest received position of each paired contact on an OpenStreetMap map, with a last-seen time. A previously seen contact turns grey if a new request goes unanswered for a minute; contacts without a previous position appear in the bottom sheet as "Last seen: never" with no pin. Tap their row to request a first location after pairing.

Requires a UnifiedPush distributor on each device (for example ntfy). Push delivery is handled by the distributor; the app does not poll for requests. There is no FCM fallback yet.

Incoming requests schedule a background job to obtain one fix and send the reply. The app does not display location notifications on either phone; responses silently update the map. Android may delay background jobs (especially in Doze), so delivery is not immediate or guaranteed. Grant background location access for automatic replies.

Build with `sh gradlew assembleFdroidDebug` or `sh gradlew assembleFdroidRelease` (JDK and Android SDK required).

GPL-3.0-or-later. See [LICENSE](LICENSE).
