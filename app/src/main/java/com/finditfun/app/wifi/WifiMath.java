package com.finditfun.app.wifi;

public final class WifiMath {
    private WifiMath() {
    }

    public static boolean isValidRssi(int rssi) {
        return rssi < 0 && rssi > -127;
    }

    public static double strengthFraction(int rssi) {
        if (rssi <= -100) return 0.0;
        if (rssi >= -40) return 1.0;
        return (rssi + 100.0) / 60.0;
    }

    public static String strengthLabel(int rssi) {
        if (!isValidRssi(rssi)) return "WAITING FOR SIGNAL";
        if (rssi >= -55) return "VERY STRONG";
        if (rssi >= -67) return "STRONG";
        if (rssi >= -75) return "USABLE";
        if (rssi >= -85) return "WEAK";
        return "VERY WEAK";
    }

    public static String bandLabel(int frequencyMhz) {
        if (frequencyMhz >= 2_400 && frequencyMhz < 2_500) return "2.4 GHz";
        if (frequencyMhz >= 5_000 && frequencyMhz < 5_925) return "5 GHz";
        if (frequencyMhz >= 5_925 && frequencyMhz < 7_125) return "6 GHz";
        if (frequencyMhz >= 57_000 && frequencyMhz < 71_000) return "60 GHz";
        return frequencyMhz > 0 ? frequencyMhz + " MHz" : "Unknown band";
    }

    public static int channelForFrequency(int frequencyMhz) {
        if (frequencyMhz == 2_484) return 14;
        if (frequencyMhz >= 2_412 && frequencyMhz <= 2_472) {
            return (frequencyMhz - 2_407) / 5;
        }
        if (frequencyMhz >= 5_000 && frequencyMhz < 5_925) {
            return (frequencyMhz - 5_000) / 5;
        }
        if (frequencyMhz >= 5_925 && frequencyMhz < 7_125) {
            return (frequencyMhz - 5_950) / 5;
        }
        return -1;
    }
}
