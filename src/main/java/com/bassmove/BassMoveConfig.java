package com.bassmove;

import com.bassmove.audio.NoteTracker;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Settings loaded from config/bassmove.properties (written with defaults on first run). */
public final class BassMoveConfig {
	/**
	 * Default layout for a 4-string bass in standard tuning (E A D G).
	 * Open strings = move, 1st fret = look, 2nd fret = jump / break / place.
	 * Notes are exact (octave included), so the same note higher up the neck does nothing.
	 */
	private static final String[][] DEFAULTS = {
		// open strings: movement (E = forward, A = left, D = back, G = right)
		{"E1", "FORWARD"},
		{"A1", "LEFT"},
		{"D2", "BACK"},
		{"G2", "RIGHT"},
		// 1st fret: looking (same order: up, left, down, right)
		{"F1", "LOOK_UP"},
		{"A#1", "TURN_LEFT"},
		{"D#2", "LOOK_DOWN"},
		{"G#2", "TURN_RIGHT"},
		// 2nd fret: actions
		{"F#1", "JUMP"},
		{"B1", "ATTACK"},
		{"E2", "JUMP_FORWARD"},
		{"A2", "USE"},
	};

	private final Map<Integer, MovementAction> actions = new HashMap<>();
	public String device = "";
	public float noiseGate = 0.02f;
	public float yinThreshold = 0.15f;
	public int stableFrames = 2;
	public int releaseFrames = 3;
	public float minHz = 30f;
	public float maxHz = 420f;
	public float turnDegreesPerTick = 4f;
	public boolean debug = false;

	public BassMoveConfig() {
		for (String[] entry : DEFAULTS) {
			actions.put(NoteTracker.midiOf(entry[0]), MovementAction.parse(entry[1], MovementAction.NONE));
		}
	}

	public MovementAction actionFor(int midiNote) {
		return actions.getOrDefault(midiNote, MovementAction.NONE);
	}

	public static BassMoveConfig load(Path file) {
		BassMoveConfig config = new BassMoveConfig();
		if (!Files.exists(file)) {
			config.writeDefaults(file);
			return config;
		}
		Properties props = new Properties();
		try (Reader reader = Files.newBufferedReader(file)) {
			props.load(reader);
		} catch (IOException e) {
			return config;
		}
		config.device = props.getProperty("device", config.device).trim();
		config.noiseGate = parseFloat(props, "noiseGate", config.noiseGate);
		config.yinThreshold = parseFloat(props, "yinThreshold", config.yinThreshold);
		config.stableFrames = (int) parseFloat(props, "stableFrames", config.stableFrames);
		config.releaseFrames = (int) parseFloat(props, "releaseFrames", config.releaseFrames);
		config.minHz = parseFloat(props, "minHz", config.minHz);
		config.maxHz = parseFloat(props, "maxHz", config.maxHz);
		config.turnDegreesPerTick = parseFloat(props, "turnDegreesPerTick", config.turnDegreesPerTick);
		config.debug = Boolean.parseBoolean(props.getProperty("debug", "false").trim());

		boolean sawNoteKeys = false;
		for (String key : props.stringPropertyNames()) {
			if (!key.startsWith("note.")) {
				continue;
			}
			int midi = NoteTracker.midiOf(key.substring(5));
			if (midi < 0) {
				continue; // e.g. an old-style "note.E" key without an octave
			}
			if (!sawNoteKeys) {
				// The file has its own note list, so it replaces the defaults.
				config.actions.clear();
				sawNoteKeys = true;
			}
			config.actions.put(midi, MovementAction.parse(props.getProperty(key), MovementAction.NONE));
		}
		return config;
	}

	private static float parseFloat(Properties props, String key, float fallback) {
		try {
			return Float.parseFloat(props.getProperty(key, "").trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private void writeDefaults(Path file) {
		StringBuilder sb = new StringBuilder();
		sb.append("# Bass Move settings. Restart the game after editing.\n\n");
		sb.append("# Part of the input device name to use (empty = system default microphone).\n");
		sb.append("# The log lists all input devices when you first toggle the mod on.\n");
		sb.append("device=\n\n");
		sb.append("# Notes quieter than this (0..1, RMS) are ignored. Raise it if room noise\n");
		sb.append("# moves you around, lower it if soft notes are missed. Turn on debug to see levels.\n");
		sb.append("noiseGate=").append(noiseGate).append("\n\n");
		sb.append("# Lower = stricter pitch detection (0.10 - 0.20 is sensible).\n");
		sb.append("yinThreshold=").append(yinThreshold).append("\n\n");
		sb.append("# Pitch frames (40 ms each) a note must last before it counts / be silent before it stops.\n");
		sb.append("stableFrames=").append(stableFrames).append("\n");
		sb.append("releaseFrames=").append(releaseFrames).append("\n\n");
		sb.append("# Pitch range searched, in Hz. B0 on a 5-string is 30.9, G4 on a 24-fret bass is 392.\n");
		sb.append("minHz=").append(minHz).append("\n");
		sb.append("maxHz=").append(maxHz).append("\n\n");
		sb.append("# Camera speed for TURN_* and LOOK_* actions.\n");
		sb.append("turnDegreesPerTick=").append(turnDegreesPerTick).append("\n\n");
		sb.append("# Log every note change plus the signal level.\n");
		sb.append("debug=false\n\n");
		sb.append("# Exact note (with octave) -> action. Notes not listed here do nothing.\n");
		sb.append("# 4-string bass, standard tuning: open strings are E1 A1 D2 G2.\n");
		sb.append("# Actions: ").append(String.join(", ", actionNames())).append("\n");
		for (String[] entry : DEFAULTS) {
			sb.append("note.").append(entry[0]).append('=').append(entry[1]).append('\n');
		}
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, sb.toString());
		} catch (IOException ignored) {
			// Running with defaults is fine if the config can't be written.
		}
	}

	private static String[] actionNames() {
		MovementAction[] all = MovementAction.values();
		String[] names = new String[all.length];
		for (int i = 0; i < all.length; i++) {
			names[i] = all[i].name();
		}
		return names;
	}
}
