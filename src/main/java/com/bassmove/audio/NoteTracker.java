package com.bassmove.audio;

/**
 * Turns a stream of per-frame pitch estimates into a stable pitch class (0 = C ... 11 = B).
 * A note must be heard for a few frames before it counts, and must be gone for a few
 * frames before it is released, so single-frame glitches don't move the player.
 */
public final class NoteTracker {
	private final int stableFrames;
	private final int releaseFrames;
	private final float maxCents;

	private int candidate = -1;
	private int candidateCount = 0;
	private int silence = 0;
	private int current = -1;

	public NoteTracker(int stableFrames, int releaseFrames, float maxCents) {
		this.stableFrames = Math.max(1, stableFrames);
		this.releaseFrames = Math.max(1, releaseFrames);
		this.maxCents = maxCents;
	}

	/** @param hz detected frequency, or <= 0 for "no note". @return current pitch class or -1. */
	public int update(float hz) {
		if (hz <= 0f) {
			silence++;
			candidate = -1;
			candidateCount = 0;
			if (silence >= releaseFrames) {
				current = -1;
			}
			return current;
		}
		silence = 0;

		double midi = 69.0 + 12.0 * Math.log(hz / 440.0) / Math.log(2.0);
		long nearest = Math.round(midi);
		if (Math.abs(midi - nearest) * 100.0 > maxCents) {
			// Between two notes (bend, slide, or a bad estimate): keep whatever we had.
			return current;
		}
		int pitchClass = (int) (((nearest % 12) + 12) % 12);

		if (pitchClass == candidate) {
			candidateCount++;
		} else {
			candidate = pitchClass;
			candidateCount = 1;
		}
		if (candidateCount >= stableFrames) {
			current = pitchClass;
		}
		return current;
	}

	public int current() {
		return current;
	}

	public static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

	public static String nameOf(int pitchClass) {
		return pitchClass < 0 ? "-" : NOTE_NAMES[pitchClass];
	}
}
