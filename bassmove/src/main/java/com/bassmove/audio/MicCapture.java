package com.bassmove.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

/**
 * Opens a microphone / audio-interface input with Java Sound and streams its first channel
 * into a callback on a background thread. Closing it releases the device completely.
 */
public final class MicCapture implements AutoCloseable {
	/** Formats tried in order. Interfaces often only offer 48 kHz or stereo. */
	private static final float[] RATES = {44100f, 48000f};
	private static final int[] CHANNELS = {1, 2};

	public interface SampleSink {
		void accept(float[] mono, int count);
	}

	private final String deviceHint;
	private volatile boolean running;
	private Thread thread;
	private TargetDataLine line;
	private float sampleRate;
	private int channels;
	private String deviceName = "";

	public MicCapture(String deviceHint) {
		this.deviceHint = deviceHint == null ? "" : deviceHint.trim().toLowerCase();
	}

	/** Names of every input device Java Sound can see, for the config's "device" option. */
	public static List<String> listInputDevices() {
		List<String> names = new ArrayList<>();
		for (Mixer.Info info : AudioSystem.getMixerInfo()) {
			Mixer mixer = AudioSystem.getMixer(info);
			if (mixer.getTargetLineInfo().length > 0) {
				names.add(info.getName());
			}
		}
		return names;
	}

	/**
	 * Opens the device and works out the sample rate. Must be called before {@link #start}.
	 * @return false if no usable input was found
	 */
	public boolean open(Consumer<String> log) {
		try {
			if (deviceHint.isEmpty()) {
				for (float rate : RATES) {
					for (int ch : CHANNELS) {
						AudioFormat fmt = new AudioFormat(rate, 16, ch, true, false);
						DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
						if (AudioSystem.isLineSupported(info)) {
							TargetDataLine candidate = (TargetDataLine) AudioSystem.getLine(info);
							if (adopt(candidate, fmt, "system default")) {
								return true;
							}
						}
					}
				}
			}
			for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
				if (!deviceHint.isEmpty() && !mixerInfo.getName().toLowerCase().contains(deviceHint)) {
					continue;
				}
				Mixer mixer = AudioSystem.getMixer(mixerInfo);
				for (float rate : RATES) {
					for (int ch : CHANNELS) {
						AudioFormat fmt = new AudioFormat(rate, 16, ch, true, false);
						DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
						if (mixer.isLineSupported(info)) {
							TargetDataLine candidate = (TargetDataLine) mixer.getLine(info);
							if (adopt(candidate, fmt, mixerInfo.getName())) {
								return true;
							}
						}
					}
				}
			}
		} catch (LineUnavailableException | SecurityException | IllegalArgumentException e) {
			log.accept("Could not open microphone: " + e);
		}
		return false;
	}

	private boolean adopt(TargetDataLine candidate, AudioFormat fmt, String name) {
		try {
			candidate.open(fmt, (int) fmt.getSampleRate() * fmt.getFrameSize() / 4);
			this.line = candidate;
			this.sampleRate = fmt.getSampleRate();
			this.channels = fmt.getChannels();
			this.deviceName = name;
			return true;
		} catch (LineUnavailableException | SecurityException e) {
			return false;
		}
	}

	public float sampleRate() {
		return sampleRate;
	}

	public String deviceName() {
		return deviceName;
	}

	public void start(SampleSink sink) {
		running = true;
		line.start();
		thread = new Thread(() -> readLoop(sink), "BassMove-Mic");
		thread.setDaemon(true);
		thread.start();
	}

	private void readLoop(SampleSink sink) {
		int frameBytes = 2 * channels;
		byte[] buffer = new byte[1024 * frameBytes];
		float[] mono = new float[1024];
		while (running) {
			int read = line.read(buffer, 0, buffer.length);
			if (read <= 0) {
				continue;
			}
			int frames = read / frameBytes;
			for (int i = 0; i < frames; i++) {
				int idx = i * frameBytes;
				short s = (short) ((buffer[idx + 1] << 8) | (buffer[idx] & 0xFF));
				mono[i] = s / 32768f;
			}
			sink.accept(mono, frames);
		}
	}

	@Override
	public void close() {
		running = false;
		if (line != null) {
			line.stop();
			line.close();
			line = null;
		}
		if (thread != null) {
			try {
				thread.join(500);
			} catch (InterruptedException ignored) {
				Thread.currentThread().interrupt();
			}
			thread = null;
		}
	}
}
