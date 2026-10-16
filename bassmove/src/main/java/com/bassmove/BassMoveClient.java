package com.bassmove;

import com.bassmove.audio.BassAnalyzer;
import com.bassmove.audio.MicCapture;
import com.bassmove.audio.NoteTracker;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BassMoveClient implements ClientModInitializer {
	public static final String MOD_ID = "bassmove";
	private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private BassMoveConfig config;
	private MovementController controller;
	private KeyMapping toggleKey;

	private MicCapture mic;
	private volatile BassAnalyzer analyzer;
	private volatile boolean enabled = false;
	private boolean starting = false;
	private int lastLoggedPitchClass = -2;
	private int debugTicks = 0;

	@Override
	public void onInitializeClient() {
		config = BassMoveConfig.load(FabricLoader.getInstance().getConfigDir().resolve("bassmove.properties"));
		controller = new MovementController(config);

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main"));
		toggleKey = KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.bassmove.toggle", InputConstants.Type.KEYSYM, InputConstants.KEY_B, category));

		ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
	}

	private void onEndTick(Minecraft client) {
		while (toggleKey.consumeClick()) {
			if (enabled || starting) {
				disable(client, "Bass control off");
			} else {
				enable(client);
			}
		}
	}

	private void onStartTick(Minecraft client) {
		if ((enabled || starting) && client.player == null) {
			// Left the world: stop listening so the mic isn't held open on the title screen.
			disable(client, "Bass control off");
			return;
		}
		BassAnalyzer current = analyzer;
		int pitchClass = (enabled && current != null) ? current.pitchClass() : -1;
		controller.tick(client, enabled, pitchClass);

		if (enabled && current != null && config.debug) {
			if (pitchClass != lastLoggedPitchClass) {
				lastLoggedPitchClass = pitchClass;
				LOGGER.info("Note {} ({} Hz) -> {}", NoteTracker.nameOf(pitchClass),
						Math.round(current.lastHz() * 10f) / 10f,
						pitchClass < 0 ? MovementAction.NONE : config.actions[pitchClass]);
			}
			if (++debugTicks >= 40) {
				debugTicks = 0;
				LOGGER.info("Input level (RMS): {}  (noise gate: {})", current.lastRms(), config.noiseGate);
			}
		}
	}

	private void enable(Minecraft client) {
		starting = true;
		tell(client, "Bass control starting...");
		// Opening an audio device can block (permission prompts, slow drivers), so keep it off the game thread.
		Thread opener = new Thread(() -> {
			MicCapture capture = new MicCapture(config.device);
			boolean ok = capture.open(LOGGER::warn);
			client.execute(() -> {
				if (!starting) {
					capture.close();
					return;
				}
				starting = false;
				if (!ok) {
					LOGGER.warn("No usable audio input. Available inputs: {}", MicCapture.listInputDevices());
					tell(client, "Couldn't open the microphone. See the game log for the device list.");
					capture.close();
					return;
				}
				LOGGER.info("Listening on '{}' at {} Hz. Inputs seen: {}",
						capture.deviceName(), capture.sampleRate(), MicCapture.listInputDevices());
				BassAnalyzer newAnalyzer = new BassAnalyzer(capture.sampleRate(), config.minHz, config.maxHz,
						config.noiseGate, config.yinThreshold, config.stableFrames, config.releaseFrames);
				analyzer = newAnalyzer;
				mic = capture;
				capture.start(newAnalyzer::accept);
				enabled = true;
				lastLoggedPitchClass = -2;
				tell(client, "Bass control on - play a note. Press the key again to stop.");
			});
		}, "BassMove-Open");
		opener.setDaemon(true);
		opener.start();
	}

	private void disable(Minecraft client, String message) {
		enabled = false;
		starting = false;
		controller.releaseAll();
		if (mic != null) {
			mic.close();
			mic = null;
		}
		analyzer = null;
		tell(client, message);
	}

	private void tell(Minecraft client, String text) {
		if (client.player != null) {
			client.player.sendSystemMessage(Component.literal("[Bass Move] " + text));
		}
	}
}
