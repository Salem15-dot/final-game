import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SoundManager {
    private static final Path ASSET_ROOT = Path.of("assets");
    private static final Map<String, Long> LAST_PLAYED_MS = new ConcurrentHashMap<>();
    private static volatile Process musicProcess;

    private SoundManager() {
    }

    public static void playMusic() {
        if (musicProcess != null && musicProcess.isAlive()) {
            return;
        }

        File file = ASSET_ROOT.resolve("battleThemeA.mp3").toFile();
        if (!file.exists()) {
            return;
        }

        try {
            String uri = file.getAbsolutePath().replace("\\", "/");
            String command = "Add-Type -AssemblyName presentationCore; "
                    + "$player = New-Object System.Windows.Media.MediaPlayer; "
                    + "$player.Volume = 0.25; "
                    + "$player.Open([uri]'file:///" + uri + "'); "
                    + "$player.Play(); "
                    + "while ($true) { Start-Sleep -Seconds 1 }";
            ProcessBuilder builder = new ProcessBuilder(
                    "powershell",
                    "-NoProfile",
                    "-WindowStyle", "Hidden",
                    "-Command",
                    command
            );
            builder.redirectErrorStream(true);
            musicProcess = builder.start();
        } catch (Exception ignored) {
            stopMusic();
        }
    }

    public static void stopMusic() {
        if (musicProcess != null) {
            musicProcess.destroy();
            musicProcess = null;
        }
    }

    public static void playPlayerWalk() {
        if (!throttle("playerWalk", 260)) {
            return;
        }
        play("walking voices/slime1.wav", -18.0f);
    }

    public static void playPlayerAttack() {
        play("battle/swing.wav", -8.0f);
    }

    public static void playPlayerHurt() {
        play("battle/sword-unsheathe3.wav", -10.0f);
    }

    public static void playCoin() {
        play("battle/sword-unsheathe.wav", -8.0f);
    }

    public static void playPowerUp() {
        play("battle/magic1.wav", -8.0f);
    }

    public static void playLevelUp() {
        play("battle/spell.wav", -8.0f);
    }

    public static void playPurchase() {
        play("battle/sword-unsheathe2.wav", -8.0f);
    }

    public static void playGoblinAttack() {
        play("goblin-voices/goblin-attack.wav", -6.0f);
    }

    public static void playGoblinDamage() {
        play("goblin-voices/gobline-reciving-damage.wav", -6.0f);
    }

    public static void playGoblinDeath() {
        play("goblin-voices/gobline-dying.wav", -6.0f);
    }

    public static void playWolfAttack() {
        play("wolf-voics/wolf-attack.wav", -6.0f);
    }

    public static void playWolfDamage() {
        play("wolf-voics/wolf-recive-damage.wav", -6.0f);
    }

    public static void playWolfDeath() {
        play("wolf-voics/wolf-attack2.wav", -6.0f);
    }

    public static void playRogueAttack() {
        play("rogue/roguer-attack.wav", -6.0f);
    }

    public static void playRogueDamage() {
        play("rogue/roguer-recive-damage.wav", -6.0f);
    }

    public static void playRogueDeath() {
        play("rogue/rogue-die.wav", -6.0f);
    }

    public static void playHit() {
        playPlayerAttack();
    }

    public static void playHurt() {
        playPlayerHurt();
    }

    public static void playEnemyDown() {
        // Legacy no-op; enemy-specific death sounds are handled per enemy type.
    }

    private static boolean throttle(String key, long minimumDelayMs) {
        long now = System.currentTimeMillis();
        Long previous = LAST_PLAYED_MS.get(key);
        if (previous != null && now - previous < minimumDelayMs) {
            return false;
        }
        LAST_PLAYED_MS.put(key, now);
        return true;
    }

    private static void play(String relativePath, float gainDb) {
        Clip clip = openClip(relativePath);
        if (clip == null) {
            return;
        }

        applyGain(clip, gainDb);
        try {
            clip.start();
        } catch (Exception ignored) {
            closeClip(clip);
            return;
        }

        Thread cleanup = new Thread(() -> {
            try {
                while (clip.isRunning()) {
                    Thread.sleep(25);
                }
            } catch (Exception ignored) {
            } finally {
                closeClip(clip);
            }
        }, "SoundManager-Cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    private static Clip openClip(String relativePath) {
        File file = ASSET_ROOT.resolve(relativePath).toFile();
        if (!file.exists()) {
            return null;
        }

        try (AudioInputStream source = AudioSystem.getAudioInputStream(file)) {
            AudioFormat sourceFormat = source.getFormat();
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    sourceFormat.getSampleRate(),
                    16,
                    sourceFormat.getChannels(),
                    sourceFormat.getChannels() * 2,
                    sourceFormat.getSampleRate(),
                    false
            );

            try (AudioInputStream decoded = AudioSystem.getAudioInputStream(targetFormat, source)) {
                Clip clip = AudioSystem.getClip();
                clip.open(decoded);
                return clip;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void applyGain(Clip clip, float gainDb) {
        try {
            if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl control = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                float value = Math.max(control.getMinimum(), Math.min(control.getMaximum(), gainDb));
                control.setValue(value);
            }
        } catch (Exception ignored) {
        }
    }

    private static void closeClip(Clip clip) {
        try {
            clip.stop();
        } catch (Exception ignored) {
        }
        try {
            clip.close();
        } catch (Exception ignored) {
        }
    }
}
