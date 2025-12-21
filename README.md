# Trackora

A professional Android application for tracking work entries with support for multiple statuses, custom fields, and comprehensive reporting.

## Features

- 📝 **Work Entry Management**: Create, edit, and track work entries with custom fields
- 📊 **Reports & Analytics**: View daily, weekly, and monthly work summaries
- 🌍 **Multi-language Support**: English and Persian (Farsi) with RTL support
- 🎨 **Material 3 Design**: Modern, professional UI following Material Design 3 guidelines
- 📅 **Jalali Calendar Support**: Persian Solar calendar support for date selection
- ⚙️ **Customizable Fields**: Configure custom field names in settings
- 🌓 **Theme Support**: Light, Dark, and System theme modes
- 💾 **Local Data Storage**: All data stored locally using Room database

## Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: Clean Architecture (Domain, Data, Presentation layers)
- **Dependency Injection**: Hilt
- **Database**: Room
- **Data Persistence**: DataStore Preferences
- **Navigation**: Jetpack Compose Navigation
- **Coroutines & Flow**: For asynchronous operations
- **Material Design 3**: Modern Material components

## Project Structure

```
Trackora/
├── app/                    # Main application module
├── core/
│   ├── common/            # Shared utilities, UI components, theme
│   ├── data/              # Data layer (Room, Repository implementations)
│   └── domain/            # Domain layer (Use cases, Models, Repository interfaces)
└── feature/
    ├── addedit/           # Add/Edit Work Entry feature
    ├── allwork/           # All Work Entries feature
    ├── reports/            # Reports feature
    └── today/              # Today's Work feature
```

## Setup Instructions

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or later
- JDK 11 or later
- Android SDK with API level 24+ (Android 7.0+)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/trackora.git
   cd trackora
   ```

2. Open the project in Android Studio

3. Sync Gradle files and wait for dependencies to download

4. Build and run the app

### Building for Release

1. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore trackora-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias trackora
   ```

2. Create `keystore.properties` in the root directory:
   ```properties
   storeFile=../path/to/trackora-release.jks
   storePassword=your_store_password
   keyAlias=trackora
   keyPassword=your_key_password
   ```

3. Build the release APK:
   ```bash
   ./gradlew assembleRelease
   ```

   Or build an Android App Bundle (recommended for Play Store):
   ```bash
   ./gradlew bundleRelease
   ```

## Version Management

Version is managed in `app/src/main/java/com/evergreen/trackora/util/AppVersion.kt`:

```kotlin
const val VERSION_MAJOR = 1
const val VERSION_MINOR = 0
const val VERSION_PATCH = 0
const val VERSION_BUILD = 1
```

Update these constants to change the app version. The build system automatically reads from this file.

## Security

- ✅ Code obfuscation enabled for release builds (ProGuard/R8)
- ✅ Resource shrinking enabled
- ✅ No hardcoded secrets or API keys
- ✅ Keystore files excluded from version control
- ✅ Sensitive configuration files in .gitignore

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Privacy

This app stores all data locally on your device. No data is transmitted to external servers. Your privacy is our priority.

## Support

For issues, questions, or suggestions, please open an issue on GitHub.

## Acknowledgments

- Material Design 3 components
- Jetpack Compose team
- Android development community

---

Made with ❤️ using Kotlin and Jetpack Compose

