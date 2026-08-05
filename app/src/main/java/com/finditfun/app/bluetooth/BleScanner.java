package com.finditfun.app.bluetooth;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.util.SparseArray;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class BleScanner {
    private static final long DEVICE_TTL_MS = 15_000;

    public interface Listener {
        void onStatus(String status);
        void onDevicesChanged();
    }

    private final Context context;
    private final Listener listener;
    private final Map<String, BleDeviceRecord> devices = new ConcurrentHashMap<>();
    private BluetoothLeScanner scanner;
    private boolean running;

    public BleScanner(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    @SuppressLint("MissingPermission")
    public void start() {
        if (running) return;
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onStatus("Nearby Devices permission is required.");
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onStatus("Nearby Devices connection permission is required for names.");
            return;
        }

        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
        if (adapter == null) {
            listener.onStatus("This phone has no Bluetooth adapter.");
            return;
        }
        if (!adapter.isEnabled()) {
            listener.onStatus("Bluetooth is off. Turn it on, then return here.");
            return;
        }
        loadPairedDevices(adapter);
        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            listener.onStatus("Bluetooth scanner is temporarily unavailable.");
            return;
        }

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .build();
        scanner.startScan(null, settings, callback);
        running = true;
        listener.onStatus("Listening for BLE advertisements…");
    }

    @SuppressLint("MissingPermission")
    public void stop() {
        if (running && scanner != null
                && context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED) {
            scanner.stopScan(callback);
        }
        running = false;
        scanner = null;
    }

    public boolean isRunning() {
        return running;
    }

    public List<BleDeviceSnapshot> snapshots(long nowMillis) {
        ArrayList<BleDeviceSnapshot> result = new ArrayList<>();
        for (Map.Entry<String, BleDeviceRecord> entry : devices.entrySet()) {
            BleDeviceSnapshot snapshot = entry.getValue().snapshot(nowMillis);
            if (snapshot.signal.ageMillis > DEVICE_TTL_MS && !snapshot.paired) {
                devices.remove(entry.getKey(), entry.getValue());
            } else {
                result.add(snapshot);
            }
        }
        result.sort(Comparator.comparingInt((BleDeviceSnapshot item) -> item.signal.smoothedRssi)
                .reversed());
        return result;
    }

    public BleDeviceSnapshot snapshot(String key, long nowMillis) {
        BleDeviceRecord device = devices.get(key);
        return device == null ? null : device.snapshot(nowMillis);
    }

    private final ScanCallback callback = new ScanCallback() {
        @Override
        @SuppressLint("MissingPermission")
        public void onScanResult(int callbackType, ScanResult result) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                listener.onStatus("Nearby Devices permission was removed.");
                return;
            }
            int rssi = result.getRssi();
            if (rssi >= 0 || rssi <= -127) return;

            String address = result.getDevice().getAddress();
            if (address == null || address.trim().isEmpty()) return;

            ScanRecord scanRecord = result.getScanRecord();
            String name = scanRecord == null ? null : scanRecord.getDeviceName();
            if ((name == null || name.trim().isEmpty())
                    && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED) {
                name = result.getDevice().getName();
            }
            String kind = classify(scanRecord);
            String manufacturer = manufacturerSummary(scanRecord);
            String services = servicesSummary(scanRecord);
            String radio = radioSummary(result);
            Integer txPower = txPower(result, scanRecord);
            int advertisementBytes = scanRecord == null ? 0 : scanRecord.getBytes().length;
            int advertiseFlags = scanRecord == null ? -1 : scanRecord.getAdvertiseFlags();
            long now = SystemClock.elapsedRealtime();
            devices.computeIfAbsent(address, ignored -> new BleDeviceRecord(address, kind))
                    .update(name, kind, result.isConnectable(), manufacturer, services, radio,
                            txPower, advertisementBytes, advertiseFlags,
                            result.getDevice().getBondState() == BluetoothDevice.BOND_BONDED,
                            rssi, now);
            listener.onDevicesChanged();
        }

        @Override
        public void onScanFailed(int errorCode) {
            running = false;
            listener.onStatus("Bluetooth scan failed (code " + errorCode + "). Tap Retry.");
        }
    };

    @SuppressLint("MissingPermission")
    private void loadPairedDevices(BluetoothAdapter adapter) {
        if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) return;
        for (BluetoothDevice device : adapter.getBondedDevices()) {
            String address = device.getAddress();
            if (address == null || address.trim().isEmpty()) continue;
            String kind = deviceType(device.getType()) + " paired device";
            String radio = deviceType(device.getType()) + " · paired · not heard yet";
            devices.computeIfAbsent(address, ignored -> new BleDeviceRecord(address, kind))
                    .updatePaired(device.getName(), kind, radio);
        }
        listener.onDevicesChanged();
    }

    private static String manufacturerSummary(ScanRecord record) {
        if (record == null || record.getManufacturerSpecificData() == null
                || record.getManufacturerSpecificData().size() == 0) {
            return "Not advertised";
        }
        SparseArray<byte[]> data = record.getManufacturerSpecificData();
        ArrayList<String> labels = new ArrayList<>();
        for (int i = 0; i < data.size(); i++) {
            int id = data.keyAt(i);
            labels.add(manufacturerName(id) + String.format(Locale.US, " (0x%04X)", id));
        }
        return String.join(", ", labels);
    }

    private static String servicesSummary(ScanRecord record) {
        if (record == null) return "None advertised";
        Set<String> labels = new LinkedHashSet<>();
        List<ParcelUuid> advertised = record.getServiceUuids();
        if (advertised != null) {
            for (ParcelUuid uuid : advertised) labels.add(serviceLabel(uuid));
        }
        Map<ParcelUuid, byte[]> data = record.getServiceData();
        if (data != null) {
            for (ParcelUuid uuid : data.keySet()) labels.add(serviceLabel(uuid) + " data");
        }
        return labels.isEmpty() ? "None advertised" : String.join(", ", labels);
    }

    @SuppressLint("MissingPermission")
    private static String radioSummary(ScanResult result) {
        ArrayList<String> parts = new ArrayList<>();
        parts.add(deviceType(result.getDevice().getType()));
        parts.add(result.isLegacy() ? "legacy advertisement" : "extended advertisement");
        parts.add(result.isConnectable() ? "connectable" : "broadcast only");
        parts.add("primary " + phyName(result.getPrimaryPhy()));
        if (result.getSecondaryPhy() != ScanResult.PHY_UNUSED) {
            parts.add("secondary " + phyName(result.getSecondaryPhy()));
        }
        if (result.getAdvertisingSid() != ScanResult.SID_NOT_PRESENT) {
            parts.add("SID " + result.getAdvertisingSid());
        }
        return String.join(" · ", parts);
    }

    private static Integer txPower(ScanResult result, ScanRecord record) {
        if (record != null && record.getTxPowerLevel() != Integer.MIN_VALUE) {
            return record.getTxPowerLevel();
        }
        return result.getTxPower() == ScanResult.TX_POWER_NOT_PRESENT ? null : result.getTxPower();
    }

    private static String manufacturerName(int id) {
        return switch (id) {
            case 0x0006 -> "Microsoft";
            case 0x004C -> "Apple";
            case 0x0059 -> "Nordic Semiconductor";
            case 0x0075 -> "Samsung";
            case 0x00E0 -> "Google";
            case 0x0131 -> "Google";
            default -> "Company";
        };
    }

    private static String serviceLabel(ParcelUuid parcelUuid) {
        String uuid = parcelUuid.getUuid().toString().toLowerCase(Locale.ROOT);
        if (uuid.startsWith("00001800")) return "Generic Access (1800)";
        if (uuid.startsWith("00001801")) return "Generic Attribute (1801)";
        if (uuid.startsWith("0000180a")) return "Device Information (180A)";
        if (uuid.startsWith("0000180d")) return "Heart Rate (180D)";
        if (uuid.startsWith("0000180f")) return "Battery (180F)";
        if (uuid.startsWith("0000fe2c")) return "Google Fast Pair (FE2C)";
        if (uuid.startsWith("0000feaa")) return "Eddystone (FEAA)";
        if (uuid.startsWith("0000fd6f")) return "Exposure Notification (FD6F)";
        if (uuid.startsWith("0000")) return uuid.substring(4, 8).toUpperCase(Locale.ROOT);
        return uuid.substring(0, 8) + "…";
    }

    private static String deviceType(int type) {
        return switch (type) {
            case BluetoothDevice.DEVICE_TYPE_CLASSIC -> "Classic";
            case BluetoothDevice.DEVICE_TYPE_DUAL -> "Dual-mode";
            case BluetoothDevice.DEVICE_TYPE_LE -> "BLE";
            default -> "Bluetooth";
        };
    }

    private static String phyName(int phy) {
        return switch (phy) {
            case BluetoothDevice.PHY_LE_1M -> "LE 1M PHY";
            case BluetoothDevice.PHY_LE_2M -> "LE 2M PHY";
            case BluetoothDevice.PHY_LE_CODED -> "LE Coded PHY";
            default -> "unknown PHY";
        };
    }

    private static String classify(ScanRecord record) {
        if (record == null) return "Unknown BLE";
        SparseArray<byte[]> manufacturers = record.getManufacturerSpecificData();
        if (manufacturers != null) {
            for (int i = 0; i < manufacturers.size(); i++) {
                int id = manufacturers.keyAt(i);
                if (id == 0x004C) return "Apple device";
                if (id == 0x00E0) return "Google device";
                if (id == 0x0006) return "Microsoft device";
                if (id == 0x0075) return "Samsung device";
            }
        }
        List<ParcelUuid> services = record.getServiceUuids();
        if (services != null) {
            for (ParcelUuid service : services) {
                String uuid = service.getUuid().toString().toLowerCase(Locale.ROOT);
                if (uuid.startsWith("0000180d")) return "Heart-rate device";
                if (uuid.startsWith("0000180f")) return "Battery device";
                if (uuid.startsWith("0000feaa")) return "Beacon";
            }
        }
        return "Unknown BLE";
    }

}
