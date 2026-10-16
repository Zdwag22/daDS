package com.bassmove.audio;

/**
 * Monophonic pitch detector based on the YIN algorithm (de Cheveigné & Kawahara, 2002).
 * Works well on low notes, where plain autocorrelation or FFT peak picking tends to jump octaves.
 * No Minecraft dependencies, so it can be tested on its own.
 */
public final class YinPitchDetector {
	private static final float FALLBACK_THRESHOLD = 0.30f;

	private final float sampleRate;
	private final int window;
	private final int tauMin;
	private final int tauMax;
	private final float threshold;
	private final float[] cmnd;

	public YinPitchDetector(float sampleRate, float minHz, float maxHz, int window, float threshold) {
		this.sampleRate = sampleRate;
		this.window = window;
		this.tauMin = Math.max(2, (int) Math.floor(sampleRate / maxHz));
		this.tauMax = (int) Math.ceil(sampleRate / minHz);
		this.threshold = threshold;
		this.cmnd = new float[tauMax + 2];
	}

	/** Number of samples {@link #detect(float[])} needs. */
	public int frameSize() {
		return window + tauMax + 1;
	}

	/** @return fundamental frequency in Hz, or -1 if no clear pitch was found. */
	public float detect(float[] x) {
		// Difference function + cumulative mean normalisation, computed in one pass.
		cmnd[0] = 1f;
		float running = 0f;
		for (int tau = 1; tau <= tauMax + 1; tau++) {
			float sum = 0f;
			for (int j = 0; j < window; j++) {
				float diff = x[j] - x[j + tau];
				sum += diff * diff;
			}
			running += sum;
			cmnd[tau] = running == 0f ? 1f : sum * tau / running;
		}

		// First dip below the threshold, then follow it down to the local minimum.
		int tauEstimate = -1;
		for (int tau = tauMin; tau <= tauMax; tau++) {
			if (cmnd[tau] < threshold) {
				while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) {
					tau++;
				}
				tauEstimate = tau;
				break;
			}
		}

		if (tauEstimate < 0) {
			int best = tauMin;
			for (int tau = tauMin; tau <= tauMax; tau++) {
				if (cmnd[tau] < cmnd[best]) {
					best = tau;
				}
			}
			if (cmnd[best] >= FALLBACK_THRESHOLD) {
				return -1f;
			}
			tauEstimate = best;
		}

		// Parabolic interpolation around the minimum for sub-sample accuracy.
		float refined = tauEstimate;
		float s0 = cmnd[tauEstimate - 1];
		float s1 = cmnd[tauEstimate];
		float s2 = cmnd[tauEstimate + 1];
		float denom = 2f * (s0 - 2f * s1 + s2);
		if (denom != 0f) {
			refined = tauEstimate + (s0 - s2) / denom;
		}
		return refined > 0f ? sampleRate / refined : -1f;
	}
}
