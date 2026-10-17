import com.bassmove.audio.BassAnalyzer;
import com.bassmove.audio.NoteTracker;
import java.util.Random;

/** Standalone check of the audio pipeline with synthetic bass tones (no Minecraft needed). */
public class PitchTest {
	static final float SR = 44100f;
	static final Random RNG = new Random(42);
	static int failures = 0;

	static double hzOf(int midi) {
		return 440.0 * Math.pow(2, (midi - 69) / 12.0);
	}

	/** Bass-like tone: weak fundamental, strong 2nd harmonic, decaying, plus noise. */
	static float[] tone(double hz, double seconds, double amp, double noise) {
		int n = (int) (seconds * SR);
		float[] out = new float[n];
		double[] harmonics = {0.45, 1.0, 0.6, 0.3, 0.15};
		for (int i = 0; i < n; i++) {
			double t = i / SR;
			double env = Math.exp(-t * 1.5);
			double s = 0;
			for (int h = 0; h < harmonics.length; h++) {
				s += harmonics[h] * Math.sin(2 * Math.PI * hz * (h + 1) * t);
			}
			out[i] = (float) (amp * env * s / 2.0 + noise * (RNG.nextDouble() * 2 - 1));
		}
		return out;
	}

	static float[] silence(double seconds, double noise) {
		float[] out = new float[(int) (seconds * SR)];
		for (int i = 0; i < out.length; i++) out[i] = (float) (noise * (RNG.nextDouble() * 2 - 1));
		return out;
	}

	static BassAnalyzer newAnalyzer() {
		return new BassAnalyzer(SR, 30f, 420f, 0.02f, 0.15f, 2, 3);
	}

	static void feed(BassAnalyzer a, float[] samples) {
		for (int i = 0; i < samples.length; i += 1024) {
			a.accept(java.util.Arrays.copyOfRange(samples, i, Math.min(samples.length, i + 1024)), Math.min(1024, samples.length - i));
		}
	}

	static void check(String name, boolean ok, String detail) {
		System.out.printf("%-34s %s  %s%n", name, ok ? "PASS" : "FAIL", detail);
		if (!ok) failures++;
	}

	public static void main(String[] args) {
		// Name parsing / formatting
		check("midiOf(E1) == 28", NoteTracker.midiOf("E1") == 28, "got " + NoteTracker.midiOf("E1"));
		check("midiOf(F#1) == 30", NoteTracker.midiOf("F#1") == 30, "got " + NoteTracker.midiOf("F#1"));
		check("nameOf(43) == G2", NoteTracker.nameOf(43).equals("G2"), NoteTracker.nameOf(43));
		check("midiOf(E) rejected", NoteTracker.midiOf("E") == -1, "got " + NoteTracker.midiOf("E"));

		// Every note in the default layout: open strings, 1st fret, 2nd fret of a 4-string bass.
		String[] layout = {
			"E1", "A1", "D2", "G2",     // open
			"F1", "A#1", "D#2", "G#2",  // 1st fret
			"F#1", "B1", "E2", "A2",    // 2nd fret
		};
		for (String name : layout) {
			int midi = NoteTracker.midiOf(name);
			BassAnalyzer a = newAnalyzer();
			feed(a, tone(hzOf(midi), 0.6, 0.35, 0.004));
			check("Note " + name, a.midiNote() == midi,
				String.format("expected %s, got %s (%.2f Hz)", name, NoteTracker.nameOf(a.midiNote()), a.lastHz()));
		}

		// Each fret on the same string must be told apart (semitone neighbours), 20 cents flat/sharp too.
		for (double cents : new double[] {-20, 20}) {
			for (String name : new String[] {"E1", "F1", "F#1"}) {
				int midi = NoteTracker.midiOf(name);
				BassAnalyzer a = newAnalyzer();
				feed(a, tone(hzOf(midi) * Math.pow(2, cents / 1200), 0.6, 0.35, 0.004));
				check(name + " detuned " + (int) cents + " cents", a.midiNote() == midi, "got " + NoteTracker.nameOf(a.midiNote()));
			}
		}

		// Room noise alone must not trigger anything.
		BassAnalyzer quiet = newAnalyzer();
		feed(quiet, silence(1.0, 0.004));
		check("Room noise ignored", quiet.midiNote() == -1, "note " + quiet.midiNote());

		// Sequence: open E -> E 1st fret -> E 2nd fret -> rest -> open A.
		BassAnalyzer seq = newAnalyzer();
		int[] expect = {28, 29, 30, -1, 33};
		double[] hz = {hzOf(28), hzOf(29), hzOf(30), 0, hzOf(33)};
		StringBuilder got = new StringBuilder();
		boolean ok = true;
		for (int i = 0; i < expect.length; i++) {
			feed(seq, hz[i] > 0 ? tone(hz[i], 0.5, 0.35, 0.004) : silence(0.5, 0.004));
			ok &= seq.midiNote() == expect[i];
			got.append(NoteTracker.nameOf(seq.midiNote())).append(' ');
		}
		check("Sequence E1 F1 F#1 rest A1", ok, got.toString().trim());

		System.out.println(failures == 0 ? "\nAll checks passed." : "\n" + failures + " check(s) FAILED.");
		System.exit(failures == 0 ? 0 : 1);
	}
}
