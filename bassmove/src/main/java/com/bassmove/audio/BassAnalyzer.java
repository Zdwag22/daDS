package com.bassmove.audio;

/**
 * Takes raw mono microphone samples, downsamples them to roughly 11 kHz (plenty for bass),
 * runs YIN every 40 ms and tracks the current note. Thread-safe for reading the results
 * from the game thread while the audio thread feeds it.
 */
public final class BassAnalyzer {
	private static final float TARGET_RATE = 11025f;
	private static final float WINDOW_SECONDS = 0.072f;
	private static final float HOP_SECONDS = 0.040f;

	private final int decimation;
	private final float gate;
	private final YinPitchDetector yin;
	private final NoteTracker tracker;
	private final float[] frame;
	private final float[] work;
	private final int window;
	private final int hop;

	private int filled = 0;
	private int decimCount = 0;
	private float decimAccumulator = 0f;

	private volatile int pitchClass = -1;
	private volatile float lastHz = -1f;
	private volatile float lastRms = 0f;

	public BassAnalyzer(float sourceRate, float minHz, float maxHz, float gate,
			float yinThreshold, int stableFrames, int releaseFrames) {
		this.decimation = Math.max(1, Math.round(sourceRate / TARGET_RATE));
		float rate = sourceRate / decimation;
		this.window = Math.round(rate * WINDOW_SECONDS);
		this.hop = Math.round(rate * HOP_SECONDS);
		this.gate = gate;
		this.yin = new YinPitchDetector(rate, minHz, maxHz, window, yinThreshold);
		this.tracker = new NoteTracker(stableFrames, releaseFrames, 40f);
		this.frame = new float[yin.frameSize()];
		this.work = new float[yin.frameSize()];
	}

	/** Feed mono samples in the range -1..1 at the source rate. Call from the audio thread only. */
	public void accept(float[] mono, int count) {
		for (int i = 0; i < count; i++) {
			decimAccumulator += mono[i];
			if (++decimCount == decimation) {
				push(decimAccumulator / decimation);
				decimAccumulator = 0f;
				decimCount = 0;
			}
		}
	}

	private void push(float sample) {
		frame[filled++] = sample;
		if (filled == frame.length) {
			analyze();
			System.arraycopy(frame, hop, frame, 0, frame.length - hop);
			filled = frame.length - hop;
		}
	}

	private void analyze() {
		double mean = 0.0;
		for (float v : frame) {
			mean += v;
		}
		mean /= frame.length;

		double energy = 0.0;
		for (int i = 0; i < frame.length; i++) {
			float v = (float) (frame[i] - mean);
			work[i] = v;
			if (i < window) {
				energy += v * v;
			}
		}
		float rms = (float) Math.sqrt(energy / window);
		lastRms = rms;

		float hz = rms < gate ? -1f : yin.detect(work);
		lastHz = hz;
		pitchClass = tracker.update(hz);
	}

	/** Current stable pitch class (0 = C ... 11 = B), or -1 when nothing is being played. */
	public int pitchClass() {
		return pitchClass;
	}

	public float lastHz() {
		return lastHz;
	}

	public float lastRms() {
		return lastRms;
	}
}
