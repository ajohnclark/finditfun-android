package com.finditfun.app.sound;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AcousticAnalysis {
    public static final float SPEED_OF_SOUND_METERS_PER_SECOND = 343f;
    public static final float DEFAULT_MAX_DISTANCE_METERS = 8f;
    private static final float MIN_ECHO_DISTANCE_METERS = 0.45f;
    private static final float MIN_PEAK_SPACING_METERS = 0.35f;
    private static final int PROFILE_BINS = 160;
    private static final int MAX_PEAKS = 6;

    public static final class EchoPeak {
        public final float distanceMeters;
        public final float strength;

        EchoPeak(float distanceMeters, float strength) {
            this.distanceMeters = distanceMeters;
            this.strength = strength;
        }
    }

    public static final class Result {
        public final List<EchoPeak> peaks;
        public final float[] profile;
        public final float maxDistanceMeters;
        public final float directLevel;
        public final boolean chirpDetected;
        public final float capturedPeakFraction;
        public final float capturedRmsFraction;
        public final String captureSource;
        public final int outputSampleRate;
        public final int outputChannels;
        public final int playedFrames;
        public final int expectedFrames;
        public final int underrunCount;
        public final int startThresholdFrames;

        Result(List<EchoPeak> peaks, float[] profile, float maxDistanceMeters,
               float directLevel, boolean chirpDetected, float capturedPeakFraction,
               float capturedRmsFraction) {
            this.peaks = List.copyOf(peaks);
            this.profile = profile.clone();
            this.maxDistanceMeters = maxDistanceMeters;
            this.directLevel = directLevel;
            this.chirpDetected = chirpDetected;
            this.capturedPeakFraction = capturedPeakFraction;
            this.capturedRmsFraction = capturedRmsFraction;
            this.captureSource = "unknown";
            this.outputSampleRate = 0;
            this.outputChannels = 0;
            this.playedFrames = 0;
            this.expectedFrames = 0;
            this.underrunCount = 0;
            this.startThresholdFrames = 0;
        }

        private Result(Result source, String captureSource, int outputSampleRate,
                       int outputChannels, int playedFrames, int expectedFrames,
                       int underrunCount, int startThresholdFrames) {
            this.peaks = source.peaks;
            this.profile = source.profile.clone();
            this.maxDistanceMeters = source.maxDistanceMeters;
            this.directLevel = source.directLevel;
            this.chirpDetected = source.chirpDetected;
            this.capturedPeakFraction = source.capturedPeakFraction;
            this.capturedRmsFraction = source.capturedRmsFraction;
            this.captureSource = captureSource;
            this.outputSampleRate = outputSampleRate;
            this.outputChannels = outputChannels;
            this.playedFrames = playedFrames;
            this.expectedFrames = expectedFrames;
            this.underrunCount = underrunCount;
            this.startThresholdFrames = startThresholdFrames;
        }

        Result withDeviceDiagnostics(String captureSource, int outputSampleRate,
                                     int outputChannels, int playedFrames,
                                     int expectedFrames, int underrunCount,
                                     int startThresholdFrames) {
            return new Result(this, captureSource, outputSampleRate, outputChannels,
                    playedFrames, expectedFrames, underrunCount,
                    startThresholdFrames);
        }

        public boolean playbackCompleted() {
            return expectedFrames > 0 && playedFrames >= expectedFrames * 0.9f;
        }
    }

    private static final class Candidate {
        final int lag;
        final float value;

        Candidate(int lag, float value) {
            this.lag = lag;
            this.value = value;
        }
    }

    private AcousticAnalysis() {
    }

    public static short[] generateChirp(int sampleRate, int durationMillis,
                                        float startHz, float endHz) {
        int count = Math.max(1, Math.round(sampleRate * durationMillis / 1_000f));
        short[] samples = new short[count];
        double duration = count / (double) sampleRate;
        double sweepRate = (endHz - startHz) / duration;
        for (int index = 0; index < count; index++) {
            double time = index / (double) sampleRate;
            double phase = 2.0 * Math.PI
                    * (startHz * time + 0.5 * sweepRate * time * time);
            double window = 0.5 - 0.5
                    * Math.cos(2.0 * Math.PI * index / Math.max(1, count - 1));
            samples[index] = (short) Math.round(Math.sin(phase) * window * 19_500.0);
        }
        return samples;
    }

    public static Result analyze(short[] recording, short[] chirp, int sampleRate,
                                 float maxDistanceMeters) {
        if (recording == null || chirp == null || chirp.length < 8
                || recording.length <= chirp.length || sampleRate <= 0) {
            return emptyResult(maxDistanceMeters);
        }

        int correlationLength = recording.length - chirp.length + 1;
        float[] correlation = new float[correlationLength];
        float[] amplitude = new float[correlationLength];
        double chirpEnergy = 0;
        for (short sample : chirp) chirpEnergy += (double) sample * sample;
        if (chirpEnergy <= 0) return emptyResult(maxDistanceMeters);

        double[] energyPrefix = new double[recording.length + 1];
        int capturedPeak = 0;
        for (int index = 0; index < recording.length; index++) {
            double square = (double) recording[index] * recording[index];
            energyPrefix[index + 1] = energyPrefix[index] + square;
            capturedPeak = Math.max(capturedPeak, Math.abs((int) recording[index]));
        }
        float capturedPeakFraction = Math.min(1f, capturedPeak / 32_768f);
        float capturedRmsFraction = (float) (Math.sqrt(
                energyPrefix[recording.length] / recording.length) / 32_768.0);

        for (int lag = 0; lag < correlationLength; lag++) {
            double sum = 0;
            for (int index = 0; index < chirp.length; index++) {
                sum += (double) recording[lag + index] * chirp[index];
            }
            double recordingEnergy = energyPrefix[lag + chirp.length]
                    - energyPrefix[lag];
            amplitude[lag] = (float) (Math.abs(sum) / chirpEnergy);
            if (recordingEnergy > 0) {
                correlation[lag] = (float) (Math.abs(sum)
                        / Math.sqrt(chirpEnergy * recordingEnergy));
            }
        }

        int directSearchEnd = Math.min(correlationLength,
                Math.round(sampleRate * 0.38f));
        int directLag = -1;
        float directLevel = 0;
        float directScore = 0;
        for (int lag = 0; lag < directSearchEnd; lag++) {
            float levelGate = Math.min(1f, amplitude[lag] / 0.0015f);
            float score = correlation[lag] * levelGate;
            if (score > directScore) {
                directScore = score;
                directLevel = correlation[lag];
                directLag = lag;
            }
        }
        if (directLag < 0 || directScore < 0.045f || directLevel < 0.055f) {
            return new Result(new ArrayList<>(), new float[PROFILE_BINS],
                    maxDistanceMeters, directLevel, false, capturedPeakFraction,
                    capturedRmsFraction);
        }

        int minimumOffset = samplesForDistance(MIN_ECHO_DISTANCE_METERS, sampleRate);
        int maximumOffset = samplesForDistance(maxDistanceMeters, sampleRate);
        int start = Math.min(correlationLength, directLag + minimumOffset);
        int end = Math.min(correlationLength - 1, directLag + maximumOffset);
        if (start >= end) {
            return new Result(new ArrayList<>(), new float[PROFILE_BINS],
                    maxDistanceMeters, directLevel, true, capturedPeakFraction,
                    capturedRmsFraction);
        }

        float echoMaximum = 0;
        for (int lag = start; lag <= end; lag++) {
            echoMaximum = Math.max(echoMaximum, amplitude[lag]);
        }
        if (echoMaximum <= 0) {
            return new Result(new ArrayList<>(), new float[PROFILE_BINS],
                    maxDistanceMeters, directLevel, true, capturedPeakFraction,
                    capturedRmsFraction);
        }

        float[] profile = new float[PROFILE_BINS];
        for (int lag = start; lag <= end; lag++) {
            float distance = distanceForSamples(lag - directLag, sampleRate);
            int bin = Math.min(PROFILE_BINS - 1,
                    Math.max(0, (int) (distance / maxDistanceMeters * PROFILE_BINS)));
            profile[bin] = Math.max(profile[bin], amplitude[lag] / echoMaximum);
        }
        smoothProfile(profile);

        float threshold = Math.max(amplitude[directLag] * 0.008f,
                echoMaximum * 0.12f);
        ArrayList<Candidate> candidates = new ArrayList<>();
        int localRadius = Math.max(3, samplesForDistance(0.08f, sampleRate));
        for (int lag = start + localRadius; lag <= end - localRadius; lag++) {
            float value = amplitude[lag];
            if (value < threshold || correlation[lag] < 0.04f) continue;
            boolean localMaximum = true;
            for (int neighbor = lag - localRadius;
                 neighbor <= lag + localRadius; neighbor++) {
                if (amplitude[neighbor] > value) {
                    localMaximum = false;
                    break;
                }
            }
            if (localMaximum) candidates.add(new Candidate(lag, value));
        }
        candidates.sort(Comparator.comparingDouble((Candidate item) -> item.value)
                .reversed());

        ArrayList<EchoPeak> peaks = new ArrayList<>();
        for (Candidate candidate : candidates) {
            float distance = distanceForSamples(candidate.lag - directLag, sampleRate);
            boolean tooClose = false;
            for (EchoPeak selected : peaks) {
                if (Math.abs(selected.distanceMeters - distance)
                        < MIN_PEAK_SPACING_METERS) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                peaks.add(new EchoPeak(distance,
                        Math.min(1f, candidate.value / echoMaximum)));
                if (peaks.size() >= MAX_PEAKS) break;
            }
        }
        peaks.sort(Comparator.comparingDouble(item -> item.distanceMeters));
        return new Result(peaks, profile, maxDistanceMeters, directLevel, true,
                capturedPeakFraction, capturedRmsFraction);
    }

    public static float distanceForSamples(int sampleOffset, int sampleRate) {
        return sampleOffset * SPEED_OF_SOUND_METERS_PER_SECOND
                / (2f * sampleRate);
    }

    private static int samplesForDistance(float distanceMeters, int sampleRate) {
        return Math.round(distanceMeters * 2f * sampleRate
                / SPEED_OF_SOUND_METERS_PER_SECOND);
    }

    private static void smoothProfile(float[] profile) {
        float[] original = profile.clone();
        for (int index = 1; index < profile.length - 1; index++) {
            profile[index] = (original[index - 1] + original[index] * 2f
                    + original[index + 1]) / 4f;
        }
    }

    private static Result emptyResult(float maxDistanceMeters) {
        return new Result(new ArrayList<>(), new float[PROFILE_BINS],
                maxDistanceMeters, 0, false, 0, 0);
    }
}
