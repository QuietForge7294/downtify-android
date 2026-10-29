# Android Auto

The phone app exposes a Media3 library service. Android Auto renders the driving interface; the phone activity is not a car screen. This is not a separate Android Automotive OS application.

## Available content

Browse playlists, liked songs, artists, albums, all library tracks, and completed offline downloads. The catalog uses the existing local library and permissions, not online search. Playlist and album order are preserved. Selecting a row starts its collection at that row in the same player/queue used by the phone. A browser that limits root entries receives one Library folder containing all six categories.

Text and voice search match local title, artist and album metadata. A unique playable voice result can start playback; ambiguous requests require choosing a search result. No download or administrative actions are exposed. Browsing never starts playback.

The existing sync coordinator runs while either the phone UI or the media service is active, with one shared sync loop. Reopening a catalog page does not rescan files or call server search. No server endpoint changes are required.

## Artwork and controllers

Trusted system/media controllers can browse and play. App-specific queue commands remain private to the phone app. External requests resolve known catalog IDs; supplied audio URLs are discarded and unknown IDs are rejected.

External artwork uses opaque read-only local content URIs, prepared asynchronously with the existing authenticated HTTP client. Images are at most 512 pixels, input is limited to 8 MiB, and the disk cache is capped at 32 MiB. Browsing reads ready images without waiting for network. Playback prepares the current and up to three following covers, without changing audio preloading settings. Images from a previous association cannot be reopened after changing accounts.

## Validation

Run `./gradlew testDebugUnitTest lint detekt spotlessCheck assembleRelease`. Catalog/callback tests cover paging, empty collections, accents, playlist duplicates, selection position, unknown IDs, supplied URLs, authentication errors and image cache isolation. Existing playback restoration and offline tests must remain green.

Then use the official [Desktop Head Unit](https://developer.android.com/training/cars/testing/dhu) and a real receiver, stationary:

1. Open Downtify from the Android Auto launcher without first opening the phone activity.
2. Browse every category, search, choose a playlist track and check queue order on the phone.
3. Check current artwork, steering-wheel controls, pause/resume, shuffle/repeat and call interruptions.
4. Reconnect with the phone locked and after process death; the queue should restore paused until playback is requested. Explicit Android force-stop restrictions still apply.
5. Disconnect the network and play completed offline files. Restore network and check catalog updates and artwork. Check both cable and wireless receiver connections where available.
6. Unpair the phone and verify that the previous library cannot be browsed or played.

For a sideloaded media APK, Android Auto's developer setting **Allow unknown sources** may be required; see [official testing instructions](https://developer.android.com/training/cars/testing). This setting does not replace the app integration. Keep the same application ID and signing certificate when updating an existing test installation.

Real receiver and voice-assistant behavior must be reported separately from automated tests. Do not claim car validation based solely on a successful APK build.
