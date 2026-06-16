## Hyperterialistic for Hacker News
Material design [Hacker News] client for Android, uses official [HackerNews/API], [Dagger] for dependency injection.

### Setup
**Requirements**
- JDK 17 or newer
- Android SDK 36.1
- Latest Android platform tools
- Android 6.0 or newer device or emulator
- AndroidX

**Dependencies**
- [Official Hacker News API][HackerNews/API], user services (e.g. login/create account/vote/comment) rely on redirect requests to Hacker News website
- [Algolia Hacker News Search API]
- [Mercury Web Parser API]
- [Android Jetpack]: appcompat-v7 / recyclerview-v7 / design / cardview-v7 / preference-v7 / customtabs
- Square [Retrofit] / [OkHttp] / [AssertJ] / [Dagger] / [LeakCanary]
- [RxJava] & [RxAndroid]
- [PDF.js]

**Build**

    ./gradlew :app:assembleDebug

Build with LeakCanary on

    ./gradlew :app:assembleDebug -Pleak

Grab your Mercury Web Parser API key [here][mercury] if you want to connect to Mercury.

### Screenshots
<img src="assets/screenshot-1.png" width="200px" />
<img src="assets/screenshot-2.png" width="200px" />
<img src="assets/screenshot-3.png" width="200px" />
<img src="assets/screenshot-4.png" width="600px" />

### Contributing
Contributions are always welcome. Please make sure you read [Contributing notes](CONTRIBUTING.md) first.

### License
    Copyright 2015 Ha Duy Trung
    
    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at
    
        http://www.apache.org/licenses/LICENSE-2.0
    
    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

[Hacker News]: https://news.ycombinator.com/
[HackerNews/API]: https://github.com/HackerNews/API
[Algolia Hacker News Search API]: https://github.com/algolia/hn-search
[Mercury Web Parser API]: https://mercury.postlight.com/web-parser/
[AOSP support library]: https://developer.android.com/tools/support-library/features.html
[Retrofit]: https://github.com/square/retrofit
[OkHttp]: https://github.com/square/okhttp
[AssertJ]: https://github.com/square/assertj-android
[Dagger]: https://github.com/square/dagger
[LeakCanary]: https://github.com/square/leakcanary
[RxJava]: https://github.com/ReactiveX/RxJava
[RxAndroid]: https://github.com/ReactiveX/RxAndroid
[mercury]: https://mercury.postlight.com/web-parser/
[PDF.js]: https://mozilla.github.io/pdf.js/
