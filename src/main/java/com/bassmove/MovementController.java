package com.bassmove;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Applies the action for the note currently being played by holding the matching game keys
 * (the same thing pressing them would do) and rotating the camera for TURN_* / LOOK_*.
 * Only keys this class pressed are ever released by it.
 */
public final class MovementController {
	/** Entity.turn() multiplies by this, as the mouse does. */
	private static final double TURN_SCALE = 0.15;

	private final BassMoveConfig config;
	private final Set<KeyMapping> held = new HashSet<>();

	public MovementController(BassMoveConfig config) {
		this.config = config;
	}

	/** Call once per client tick, before the player ticks. */
	public void tick(Minecraft mc, boolean active, int midiNote) {
		if (!active || mc.player == null || mc.gui.screen() != null) {
			releaseAll();
			return;
		}

		MovementAction action = midiNote < 0 ? MovementAction.NONE : config.actionFor(midiNote);

		Set<KeyMapping> wanted = new HashSet<>(keysFor(action, mc.options));
		for (KeyMapping key : new HashSet<>(held)) {
			if (!wanted.contains(key)) {
				key.setDown(false);
				held.remove(key);
			}
		}
		for (KeyMapping key : wanted) {
			if (held.add(key)) {
				key.setDown(true);
			}
		}

		double step = config.turnDegreesPerTick / TURN_SCALE;
		switch (action) {
			case TURN_LEFT -> mc.player.turn(-step, 0);
			case TURN_RIGHT -> mc.player.turn(step, 0);
			case LOOK_UP -> mc.player.turn(0, -step);
			case LOOK_DOWN -> mc.player.turn(0, step);
			default -> { }
		}
	}

	public void releaseAll() {
		for (KeyMapping key : held) {
			key.setDown(false);
		}
		held.clear();
	}

	private static List<KeyMapping> keysFor(MovementAction action, Options o) {
		return switch (action) {
			case FORWARD -> List.of(o.keyUp);
			case BACK -> List.of(o.keyDown);
			case LEFT -> List.of(o.keyLeft);
			case RIGHT -> List.of(o.keyRight);
			case JUMP -> List.of(o.keyJump);
			case JUMP_FORWARD -> List.of(o.keyUp, o.keyJump);
			case SNEAK -> List.of(o.keyShift);
			case SPRINT_FORWARD -> List.of(o.keyUp, o.keySprint);
			case ATTACK -> List.of(o.keyAttack);
			case USE -> List.of(o.keyUse);
			default -> List.of();
		};
	}
}
