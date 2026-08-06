package com.finditfun.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import com.finditfun.app.bluetooth.BleDeviceSnapshot;
import com.finditfun.app.bluetooth.BleScanner;
import com.finditfun.app.devices.DeviceAliasStore;
import com.finditfun.app.feedback.HuntFeedback;
import com.finditfun.app.gnss.GnssController;
import com.finditfun.app.hunt.HuntMotionController;
import com.finditfun.app.hunt.HuntSurvey;
import com.finditfun.app.magnetic.MagneticController;
import com.finditfun.app.signal.SignalMath;
import com.finditfun.app.sound.AcousticAnalysis;
import com.finditfun.app.sound.AcousticPingController;
import com.finditfun.app.ui.EchoView;
import com.finditfun.app.ui.HuntMapView;
import com.finditfun.app.ui.MagneticGraphView;
import com.finditfun.app.ui.SkyPlotView;
import com.finditfun.app.ui.WifiMapView;
import com.finditfun.app.ui.WifiSpectrumView;
import com.finditfun.app.wifi.WifiAccessPoint;
import com.finditfun.app.wifi.WifiConnectionSnapshot;
import com.finditfun.app.wifi.WifiController;
import com.finditfun.app.wifi.WifiMath;
import com.finditfun.app.wifi.WifiSurvey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@SuppressLint("SetTextI18n")
public final class MainActivity extends Activity {
    private static final int REQUEST_BLUETOOTH = 10;
    private static final int REQUEST_LOCATION = 11;
    private static final int REQUEST_ACTIVITY = 12;
    private static final int REQUEST_WIFI = 13;
    private static final int REQUEST_AUDIO = 14;

    private static final int NIGHT = Color.rgb(8, 16, 24);
    private static final int PANEL = Color.rgb(16, 28, 41);
    private static final int INK = Color.rgb(244, 247, 251);
    private static final int MUTED = Color.rgb(145, 163, 178);
    private static final int CYAN = Color.rgb(72, 217, 232);
    private static final int LIME = Color.rgb(155, 229, 100);
    private static final int AMBER = Color.rgb(255, 202, 88);
    private static final int RED = Color.rgb(255, 102, 122);

    private enum Screen { NEARBY, HUNT, WIFI, SOUND, SPACE, MAGNETIC }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final DeviceAdapter deviceAdapter = new DeviceAdapter();
    private final ArrayList<BleDeviceSnapshot> nearbyDevices = new ArrayList<>();
    private List<GnssController.Satellite> satellites = Collections.emptyList();
    private List<WifiAccessPoint> wifiAccessPoints = Collections.emptyList();

    private FrameLayout content;
    private Button nearbyNav;
    private Button wifiNav;
    private Button soundNav;
    private Button spaceNav;
    private Button magneticNav;
    private Screen screen = Screen.NEARBY;
    private boolean foreground;

    private BleScanner bleScanner;
    private DeviceAliasStore aliasStore;
    private GnssController gnssController;
    private MagneticController magneticController;
    private WifiController wifiController;
    private AcousticPingController acousticPingController;
    private HuntMotionController motionController;
    private HuntFeedback feedback;
    private final HuntSurvey huntSurvey = new HuntSurvey();
    private final WifiSurvey wifiSurvey = new WifiSurvey();
    private String huntKey;
    private HuntMapView.Mode huntMapMode = HuntMapView.Mode.BEARING;
    private float huntHeading;
    private float huntX;
    private float huntY;
    private int huntSteps;
    private String huntMotionStatus = "Orientation starting…";
    private int lastSurveySampleCount = -1;
    private boolean soundEnabled = true;
    private boolean hapticsEnabled = true;
    private boolean wifiWalking = true;
    private String wifiStatus = "Preparing Wi-Fi…";
    private WifiConnectionSnapshot wifiConnection;
    private AcousticAnalysis.Result soundResult;
    private boolean soundContinuous;
    private boolean pendingSoundPing;
    private boolean pendingSoundContinuous;
    private String soundStatus = "Ready for an acoustic snapshot.";

    private String bleStatus = "Starting Bluetooth…";
    private TextView nearbyStatusView;
    private Button nearbyActionButton;
    private TextView huntNameView;
    private TextView huntMetaView;
    private TextView huntRssiView;
    private TextView huntTrendView;
    private TextView huntDetailsView;
    private HuntMapView huntMapView;
    private Button huntModeButton;
    private Button soundButton;
    private Button hapticsButton;
    private TextView spaceStatusView;
    private TextView spaceCountView;
    private TextView spaceDetailsView;
    private SkyPlotView skyPlotView;
    private TextView magneticStatusView;
    private TextView magneticValueView;
    private TextView magneticStateView;
    private TextView magneticAxesView;
    private MagneticGraphView magneticGraphView;
    private TextView wifiStatusView;
    private TextView wifiNetworkView;
    private TextView wifiRssiView;
    private TextView wifiStrengthView;
    private TextView wifiDetailsView;
    private TextView wifiNearbyView;
    private Button wifiWalkButton;
    private WifiMapView wifiMapView;
    private WifiSpectrumView wifiSpectrumView;
    private TextView soundStatusView;
    private TextView soundResultsView;
    private Button soundPingButton;
    private Button soundContinuousButton;
    private EchoView echoView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(NIGHT);
        getWindow().setNavigationBarColor(NIGHT);

        aliasStore = new DeviceAliasStore(this);
        bleScanner = new BleScanner(this, new BleScanner.Listener() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> {
                    bleStatus = status;
                    refreshNearby();
                });
            }

            @Override
            public void onDevicesChanged() {
                runOnUiThread(() -> {
                    refreshNearby();
                    refreshHunt();
                });
            }
        });
        gnssController = new GnssController(this, new GnssController.Listener() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> {
                    if (spaceStatusView != null) spaceStatusView.setText(status);
                });
            }

            @Override
            public void onSatellites(List<GnssController.Satellite> newSatellites) {
                runOnUiThread(() -> {
                    satellites = new ArrayList<>(newSatellites);
                    refreshSpace();
                });
            }
        });
        magneticController = new MagneticController(this, new MagneticController.Listener() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> {
                    if (magneticStatusView != null) magneticStatusView.setText(status);
                });
            }

            @Override
            public void onReading(float x, float y, float z, float magnitude) {
                runOnUiThread(() -> refreshMagnetic(x, y, z, magnitude));
            }
        });
        wifiController = new WifiController(this, new WifiController.Listener() {
            @Override
            public void onStatus(String status) {
                runOnUiThread(() -> {
                    wifiStatus = status;
                    refreshWifiUi();
                });
            }

            @Override
            public void onConnection(WifiConnectionSnapshot connection) {
                runOnUiThread(() -> {
                    wifiConnection = connection;
                    if (screen == Screen.WIFI && wifiWalking && connection != null) {
                        wifiSurvey.add(huntX, huntY, connection.rssi,
                                SystemClock.elapsedRealtime());
                    }
                    refreshWifiUi();
                });
            }

            @Override
            public void onAccessPoints(List<WifiAccessPoint> accessPoints) {
                runOnUiThread(() -> {
                    wifiAccessPoints = new ArrayList<>(accessPoints);
                    refreshWifiUi();
                });
            }
        });
        acousticPingController = new AcousticPingController(this,
                new AcousticPingController.Listener() {
                    @Override
                    public void onStatus(String status) {
                        runOnUiThread(() -> {
                            soundStatus = status;
                            if (status.startsWith("Sound ping failed")) {
                                soundContinuous = false;
                            }
                            refreshSoundUi();
                        });
                    }

                    @Override
                    public void onPingStarted() {
                        runOnUiThread(() -> {
                            soundStatus = "Ping sent · listening for reflections…";
                            if (echoView != null) echoView.startPing();
                            refreshSoundUi();
                        });
                    }

                    @Override
                    public void onResult(AcousticAnalysis.Result result) {
                        runOnUiThread(() -> {
                            soundResult = result;
                            if (!result.playbackCompleted()) {
                                soundStatus = "Speaker playback was incomplete · retrying is safe.";
                            } else if (result.chirpDetected) {
                                soundStatus = "Room response captured locally.";
                            } else {
                                soundStatus = "No clean chirp · next ping will try another mic path.";
                            }
                            refreshSoundUi();
                            if (soundContinuous && foreground && screen == Screen.SOUND) {
                                handler.removeCallbacks(soundLoop);
                                handler.postDelayed(soundLoop, 900);
                            }
                        });
                    }
                });
        motionController = new HuntMotionController(this,
                (headingDegrees, xSteps, ySteps, steps, status) -> runOnUiThread(() -> {
                    huntHeading = headingDegrees;
                    huntX = xSteps;
                    huntY = ySteps;
                    huntSteps = steps;
                    huntMotionStatus = status;
                    refreshHuntMap();
                    refreshWifiMap();
                }));

        buildShell();
        showScreen(Screen.NEARBY);
        if (!hasBluetoothPermissions()) requestBluetoothPermissions();
    }

    @Override
    protected void onStart() {
        super.onStart();
        foreground = true;
        startCurrentSource();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onStop() {
        foreground = false;
        handler.removeCallbacks(ticker);
        stopSources();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        closeFeedback();
        if (acousticPingController != null) acousticPingController.close();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (screen == Screen.HUNT) {
            showScreen(Screen.NEARBY);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH || requestCode == REQUEST_LOCATION
                || requestCode == REQUEST_ACTIVITY || requestCode == REQUEST_WIFI
                || requestCode == REQUEST_AUDIO) {
            stopSources();
            startCurrentSource();
            refreshNearby();
            refreshWifiUi();
            refreshSoundUi();
        }
        if (requestCode == REQUEST_AUDIO) {
            boolean runContinuous = pendingSoundContinuous;
            boolean runOnce = pendingSoundPing;
            pendingSoundContinuous = false;
            pendingSoundPing = false;
            if (hasAudioPermission() && screen == Screen.SOUND) {
                if (runContinuous) {
                    soundContinuous = true;
                    startSoundPing();
                } else if (runOnce) {
                    startSoundPing();
                }
            } else {
                soundStatus = "Microphone permission was not granted.";
                refreshSoundUi();
            }
        }
    }

    private void buildShell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(NIGHT);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout nav = new LinearLayout(this);
        nav.setPadding(dp(8), dp(8), dp(8), dp(10));
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.rgb(11, 21, 31));
        nearbyNav = navButton("Nearby", () -> showScreen(Screen.NEARBY));
        wifiNav = navButton("Wi-Fi", () -> showScreen(Screen.WIFI));
        soundNav = navButton("Sound", () -> showScreen(Screen.SOUND));
        spaceNav = navButton("Space", () -> showScreen(Screen.SPACE));
        magneticNav = navButton("Magnet", () -> showScreen(Screen.MAGNETIC));
        nav.addView(nearbyNav, weightedNavParams());
        nav.addView(wifiNav, weightedNavParams());
        nav.addView(soundNav, weightedNavParams());
        nav.addView(spaceNav, weightedNavParams());
        nav.addView(magneticNav, weightedNavParams());
        root.addView(nav, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        setContentView(root);
    }

    private void showScreen(Screen target) {
        stopSources();
        screen = target;
        clearViewReferences();
        content.removeAllViews();
        if (target == Screen.NEARBY) buildNearby();
        if (target == Screen.HUNT) buildHunt();
        if (target == Screen.WIFI) buildWifi();
        if (target == Screen.SOUND) buildSound();
        if (target == Screen.SPACE) buildSpace();
        if (target == Screen.MAGNETIC) buildMagnetic();
        updateNavigation();
        if (foreground) startCurrentSource();
    }

    private void clearViewReferences() {
        nearbyStatusView = null;
        nearbyActionButton = null;
        huntNameView = null;
        huntMetaView = null;
        huntRssiView = null;
        huntTrendView = null;
        huntDetailsView = null;
        huntMapView = null;
        huntModeButton = null;
        soundButton = null;
        hapticsButton = null;
        spaceStatusView = null;
        spaceCountView = null;
        spaceDetailsView = null;
        skyPlotView = null;
        magneticStatusView = null;
        magneticValueView = null;
        magneticStateView = null;
        magneticAxesView = null;
        magneticGraphView = null;
        wifiStatusView = null;
        wifiNetworkView = null;
        wifiRssiView = null;
        wifiStrengthView = null;
        wifiDetailsView = null;
        wifiNearbyView = null;
        wifiWalkButton = null;
        wifiMapView = null;
        wifiSpectrumView = null;
        soundStatusView = null;
        soundResultsView = null;
        soundPingButton = null;
        soundContinuousButton = null;
        echoView = null;
    }

    private void buildNearby() {
        LinearLayout page = verticalPage();
        page.addView(label("FIND IT FUN", 12, CYAN, Typeface.BOLD));
        page.addView(label("What’s broadcasting nearby?", 28, INK, Typeface.BOLD));
        page.addView(label("BLE devices appear only while they advertise. Signal strength is a clue, not a direction or exact distance.",
                14, MUTED, Typeface.NORMAL));
        page.addView(label("Tap to hunt · hold a row to name it or inspect its broadcast.",
                13, CYAN, Typeface.BOLD));

        nearbyStatusView = label(bleStatus, 15, INK, Typeface.BOLD);
        nearbyStatusView.setPadding(0, dp(14), 0, dp(6));
        page.addView(nearbyStatusView);

        nearbyActionButton = actionButton("Retry scan", () -> {
            if (!hasBluetoothPermissions()) requestBluetoothPermissions();
            else {
                bleScanner.stop();
                bleScanner.start();
            }
        });
        page.addView(nearbyActionButton,
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        ListView list = new ListView(this);
        list.setAdapter(deviceAdapter);
        list.setDividerHeight(dp(8));
        list.setDivider(null);
        list.setPadding(0, dp(12), 0, 0);
        list.setClipToPadding(false);
        list.setOnItemClickListener((parent, view, position, id) -> {
            BleDeviceSnapshot selected = nearbyDevices.get(position);
            huntKey = selected.key;
            resetHuntSurvey();
            showScreen(Screen.HUNT);
        });
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showDeviceDialog(nearbyDevices.get(position));
            return true;
        });
        TextView empty = label("Listening… nearby advertisements will collect here.",
                16, MUTED, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        list.setEmptyView(empty);
        FrameLayout listPane = new FrameLayout(this);
        listPane.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        listPane.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        page.addView(listPane, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(page);
        refreshNearby();
    }

    private void buildHunt() {
        LinearLayout page = verticalPage();
        Button back = actionButton("‹  Back to signals", () -> showScreen(Screen.NEARBY));
        page.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        huntNameView = label("Selected signal", 24, INK, Typeface.BOLD);
        huntNameView.setGravity(Gravity.CENTER);
        huntNameView.setPadding(0, dp(10), 0, 0);
        page.addView(huntNameView);
        huntMetaView = label("Observed Bluetooth metadata", 13, MUTED, Typeface.NORMAL);
        huntMetaView.setGravity(Gravity.CENTER);
        page.addView(huntMetaView);

        Button deviceInfo = actionButton("Name / device info", () -> {
            BleDeviceSnapshot item = huntKey == null ? null
                    : bleScanner.snapshot(huntKey, SystemClock.elapsedRealtime());
            if (item != null) showDeviceDialog(item);
        });
        page.addView(deviceInfo, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42)));

        huntMapView = new HuntMapView(this);
        huntMapView.setMode(huntMapMode);
        huntMapView.setBackground(rounded(Color.rgb(10, 24, 35), 16));
        LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        mapParams.setMargins(0, dp(8), 0, dp(6));
        page.addView(huntMapView, mapParams);

        huntRssiView = label("— dBm", 38, INK, Typeface.BOLD);
        huntRssiView.setGravity(Gravity.CENTER);
        page.addView(huntRssiView);
        huntTrendView = label("WAITING FOR SIGNAL", 17, CYAN, Typeface.BOLD);
        huntTrendView.setGravity(Gravity.CENTER);
        page.addView(huntTrendView);
        huntDetailsView = label("Walk slowly and watch for a sustained change.",
                13, MUTED, Typeface.NORMAL);
        huntDetailsView.setGravity(Gravity.CENTER);
        huntDetailsView.setPadding(0, dp(3), 0, dp(7));
        page.addView(huntDetailsView);

        LinearLayout surveyControls = new LinearLayout(this);
        huntModeButton = actionButton("", () -> {
            huntMapMode = huntMapMode == HuntMapView.Mode.BEARING
                    ? HuntMapView.Mode.TRAIL : HuntMapView.Mode.BEARING;
            if (huntMapMode == HuntMapView.Mode.TRAIL && !hasActivityPermission()) {
                requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                        REQUEST_ACTIVITY);
            }
            updateHuntModeButton();
            refreshHuntMap();
        });
        Button reset = actionButton("Reset survey", this::resetHuntSurvey);
        surveyControls.addView(huntModeButton, weightedNavParams());
        surveyControls.addView(reset, weightedNavParams());
        page.addView(surveyControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        LinearLayout toggles = new LinearLayout(this);
        soundButton = actionButton("", () -> {
            soundEnabled = !soundEnabled;
            if (feedback != null) feedback.setSoundEnabled(soundEnabled);
            updateFeedbackButtons();
        });
        hapticsButton = actionButton("", () -> {
            hapticsEnabled = !hapticsEnabled;
            if (feedback != null) feedback.setHapticsEnabled(hapticsEnabled);
            updateFeedbackButtons();
        });
        toggles.addView(soundButton, weightedNavParams());
        toggles.addView(hapticsButton, weightedNavParams());
        page.addView(toggles, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(page);
        updateHuntModeButton();
        updateFeedbackButtons();
        refreshHunt();
    }

    private void buildWifi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = verticalPage();
        page.addView(label("WI-FI WALK", 12, CYAN, Typeface.BOLD));
        page.addView(label("Paint the room with radio", 28, INK, Typeface.BOLD));
        page.addView(label("Walk slowly with the phone held consistently. The connected network updates live; full nearby scans are throttled by Android.",
                14, MUTED, Typeface.NORMAL));

        wifiStatusView = label(wifiStatus, 14, INK, Typeface.BOLD);
        wifiStatusView.setPadding(0, dp(12), 0, dp(7));
        page.addView(wifiStatusView);

        Button scanButton = actionButton("Scan nearby Wi-Fi", () -> {
            if (!hasWifiPermissions()) requestWifiPermissions();
            else if (wifiController.isRunning()) wifiController.requestScan();
            else wifiController.start();
        });
        page.addView(scanButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        wifiNetworkView = label("Not connected to Wi-Fi", 17, MUTED, Typeface.BOLD);
        wifiNetworkView.setGravity(Gravity.CENTER);
        wifiNetworkView.setPadding(0, dp(12), 0, 0);
        page.addView(wifiNetworkView);
        wifiRssiView = label("— dBm", 44, INK, Typeface.BOLD);
        wifiRssiView.setGravity(Gravity.CENTER);
        page.addView(wifiRssiView);
        wifiStrengthView = label("WAITING FOR SIGNAL", 17, CYAN, Typeface.BOLD);
        wifiStrengthView.setGravity(Gravity.CENTER);
        page.addView(wifiStrengthView);
        wifiDetailsView = label("Connect to a network to paint its live signal.",
                13, MUTED, Typeface.NORMAL);
        wifiDetailsView.setGravity(Gravity.CENTER);
        wifiDetailsView.setPadding(0, dp(3), 0, dp(7));
        page.addView(wifiDetailsView);

        wifiMapView = new WifiMapView(this);
        wifiMapView.setBackground(rounded(Color.rgb(10, 24, 35), 16));
        page.addView(wifiMapView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));

        LinearLayout walkControls = new LinearLayout(this);
        wifiWalkButton = actionButton("", () -> {
            if (!hasActivityPermission()) {
                requestPermissions(new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                        REQUEST_ACTIVITY);
                return;
            }
            wifiWalking = !wifiWalking;
            refreshWifiUi();
        });
        Button reset = actionButton("Reset map", this::resetWifiSurvey);
        walkControls.addView(wifiWalkButton, weightedNavParams());
        walkControls.addView(reset, weightedNavParams());
        page.addView(walkControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        TextView spectrumTitle = label("NEARBY RADIO LANES", 12, CYAN, Typeface.BOLD);
        spectrumTitle.setPadding(0, dp(18), 0, dp(4));
        page.addView(spectrumTitle);
        page.addView(label("Bubble position is frequency; size is signal strength. A white ring marks the connected access point.",
                13, MUTED, Typeface.NORMAL));
        wifiSpectrumView = new WifiSpectrumView(this);
        wifiSpectrumView.setBackground(rounded(Color.rgb(10, 24, 35), 16));
        LinearLayout.LayoutParams spectrumParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(240));
        spectrumParams.setMargins(0, dp(8), 0, dp(8));
        page.addView(wifiSpectrumView, spectrumParams);

        wifiNearbyView = label("No nearby scan results yet.", 13, MUTED, Typeface.NORMAL);
        wifiNearbyView.setPadding(0, 0, 0, dp(22));
        page.addView(wifiNearbyView);
        scroll.addView(page);
        content.addView(scroll);
        refreshWifiUi();
    }

    private void buildSound() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = verticalPage();
        page.addView(label("SOUND PING", 12, CYAN, Typeface.BOLD));
        page.addView(label("Listen to the shape of echoes", 28, INK, Typeface.BOLD));
        page.addView(label("The Pixel emits a short audible chirp, records about a third of a second, and compares the room response with the original sound. Echo rings estimate distance—not direction or object identity.",
                14, MUTED, Typeface.NORMAL));

        soundStatusView = label(soundStatus, 14, INK, Typeface.BOLD);
        soundStatusView.setPadding(0, dp(13), 0, dp(8));
        page.addView(soundStatusView);

        echoView = new EchoView(this);
        echoView.setBackground(rounded(Color.rgb(10, 24, 35), 16));
        if (soundResult != null) echoView.setResult(soundResult);
        page.addView(echoView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(390)));

        LinearLayout controls = new LinearLayout(this);
        soundPingButton = actionButton("", this::startSoundPing);
        soundContinuousButton = actionButton("", this::toggleSoundContinuous);
        controls.addView(soundPingButton, weightedNavParams());
        controls.addView(soundContinuousButton, weightedNavParams());
        LinearLayout.LayoutParams controlParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        controlParams.setMargins(0, dp(8), 0, 0);
        page.addView(controls, controlParams);

        soundResultsView = label("No acoustic snapshot yet.", 14, INK, Typeface.NORMAL);
        soundResultsView.setPadding(0, dp(14), 0, dp(8));
        page.addView(soundResultsView);
        page.addView(label("For the cleanest experiment: disconnect earbuds, set media volume near 50%, keep the bottom speaker uncovered, hold still, and avoid talking during the ping. Raw microphone samples remain only in memory and are discarded after analysis.",
                13, MUTED, Typeface.NORMAL));
        page.addView(label("A full ring means ‘an echo may be this far away somewhere around the phone.’ Multiple walls and reflections can produce false or merged rings.",
                13, AMBER, Typeface.BOLD));

        scroll.addView(page);
        content.addView(scroll);
        refreshSoundUi();
    }

    private void buildSpace() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = verticalPage();
        page.addView(label("SPACE MODE", 12, CYAN, Typeface.BOLD));
        page.addView(label("Signals from orbit", 28, INK, Typeface.BOLD));
        page.addView(label("Your phone’s GNSS receiver can hear navigation satellites. This is real space radio data—not astronomy or alien detection.",
                14, MUTED, Typeface.NORMAL));
        spaceStatusView = label("Preparing GNSS…", 15, INK, Typeface.BOLD);
        spaceStatusView.setPadding(0, dp(14), 0, dp(6));
        page.addView(spaceStatusView);
        Button permission = actionButton("Grant precise location / Retry", () -> {
            if (!hasLocationPermission()) {
                requestPermissions(new String[]{
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        },
                        REQUEST_LOCATION);
            } else {
                gnssController.stop();
                gnssController.start();
            }
        });
        page.addView(permission, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        skyPlotView = new SkyPlotView(this);
        page.addView(skyPlotView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(330)));
        spaceCountView = label("0 satellites", 22, INK, Typeface.BOLD);
        spaceCountView.setGravity(Gravity.CENTER);
        page.addView(spaceCountView);
        spaceDetailsView = label("Move near a window or outdoors for the best view.",
                14, MUTED, Typeface.NORMAL);
        spaceDetailsView.setPadding(0, dp(8), 0, dp(24));
        page.addView(spaceDetailsView);
        scroll.addView(page);
        content.addView(scroll);
        refreshSpace();
    }

    private void buildMagnetic() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout page = verticalPage();
        page.addView(label("MAGNETIC MODE", 12, AMBER, Typeface.BOLD));
        page.addView(label("Invisible field meter", 28, INK, Typeface.BOLD));
        page.addView(label("Wave the phone near magnets, speakers, motors, and metal. Readings show field strength, not safety or paranormal activity.",
                14, MUTED, Typeface.NORMAL));
        magneticStatusView = label("Preparing sensor…", 15, INK, Typeface.BOLD);
        magneticStatusView.setPadding(0, dp(14), 0, dp(6));
        page.addView(magneticStatusView);
        magneticValueView = label("— µT", 48, INK, Typeface.BOLD);
        magneticValueView.setGravity(Gravity.CENTER);
        magneticValueView.setPadding(0, dp(12), 0, 0);
        page.addView(magneticValueView);
        magneticStateView = label("WAITING FOR FIELD", 18, AMBER, Typeface.BOLD);
        magneticStateView.setGravity(Gravity.CENTER);
        page.addView(magneticStateView);
        magneticAxesView = label("X —   Y —   Z —", 14, MUTED, Typeface.NORMAL);
        magneticAxesView.setGravity(Gravity.CENTER);
        magneticAxesView.setPadding(0, dp(6), 0, dp(16));
        page.addView(magneticAxesView);
        magneticGraphView = new MagneticGraphView(this);
        magneticGraphView.setBackground(rounded(Color.rgb(10, 24, 35), 16));
        page.addView(magneticGraphView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
        page.addView(label("Typical background varies with location and calibration. Rotate the phone and use relative changes for the fun part.",
                14, MUTED, Typeface.NORMAL));
        scroll.addView(page);
        content.addView(scroll);
    }

    private void startCurrentSource() {
        if (!foreground) return;
        if (screen == Screen.NEARBY || screen == Screen.HUNT) {
            if (hasBluetoothPermissions()) bleScanner.start();
            else bleStatus = "Nearby Devices permission is required.";
        }
        if (screen == Screen.HUNT) {
            motionController.start();
            if (feedback == null) feedback = new HuntFeedback(this);
            feedback.setSoundEnabled(soundEnabled);
            feedback.setHapticsEnabled(hapticsEnabled);
            feedback.start();
        }
        if (screen == Screen.WIFI) {
            motionController.start();
            if (hasWifiPermissions()) {
                wifiController.start();
            } else {
                wifiStatus = "Precise location and Nearby Wi-Fi permission unlock the map.";
                refreshWifiUi();
            }
        }
        if (screen == Screen.SPACE) {
            if (hasLocationPermission()) gnssController.start();
            else if (spaceStatusView != null) {
                spaceStatusView.setText("Precise location permission unlocks satellite data.");
            }
        }
        if (screen == Screen.MAGNETIC) magneticController.start();
    }

    private void stopSources() {
        if (bleScanner != null) bleScanner.stop();
        if (wifiController != null) wifiController.stop();
        handler.removeCallbacks(soundLoop);
        soundContinuous = false;
        if (acousticPingController != null) acousticPingController.cancel();
        if (gnssController != null) gnssController.stop();
        if (magneticController != null) magneticController.stop();
        if (motionController != null) motionController.stop();
        closeFeedback();
    }

    private void closeFeedback() {
        if (feedback != null) {
            feedback.close();
            feedback = null;
        }
    }

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (!foreground) return;
            refreshNearby();
            refreshHunt();
            if (screen == Screen.WIFI && wifiController != null) {
                wifiController.refreshConnection();
            }
            handler.postDelayed(this, 500);
        }
    };

    private final Runnable soundLoop = new Runnable() {
        @Override
        public void run() {
            if (foreground && screen == Screen.SOUND && soundContinuous) {
                startSoundPing();
            }
        }
    };

    private void refreshNearby() {
        if (bleScanner == null) return;
        nearbyDevices.clear();
        nearbyDevices.addAll(bleScanner.snapshots(SystemClock.elapsedRealtime()));
        nearbyDevices.sort((left, right) -> {
            int savedOrder = Boolean.compare(aliasStore.isSaved(right.key),
                    aliasStore.isSaved(left.key));
            return savedOrder != 0 ? savedOrder
                    : Integer.compare(right.signal.smoothedRssi, left.signal.smoothedRssi);
        });
        deviceAdapter.notifyDataSetChanged();
        if (nearbyStatusView != null) {
            int live = 0;
            int pairedIdle = 0;
            for (BleDeviceSnapshot item : nearbyDevices) {
                if (item.signal.sampleCount > 0) live++;
                else if (item.paired) pairedIdle++;
            }
            String count = live == 1 ? "1 live signal" : live + " live signals";
            if (pairedIdle > 0) count += "  ·  " + pairedIdle + " paired idle";
            nearbyStatusView.setText(count + "  ·  " + bleStatus);
        }
        if (nearbyActionButton != null) {
            nearbyActionButton.setText(hasBluetoothPermissions()
                    ? "Retry scan" : "Grant Nearby Devices permission");
        }
    }

    private void refreshHunt() {
        if (huntRssiView == null || bleScanner == null) return;
        BleDeviceSnapshot item = huntKey == null ? null
                : bleScanner.snapshot(huntKey, SystemClock.elapsedRealtime());
        if (item == null) {
            huntNameView.setText("Signal not seen yet");
            huntMetaView.setText("The device may have stopped advertising");
            huntRssiView.setText("— dBm");
            huntTrendView.setText("KEEP MOVING SLOWLY");
            huntTrendView.setTextColor(RED);
            huntDetailsView.setText("The device may have stopped advertising. Return to Nearby to choose again.");
            refreshHuntMap();
            if (feedback != null) feedback.update(null);
            return;
        }

        huntNameView.setText(displayName(item));
        String observed = item.observedName == null ? "No advertised name" : item.observedName;
        huntMetaView.setText((aliasStore.isSaved(item.key) ? "★ MY DEVICE  ·  " : "")
                + observed + "  ·  " + item.manufacturer);
        if (item.signal.sampleCount == 0) {
            huntRssiView.setText("— dBm");
            huntTrendView.setText("PAIRED · NOT HEARD RIGHT NOW");
            huntTrendView.setTextColor(MUTED);
            String limitation = item.radio.startsWith("Classic")
                    ? "Classic-only Bluetooth exposes no trackable RSSI to ordinary Android apps."
                    : "The device is remembered, but it must advertise before Hunt can measure it.";
            huntDetailsView.setText(limitation);
            refreshHuntMap();
            if (feedback != null) feedback.update(null);
            return;
        }
        boolean fresh = item.signal.fresh;
        int rssi = item.signal.liveRssi;
        if (fresh && item.signal.sampleCount != lastSurveySampleCount) {
            huntSurvey.add(huntHeading, huntX, huntY, rssi);
            lastSurveySampleCount = item.signal.sampleCount;
        }
        huntRssiView.setText(fresh ? rssi + " dBm" : "— dBm");
        String trend = trendLabel(item.signal.trend);
        String label = fresh ? SignalMath.proximity(rssi) + "  ·  " + trend : "SIGNAL LOST";
        huntTrendView.setText(label);
        huntTrendView.setTextColor(fresh ? colorForRssi(rssi) : RED);
        HuntSurvey.Snapshot survey = huntSurvey.snapshot();
        String surveyDetails = huntMapMode == HuntMapView.Mode.BEARING
                ? survey.totalSamples + " points  ·  " + survey.coveredSectors
                        + "/" + HuntSurvey.SECTOR_COUNT + " sectors  ·  "
                        + survey.readinessPercent + "% direction readiness"
                : huntSteps + " steps  ·  " + survey.totalSamples + " points  ·  "
                        + huntMotionStatus;
        huntDetailsView.setText("Best " + item.signal.peakRssi + " dBm  ·  "
                + ageLabel(item.signal.ageMillis) + "\n" + surveyDetails);
        refreshHuntMap();
        if (feedback != null) feedback.update(fresh ? rssi : null);
    }

    private void refreshSpace() {
        if (skyPlotView == null) return;
        skyPlotView.setSatellites(satellites);
        int used = 0;
        for (GnssController.Satellite satellite : satellites) {
            if (satellite.usedInFix) used++;
        }
        spaceCountView.setText(satellites.size() + " visible  ·  " + used + " used in fix");
        if (satellites.isEmpty()) {
            spaceDetailsView.setText("No satellites reported yet. Move near a window or outdoors.");
            return;
        }
        StringBuilder details = new StringBuilder();
        int limit = Math.min(8, satellites.size());
        for (int i = 0; i < limit; i++) {
            GnssController.Satellite satellite = satellites.get(i);
            if (i > 0) details.append('\n');
            details.append(satellite.constellation).append(' ').append(satellite.svid)
                    .append("  ·  ").append(Math.round(satellite.cn0)).append(" dB-Hz")
                    .append("  ·  ").append(Math.round(satellite.elevation)).append("° high");
            if (satellite.carrierMhz != null) {
                details.append("  ·  ").append(String.format(Locale.US, "%.1f MHz", satellite.carrierMhz));
            }
        }
        spaceDetailsView.setText(details.toString());
    }

    private void refreshMagnetic(float x, float y, float z, float magnitude) {
        if (magneticValueView == null) return;
        magneticValueView.setText(String.format(Locale.US, "%.1f µT", magnitude));
        String state;
        int color;
        if (magnitude < 20f) {
            state = "LOW / ORIENTATION-SENSITIVE";
            color = CYAN;
        } else if (magnitude <= 70f) {
            state = "EARTH-SCALE BACKGROUND";
            color = LIME;
        } else if (magnitude <= 200f) {
            state = "ELEVATED NEARBY FIELD";
            color = AMBER;
        } else {
            state = "STRONG NEARBY FIELD";
            color = RED;
        }
        magneticStateView.setText(state);
        magneticStateView.setTextColor(color);
        magneticAxesView.setText(String.format(Locale.US,
                "X %.1f   Y %.1f   Z %.1f µT", x, y, z));
        magneticGraphView.addReading(magnitude);
    }

    private void updateFeedbackButtons() {
        if (soundButton != null) soundButton.setText(soundEnabled ? "Sound on" : "Sound off");
        if (hapticsButton != null) hapticsButton.setText(hapticsEnabled ? "Haptics on" : "Haptics off");
    }

    private void updateHuntModeButton() {
        if (huntModeButton != null) {
            huntModeButton.setText(huntMapMode == HuntMapView.Mode.BEARING
                    ? "View: Compass" : "View: Trail");
        }
        if (huntMapView != null) huntMapView.setMode(huntMapMode);
    }

    private void refreshHuntMap() {
        if (huntMapView != null) {
            huntMapView.setSurvey(huntSurvey.snapshot(), huntHeading, huntX, huntY);
        }
    }

    private void refreshWifiUi() {
        if (wifiStatusView != null) wifiStatusView.setText(wifiStatus);
        if (wifiWalkButton != null) {
            wifiWalkButton.setText(!hasActivityPermission() ? "Allow steps"
                    : wifiWalking ? "Pause walk" : "Resume walk");
        }

        WifiSurvey.Snapshot survey = wifiSurvey.snapshot();
        if (wifiConnection == null) {
            if (wifiNetworkView != null) wifiNetworkView.setText("Not connected to Wi-Fi");
            if (wifiRssiView != null) wifiRssiView.setText("— dBm");
            if (wifiStrengthView != null) {
                wifiStrengthView.setText("WAITING FOR SIGNAL");
                wifiStrengthView.setTextColor(MUTED);
            }
            if (wifiDetailsView != null) {
                wifiDetailsView.setText("Connect to a Wi-Fi network, then walk around to paint its relative signal.\n"
                        + huntMotionStatus);
            }
        } else {
            int rssi = wifiConnection.rssi;
            if (wifiNetworkView != null) wifiNetworkView.setText(wifiConnection.ssid);
            if (wifiRssiView != null) wifiRssiView.setText(rssi + " dBm");
            if (wifiStrengthView != null) {
                wifiStrengthView.setText(WifiMath.strengthLabel(rssi));
                wifiStrengthView.setTextColor(colorForRssi(rssi));
            }
            if (wifiDetailsView != null) {
                int channel = WifiMath.channelForFrequency(wifiConnection.frequencyMhz);
                String channelText = channel > 0 ? " · channel " + channel : "";
                String mloText = wifiConnection.mloLinkCount > 0
                        ? " · " + wifiConnection.mloLinkCount + " MLO links" : "";
                String points = survey.samples.size() == 1 ? "1 point"
                        : survey.samples.size() + " points";
                String best = survey.samples.isEmpty() ? "—"
                        : survey.bestRssi + " dBm best · " + survey.averageRssi + " dBm average";
                wifiDetailsView.setText(WifiMath.bandLabel(wifiConnection.frequencyMhz)
                        + channelText + " · " + wifiConnection.frequencyMhz + " MHz\n"
                        + wifiStandardLabel(wifiConnection.wifiStandard)
                        + " · Rx " + wifiConnection.rxMbps + " / Tx "
                        + wifiConnection.txMbps + " Mbps" + mloText + "\n"
                        + huntSteps + " steps · " + points + " · " + best + "\n"
                        + (wifiWalking ? huntMotionStatus : "Walk capture paused"));
            }
        }

        if (wifiSpectrumView != null) {
            wifiSpectrumView.setAccessPoints(wifiAccessPoints,
                    wifiConnection == null ? null : wifiConnection.bssid);
        }
        if (wifiNearbyView != null) {
            if (wifiAccessPoints.isEmpty()) {
                wifiNearbyView.setText("No nearby scan results yet. Connected Wi-Fi still updates live.");
            } else {
                StringBuilder text = new StringBuilder();
                int rttCount = 0;
                for (WifiAccessPoint item : wifiAccessPoints) {
                    if (item.rttCapable) rttCount++;
                }
                text.append(wifiAccessPoints.size()).append(" access points")
                        .append(" · ").append(rttCount).append(" RTT-capable");
                int limit = Math.min(8, wifiAccessPoints.size());
                for (int i = 0; i < limit; i++) {
                    WifiAccessPoint item = wifiAccessPoints.get(i);
                    text.append('\n').append(item.ssid)
                            .append(" · ").append(item.rssi).append(" dBm · ")
                            .append(WifiMath.bandLabel(item.frequencyMhz));
                    if (item.channel > 0) text.append(" ch ").append(item.channel);
                    if (item.rttCapable) text.append(" · RTT");
                }
                wifiNearbyView.setText(text.toString());
            }
        }
        refreshWifiMap();
    }

    private void refreshWifiMap() {
        if (wifiMapView != null) {
            wifiMapView.setSurvey(wifiSurvey.snapshot(), huntHeading, huntX, huntY);
        }
    }

    private void resetWifiSurvey() {
        wifiSurvey.reset();
        wifiWalking = true;
        if (motionController != null) motionController.reset();
        refreshWifiUi();
    }

    private void startSoundPing() {
        if (!hasAudioPermission()) {
            pendingSoundPing = true;
            pendingSoundContinuous = false;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO);
            return;
        }
        handler.removeCallbacks(soundLoop);
        acousticPingController.ping();
        refreshSoundUi();
    }

    private void toggleSoundContinuous() {
        if (soundContinuous) {
            soundContinuous = false;
            handler.removeCallbacks(soundLoop);
            acousticPingController.cancel();
            soundStatus = "Continuous ping stopped.";
            refreshSoundUi();
            return;
        }
        if (!hasAudioPermission()) {
            pendingSoundContinuous = true;
            pendingSoundPing = false;
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_AUDIO);
            return;
        }
        soundContinuous = true;
        startSoundPing();
    }

    private void refreshSoundUi() {
        if (soundStatusView != null) soundStatusView.setText(soundStatus);
        boolean busy = acousticPingController != null && acousticPingController.isBusy();
        if (soundPingButton != null) {
            soundPingButton.setText(hasAudioPermission() ? "Ping once" : "Allow mic + ping");
            soundPingButton.setEnabled(!busy);
        }
        if (soundContinuousButton != null) {
            String text;
            if (soundContinuous) text = "Stop continuous";
            else text = hasAudioPermission() ? "Continuous" : "Allow mic + loop";
            soundContinuousButton.setText(text);
        }
        if (echoView != null && soundResult != null) echoView.setResult(soundResult);
        if (soundResultsView == null) return;
        if (soundResult == null) {
            soundResultsView.setText("No acoustic snapshot yet. One ping will show up to six separated echo peaks within an experimental 0.45–8 meter window.");
            return;
        }
        String diagnostics = "Mic peak "
                + Math.round(soundResult.capturedPeakFraction * 100) + "% · RMS "
                + Math.round(soundResult.capturedRmsFraction * 100) + "% · chirp match "
                + Math.round(soundResult.directLevel * 100) + "%\nOutput "
                + soundResult.outputSampleRate / 1_000f + " kHz · "
                + soundResult.outputChannels + (soundResult.outputChannels == 1
                        ? " channel" : " channels")
                + " · played " + soundResult.playedFrames + "/"
                + soundResult.expectedFrames + " frames · "
                + soundResult.underrunCount + " underruns · threshold "
                + soundResult.startThresholdFrames + "\nInput: "
                + soundResult.captureSource;
        if (!soundResult.playbackCompleted()) {
            soundResultsView.setText("Android did not consume the complete chirp buffer. The echo result is not usable.\n"
                    + diagnostics);
            return;
        }
        if (!soundResult.chirpDetected) {
            String hint = soundResult.capturedPeakFraction < 0.005f
                    ? "The capture was nearly silent. Close other microphone apps and uncover the microphone openings."
                    : "The microphone heard audio, but it did not match the emitted sweep strongly enough.";
            soundResultsView.setText(hint
                    + " The next ping will automatically try another microphone path.\n"
                    + diagnostics);
            return;
        }
        if (soundResult.peaks.isEmpty()) {
            soundResultsView.setText("The chirp was captured, but no clearly separated echo peaks passed the detector. Try a larger room or change the phone position.\n"
                    + diagnostics);
            return;
        }
        StringBuilder text = new StringBuilder("Strong separated echo rings:");
        for (AcousticAnalysis.EchoPeak peak : soundResult.peaks) {
            text.append("\n")
                    .append(String.format(Locale.US, "%.2f m", peak.distanceMeters))
                    .append(" · relative strength ")
                    .append(Math.round(peak.strength * 100)).append('%');
        }
        text.append("\n\n").append(diagnostics)
                .append("\nDistances are experimental round-trip delays, not identified walls or objects.");
        soundResultsView.setText(text.toString());
    }

    private static String wifiStandardLabel(int standard) {
        return switch (standard) {
            case 1 -> "Wi-Fi legacy";
            case 4 -> "Wi-Fi 4 (802.11n)";
            case 5 -> "Wi-Fi 5 (802.11ac)";
            case 6 -> "Wi-Fi 6/6E (802.11ax)";
            case 7 -> "WiGig (802.11ad)";
            case 8 -> "Wi-Fi 7 (802.11be)";
            default -> "Wi-Fi standard unknown";
        };
    }

    private void resetHuntSurvey() {
        huntSurvey.reset();
        lastSurveySampleCount = -1;
        if (motionController != null) motionController.reset();
        refreshHuntMap();
        refreshHunt();
    }

    private void showDeviceDialog(BleDeviceSnapshot item) {
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(4), dp(22), 0);

        EditText alias = new EditText(this);
        alias.setSingleLine(true);
        alias.setHint("e.g. My earbuds");
        String saved = aliasStore.aliasFor(item.key);
        if (saved != null) alias.setText(saved);
        body.addView(alias, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView details = label(deviceMetadata(item), 13, Color.rgb(55, 65, 75),
                Typeface.NORMAL);
        details.setPadding(0, dp(12), 0, dp(4));
        body.addView(details);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("My Device · #" + item.anonymousId)
                .setView(body)
                .setPositiveButton("Save name", (dialog, which) -> {
                    aliasStore.save(item.key, alias.getText().toString());
                    refreshNearby();
                    refreshHunt();
                })
                .setNegativeButton("Close", null);
        if (aliasStore.isSaved(item.key)) {
            builder.setNeutralButton("Forget name", (dialog, which) -> {
                aliasStore.forget(item.key);
                refreshNearby();
                refreshHunt();
            });
        }
        builder.show();
    }

    private String deviceMetadata(BleDeviceSnapshot item) {
        StringBuilder text = new StringBuilder();
        if (item.paired) text.append("Paired Android name: ").append(item.displayName).append('\n');
        text.append("Advertised name: ")
                .append(item.observedName == null ? "Not advertised" : item.observedName)
                .append("\nType guess: ").append(item.kind)
                .append("\nManufacturer data: ").append(item.manufacturer)
                .append("\nServices: ").append(item.services)
                .append("\nRadio: ").append(item.radio)
                .append("\nTransmit power: ")
                .append(item.txPower == null ? "Not advertised" : item.txPower + " dBm")
                .append("\nAdvertisement: ").append(item.advertisementBytes).append(" bytes");
        if (item.advertiseFlags >= 0) {
            text.append(String.format(Locale.US, " · flags 0x%02X", item.advertiseFlags));
        }
        text.append("\n\nSaved names stay only on this phone. Some privacy-preserving devices rotate their hidden Bluetooth address; those may occasionally need to be named again.");
        return text.toString();
    }

    private String displayName(BleDeviceSnapshot item) {
        String alias = aliasStore.aliasFor(item.key);
        return alias == null ? item.displayName : alias;
    }

    private void updateNavigation() {
        styleNav(nearbyNav, screen == Screen.NEARBY || screen == Screen.HUNT);
        styleNav(wifiNav, screen == Screen.WIFI);
        styleNav(soundNav, screen == Screen.SOUND);
        styleNav(spaceNav, screen == Screen.SPACE);
        styleNav(magneticNav, screen == Screen.MAGNETIC);
        if (screen == Screen.HUNT || screen == Screen.WIFI || screen == Screen.SOUND) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private boolean hasBluetoothPermissions() {
        return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasActivityPermission() {
        return checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasAudioPermission() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasWifiPermissions() {
        boolean precise = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return precise && (Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                == PackageManager.PERMISSION_GRANTED);
    }

    private void requestWifiPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.NEARBY_WIFI_DEVICES
            }, REQUEST_WIFI);
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_WIFI);
        }
    }

    private void requestBluetoothPermissions() {
        requestPermissions(new String[]{
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
        }, REQUEST_BLUETOOTH);
    }

    private LinearLayout verticalPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(12));
        page.setBackgroundColor(NIGHT);
        return page;
    }

    private TextView label(String text, int sizeSp, int color, int style) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(sizeSp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create("sans", style));
        view.setLineSpacing(0, 1.12f);
        return view;
    }

    private Button navButton(String text, Runnable action) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(12);
        button.setMinWidth(0);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private Button actionButton(String text, Runnable action) {
        Button button = navButton(text, action);
        button.setTextColor(INK);
        button.setBackground(rounded(PANEL, 12));
        return button;
    }

    private void styleNav(Button button, boolean selected) {
        button.setTextColor(selected ? NIGHT : MUTED);
        button.setBackground(rounded(selected ? CYAN : Color.TRANSPARENT, 12));
    }

    private LinearLayout.LayoutParams weightedNavParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radiusDp));
        return background;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static String trendLabel(SignalMath.Trend trend) {
        return switch (trend) {
            case WARMER -> "WARMER ↑";
            case COLDER -> "COLDER ↓";
            case STEADY -> "STEADY";
            case UNKNOWN -> "LEARNING";
        };
    }

    private static String ageLabel(long ageMillis) {
        if (ageMillis < 1_000) return "now";
        return String.format(Locale.US, "%.1fs ago", ageMillis / 1_000.0);
    }

    private static int colorForRssi(int rssi) {
        if (rssi >= -60) return LIME;
        if (rssi >= -72) return CYAN;
        if (rssi >= -85) return AMBER;
        return RED;
    }

    private final class DeviceAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return nearbyDevices.size();
        }

        @Override
        public BleDeviceSnapshot getItem(int position) {
            return nearbyDevices.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).key.hashCode();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DeviceRow row;
            if (convertView == null) {
                LinearLayout box = new LinearLayout(MainActivity.this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(dp(14), dp(12), dp(14), dp(12));
                box.setBackground(rounded(PANEL, 14));
                TextView name = label("", 17, INK, Typeface.BOLD);
                TextView signal = label("", 16, CYAN, Typeface.BOLD);
                TextView details = label("", 13, MUTED, Typeface.NORMAL);
                box.addView(name);
                box.addView(signal);
                box.addView(details);
                row = new DeviceRow(name, signal, details);
                box.setTag(row);
                convertView = box;
            } else {
                row = (DeviceRow) convertView.getTag();
            }
            BleDeviceSnapshot item = getItem(position);
            row.name.setText((aliasStore.isSaved(item.key) ? "★ " : "") + displayName(item));
            if (item.signal.sampleCount == 0) {
                row.signal.setText("Paired · not heard right now");
                row.signal.setTextColor(MUTED);
            } else {
                row.signal.setText(item.signal.smoothedRssi + " dBm  ·  "
                        + SignalMath.proximity(item.signal.smoothedRssi));
                row.signal.setTextColor(colorForRssi(item.signal.smoothedRssi));
            }
            row.details.setText(item.kind + "  ·  " + item.manufacturer
                    + (item.connectable ? "  ·  connectable" : "")
                    + (item.signal.sampleCount == 0 ? "" : "  ·  "
                            + ageLabel(item.signal.ageMillis)) + "  ›");
            return convertView;
        }
    }

    private static final class DeviceRow {
        final TextView name;
        final TextView signal;
        final TextView details;

        DeviceRow(TextView name, TextView signal, TextView details) {
            this.name = name;
            this.signal = signal;
            this.details = details;
        }
    }
}
