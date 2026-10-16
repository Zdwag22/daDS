import com.bassmove.audio.BassAnalyzer;
import com.bassmove.audio.NoteTracker;
import java.util.Random;

/** Standalone check of the audio pipeline with synthetic bass tones (no Minecraft needed). */
public class PitchTest {
	static final float SR = 44100f;
	static final Random RNG = new Random(42);
	static int failures = 0;

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
		Object[][] notes = {
			{"B0 (5-string low B)", 30.87, 11}, {"E1 (open E)", 41.20, 4}, {"F1", 43.65, 5},
			{"A1 (open A)", 55.00, 9}, {"D2 (open D)", 73.42, 2}, {"G2 (open G)", 98.00, 7},
			{"C3", 130.81, 0}, {"E3", 164.81, 4}, {"G4 (24th fret G)", 392.00, 7},
		};
		for (Object[] n : notes) {
			BassAnalyzer a = newAnalyzer();
			feed(a, tone((double) n[1], 0.6, 0.35, 0.004));
			int pc = a.pitchClass();
			check((String) n[0], pc == (int) n[2],
				String.format("expected %s, got %s (%.2f Hz detected)", NoteTracker.nameOf((int) n[2]), NoteTracker.nameOf(pc), a.lastHz()));
		}

		// Detune check: the same E, 20 cents flat and sharp, should still read as E.
		for (double cents : new double[] {-20, 20}) {
			BassAnalyzer a = newAnalyzer();
			feed(a, tone(41.20 * Math.pow(2, cents / 1200), 0.6, 0.35, 0.004));
			check("E1 detuned " + (int) cents + " cents", a.pitchClass() == 4, "got " + NoteTracker.nameOf(a.pitchClass()));
		}

		// Room noise alone must not trigger anything.
		BassAnalyzer quiet = newAnalyzer();
		feed(quiet, silence(1.0, 0.004));
		check("Room noise ignored", quiet.pitchClass() == -1, "pitch class " + quiet.pitchClass());

		// Sequence: E -> A -> silence -> D, checked at the end of each segment.
		BassAnalyzer seq = newAnalyzer();
		feed(seq, tone(41.20, 0.5, 0.35, 0.004));
		int first = seq.pitchClass();
		feed(seq, tone(55.00, 0.5, 0.35, 0.004));
		int second = seq.pitchClass();
		feed(seq, silence(0.5, 0.004));
		int third = seq.pitchClass();
		feed(seq, tone(73.42, 0.5, 0.35, 0.004));
		int fourth = seq.pitchClass();
		check("Sequence E,A,rest,D", first == 4 && second == 9 && third == -1 && fourth == 2,
			String.format("%s %s %s %s", NoteTracker.nameOf(first), NoteTracker.nameOf(second), NoteTracker.nameOf(third), NoteTracker.nameOf(fourth)));

		System.out.println(failures == 0 ? "\nAll checks passed." : "\n" + failures + " check(s) FAILED.");
		System.exit(failures == 0 ? 0 : 1);
	}
}
