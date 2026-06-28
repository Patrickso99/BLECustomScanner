# BLE Custom Scanner

A Kotlin Multiplatform (KMP) project that provides a custom Bluetooth Low Energy (BLE) scanner for Android and iOS. It uses Compose Multiplatform for a shared UI and platform-specific implementations of the BLE stack.

## Features

- **Device Discovery**: Scan for nearby BLE devices and display their name, RSSI, and manufacturer data.
- **Connection Management**: Connect to and disconnect from BLE peripherals.
- **GATT Exploration**: Automatically discover and display services, characteristics, and descriptors.
- **Real-time Updates**: Characteristic value updates (via polling or notifications) are displayed in the UI.
- **Paired Devices**: Quickly access and connect to already paired/bonded devices.
- **Permission Handling**: Integrated permission request flows for both Android and iOS.

## Project Structure

This project follows the standard Kotlin Multiplatform structure:

- `shared/`: The core logic and UI shared between platforms.
    - `commonMain/`: Shared Compose UI, ViewModels, and `BleController` interface.
    - `androidMain/`: Android-specific implementation of `BleController` using `BluetoothLeScanner` and `BluetoothGatt`.
    - `iosMain/`: iOS-specific implementation of `BleController` using `CoreBluetooth`.
- `androidApp/`: Entry point for the Android application.
- `iosApp/`: Entry point for the iOS application.

## Getting Started

### Prerequisites

- **Android**: Android Studio with the Kotlin Multiplatform plugin installed.
- **iOS**: A Mac with Xcode installed.

### Running the Apps

#### Android
You can run the Android app directly from Android Studio or via the command line:
```bash
./gradlew :androidApp:assembleDebug
```

#### iOS
Open the `iosApp/iosApp.xcworkspace` (or the project folder) in Xcode and run it on a real device or simulator. Note that BLE scanning requires a real device.

## Implementation Details

The `BleController` interface defines the contract for platform-specific Bluetooth operations. All state is exposed via Kotlin `StateFlow`, allowing the shared Compose UI to reactively update as devices are discovered or connection states change.

On Android, the controller uses the modern `BluetoothGattConnectionSettings` API on supported versions (API 37+) while maintaining backwards compatibility.
On iOS, it leverages `CBCentralManager` and `CBPeripheralDelegate` to manage connections and service discovery.

---
Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…
