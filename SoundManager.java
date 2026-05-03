import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public final class SoundManager {
    private static final AudioFormat FORMAT = new AudioFormat(44100f, 16, 1, true, false);
    private static volatile boolean musicRunning = false;
    private static Thread musicThread;

    private SoundManager() {
    }

    public static void playMusic() {
        if (musicRunning) {
            return;
        }
        musicRunning = true;
        musicThread = new Thread(() -> {
            int[] melody = {262, 330, 392, 330, 294, 349, 440, 392};
            int[] bass = {131, 131, 147, 147, 165, 165, 147, 147};
            while (musicRunning) {
                for (int i = 0; i < melody.length && musicRunning; i++) {
                    playChord(melody[i], bass[i], 180, 0.18);
                }
            }
        }, "BrawlerArena-Music");
        musicThread.setDaemon(true);
        musicThread.start();
    }

    public static void stopMusic() {
        musicRunning = false;
    }

    public static void playHit() {
        playToneAsync(720, 55, 0.20);
    }

    public static void playHurt() {
        playToneAsync(180, 120, 0.18);
    }

    public static void playCoin() {
        playToneAsync(1040, 90, 0.16);
    }

    public static void playPowerUp() {
        playToneAsync(880, 120, 0.18);
        playToneAsync(1175, 90, 0.14);
    }

    public static void playPurchase() {
        playToneAsync(523, 90, 0.14);
        playToneAsync(659, 90, 0.14);
    }

    public static void playEnemyDown() {
        playToneAsync(220, 140, 0.18);
        playToneAsync(110, 120, 0.14);
    }

    private static void playToneAsync(int frequencyHz, int durationMs, double volume) {
        Thread thread = new Thread(() -> playTone(frequencyHz, durationMs, volume), "BrawlerArena-SFX");
        thread.setDaemon(true);
        thread.start();
    }

    private static void playChord(int frequencyA, int frequencyB, int durationMs, double volume) {
        byte[] buffer = createChordBuffer(frequencyA, frequencyB, durationMs, volume);
        playBuffer(buffer);
    }

    private static void playTone(int frequencyHz, int durationMs, double volume) {
        byte[] buffer = createToneBuffer(frequencyHz, durationMs, volume);
        playBuffer(buffer);
    }

    private static byte[] createToneBuffer(int frequencyHz, int durationMs, double volume) {
        int sampleCount = (int) (FORMAT.getSampleRate() * durationMs / 1000.0);
        byte[] buffer = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            double time = i / FORMAT.getSampleRate();
            double envelope = 1.0;
            if (i < 40) {
                envelope = i / 40.0;
            } else if (i > sampleCount - 80) {
                envelope = Math.max(0.0, (sampleCount - i) / 80.0);
            }
            short sample = (short) (Math.sin(2.0 * Math.PI * frequencyHz * time) * Short.MAX_VALUE * volume * envelope);
            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return buffer;
    }

    private static byte[] createChordBuffer(int frequencyA, int frequencyB, int durationMs, double volume) {
        int sampleCount = (int) (FORMAT.getSampleRate() * durationMs / 1000.0);
        byte[] buffer = new byte[sampleCount * 2];
        for (int i = 0; i < sampleCount; i++) {
            double time = i / FORMAT.getSampleRate();
            double envelope = 1.0;
            if (i < 40) {
                envelope = i / 40.0;
            } else if (i > sampleCount - 80) {
                envelope = Math.max(0.0, (sampleCount - i) / 80.0);
            }
            double wave = (Math.sin(2.0 * Math.PI * frequencyA * time) + Math.sin(2.0 * Math.PI * frequencyB * time)) / 2.0;
            short sample = (short) (wave * Short.MAX_VALUE * volume * envelope);
            buffer[i * 2] = (byte) (sample & 0xFF);
            buffer[i * 2 + 1] = (byte) ((sample >> 8) & 0xFF);
        }
        return buffer;
    }

    private static void playBuffer(byte[] buffer) {
        SourceDataLine line = null;
        try {
            line = AudioSystem.getSourceDataLine(FORMAT);
            line.open(FORMAT);
            line.start();
            line.write(buffer, 0, buffer.length);
            line.drain();
        } catch (Exception ignored) {
            // Audio output is optional; silently skip if unavailable.
        } finally {
            if (line != null) {
                try {
                    line.stop();
                } catch (Exception ignored) {
                }
                try {
                    line.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
