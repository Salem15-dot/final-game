/**
 * GameView.java
 * Handles all rendering. Sets up JFrame and GamePanel.
 */

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class GameView {
    
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
            setBackground(new Color(100, 150, 200)); // Sky blue placeholder
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
        }
    }
    
    // ========== BACKGROUND VIEW ==========
    public static class BackgroundView {
        private BufferedImage backgroundImage;
        
        public BackgroundView() {
            // TODO: Load country-platform-preview.png from assets folder
            // For now, we'll draw a placeholder
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
            // TODO: Load image from file
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
            // TODO: Extract and return subimage
            return null;
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
        
        public CharacterView() {
            this.animationMap = new HashMap<>();
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
            
            BufferedImage frame = anim.getCurrentFrame();
            if (frame == null) return;
            
            // Draw frame at entity position
            int drawX = (int) entity.getX();
            int drawY = (int) entity.getY();
            g.drawImage(frame, drawX, drawY, (int) entity.getWidth(), (int) entity.getHeight(), null);
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
            // TODO: Load from Ars Notoria hero sheet
            // Map each PlayerState to an Animation
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Player)) return null;
            GameModel.Player player = (GameModel.Player) entity;
            
            // TODO: Return animation for current state
            return animationMap.getOrDefault(GameModel.PlayerState.IDLE, null);
        }
    }
    
    // ========== GOBLIN VIEW ==========
    public static class GoblinView extends CharacterView {
        @Override
        protected void loadAnimations() {
            // TODO: Load from LPC sheet (side-walk and side-slash rows only)
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Goblin)) return null;
            GameModel.Goblin goblin = (GameModel.Goblin) entity;
            
            // TODO: Return animation for current state
            return animationMap.getOrDefault(GameModel.EnemyState.WALK, null);
        }
    }
    
    // ========== WOLF VIEW ==========
    public static class WolfView extends CharacterView {
        @Override
        protected void loadAnimations() {
            // TODO: Load from wolfsheet1
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Wolf)) return null;
            GameModel.Wolf wolf = (GameModel.Wolf) entity;
            
            // TODO: Return animation for current state
            return animationMap.getOrDefault(GameModel.EnemyState.WALK, null);
        }
    }
    
    // ========== ROGUE VIEW ==========
    public static class RogueView extends CharacterView {
        @Override
        protected void loadAnimations() {
            // TODO: Load from calciumtrice sheet
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Rogue)) return null;
            GameModel.Rogue rogue = (GameModel.Rogue) entity;
            
            // TODO: Return animation for current state
            return animationMap.getOrDefault(GameModel.EnemyState.WALK, null);
        }
    }
}
