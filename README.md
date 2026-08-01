# Nimmdas — Android

Android client for [nimmdas.at](https://nimmdas.at), an Austrian classifieds marketplace:
listings, search with a map view, chat with calls, and a coin system.

The app talks to the nimmdas.at server. That server is a separate, non-free service —
the app is useless without an account there.

## Build variants

| Flavor   | Push notifications | Notes                                              |
|----------|--------------------|----------------------------------------------------|
| `play`   | Firebase Cloud Messaging | Google Play build. Needs `app/src/play/google-services.json`. |
| `fdroid` | none               | Builds without any proprietary Google library. New messages arrive while the app is open. |

```bash
./gradlew assembleFdroidRelease
```

The `play` flavor additionally requires a `google-services.json` from your own Firebase
project at `app/src/play/`. It is deliberately not part of this repository.

Release signing reads `keystore.properties` from the project root (git-ignored):

```properties
storeFile=/path/to/upload-keystore.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release build stays unsigned, which is what F-Droid needs.

## Requirements

- JDK 17
- Android SDK 35

## License

GNU General Public License v3.0 or later — see [LICENSE](LICENSE).
