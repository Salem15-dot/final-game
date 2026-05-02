/**
 * GameView.java
 * Handles all rendering. Sets up JFrame and GamePanel.
 */

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

public class GameView {
    private static final Path ASSETS_DIR = Path.of("assets");
    
    private GameWindow gameWindow;
    private GamePanel gamePanel;
    
    public GameView(GameModel model) {
        this.gameWindow = new GameWindow(model);
        this.gamePanel = gameWindow.getGamePanel();
    }
    
    /**
     * Get the game window for display.
     */
    public GameWindow getGameWindow() {
        return gameWindow;
    }
    
    /**
     * Request a repaint of the game panel.
     */
    public void render() {
        gamePanel.repaint();
    }
    
    // ========== GAME WINDOW ==========
    public static class GameWindow extends JFrame {
        private GamePanel gamePanel;
        
        public GameWindow(GameModel model) {
            setTitle("Brawler Arena");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setResizable(false);
            setLocationRelativeTo(null);
            
            gamePanel = new GamePanel(model);
            add(gamePanel);
            
            pack();
            setVisible(true);
        }
        
        public GamePanel getGamePanel() {
            return gamePanel;
        }
    }
    
    // ========== GAME PANEL ==========
    public static class GamePanel extends JPanel {
        private GameModel model;
        private BackgroundView backgroundView;
        private HUDView hudView;
        private Map<Class<?>, CharacterView> characterViews;
        
        public GamePanel(GameModel model) {
            this.model = model;
            setPreferredSize(new Dimension(GameModel.WORLD_WIDTH, GameModel.WORLD_HEIGHT));
            setBackground(new Color(100, 150, 200));
            setFocusable(true);
            
            this.backgroundView = new BackgroundView();
            this.hudView = new HUDView();
            this.characterViews = new HashMap<>();
            
            // Initialize character views
            characterViews.put(GameModel.Player.class, new PlayerView());
            characterViews.put(GameModel.Goblin.class, new GoblinView());
            characterViews.put(GameModel.Wolf.class, new WolfView());
            characterViews.put(GameModel.Rogue.class, new RogueView());
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw background
            backgroundView.draw(g2d, getWidth(), getHeight());
            
            // Draw player
            if (model.getPlayer() != null) {
                CharacterView playerView = characterViews.get(GameModel.Player.class);
                if (playerView != null) {
                    playerView.draw(g2d, model.getPlayer());
                }
            }
            
            // Draw enemies
            for (GameModel.Enemy enemy : model.getEnemies()) {
                CharacterView enemyView = characterViews.get(enemy.getClass());
                if (enemyView != null) {
                    enemyView.draw(g2d, enemy);
                }
            }
            
            // Draw HUD
            hudView.draw(g2d, model);

            // Screen overlays: pause, respawn, game over, victory
            GameModel.Player player = model.getPlayer();

            if (model.getGameState() == GameModel.GameState.PAUSED) {
                drawDarkOverlay(g2d, "PAUSED - Press P or Esc to resume");
            } else if (model.getGameState() == GameModel.GameState.GAME_OVER) {
                drawDarkOverlay(g2d, "GAME OVER - Press R to restart");
            } else if (model.getGameState() == GameModel.GameState.VICTORY) {
                drawDarkOverlay(g2d, "VICTORY!");
            } else if (player != null && player.getState() == GameModel.PlayerState.DEAD && player.getRespawnRemaining() > 0) {
                drawDarkOverlay(g2d, "Respawning...");
            }
        }

        private void drawDarkOverlay(Graphics2D g2d, String message) {
            Composite old = g2d.getComposite();
            g2d.setColor(new Color(0, 0, 0, 140));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setComposite(old);

            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 36));
            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(message)) / 2;
            int y = (getHeight() / 2) - (fm.getHeight() / 2) + fm.getAscent();
            g2d.drawString(message, x, y);
        }
    }
    
    // ========== BACKGROUND VIEW ==========
    public static class BackgroundView {
        private BufferedImage backgroundImage;
        
        public BackgroundView() {
            backgroundImage = loadImage(ASSETS_DIR.resolve("country-platform-preview.png").toString());
        }
        
        public void draw(Graphics2D g, int panelWidth, int panelHeight) {
            if (backgroundImage != null) {
                // Draw scaled background
                g.drawImage(backgroundImage, 0, 0, panelWidth, panelHeight, null);
            } else {
                // Placeholder: draw a simple gradient
                GradientPaint gradient = new GradientPaint(0, 0, new Color(135, 206, 235),
                        0, panelHeight, new Color(90, 140, 90));
                g.setPaint(gradient);
                g.fillRect(0, 0, panelWidth, panelHeight);
                
                // Draw ground line
                g.setColor(new Color(101, 67, 33));
                g.fillRect(0, GameModel.GROUND_Y, panelWidth, panelHeight - GameModel.GROUND_Y);
            }
        }
    }
    
    // ========== HUD VIEW ==========
    public static class HUDView {
        public void draw(Graphics2D g, GameModel model) {
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.BOLD, 20));
            
            // Level number (top-center)
            String levelText = "Level " + model.getCurrentLevel();
            FontMetrics fm = g.getFontMetrics();
            int x = (GameModel.WORLD_WIDTH - fm.stringWidth(levelText)) / 2;
            g.drawString(levelText, x, 40);
            
            // Timer (top-center, below level)
            long secondsRemaining = model.getLevelTimeRemaining() / 1000;
            String timerText = "Time: " + secondsRemaining + "s";
            x = (GameModel.WORLD_WIDTH - fm.stringWidth(timerText)) / 2;
            g.drawString(timerText, x, 70);
            
            // Lives (top-left)
            GameModel.Player player = model.getPlayer();
            if (player != null) {
                String livesText = "Lives: " + player.getLives();
                g.drawString(livesText, 20, 40);
                
                // Health bar (top-left, below lives)
                drawHealthBar(g, 20, 60, 200, 20, player.getHp(), player.getMaxHp(), "HP: ");
            }
        }
        
        private void drawHealthBar(Graphics2D g, int x, int y, int width, int height,
                                   int current, int max, String label) {
            // Draw background
            g.setColor(Color.DARK_GRAY);
            g.fillRect(x, y, width, height);
            
            // Draw health
            g.setColor(Color.GREEN);
            int healthWidth = (int) ((double) current / max * width);
            g.fillRect(x, y, healthWidth, height);
            
            // Draw border
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2));
            g.drawRect(x, y, width, height);
            
            // Draw text
            g.setColor(Color.WHITE);
            g.setFont(new Font("Arial", Font.PLAIN, 12));
            g.drawString(label + current + "/" + max, x + 5, y + height - 5);
        }
    }
    
    // ========== SPRITE SHEET HELPER ==========
    public static class SpriteSheet {
        private BufferedImage image;
        
        public SpriteSheet(String imagePath) {
            this.image = loadImage(imagePath);
        }
        
        /**
         * Extract a frame from the sprite sheet.
         * @param col Column index (0-based)
         * @param row Row index (0-based)
         * @param w Frame width
         * @param h Frame height
         * @return BufferedImage of the requested frame
         */
        public BufferedImage getFrame(int col, int row, int w, int h) {
            if (image == null || w <= 0 || h <= 0) {
                return null;
            }

            int x = col * w;
            int y = row * h;
            if (x < 0 || y < 0 || x + w > image.getWidth() || y + h > image.getHeight()) {
                return null;
            }
            return image.getSubimage(x, y, w, h);
        }

        public BufferedImage getImage() {
            return image;
        }
    }
    
    // ========== ANIMATION CLASS ==========
    public static class Animation {
        private java.util.List<BufferedImage> frames;
        private int currentFrameIndex;
        private long msPerFrame;
        private long elapsedTime;
        private boolean looping;
        
        public Animation(java.util.List<BufferedImage> frames, long msPerFrame, boolean looping) {
            this.frames = frames;
            this.msPerFrame = msPerFrame;
            this.looping = looping;
            this.currentFrameIndex = 0;
            this.elapsedTime = 0;
        }
        
        /**
         * Advance animation by deltaTime milliseconds.
         */
        public void update(long deltaTimeMs) {
            if (frames.isEmpty()) return;
            
            elapsedTime += deltaTimeMs;
            
            while (elapsedTime >= msPerFrame) {
                elapsedTime -= msPerFrame;
                currentFrameIndex++;
                
                if (currentFrameIndex >= frames.size()) {
                    if (looping) {
                        currentFrameIndex = 0;
                    } else {
                        currentFrameIndex = frames.size() - 1;
                        return;
                    }
                }
            }
        }
        
        /**
         * Get the current frame image.
         */
        public BufferedImage getCurrentFrame() {
            if (frames.isEmpty()) return null;
            return frames.get(Math.min(currentFrameIndex, frames.size() - 1));
        }
        
        /**
         * Reset animation to first frame.
         */
        public void reset() {
            currentFrameIndex = 0;
            elapsedTime = 0;
        }
    }
    
    // ========== CHARACTER VIEW BASE CLASS ==========
    public static abstract class CharacterView {
        protected Map<Enum<?>, Animation> animationMap;
        private long lastUpdateMs;
        
        public CharacterView() {
            this.animationMap = new HashMap<>();
            this.lastUpdateMs = System.currentTimeMillis();
            loadAnimations();
        }
        
        /**
         * Load all state-specific animations for this character.
         * Each subclass must populate animationMap.
         */
        protected abstract void loadAnimations();
        
        /**
         * Draw the character at its current state.
         */
        public void draw(Graphics2D g, GameModel.GameEntity entity) {
            // Get appropriate animation
            Animation anim = getAnimationForEntity(entity);
            if (anim == null) return;

            long now = System.currentTimeMillis();
            long delta = Math.max(0, now - lastUpdateMs);
            lastUpdateMs = now;
            anim.update(delta);
            
            BufferedImage frame = anim.getCurrentFrame();
            if (frame == null) return;
            
            // Draw frame at entity position
            int drawX = (int) entity.getX();
            int drawY = (int) entity.getY();
            int drawW = (int) entity.getWidth();
            int drawH = (int) entity.getHeight();

            int facing = 1;
            if (entity instanceof GameModel.Player) {
                facing = ((GameModel.Player) entity).getFacing();
            } else if (entity instanceof GameModel.Enemy) {
                facing = ((GameModel.Enemy) entity).getFacing();
            }

            if (facing < 0) {
                g.drawImage(frame, drawX + drawW, drawY, -drawW, drawH, null);
            } else {
                g.drawImage(frame, drawX, drawY, drawW, drawH, null);
            }
        }
        
        /**
         * Get animation for entity's current state.
         */
        protected abstract Animation getAnimationForEntity(GameModel.GameEntity entity);
        
        /**
         * Update animation state (call from game loop).
         */
        public void update(long deltaTimeMs, GameModel.GameEntity entity) {
            Animation anim = getAnimationForEntity(entity);
            if (anim != null) {
                anim.update(deltaTimeMs);
            }
        }
    }
    
    // ========== PLAYER VIEW ==========
    public static class PlayerView extends CharacterView {
        @Override
        protected void loadAnimations() {
            SpriteSheet sheet = new SpriteSheet(ASSETS_DIR.resolve("player-spritemap-v9.png").toString());
            int frameW = 46;
            int frameH = 50;

            animationMap.put(GameModel.PlayerState.IDLE, buildAnimation(sheet, 0, 8, frameW, frameH, 120, true));
            animationMap.put(GameModel.PlayerState.WALK, buildAnimation(sheet, 1, 8, frameW, frameH, 90, true));
            animationMap.put(GameModel.PlayerState.RUN, buildAnimation(sheet, 1, 8, frameW, frameH, 65, true));
            animationMap.put(GameModel.PlayerState.JUMP, buildAnimation(sheet, 2, 8, frameW, frameH, 140, true));
            animationMap.put(GameModel.PlayerState.PUNCH, buildAnimation(sheet, 3, 8, frameW, frameH, 70, false));
            animationMap.put(GameModel.PlayerState.KICK, buildAnimation(sheet, 3, 8, frameW, frameH, 60, false));
            animationMap.put(GameModel.PlayerState.HURT, buildAnimation(sheet, 0, 8, frameW, frameH, 180, false));
            animationMap.put(GameModel.PlayerState.DEAD, buildAnimation(sheet, 0, 1, frameW, frameH, 999, false));
            animationMap.put(GameModel.PlayerState.RESPAWNING, buildAnimation(sheet, 0, 8, frameW, frameH, 90, true));
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Player)) return null;
            GameModel.Player player = (GameModel.Player) entity;
            return animationMap.getOrDefault(player.getState(), animationMap.get(GameModel.PlayerState.IDLE));
        }
    }
    
    // ========== GOBLIN VIEW ==========
    public static class GoblinView extends CharacterView {
        @Override
        protected void loadAnimations() {
            SpriteSheet sheet = new SpriteSheet(ASSETS_DIR.resolve("goblin.png").toString());
            int frameW = 64;
            int frameH = 64;

            animationMap.put(GameModel.EnemyState.WALK, buildAnimation(sheet, 2, 9, frameW, frameH, 100, true));
            animationMap.put(GameModel.EnemyState.ATTACK, buildAnimation(sheet, 3, 6, frameW, frameH, 80, true));
            animationMap.put(GameModel.EnemyState.HURT, buildAnimation(sheet, 2, 2, frameW, frameH, 120, false));
            animationMap.put(GameModel.EnemyState.DEAD, buildAnimation(sheet, 2, 1, frameW, frameH, 999, false));
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Goblin)) return null;
            GameModel.Goblin goblin = (GameModel.Goblin) entity;
            return animationMap.getOrDefault(goblin.getState(), animationMap.get(GameModel.EnemyState.WALK));
        }
    }
    
    // ========== WOLF VIEW ==========
    public static class WolfView extends CharacterView {
        @Override
        protected void loadAnimations() {
            SpriteSheet sheet = new SpriteSheet(ASSETS_DIR.resolve("wolfsheet1.png").toString());
            int frameW = 64;
            int frameH = 64;

            animationMap.put(GameModel.EnemyState.WALK, buildAnimation(sheet, 1, 8, frameW, frameH, 90, true));
            animationMap.put(GameModel.EnemyState.ATTACK, buildAnimation(sheet, 2, 8, frameW, frameH, 75, true));
            animationMap.put(GameModel.EnemyState.HURT, buildAnimation(sheet, 3, 4, frameW, frameH, 100, false));
            animationMap.put(GameModel.EnemyState.DEAD, buildAnimation(sheet, 4, 8, frameW, frameH, 120, false));
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Wolf)) return null;
            GameModel.Wolf wolf = (GameModel.Wolf) entity;
            return animationMap.getOrDefault(wolf.getState(), animationMap.get(GameModel.EnemyState.WALK));
        }
    }
    
    // ========== ROGUE VIEW ==========
    public static class RogueView extends CharacterView {
        @Override
        protected void loadAnimations() {
            SpriteSheet sheet = new SpriteSheet(ASSETS_DIR.resolve("rogue spritesheet calciumtrice.png").toString());
            int frameW = 64;
            int frameH = 64;

            animationMap.put(GameModel.EnemyState.WALK, buildAnimation(sheet, 1, 5, frameW, frameH, 100, true));
            animationMap.put(GameModel.EnemyState.ATTACK, buildAnimation(sheet, 2, 5, frameW, frameH, 90, true));
            animationMap.put(GameModel.EnemyState.HURT, buildAnimation(sheet, 3, 2, frameW, frameH, 130, false));
            animationMap.put(GameModel.EnemyState.DEAD, buildAnimation(sheet, 4, 5, frameW, frameH, 120, false));
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Rogue)) return null;
            GameModel.Rogue rogue = (GameModel.Rogue) entity;
            return animationMap.getOrDefault(rogue.getState(), animationMap.get(GameModel.EnemyState.WALK));
        }
    }

    private static BufferedImage loadImage(String imagePath) {
        try {
            return ImageIO.read(Path.of(imagePath).toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private static Animation buildAnimation(SpriteSheet sheet, int row, int frameCount, int frameW, int frameH,
                                            long msPerFrame, boolean looping) {
        List<BufferedImage> frames = new ArrayList<>();
        if (sheet != null && sheet.getImage() != null) {
            for (int col = 0; col < frameCount; col++) {
                BufferedImage frame = sheet.getFrame(col, row, frameW, frameH);
                if (frame == null) {
                    break;
                }
                frames.add(frame);
            }
        }

        if (frames.isEmpty()) {
            frames.add(createFallbackFrame(frameW, frameH));
        }
        return new Animation(frames, msPerFrame, looping);
    }

    private static BufferedImage createFallbackFrame(int width, int height) {
        int safeW = Math.max(16, width);
        int safeH = Math.max(16, height);
        BufferedImage fallback = new BufferedImage(safeW, safeH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = fallback.createGraphics();
        g.setColor(new Color(220, 80, 80));
        g.fillRect(0, 0, safeW, safeH);
        g.setColor(Color.BLACK);
        g.drawRect(0, 0, safeW - 1, safeH - 1);
        g.dispose();
        return fallback;
    }
}
