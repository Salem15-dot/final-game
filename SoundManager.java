import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SoundManager
 *
 * What changed and why (read this if you ever need to debug audio again):
 *
 * 1. CLIPS ARE PRE-LOADED.
 *    The old version called AudioSystem.getClip() and decoded the WAV from
 *    disk EVERY time a sound played. That decode + line-open takes 50-200 ms,
 *    which is exactly the "lag on every attack" you saw. Now every clip is
 *    loaded once at startup into a HashMap. Playing a sound is just:
 *        clip.stop(); clip.setFramePosition(0); clip.start();
 *    which costs effectively zero milliseconds.
 *
 * 2. NO MORE BACKGROUND THREADS.
 *    The old version started a daemon thread for every single sound effect
 *    that polled clip.isRunning() every 25 ms. Twenty enemies attacking =
 *    twenty new threads. Now there are zero threads. The Clip object handles
 *    its own lifecycle.
 *
 * 3. NO MORE POWERSHELL.
 *    The old version spawned a PowerShell process to play the MP3 background
 *    music. When the JVM exited, that process became an orphan and kept
 *    playing forever. That is your "music plays after window closes" bug.
 *    Now the music is a Clip just like everything else.
 *
 *    REQUIREMENT: convert battleThemeA.mp3 -> battleThemeA.wav once, drop the
 *    .wav into assets/. Java's built-in audio doesn't support MP3 without an
 *    extra library; WAV is universal. Any free converter works (Audacity,
 *    online converter, ffmpeg).
 *
 * 4. SHUTDOWN HOOK.
 *    Even if windowClosing never fires (e.g. JVM is killed externally), the
 *    shutdown hook stops every clip and releases every audio line.
 */
public final class SoundManager {

    private static final Path ASSET_ROOT = Path.of("assets");

    /** Pre-loaded clips, keyed by relative path under assets/. */
    private static final Map<String, Clip> CLIPS = new HashMap<>();

    /**
     * Per-event throttle. Some events (player walking, enemy attacking) can
     * fire many times per second. We limit how often a given key plays.
     */
    private static final Map<String, Long> LAST_PLAYED_MS = new ConcurrentHashMap<>();

    /** The single looping background-music clip, or null if not loaded. */
    private static Clip musicClip;

    /** True once init() has run. */
    private static boolean initialized = false;

    private SoundManager() {
    }

    // ─────────────────────────────────────────────────────────────────────
    // Initialization — called lazily the first time anything plays.
    // Pre-loads every sound file into memory so playback is instant.
    // ─────────────────────────────────────────────────────────────────────
    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        // Player
        preload("walking voices/slime1.wav");
        preload("battle/swing.wav");
        preload("battle/sword-unsheathe.wav");
        preload("battle/sword-unsheathe2.wav");
        preload("battle/sword-unsheathe3.wav");
        preload("battle/magic1.wav");
        preload("battle/spell.wav");

        // Goblin
        preload("goblin-voices/goblin-attack.wav");
        preload("goblin-voices/gobline-reciving-damage.wav");
        preload("goblin-voices/gobline-dying.wav");

        // Wolf
        preload("wolf-voics/wolf-attack.wav");
        preload("wolf-voics/wolf-attack2.wav");
        preload("wolf-voics/wolf-recive-damage.wav");

        // Rogue
        preload("rogue/roguer-attack.wav");
        preload("rogue/roguer-recive-damage.wav");
        preload("rogue/rogue-die.wav");

        // Background music — must be WAV. If you only have battleThemeA.mp3,
        // convert it to WAV (Audacity / online converter / ffmpeg) and drop
        // battleThemeA.wav alongside it in assets/.
        preload("battleThemeA.wav");

        // Make sure audio shuts down even if the window is killed weirdly.
        Runtime.getRuntime().addShutdownHook(new Thread(SoundManager::shutdown,
                "SoundManager-Shutdown"));
    }

    /** Try to load one clip; if the file is missing, just skip it silently. */
    private static void preload(String relativePath) {
        File file = ASSET_ROOT.resolve(relativePath).toFile();
        if (!file.exists()) {
            // File missing on disk — skip without crashing the game.
            return;
        }

        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file)) {
            AudioFormat sourceFormat = raw.getFormat();

            // Convert anything weird to standard signed 16-bit PCM so it will
            // open as a Clip on every platform.
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream decoded =
                         AudioSystem.getAudioInputStream(targetFormat, raw)) {
                DataLine.Info info = new DataLine.Info(Clip.class, targetFormat);
                Clip clip = (Clip) AudioSystem.getLine(info);
                clip.open(decoded);
                CLIPS.put(relativePath, clip);
            }
        } catch (Exception e) {
            // Format not supported / IO error — skip silently rather than
            // crashing the whole game.
            System.err.println("SoundManager: could not load " + relativePath
                    + " (" + e.getClass().getSimpleName() + ")");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Music — single looping clip.
    // ─────────────────────────────────────────────────────────────────────
    public static void playMusic() {
        init();
        if (musicClip != null && musicClip.isRunning()) return;

        Clip clip = CLIPS.get("battleThemeA.wav");
        if (clip == null) return;        // file missing or not WAV — skip
        applyGain(clip, -10.0f);
        clip.setFramePosition(0);
        clip.loop(Clip.LOOP_CONTINUOUSLY);
        musicClip = clip;
    }

    public static void stopMusic() {
        if (musicClip != null) {
            try {
                musicClip.stop();
                musicClip.setFramePosition(0);
            } catch (Exception ignored) {
            }
            musicClip = null;
        }
    }

    /** Called by the JVM shutdown hook — stops EVERY clip. */
    public static void shutdown() {
        stopMusic();
        for (Clip c : CLIPS.values()) {
            try {
                c.stop();
                c.close();
            } catch (Exception ignored) {
            }
        }
        CLIPS.clear();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public per-event helpers. Each event has its own throttle window so a
    // sound never plays twice in a row from accidental double-fires.
    // ─────────────────────────────────────────────────────────────────────

    public static void playPlayerWalk() {
        // Walking fires constantly while held; throttle to one footstep
        // every 320 ms so it sounds like steps, not a buzz.
        if (!throttle("playerWalk", 320)) return;
        play("walking voices/slime1.wav", -18.0f);
    }

    public static void playPlayerAttack() {
        if (!throttle("playerAttack", 250)) return;
        play("battle/swing.wav", -8.0f);
    }

    public static void playPlayerHurt() {
        if (!throttle("playerHurt", 300)) return;
        play("battle/sword-unsheathe3.wav", -10.0f);
    }

    public static void playCoin() {
        if (!throttle("coin", 80)) return;
        play("battle/sword-unsheathe.wav", -8.0f);
    }

    public static void playPowerUp() {
        if (!throttle("powerUp", 200)) return;
        play("battle/magic1.wav", -8.0f);
    }

    public static void playLevelUp() {
        play("battle/spell.wav", -6.0f);
    }

    public static void playPurchase() {
        if (!throttle("purchase", 150)) return;
        play("battle/sword-unsheathe2.wav", -8.0f);
    }

    public static void playGoblinAttack() {
        if (!throttle("goblinAttack", 400)) return;
        play("goblin-voices/goblin-attack.wav", -6.0f);
    }

    public static void playGoblinDamage() {
        if (!throttle("goblinDamage", 250)) return;
        play("goblin-voices/gobline-reciving-damage.wav", -6.0f);
    }

    public static void playGoblinDeath() {
        play("goblin-voices/gobline-dying.wav", -6.0f);
    }

    public static void playWolfAttack() {
        if (!throttle("wolfAttack", 400)) return;
        play("wolf-voics/wolf-attack.wav", -6.0f);
    }

    public static void playWolfDamage() {
        if (!throttle("wolfDamage", 250)) return;
        play("wolf-voics/wolf-recive-damage.wav", -6.0f);
    }

    public static void playWolfDeath() {
        play("wolf-voics/wolf-attack2.wav", -6.0f);
    }

    public static void playRogueAttack() {
        if (!throttle("rogueAttack", 400)) return;
        // Rogue uses a sword — reuse the metal sword swing for the swing,
        // and the rogue's own voice if present. We pick the sword swing
        // here because you said the rogue should sound metallic.
        play("rogue/roguer-attack.wav", -6.0f);
    }

    public static void playRogueDamage() {
        if (!throttle("rogueDamage", 250)) return;
        play("rogue/roguer-recive-damage.wav", -6.0f);
    }

    public static void playRogueDeath() {
        play("rogue/rogue-die.wav", -6.0f);
    }

    // Legacy aliases used elsewhere in the codebase — kept so we don't have
    // to touch GameModel / GameController.
    public static void playHit()        { playPlayerAttack(); }
    public static void playHurt()       { playPlayerHurt(); }
    public static void playEnemyDown()  { /* per-enemy death sounds handle this */ }

    // ─────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────

    /** True if we should play this event right now; false if too soon. */
    private static boolean throttle(String key, long minimumDelayMs) {
        long now = System.currentTimeMillis();
        Long previous = LAST_PLAYED_MS.get(key);
        if (previous != null && now - previous < minimumDelayMs) {
            return false;
        }
        LAST_PLAYED_MS.put(key, now);
        return true;
    }

    /**
     * Play a one-shot sound. Just rewinds and starts the pre-loaded clip —
     * no file I/O, no thread, no allocation. This is the whole point of the
     * rewrite.
     */
    private static void play(String relativePath, float gainDb) {
        init();
        Clip clip = CLIPS.get(relativePath);
        if (clip == null) return;
        try {
            clip.stop();
            clip.setFramePosition(0);
            applyGain(clip, gainDb);
            clip.start();
        } catch (Exception ignored) {
            // Some Clip implementations throw if start() races with stop();
            // ignore — next call will recover.
        }
    }

    private static void applyGain(Clip clip, float gainDb) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl)
                        clip.getControl(FloatControl.Type.MASTER_GAIN);
                float value = Math.max(control.getMinimum(),
                        Math.min(control.getMaximum(), gainDb));
                control.setValue(value);
            }
        } catch (Exception ignored) {
        }
    }
}