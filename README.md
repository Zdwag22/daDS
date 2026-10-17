# Bass Move (Fabric, Minecraft 26.2)

Plug in a bass (or play near a mic), press **B** in game, and the notes you play become movement.
Client-side only, works in singleplayer and on servers (it just presses your movement keys).

## Controls (4-string bass, standard tuning E A D G)

| String | Open: **move** | 1st fret: **look** | 2nd fret: **act** |
|--------|----------------|--------------------|--------------------|
| E | walk forward | look up | jump |
| A | strafe left  | turn left | break blocks (hold) |
| D | walk back    | look down | jump forward |
| G | strafe right | turn right | place / use |

Notes are matched exactly (octave included: E1, F1, F#1, A1 ...), so the same note played higher up the neck
does nothing. That keeps the controls to the first three frets of each string and stops stray notes moving you.
Change anything in `config/bassmove.properties` (created on first launch), for example `note.E1=SPRINT_FORWARD`.
Actions: `FORWARD, BACK, LEFT, RIGHT, JUMP, JUMP_FORWARD, SNEAK, SPRINT_FORWARD, TURN_LEFT, TURN_RIGHT,
LOOK_UP, LOOK_DOWN, ATTACK, USE, NONE`. If you had an older config file, delete it so it regenerates.

## Building

Needs **JDK 25**. This project was written against Fabric Loom 1.17 (non-remapping), Fabric Loader 0.19.3+
and Fabric API for 26.2.

1. Easiest: generate a blank project at <https://fabricmc.net/develop> (Minecraft 26.2), then copy this
   project's `src/` folder over the generated one. That also gives you the Gradle wrapper.
   Alternatively use the `build.gradle`, `gradle.properties` and `settings.gradle` here and run `gradle wrapper` once.
2. `./gradlew build` and take the jar from `build/libs/` (not the `-sources` one).
3. Drop it in your `mods` folder with Fabric API.

If `fabric_version` in `gradle.properties` isn't found, pick a current `+26.2` build from the Fabric site.

## Using it

- **B** toggles it (rebindable under Controls > Bass Move). The mic is only open while it's on.
- Opens menus -> it releases all keys and pauses until you're back in the world.
- Choose an audio-interface input by putting part of its name in `device=`. All input names are written to
  `logs/latest.log` when you toggle on.
- Room noise moving you around, or soft notes missed? Set `debug=true`, watch the logged input level
  while playing and idle, and put `noiseGate` between the two.
- On macOS/Windows, the launcher (or Java) needs microphone permission in system settings.
- Turning is smooth while a note rings (`turnDegreesPerTick`). Latency is roughly 0.2 seconds.
- The mod only releases keys it pressed itself. If you hold the same key physically while a note ends, release and re-press it.
- It's equivalent to a macro, so check a server's rules before using it there.

## Testing the pitch detection without the game

```
javac -d out src/main/java/com/bassmove/audio/*.java tools/PitchTest.java
java -cp out PitchTest
```

Uses synthetic bass tones (B0 to G4, detuned notes, noise, note sequences) and needs no Minecraft.

## Layout

- `audio/YinPitchDetector` - YIN pitch detection, good on low notes
- `audio/NoteTracker` - debounces pitches into stable notes
- `audio/BassAnalyzer` - downsampling, noise gate, ties the two together
- `audio/MicCapture` - Java Sound input on a background thread
- `MovementController` - turns notes into held keys and camera turns
- `BassMoveClient` - entrypoint, toggle key, lifecycle
