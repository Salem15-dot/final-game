import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SoundManager — paths now match the EXACT files in your assets folder.
 *
 * Why nothing was playing before:
 *   - Code was looking for "battle/swing.wav" but your folder only has
 *     sword-unsheathe2/3/4/5.wav. Same for magic1.wav, spell.wav, etc.
 *   - Music was looking for battleThemeA.wav but you only have .mp3.
 *   - When a file is missing, the old code skipped silently — so you had
 *     no idea most of the events were silent.
 *
 * What changed:
 *   - All paths now match real files I can see in your screenshot.
 *   - On startup, this prints "[SOUND] OK <file>" for everything that
 *     loaded, and "[SOUND] MISSING <file>" / "[SOUND] FAILED <file>"
 *     for anything that didn't. You can see what's working in the terminal.
 *   - Music: still requires a .wav. If battleThemeA.wav is missing, the
 *     game just runs silent for music (no crash). Convert your .mp3 with
 *     Audacity or convertio.co once and drop it in assets/.
 */
public final class SoundManager {

    private static final Path ASSET_ROOT = Path.of("assets");

    private static final Map<String, Clip> CLIPS = new HashMap<>();
    private static final Map<String, Long> LAST_PLAYED_MS = new ConcurrentHashMap<>();

    private static Clip musicClip;
    private static volatile Process musicProcess;
    private static boolean initialized = false;

    private SoundManager() { }

    // ─────────────────────────────────────────────────────────────────────
    // Sound files mapped to events. Every path here is taken straight from
    // your assets folder screenshot. If you add or rename files, update
    // these constants and that's the only change needed.
    // ─────────────────────────────────────────────────────────────────────

    // Player
    private static final String SND_PLAYER_WALK   = "walking voices/slime1.wav";       // if missing, walking is silent
    private static final String SND_PLAYER_ATTACK = "battle/sword-unsheathe2.wav";     // sword swing
    private static final String SND_PLAYER_HURT   = "battle/sword-unsheathe3.wav";

    // Pickups / UI
    private static final String SND_COIN          = "battle/sword-unsheathe4.wav";
    private static final String SND_POWERUP       = "battle/sword-unsheathe5.wav";
    private static final String SND_LEVEL_UP      = "battle/sword-unsheathe5.wav";     // reuse — no spell.wav
    private static final String SND_PURCHASE      = "battle/sword-unsheathe2.wav";

    // Goblin
    private static final String SND_GOBLIN_ATTACK = "goblin-voices/goblin-attack.wav";
    private static final String SND_GOBLIN_HURT   = "goblin-voices/gobline-reciving-damage.wav";
    private static final String SND_GOBLIN_DEATH  = "goblin-voices/gobline-dying.wav";

    // Wolf
    private static final String SND_WOLF_ATTACK   = "wolf-voics/wolf-attack.wav";
    private static final String SND_WOLF_HURT     = "wolf-voics/wolf-recive-damage.wav";
    private static final String SND_WOLF_DEATH    = "wolf-voics/wolf-attack2.wav";     // no dedicated death — reuse attack2

    // Rogue
    private static final String SND_ROGUE_ATTACK  = "rogue/roguer-attack.wav";
    private static final String SND_ROGUE_HURT    = "rogue/roguer-recive-damage.wav";
    private static final String SND_ROGUE_DEATH   = "rogue/rogue-die.wav";

    // Background music — must be .wav. Convert battleThemeA.mp3 → .wav once.
    private static final String SND_MUSIC         = "battleThemeA.wav";

    // ─────────────────────────────────────────────────────────────────────
    // Init — preload every clip ONCE. Prints a diagnostic line per file so
    // you can see what worked and what didn't in the console.
    // ─────────────────────────────────────────────────────────────────────
    private static synchronized void init() {
        if (initialized) return;
        initialized = true;

        System.out.println("[SOUND] Loading audio from: " + ASSET_ROOT.toAbsolutePath());

        String[] paths = {
            SND_PLAYER_WALK, SND_PLAYER_ATTACK, SND_PLAYER_HURT,
            SND_COIN, SND_POWERUP, SND_LEVEL_UP, SND_PURCHASE,
            SND_GOBLIN_ATTACK, SND_GOBLIN_HURT, SND_GOBLIN_DEATH,
            SND_WOLF_ATTACK, SND_WOLF_HURT, SND_WOLF_DEATH,
            SND_ROGUE_ATTACK, SND_ROGUE_HURT, SND_ROGUE_DEATH,
            SND_MUSIC
        };

        for (String p : paths) {
            preload(p);
        }

        Runtime.getRuntime().addShutdownHook(
            new Thread(SoundManager::shutdown, "SoundManager-Shutdown"));
    }

    /**
     * Load one clip. Tries the simple path first (most WAVs work this way),
     * and falls back to format conversion if the WAV has an unusual format
     * like 24-bit or floating-point samples.
     */
    private static void preload(String relativePath) {
        File file = ASSET_ROOT.resolve(relativePath).toFile();
        if (!file.exists()) {
            System.out.println("[SOUND] MISSING " + relativePath);
            return;
        }

        // Attempt 1 — simple direct open. Works for normal PCM WAVs.
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            CLIPS.put(relativePath, clip);
            System.out.println("[SOUND] OK      " + relativePath);
            return;
        } catch (Exception simpleFail) {
            // Fall through to attempt 2.
        }

        // Attempt 2 — convert to standard 16-bit PCM and try again.
        try (AudioInputStream raw = AudioSystem.getAudioInputStream(file)) {
            AudioFormat src = raw.getFormat();
            AudioFormat target = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                src.getSampleRate(),
                16,
                src.getChannels(),
                src.getChannels() * 2,
                src.getSampleRate(),
                false
            );
            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(target, raw)) {
                Clip clip = AudioSystem.getClip();
                clip.open(decoded);
                CLIPS.put(relativePath, clip);
                System.out.println("[SOUND] OK*     " + relativePath + " (after format conversion)");
                return;
            }
        } catch (Exception convertFail) {
            System.out.println("[SOUND] FAILED  " + relativePath
                + " — " + convertFail.getClass().getSimpleName()
                + ": " + convertFail.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Music
    // ─────────────────────────────────────────────────────────────────────
    public static void playMusic() {
        init();
        if (musicClip != null && musicClip.isRunning()) return;

        Clip clip = CLIPS.get(SND_MUSIC);
        if (clip != null) {
            applyGain(clip, -10.0f);
            clip.setFramePosition(0);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            musicClip = clip;
            return;
        }

        // WAV not found/loaded — try MP3 fallback via PowerShell MediaPlayer
        File mp3 = ASSET_ROOT.resolve("battleThemeA.mp3").toFile();
        if (mp3.exists()) {
            try {
                String uri = mp3.getAbsolutePath().replace("\\", "/");
                // =============================================================================
// SINGLE EDIT — in SoundManager.java, inside the playMusic() method.
//
// Find the PowerShell command string (the line starting with:
//     String command = "Add-Type -AssemblyName presentationCore; "
// and replace the WHOLE string assignment with the version below.
//
// Nothing else in the file needs to change. The .wav path (which uses
// Clip.LOOP_CONTINUOUSLY) already loops correctly. This fix is only for
// the MP3 fallback path.
// =============================================================================

String command =
    "Add-Type -AssemblyName presentationCore; "
  + "$player = New-Object System.Windows.Media.MediaPlayer; "
  + "$player.Volume = 0.25; "
  // Register an event handler that fires the moment the song ends.
  // Inside the handler we rewind to position zero and call Play() again,
  // giving us a perfect seamless loop for as long as the JVM keeps the
  // PowerShell process alive (i.e. as long as the window is open).
  + "Register-ObjectEvent -InputObject $player -EventName MediaEnded "
  +   "-Action { $player.Position = [TimeSpan]::Zero; $player.Play() } "
  +   "| Out-Null; "
  + "$player.Open([uri]'file:///" + uri + "'); "
  + "$player.Play(); "
  // Idle loop so the PowerShell process stays alive and the event handler
  // can fire each time the song finishes. Killed when the JVM shuts down
  // and your stopMusic() / shutdown hook calls musicProcess.destroy().
  + "while ($true) { Start-Sleep -Seconds 1 }";


// =============================================================================
// HOW IT WORKS
//
//  1. MediaPlayer plays the MP3 once (Play()).
//  2. When the song ends, MediaPlayer fires the MediaEnded event.
//  3. Our handler resets Position to zero and calls Play() again.
//  4. Step 2 repeats forever — that's the loop.
//
// The Start-Sleep idle loop is unchanged; it just keeps the PowerShell
// process alive so the event handler stays subscribed. Your existing
// shutdown hook still calls musicProcess.destroy() on exit, so the
// music still stops cleanly when the window closes.
//
// No other method in SoundManager changes. MethodTester is unaffected.
// =============================================================================
                ProcessBuilder builder = new ProcessBuilder(
                        "powershell",
                        "-NoProfile",
                        "-WindowStyle", "Hidden",
                        "-Command",
                        command
                );
                builder.redirectErrorStream(true);
                musicProcess = builder.start();
                System.out.println("[SOUND] Playing MP3 fallback: " + mp3.getName());
                return;
            } catch (Exception ignored) {
                // fall through — we will print missing message below
            }
        }

        System.out.println("[SOUND] No music ? convert assets/battleThemeA.mp3 to assets/battleThemeA.wav");
    }

    public static void stopMusic() {
        if (musicClip != null) {
            try {
                musicClip.stop();
                musicClip.setFramePosition(0);
            } catch (Exception ignored) { }
            musicClip = null;
        }
        if (musicProcess != null) {
            try {
                musicProcess.destroy();
            } catch (Exception ignored) { }
            musicProcess = null;
        }
    }

    public static void shutdown() {
        stopMusic();
        for (Clip c : CLIPS.values()) {
            try { c.stop(); c.close(); } catch (Exception ignored) { }
        }
        CLIPS.clear();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public per-event helpers
    // ─────────────────────────────────────────────────────────────────────

    public static void playPlayerWalk() {
        if (!throttle("playerWalk", 320)) return;
        play(SND_PLAYER_WALK, -18.0f);
    }
    public static void playPlayerAttack() {
        if (!throttle("playerAttack", 250)) return;
        play(SND_PLAYER_ATTACK, -8.0f);
    }
    public static void playPlayerHurt() {
        if (!throttle("playerHurt", 300)) return;
        play(SND_PLAYER_HURT, -10.0f);
    }
    public static void playCoin() {
        if (!throttle("coin", 80)) return;
        play(SND_COIN, -8.0f);
    }
    public static void playPowerUp() {
        if (!throttle("powerUp", 200)) return;
        play(SND_POWERUP, -8.0f);
    }
    public static void playLevelUp()  { play(SND_LEVEL_UP, -6.0f); }
    public static void playPurchase() {
        if (!throttle("purchase", 150)) return;
        play(SND_PURCHASE, -8.0f);
    }

    public static void playGoblinAttack() {
        if (!throttle("goblinAttack", 400)) return;
        play(SND_GOBLIN_ATTACK, -6.0f);
    }
    public static void playGoblinDamage() {
        if (!throttle("goblinDamage", 250)) return;
        play(SND_GOBLIN_HURT, -6.0f);
    }
    public static void playGoblinDeath() { play(SND_GOBLIN_DEATH, -6.0f); }

    public static void playWolfAttack() {
        if (!throttle("wolfAttack", 400)) return;
        play(SND_WOLF_ATTACK, -6.0f);
    }
    public static void playWolfDamage() {
        if (!throttle("wolfDamage", 250)) return;
        play(SND_WOLF_HURT, -6.0f);
    }
    public static void playWolfDeath() { play(SND_WOLF_DEATH, -6.0f); }

    public static void playRogueAttack() {
        if (!throttle("rogueAttack", 400)) return;
        play(SND_ROGUE_ATTACK, -6.0f);
    }
    public static void playRogueDamage() {
        if (!throttle("rogueDamage", 250)) return;
        play(SND_ROGUE_HURT, -6.0f);
    }
    public static void playRogueDeath() { play(SND_ROGUE_DEATH, -6.0f); }

    // Legacy aliases used by older code.
    public static void playHit()        { playPlayerAttack(); }
    public static void playHurt()       { playPlayerHurt(); }
    public static void playEnemyDown()  { /* per-enemy death sounds handle this */ }

    // ─────────────────────────────────────────────────────────────────────
    // Internals
    // ─────────────────────────────────────────────────────────────────────

    private static boolean throttle(String key, long minimumDelayMs) {
        long now = System.currentTimeMillis();
        Long previous = LAST_PLAYED_MS.get(key);
        if (previous != null && now - previous < minimumDelayMs) return false;
        LAST_PLAYED_MS.put(key, now);
        return true;
    }

    private static void play(String relativePath, float gainDb) {
        init();
        Clip clip = CLIPS.get(relativePath);
        if (clip == null) return;
        try {
            clip.stop();
            clip.setFramePosition(0);
            applyGain(clip, gainDb);
            clip.start();
        } catch (Exception ignored) { }
    }

    private static void applyGain(Clip clip, float gainDb) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl c = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float v = Math.max(c.getMinimum(), Math.min(c.getMaximum(), gainDb));
                c.setValue(v);
            }
        } catch (Exception ignored) { }
    }
}