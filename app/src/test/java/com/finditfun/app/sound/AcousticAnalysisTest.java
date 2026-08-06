package com.finditfun.app.sound;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class AcousticAnalysisTest {
    private static final int SAMPLE_RATE = 48_000;

    @Test
    public void convertsRoundTripDelayToDistance() {
        int samples = Math.round(2f * 3f * SAMPLE_RATE
                / AcousticAnalysis.SPEED_OF_SOUND_METERS_PER_SECOND);
        assertEquals(3f, AcousticAnalysis.distanceForSamples(samples, SAMPLE_RATE),
                0.01f);
    }

    @Test
    public void chirpUsesRequestedDurationAndFadesEnds() {
        short[] chirp = AcousticAnalysis.generateChirp(SAMPLE_RATE, 30,
                2_200, 7_800);
        assertEquals(1_440, chirp.length);
        assertEquals(0, chirp[0]);
        assertTrue(Math.abs(chirp[chirp.length - 1]) < 5);
    }

    @Test
    public void syntheticRoomFindsKnownEchoDistances() {
        short[] chirp = AcousticAnalysis.generateChirp(SAMPLE_RATE, 24,
                2_400, 8_000);
        short[] recording = new short[16_000];
        int direct = 1_800;
        addSignal(recording, chirp, direct, 0.75f);
        addSignal(recording, chirp, direct + samplesForDistance(1.8f), 0.28f);
        addSignal(recording, chirp, direct + samplesForDistance(4.2f), 0.18f);

        AcousticAnalysis.Result result = AcousticAnalysis.analyze(recording,
                chirp, SAMPLE_RATE, 8f);

        assertTrue(result.chirpDetected);
        assertTrue(result.directLevel > 0.5f);
        assertTrue(hasPeakNear(result, 1.8f, 0.12f));
        assertTrue(hasPeakNear(result, 4.2f, 0.12f));
    }

    @Test
    public void silenceProducesNoInventedEchoes() {
        short[] chirp = AcousticAnalysis.generateChirp(SAMPLE_RATE, 20,
                2_000, 7_000);
        AcousticAnalysis.Result result = AcousticAnalysis.analyze(
                new short[12_000], chirp, SAMPLE_RATE, 8f);
        assertTrue(result.peaks.isEmpty());
        assertTrue(!result.chirpDetected);
        assertEquals(0f, result.directLevel, 0.0001f);
    }

    @Test
    public void normalizedMatcherRecognizesAQuietChirp() {
        short[] chirp = AcousticAnalysis.generateChirp(SAMPLE_RATE, 42,
                1_200, 6_500);
        short[] recording = new short[24_000];
        addSignal(recording, chirp, 7_000, 0.004f);
        AcousticAnalysis.Result result = AcousticAnalysis.analyze(recording,
                chirp, SAMPLE_RATE, 8f);
        assertTrue(result.chirpDetected);
        assertTrue(result.directLevel > 0.8f);
        assertTrue(result.capturedPeakFraction < 0.01f);
    }

    @Test
    public void playbackDiagnosticsRequireMostFramesToComplete() {
        AcousticAnalysis.Result base = AcousticAnalysis.analyze(new short[12_000],
                AcousticAnalysis.generateChirp(SAMPLE_RATE, 20, 2_000, 7_000),
                SAMPLE_RATE, 8f);
        AcousticAnalysis.Result complete = base.withDeviceDiagnostics(
                "camcorder", SAMPLE_RATE, 2, 950, 1_000, 0, 1_000);
        AcousticAnalysis.Result shortPlayback = base.withDeviceDiagnostics(
                "camcorder", SAMPLE_RATE, 2, 899, 1_000, 1, 1_000);
        assertTrue(complete.playbackCompleted());
        assertTrue(!shortPlayback.playbackCompleted());
    }

    private static int samplesForDistance(float meters) {
        return Math.round(meters * 2f * SAMPLE_RATE
                / AcousticAnalysis.SPEED_OF_SOUND_METERS_PER_SECOND);
    }

    private static void addSignal(short[] recording, short[] signal, int offset,
                                  float scale) {
        for (int index = 0; index < signal.length
                && offset + index < recording.length; index++) {
            int value = recording[offset + index]
                    + Math.round(signal[index] * scale);
            recording[offset + index] = (short) Math.max(Short.MIN_VALUE,
                    Math.min(Short.MAX_VALUE, value));
        }
    }

    private static boolean hasPeakNear(AcousticAnalysis.Result result,
                                       float distance, float tolerance) {
        for (AcousticAnalysis.EchoPeak peak : result.peaks) {
            if (Math.abs(peak.distanceMeters - distance) <= tolerance) return true;
        }
        return false;
    }
}
