package com.bassmove;

/** Everything a bass note can be mapped to. Names are used as-is in the config file. */
public enum MovementAction {
	NONE,
	FORWARD,
	BACK,
	LEFT,
	RIGHT,
	JUMP,
	JUMP_FORWARD,
	SNEAK,
	SPRINT_FORWARD,
	TURN_LEFT,
	TURN_RIGHT,
	LOOK_UP,
	LOOK_DOWN,
	ATTACK,
	USE;

	public static MovementAction parse(String text, MovementAction fallback) {
		if (text == null) {
			return fallback;
		}
		try {
			return valueOf(text.trim().toUpperCase().replace(' ', '_'));
		} catch (IllegalArgumentException e) {
			return fallback;
		}
	}
}
