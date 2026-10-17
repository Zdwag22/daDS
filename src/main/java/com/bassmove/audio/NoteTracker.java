package com.bassmove.audio;

/**
 * Turns a stream of per-frame pitch estimates into a stable MIDI note number
 * (E1 = 28, A1 = 33, D2 = 38, G2 = 43 on a 4-string bass).
 * A note must be heard for a few frames before it counts, and must be gone for a few
 * frames before it is released, so single-frame glitches don't move the player.
 */
public final class NoteTracker {
	public static final String[] NOTE_NAMES = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};

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

	/** @param hz detected frequency, or <= 0 for "no note". @return current MIDI note or -1. */
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
		if (Math.abs(midi - nearest) * 100.0 > maxCents || nearest < 0) {
			// Between two notes (bend, slide, or a bad estimate): keep whatever we had.
			return current;
		}
		int note = (int) nearest;

		if (note == candidate) {
			candidateCount++;
		} else {
			candidate = note;
			candidateCount = 1;
		}
		if (candidateCount >= stableFrames) {
			current = note;
		}
		return current;
	}

	public int current() {
		return current;
	}

	/** MIDI note to a name like "F#1". */
	public static String nameOf(int midi) {
		return midi < 0 ? "-" : NOTE_NAMES[midi % 12] + (midi / 12 - 1);
	}

	/** Name like "F#1" or "E2" to a MIDI note, or -1 if it can't be read. */
	public static int midiOf(String name) {
		if (name == null) {
			return -1;
		}
		String s = name.trim().toUpperCase();
		if (s.length() < 2) {
			return -1;
		}
		int len = s.charAt(1) == '#' ? 2 : 1;
		int pitchClass = -1;
		for (int i = 0; i < 12; i++) {
			if (NOTE_NAMES[i].equals(s.substring(0, len))) {
				pitchClass = i;
				break;
			}
		}
		if (pitchClass < 0) {
			return -1;
		}
		try {
			int octave = Integer.parseInt(s.substring(len));
			int midi = (octave + 1) * 12 + pitchClass;
			return midi >= 0 && midi < 128 ? midi : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
