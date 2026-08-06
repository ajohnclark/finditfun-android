package com.finditfun.app.sound;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AcousticPingController {
    public interface Listener {
        void onStatus(String status);
        void onPingStarted();
        void onResult(AcousticAnalysis.Result result);
    }

    public static final int SAMPLE_RATE = 48_000;
    private static final int CHIRP_MILLIS = 28;
    private static final int LEAD_SILENCE_MILLIS = 42;
    private static final int RECORD_MILLIS = 320;

    private final Context context;
    private final Listener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private final Object audioLock = new Object();
    private volatile boolean canceled;
    private AudioRecord activeRecorder;
    private AudioTrack activeTrack;
    private int captureSourceCursor;

    private static final class Playback {
        final AudioTrack track;
        final int sampleRate;
        final int channelCount;

        Playback(AudioTrack track, int sampleRate, int channelCount) {
            this.track = track;
            this.sampleRate = sampleRate;
            this.channelCount = channelCount;
        }
    }

    private static final class Recording {
        final AudioRecord recorder;
        final String sourceName;
        final int sourceIndex;
        final int sourceCount;

        Recording(AudioRecord recorder, String sourceName, int sourceIndex,
                  int sourceCount) {
            this.recorder = recorder;
            this.sourceName = sourceName;
            this.sourceIndex = sourceIndex;
            this.sourceCount = sourceCount;
        }
    }

    public AcousticPingController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public boolean isBusy() {
        return busy.get();
    }

    public void ping() {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onStatus("Microphone permission is required for room echoes.");
            return;
        }
        if (!busy.compareAndSet(false, true)) {
            listener.onStatus("A sound ping is already listening…");
            return;
        }
        canceled = false;
        listener.onPingStarted();
        executor.execute(() -> {
            try {
                AcousticAnalysis.Result result = captureAndAnalyze();
                if (!canceled && result != null) {
                    mainHandler.post(() -> {
                        if (!canceled) listener.onResult(result);
                    });
                }
            } catch (RuntimeException error) {
                if (!canceled) {
                    String message = error.getMessage();
                    if (message == null || message.isBlank()) {
                        message = error.getClass().getSimpleName();
                    }
                    String finalMessage = message;
                    mainHandler.post(() -> listener.onStatus(
                            "Sound ping failed: " + finalMessage));
                }
            } finally {
                busy.set(false);
            }
        });
    }

    public void cancel() {
        canceled = true;
        synchronized (audioLock) {
            safeStop(activeTrack);
            safeStop(activeRecorder);
        }
    }

    public void close() {
        cancel();
        executor.shutdownNow();
    }

    @SuppressLint("MissingPermission")
    private AcousticAnalysis.Result captureAndAnalyze() {
        Playback playback = createPlayback();
        AudioTrack track = playback.track;
        int sampleRate = playback.sampleRate;
        short[] chirp = AcousticAnalysis.generateChirp(sampleRate, CHIRP_MILLIS,
                2_200, 7_800);
        int leadSamples = sampleRate * LEAD_SILENCE_MILLIS / 1_000;
        short[] monoOutput = new short[leadSamples + chirp.length];
        System.arraycopy(chirp, 0, monoOutput, leadSamples, chirp.length);
        short[] output = interleave(monoOutput, playback.channelCount);
        int expectedFrames = monoOutput.length;
        if (expectedFrames < 1) {
            track.release();
            throw new IllegalStateException("The generated chirp was empty");
        }
        short[] recording = new short[sampleRate * RECORD_MILLIS / 1_000];

        AudioFormat inputFormat = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build();
        Recording recordingDevice;
        try {
            recordingDevice = createRecorder(inputFormat, recording.length * 2,
                    sampleRate);
        } catch (RuntimeException error) {
            track.release();
            throw error;
        }
        AudioRecord recorder = recordingDevice.recorder;

        track.setVolume(0.62f);

        synchronized (audioLock) {
            activeRecorder = recorder;
            activeTrack = track;
        }
        int captured = 0;
        int playedFrames = 0;
        int underrunCount = 0;
        int startThresholdFrames = 0;
        try {
            recorder.startRecording();
            if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("The microphone did not start");
            }
            startThresholdFrames = configureStartThreshold(track, expectedFrames);
            int written = track.write(output, 0, output.length,
                    AudioTrack.WRITE_BLOCKING);
            if (written < output.length) {
                throw new IllegalStateException("The chirp could not be pre-buffered");
            }
            track.play();
            while (!canceled && captured < recording.length) {
                int count = recorder.read(recording, captured,
                        recording.length - captured, AudioRecord.READ_BLOCKING);
                if (count < 0) {
                    throw new IllegalStateException("Microphone read error " + count);
                }
                if (count == 0) continue;
                captured += count;
            }
            long playbackDeadline = SystemClock.uptimeMillis() + 180;
            do {
                playedFrames = track.getPlaybackHeadPosition();
                if (playedFrames >= expectedFrames || canceled) break;
                try {
                    Thread.sleep(5);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } while (SystemClock.uptimeMillis() < playbackDeadline);
            underrunCount = track.getUnderrunCount();
        } finally {
            safeStop(track);
            safeStop(recorder);
            synchronized (audioLock) {
                activeTrack = null;
                activeRecorder = null;
            }
            track.release();
            recorder.release();
        }
        if (canceled) return null;
        if (captured <= chirp.length) {
            throw new IllegalStateException("Not enough microphone audio was captured");
        }
        AcousticAnalysis.Result result = AcousticAnalysis.analyze(
                Arrays.copyOf(recording, captured), chirp, sampleRate,
                AcousticAnalysis.DEFAULT_MAX_DISTANCE_METERS)
                .withDeviceDiagnostics(recordingDevice.sourceName, sampleRate,
                        playback.channelCount, playedFrames, expectedFrames,
                        underrunCount, startThresholdFrames);
        if (result.playbackCompleted() && !result.chirpDetected) {
            captureSourceCursor = (recordingDevice.sourceIndex + 1)
                    % recordingDevice.sourceCount;
        } else {
            captureSourceCursor = recordingDevice.sourceIndex;
        }
        return result;
    }

    @SuppressLint("MissingPermission")
    private Recording createRecorder(AudioFormat format, int desiredBufferBytes,
                                     int sampleRate) {
        int minimum = AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) throw new IllegalStateException("Unsupported microphone format");
        int bufferBytes = Math.max(minimum, desiredBufferBytes);
        AudioManager manager = context.getSystemService(AudioManager.class);
        boolean unprocessed = manager != null && "true".equalsIgnoreCase(
                manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
        int[] sources = unprocessed
                ? new int[]{
                        MediaRecorder.AudioSource.CAMCORDER,
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.UNPROCESSED,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        MediaRecorder.AudioSource.DEFAULT
                }
                : new int[]{
                        MediaRecorder.AudioSource.CAMCORDER,
                        MediaRecorder.AudioSource.MIC,
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        MediaRecorder.AudioSource.DEFAULT
                };
        RuntimeException lastError = null;
        for (int offset = 0; offset < sources.length; offset++) {
            int index = (captureSourceCursor + offset) % sources.length;
            AudioRecord recorder = null;
            try {
                recorder = buildRecorder(sources[index], format, bufferBytes);
                if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                    return new Recording(recorder, sourceName(sources[index]), index,
                            sources.length);
                }
            } catch (RuntimeException error) {
                lastError = error;
            }
            if (recorder != null) recorder.release();
        }
        String detail = lastError == null ? "no compatible source"
                : lastError.getClass().getSimpleName();
        throw new IllegalStateException("The phone microphone could not be initialized ("
                + detail + ")");
    }

    private Playback createPlayback() {
        int nativeRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        int[] sampleRates = nativeRate > 0 && nativeRate != SAMPLE_RATE
                ? new int[]{SAMPLE_RATE, nativeRate, 44_100}
                : new int[]{SAMPLE_RATE, 44_100};
        int[] channelMasks = new int[]{
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.CHANNEL_OUT_STEREO
        };
        RuntimeException lastError = null;
        for (int sampleRate : sampleRates) {
            for (int channelMask : channelMasks) {
                try {
                    Playback playback = buildPlayback(sampleRate, channelMask);
                    if (playback != null) return playback;
                } catch (RuntimeException error) {
                    lastError = error;
                }
            }
        }
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(audioAttributes())
                    .setBufferSizeInBytes(SAMPLE_RATE * 2 * 2 / 5)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build();
            if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                return new Playback(track, track.getSampleRate(),
                        Math.max(1, track.getChannelCount()));
            }
            track.release();
        } catch (RuntimeException error) {
            lastError = error;
        }
        String detail = lastError == null ? "no supported PCM output"
                : lastError.getClass().getSimpleName();
        throw new IllegalStateException("The phone speaker rejected streaming audio ("
                + detail + ")");
    }

    private Playback buildPlayback(int sampleRate, int channelMask) {
        int minimum = AudioTrack.getMinBufferSize(sampleRate, channelMask,
                AudioFormat.ENCODING_PCM_16BIT);
        if (minimum <= 0) return null;
        AudioFormat format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build();
        int channels = channelMask == AudioFormat.CHANNEL_OUT_STEREO ? 2 : 1;
        int prebufferBytes = sampleRate * channels * 2 / 5;
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(audioAttributes())
                .setAudioFormat(format)
                .setBufferSizeInBytes(Math.max(minimum * 2, prebufferBytes))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_NONE)
                .build();
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            return null;
        }
        return new Playback(track, sampleRate, channels);
    }

    private static AudioAttributes audioAttributes() {
        return new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
    }

    private static short[] interleave(short[] mono, int channelCount) {
        if (channelCount <= 1) return mono;
        short[] output = new short[mono.length * channelCount];
        for (int frame = 0; frame < mono.length; frame++) {
            for (int channel = 0; channel < channelCount; channel++) {
                output[frame * channelCount + channel] = mono[frame];
            }
        }
        return output;
    }

    @SuppressLint("MissingPermission")
    private static AudioRecord buildRecorder(int source, AudioFormat format,
                                             int bufferBytes) {
        return new AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .build();
    }

    private static String sourceName(int source) {
        if (source == MediaRecorder.AudioSource.CAMCORDER) return "camcorder";
        if (source == MediaRecorder.AudioSource.MIC) return "ambient mic";
        if (source == MediaRecorder.AudioSource.UNPROCESSED) return "unprocessed";
        if (source == MediaRecorder.AudioSource.VOICE_RECOGNITION) {
            return "voice recognition";
        }
        return "default mic";
    }

    @SuppressLint("Range")
    private static int configureStartThreshold(AudioTrack track, int frames) {
        if (frames < 1) throw new IllegalArgumentException("frames must be positive");
        return track.setStartThresholdInFrames(frames);
    }

    private static void safeStop(AudioTrack track) {
        if (track == null) return;
        try {
            if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) track.stop();
        } catch (IllegalStateException ignored) {
            // A concurrent cancel may find an audio device already stopped.
        }
    }

    private static void safeStop(AudioRecord recorder) {
        if (recorder == null) return;
        try {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                recorder.stop();
            }
        } catch (IllegalStateException ignored) {
            // A concurrent cancel may find an audio device already stopped.
        }
    }
}
