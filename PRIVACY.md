# Privacy

Find It Fun is designed to process radio and sensor observations locally.

## Data the Android app observes

- Nearby Bluetooth Low Energy advertisements, including signal strength,
  advertised names, service identifiers, and manufacturer identifiers.
- Names of Bluetooth devices already paired with the phone.
- GNSS satellite status and signal strength while Space mode is open.
- Magnetic-field, compass, and step-detector readings used by the live views.

These observations remain in memory and are not written to scan-history files.
The app has no Internet permission, network client, analytics, advertising SDK,
account system, or remote service.

## Data stored on the phone

If a user gives a nearby device a custom name, the alias and the Android-provided
Bluetooth device identifier are stored in app-private `SharedPreferences`. Users
can forget an alias in the device dialog, and uninstalling the app deletes all
app data. Cloud backup and device-to-device transfer are disabled for every app
data domain.

## Permissions

- Nearby devices: scan for BLE advertisements and show paired-device metadata.
- Location: activate Android's GNSS receiver for the user-invoked Space view.
- Physical activity: count steps for the user-invoked Hunt trail view.
- Vibration: provide optional proximity feedback.

The app does not request contacts, microphone, camera, storage, phone, account,
or Internet access.

## Windows survey helper

The optional scripts in `tools/` print nearby BLE observations to standard
output. They do not save or transmit results. A user who redirects that output
to a file is responsible for protecting or deleting the resulting scan data,
which can contain nearby device names and hardware addresses.
