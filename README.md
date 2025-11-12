# Pelopen

An Android application that uses the peloton-sensors library to display real-time Peloton bike sensor data.

## Features

- Real-time display of Peloton bike sensor data:
  - Power (Watts)
  - Cadence (RPM)
  - Resistance
  - Speed (m/s)
- Support for Peloton Bike (Gen 2) and Peloton Bike+
- Automatic bike model detection
- Dead sensor detection
- Dummy sensor interface for testing on non-Peloton devices

## Requirements

- Android SDK 21 (Android 5.0) or higher
- Target SDK 33
- Peloton bike with sensor service (for real sensor data)
- Or use dummy sensor mode for testing on emulators/non-Peloton devices

## Building

1. Open the project in Android Studio
2. Sync Gradle files
3. Build and run on a device or emulator

## Usage

When running on a Peloton bike:
- The app automatically detects the bike model (Bike or Bike+)
- Connects to the Peloton sensor service
- Displays real-time sensor data

When running on a non-Peloton device:
- Uses the DummySensorInterface which generates simulated sensor data
- Useful for testing and development

## Permissions

The app requires the following permissions (automatically merged from the peloton-sensors library):
- `onepeloton.permission.ACCESS_SENSOR_SERVICE`
- `onepeloton.permission.SUBSCRIPTION_TYPE_ACCESS`

## Library

This project uses the `peloton-sensors-release.aar` library located in `app/libs/`.

## License

See the peloton-sensors library license for details.

