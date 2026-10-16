package com.bassmove;

import com.bassmove.audio.NoteTracker;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Settings loaded from config/bassmove.properties (written with defaults on first run). */
public final class BassMoveConfig {
	/** Default note -> action mapping, indexed C, C#, D, D#, E, F, F#, G, G#, A, A#, B. */
	private static final MovementAction[] DEFAULT_ACTIONS = {
		MovementAction.LEFT,           // C
		MovementAction.TURN_LEFT,      // C#
		MovementAction.BACK,           // D
		MovementAction.SNEAK,          // D#
		MovementAction.FORWARD,        // E
		MovementAction.JUMP,           // F
		MovementAction.LOOK_UP,        // F#
		MovementAction.RIGHT,          // G
		MovementAction.TURN_RIGHT,     // G#
		MovementAction.SPRINT_FORWARD, // A
		MovementAction.LOOK_DOWN,      // A#
		MovementAction.NONE,           // B
	};

	public final MovementAction[] actions = DEFAULT_ACTIONS.clone();
	public String device = "";
	public float noiseGate = 0.02f;
	public float yinThreshold = 0.15f;
	public int stableFrames = 2;
	public int releaseFrames = 3;
	public float minHz = 30f;
	public float maxHz = 420f;
	public float turnDegreesPerTick = 4f;
	public boolean debug = false;

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
		for (int i = 0; i < 12; i++) {
			config.actions[i] = MovementAction.parse(
					props.getProperty("note." + NoteTracker.NOTE_NAMES[i]), DEFAULT_ACTIONS[i]);
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
		sb.append("# Note -> action. Any octave of the note triggers it.\n");
		sb.append("# Actions: ").append(String.join(", ", actionNames())).append("\n");
		for (int i = 0; i < 12; i++) {
			sb.append("note.").append(NoteTracker.NOTE_NAMES[i]).append('=').append(actions[i]).append('\n');
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
