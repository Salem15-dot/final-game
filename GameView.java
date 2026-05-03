/**
 * GameView.java
 * Handles all rendering. Sets up JFrame and GamePanel.
 */

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
            setResizable(true);
            setLocationRelativeTo(null);
            
            gamePanel = new GamePanel(model);
            add(gamePanel);
            
            pack();
            // Start the window maximized to fill the screen
            setExtendedState(getExtendedState() | JFrame.MAXIMIZED_BOTH);
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
        private Rectangle victoryYesButtonBounds = new Rectangle();
        private Rectangle victoryNoButtonBounds = new Rectangle();
        private Rectangle howButtonBounds = new Rectangle();
        
        // Animation system fields
        private BufferedImage playerSheet, goblinSheet, rogueSheet, wolfSheet;
        
        // AnimClip instances for each animation
        private AnimClip playerIdle, playerCrouch, playerWalk, playerRun,
            playerJump, playerFall, playerPunch, playerKick, playerHurt,
            goblinWalkL, goblinWalkR, goblinAttackL, goblinAttackR,
            rogueWalkL, rogueWalkR, rogueAttackL, rogueAttackR,
            wolfWalkL, wolfAttackL;
        
        /**
         * AnimClip describes one animation strip on a sprite sheet.
         */
        private static class AnimClip {
            final BufferedImage sheet;
            final int row, startCol, frameCount, fw, fh;

            AnimClip(BufferedImage sheet, int row, int startCol, int frameCount, int fw, int fh) {
                this.sheet = sheet;
                this.row = row;
                this.startCol = startCol;
                this.frameCount = frameCount;
                this.fw = fw;
                this.fh = fh;
            }

            /** Returns the correct sub-image for the given animation frame index. */
            BufferedImage getFrame(int frameIndex) {
                int col = startCol + (frameIndex % frameCount);
                if (sheet == null || col < 0 || row < 0) return null;
                try {
                    return sheet.getSubimage(col * fw, row * fh, fw, fh);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        
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
            
            // Load sprite sheet animations
            loadAnimations();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // Draw background
            backgroundView.draw(g2d, getWidth(), getHeight());

            drawPowerUps(g2d);
            
            GameModel.Player player = model.getPlayer();
            
            // Draw enemies (behind the player visually)
            for (GameModel.Enemy enemy : model.getEnemies()) {
                if (enemy.isDead()) continue;

                boolean facingLeft = (enemy.getX() > player.getX());
                AnimClip clip = getEnemyClip(enemy, facingLeft);
                if (clip == null) continue;
                
                BufferedImage sprite = clip.getFrame(0);
                
                // Scale: goblin/rogue are small; scale up so they match hero
                String enemyType = enemy.getClass().getSimpleName();
                int scale = enemyType.equals("Goblin") ? 2
                          : enemyType.equals("Rogue") ? 3
                          : 2; // Wolf

                int drawY = (int) enemy.getY() + getEnemyDrawYOffset(enemyType);

                // Wolf sprite faces right by default; flip only when it needs to face left.
                boolean shouldFlip = facingLeft && enemyType.equals("Wolf");

                drawSprite(g2d, sprite, (int) enemy.getX(), drawY, scale, shouldFlip);
                drawEnemyHealthBar(g2d, enemy, (int) enemy.getX(), drawY, sprite == null ? (int) (enemy.getWidth() * scale) : sprite.getWidth() * scale);
            }

            // Draw player
            if (player != null) {
                AnimClip pClip = getPlayerClip(player.getState(), model.isCrouching());
                if (pClip != null) {
                    BufferedImage pSprite = pClip.getFrame(0);
                    boolean playerFacesLeft = (player.getFacing() < 0);
                    drawSprite(g2d, pSprite, (int)player.getX(), (int)player.getY(), 3, playerFacesLeft);
                }
            }
            
            // Draw HUD
            hudView.draw(g2d, model);
            drawEffectTimers(g2d);
            drawFloatingTexts(g2d);
            drawMoneyItems(g2d);
            drawAbilityPanel(g2d);

            // Screen overlays: pause, respawn, game over, victory
            if (model.getGameState() == GameModel.GameState.TITLE) {
                drawTitleScreen(g2d);
                return;
            }
            if (model.getGameState() == GameModel.GameState.PAUSED) {
                drawPauseOverlay(g2d);
            } else if (model.getGameState() == GameModel.GameState.GAME_OVER) {
                drawGameOverOverlay(g2d);
            } else if (model.getGameState() == GameModel.GameState.VICTORY) {
                drawVictoryPrompt(g2d);
            } else if (player != null && player.getState() == GameModel.PlayerState.DEAD && player.getRespawnRemaining() > 0) {
                drawDarkOverlay(g2d, "Respawning...");
            }
        }

        private void drawTitleScreen(Graphics2D g2d) {
            // Full-screen dark backdrop
            Composite oldComp = g2d.getComposite();
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRect(0, 0, getWidth(), getHeight());

            // Title (large, centered higher)
            String title = "BRAWLER ARENA";
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 56));
            FontMetrics titleFm = g2d.getFontMetrics();
            int titleX = (getWidth() - titleFm.stringWidth(title)) / 2;
            int titleY = (getHeight() / 2) - 60;
            g2d.drawString(title, titleX, titleY);

            // Subtitle / prompt
            String prompt = "Press SPACE to start";
            g2d.setFont(new Font("Arial", Font.PLAIN, 22));
            FontMetrics fm = g2d.getFontMetrics();
            int promptX = (getWidth() - fm.stringWidth(prompt)) / 2;
            int promptY = titleY + 54;
            g2d.drawString(prompt, promptX, promptY);

            // How to play button (centered below prompt)
            int buttonW = 220;
            int buttonH = 40;
            int bx = (getWidth() - buttonW) / 2;
            int by = promptY + 30;
            howButtonBounds = new Rectangle(bx, by, buttonW, buttonH);
            drawButton(g2d, howButtonBounds, "How to play");

            // High score box at top-right to avoid overlapping central text
            String hs = "High Score: " + model.getHighScore();
            int pad = 12;
            g2d.setFont(new Font("Arial", Font.BOLD, 16));
            FontMetrics hfm = g2d.getFontMetrics();
            int boxW = hfm.stringWidth(hs) + pad * 2;
            int boxH = 28;
            int boxX = getWidth() - boxW - 20;
            int boxY = 20;
            g2d.setColor(new Color(255, 255, 255, 220));
            g2d.fillRoundRect(boxX, boxY, boxW, boxH, 10, 10);
            g2d.setColor(Color.BLACK);
            g2d.drawRoundRect(boxX, boxY, boxW, boxH, 10, 10);
            g2d.setColor(Color.BLACK);
            int textX = boxX + pad;
            int textY = boxY + ((boxH + hfm.getAscent()) / 2) - 4;
            g2d.drawString(hs, textX, textY);
            g2d.setComposite(oldComp);
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

        private void drawGameOverOverlay(Graphics2D g2d) {
            // Dark background
            Composite old = g2d.getComposite();
            g2d.setColor(new Color(0,0,0,180));
            g2d.fillRect(0,0,getWidth(), getHeight());
            g2d.setComposite(old);

            // Main message
            String msg = "GAME OVER - Press R to restart";
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 36));
            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(msg)) / 2;
            int y = (getHeight() / 2) - (fm.getHeight() / 2) + fm.getAscent();
            g2d.drawString(msg, x, y);

            // High score line below
            String hs = "High Score: " + model.getHighScore();
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            FontMetrics hfm = g2d.getFontMetrics();
            int hx = (getWidth() - hfm.stringWidth(hs)) / 2;
            int hy = y + 40;
            g2d.drawString(hs, hx, hy);
        }

        private void drawPauseOverlay(Graphics2D g2d) {
            // Semi-opaque dark background
            Composite old = g2d.getComposite();
            g2d.setColor(new Color(0,0,0,180));
            g2d.fillRect(0,0,getWidth(), getHeight());
            g2d.setComposite(old);

            // Main pause message
            String msg = "PAUSED - Press P or Esc to resume";
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 36));
            FontMetrics fm = g2d.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(msg)) / 2;
            int y = (getHeight() / 2) - (fm.getHeight() / 2) + fm.getAscent();
            g2d.drawString(msg, x, y);

            // High score line below
            String hs = "High Score: " + model.getHighScore();
            g2d.setFont(new Font("Arial", Font.PLAIN, 18));
            FontMetrics hfm = g2d.getFontMetrics();
            int hx = (getWidth() - hfm.stringWidth(hs)) / 2;
            int hy = y + 40;
            g2d.drawString(hs, hx, hy);
        }

        private void drawVictoryPrompt(Graphics2D g2d) {
            drawDarkOverlay(g2d, "VICTORY!");

            String prompt = "Ready for the real game?";
            g2d.setFont(new Font("Arial", Font.BOLD, 24));
            FontMetrics fm = g2d.getFontMetrics();
            int promptX = (getWidth() - fm.stringWidth(prompt)) / 2;
            g2d.drawString(prompt, promptX, getHeight() / 2 + 30);

            // Show high score beneath the prompt
            String hs = "High Score: " + model.getHighScore();
            g2d.setFont(new Font("Arial", Font.PLAIN, 16));
            FontMetrics hfm = g2d.getFontMetrics();
            int hsX = (getWidth() - hfm.stringWidth(hs)) / 2;
            g2d.drawString(hs, hsX, getHeight() / 2 + 58);

            int buttonY = getHeight() / 2 + 70;
            int buttonW = 120;
            int buttonH = 42;
            int spacing = 20;
            int totalWidth = (buttonW * 2) + spacing;
            int startX = (getWidth() - totalWidth) / 2;

            victoryYesButtonBounds = new Rectangle(startX, buttonY, buttonW, buttonH);
            victoryNoButtonBounds = new Rectangle(startX + buttonW + spacing, buttonY, buttonW, buttonH);

            drawButton(g2d, victoryYesButtonBounds, "Yes");
            drawButton(g2d, victoryNoButtonBounds, "No");
        }

        private void drawButton(Graphics2D g2d, Rectangle bounds, String text) {
            g2d.setColor(new Color(255, 255, 255, 220));
            g2d.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);
            g2d.setColor(Color.BLACK);
            g2d.drawRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, 14, 14);
            g2d.setFont(new Font("Arial", Font.BOLD, 22));
            FontMetrics fm = g2d.getFontMetrics();
            int textX = bounds.x + (bounds.width - fm.stringWidth(text)) / 2;
            int textY = bounds.y + ((bounds.height + fm.getAscent()) / 2) - 4;
            g2d.drawString(text, textX, textY);
        }

        public Rectangle getVictoryYesButtonBounds() {
            return new Rectangle(victoryYesButtonBounds);
        }

        public Rectangle getVictoryNoButtonBounds() {
            return new Rectangle(victoryNoButtonBounds);
        }

        public Rectangle getHowButtonBounds() {
            return new Rectangle(howButtonBounds);
        }

        private void drawPowerUps(Graphics2D g2d) {
            if (model == null) {
                return;
            }
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            for (GameModel.PowerUp powerUp : model.getPowerUps()) {
                int drawX = (int) Math.round(powerUp.getX());
                int drawY = (int) Math.round(powerUp.getY());
                g2d.setColor(powerUp.getColor());
                g2d.fillRoundRect(drawX, drawY, (int) powerUp.getWidth(), (int) powerUp.getHeight(), 10, 10);
                g2d.setColor(Color.WHITE);
                g2d.drawRoundRect(drawX, drawY, (int) powerUp.getWidth(), (int) powerUp.getHeight(), 10, 10);
                String label = powerUp.getLabel();
                FontMetrics fm = g2d.getFontMetrics();
                int textX = drawX + ((int) powerUp.getWidth() - fm.stringWidth(label)) / 2;
                int textY = drawY + ((int) powerUp.getHeight() + fm.getAscent()) / 2 - 2;
                g2d.drawString(label, textX, textY);
            }
        }

        private void drawEnemyHealthBar(Graphics2D g2d, GameModel.Enemy enemy, int x, int y, int spriteWidth) {
            int barWidth = Math.max(34, spriteWidth - 6);
            int barHeight = 7;
            int barX = x + (spriteWidth - barWidth) / 2;
            int barY = y - 14;
            double ratio = enemy.getMaxHp() <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, enemy.getHp() / (double) enemy.getMaxHp()));

            g2d.setColor(new Color(0, 0, 0, 170));
            g2d.fillRoundRect(barX, barY, barWidth, barHeight, 8, 8);
            g2d.setColor(new Color(220, 50, 50));
            g2d.fillRoundRect(barX, barY, (int) Math.round(barWidth * ratio), barHeight, 8, 8);
            g2d.setColor(new Color(255, 255, 255, 180));
            g2d.drawRoundRect(barX, barY, barWidth, barHeight, 8, 8);
        }

        private void drawFloatingTexts(Graphics2D g2d) {
            if (model == null) {
                return;
            }
            g2d.setFont(new Font("Arial", Font.BOLD, 18));
            for (GameModel.FloatingText text : model.getFloatingTexts()) {
                float alpha = Math.max(0f, Math.min(1f, text.getRemainingMs() / 900f));
                Composite old = g2d.getComposite();
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2d.setColor(text.getColor());
                FontMetrics fm = g2d.getFontMetrics();
                int drawX = (int) Math.round(text.getX()) - fm.stringWidth(text.getText()) / 2;
                int drawY = (int) Math.round(text.getY());
                g2d.drawString(text.getText(), drawX, drawY);
                g2d.setComposite(old);
            }
        }

        private void drawEffectTimers(Graphics2D g2d) {
            if (model == null) {
                return;
            }
            List<String> effects = model.getActiveEffectSummaries();
            if (effects.isEmpty()) {
                return;
            }

            int panelX = 20;
            int panelY = 110;
            int panelWidth = 200;
            int panelHeight = 26 + effects.size() * 20;

            g2d.setColor(new Color(0, 0, 0, 170));
            g2d.fillRoundRect(panelX, panelY, panelWidth, panelHeight, 16, 16);
            g2d.setColor(new Color(255, 255, 255, 180));
            g2d.drawRoundRect(panelX, panelY, panelWidth, panelHeight, 16, 16);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString("Active Effects", panelX + 12, panelY + 18);

            int lineY = panelY + 38;
            for (String effect : effects) {
                g2d.drawString(effect, panelX + 12, lineY);
                lineY += 20;
            }
        }

        private void drawMoneyItems(Graphics2D g2d) {
            if (model == null) return;
            for (GameModel.Money m : model.getMoneyList()) {
                int cx = (int) Math.round(m.getX());
                int cy = (int) Math.round(m.getY());
                int size = Math.max(10, m.getValue() * 8);
                g2d.setColor(new Color(212, 175, 55)); // gold
                g2d.fillOval(cx, cy, size, size);
                g2d.setColor(Color.BLACK);
                g2d.drawOval(cx, cy, size, size);
            }
        }

        private void drawAbilityPanel(Graphics2D g2d) {
            if (model == null) return;
            int panelX = getWidth() - 260;
            int panelY = 20;
            int panelW = 240;
            int panelH = 156;
            g2d.setColor(new Color(0,0,0,160));
            g2d.fillRoundRect(panelX, panelY, panelW, panelH, 12, 12);
            g2d.setColor(Color.WHITE);
            g2d.drawRoundRect(panelX, panelY, panelW, panelH, 12, 12);
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString("Upgrades (1-4)", panelX + 12, panelY + 22);
            g2d.setColor(Color.YELLOW);
            g2d.drawString("Money: $" + model.getPlayerMoney(), panelX + 12, panelY + 40);

            String[] names = {"Atk Speed", "Health", "Damage", "Jump"};
            GameModel.AbilityType[] types = {GameModel.AbilityType.SPEED, GameModel.AbilityType.HEALTH, GameModel.AbilityType.DAMAGE, GameModel.AbilityType.JUMP};
            int y = panelY + 62;
            g2d.setFont(new Font("Arial", Font.PLAIN, 12));
            for (int i = 0; i < names.length; i++) {
                String name = names[i];
                int level = model.getUpgradeLevel(types[i]);
                int cost = model.getUpgradeCost(types[i]);
                String line = String.format("%d) %s Lv%d  Cost:$%d", i+1, name, level, cost);
                g2d.drawString(line, panelX + 12, y);
                y += 22;
            }
            g2d.setFont(new Font("Arial", Font.ITALIC, 11));
            g2d.drawString("Health heals +10 and max+10", panelX + 12, panelY + panelH - 12);
        }
        
        /**
         * Load all sprite sheets and create AnimClip instances.
         * Call from constructor.
         */
        private void loadAnimations() {
            try {
                playerSheet = ImageIO.read(new File("assets/player-spritemap-v9.png"));
                goblinSheet = ImageIO.read(new File("assets/goblin.png"));
                rogueSheet = ImageIO.read(new File("assets/rogue spritesheet calciumtrice.png"));
                wolfSheet = ImageIO.read(new File("assets/wolfsheet1.png"));
            } catch (IOException e) {
                System.err.println("ERROR loading sprite sheet: " + e.getMessage());
                e.printStackTrace();
                return;
            }

            // PLAYER: 46 x 50, 4 rows x 8 cols.
            int pw = 46, ph = 50;
            playerIdle = new AnimClip(playerSheet, 0, 0, 1, pw, ph);
            playerCrouch = new AnimClip(playerSheet, 0, 1, 1, pw, ph);
            playerPunch = new AnimClip(playerSheet, 0, 3, 1, pw, ph);
            playerKick = new AnimClip(playerSheet, 2, 3, 1, pw, ph);
            playerJump = new AnimClip(playerSheet, 0, 7, 1, pw, ph);
            playerWalk = new AnimClip(playerSheet, 3, 0, 1, pw, ph);
            playerRun = new AnimClip(playerSheet, 3, 4, 1, pw, ph);
            playerHurt = new AnimClip(playerSheet, 2, 0, 1, pw, ph);
            playerFall = new AnimClip(playerSheet, 2, 1, 1, pw, ph);

            // GOBLIN: 64 x 64, 5 rows x 11 cols.
            int gw = 64, gh = 64;
            goblinWalkL = new AnimClip(goblinSheet, 1, 0, 1, gw, gh);
            goblinWalkR = new AnimClip(goblinSheet, 3, 0, 1, gw, gh);
            goblinAttackL = new AnimClip(goblinSheet, 1, 8, 1, gw, gh);
            goblinAttackR = new AnimClip(goblinSheet, 3, 8, 1, gw, gh);

            // ROGUE: 32 x 32, use one clean side-facing frame.
            int rw = 32, rh = 32;
            rogueWalkL = new AnimClip(rogueSheet, 1, 0, 1, rw, rh);
            rogueWalkR = new AnimClip(rogueSheet, 1, 0, 1, rw, rh);
            rogueAttackL = new AnimClip(rogueSheet, 3, 5, 1, rw, rh);
            rogueAttackR = new AnimClip(rogueSheet, 3, 5, 1, rw, rh);

            // WOLF: quadruped section starts at col 5.
            int ww = 64, wh = 64;
            wolfWalkL = new AnimClip(wolfSheet, 1, 5, 1, ww, wh);
            wolfAttackL = new AnimClip(wolfSheet, 3, 7, 1, ww, wh);
        }

        /**
         * Draw a sprite at (x, y) scaled by 'scale', optionally flipped horizontally.
         */
        private void drawSprite(Graphics g, BufferedImage sprite, int x, int y, int scale, boolean flipH) {
            if (sprite == null) return;
            int dw = sprite.getWidth() * scale;
            int dh = sprite.getHeight() * scale;
            if (flipH) {
                g.drawImage(sprite, x + dw, y, -dw, dh, null);
            } else {
                g.drawImage(sprite, x, y, dw, dh, null);
            }
        }

        /**
         * Get the correct AnimClip for the player's current state.
         */
        private AnimClip getPlayerClip(GameModel.PlayerState state, boolean isCrouching) {
            if (isCrouching && state == GameModel.PlayerState.IDLE) return playerCrouch;
            switch (state) {
                case WALK: return playerWalk;
                case RUN: return playerRun;
                case JUMP: return playerJump;
                case PUNCH: return playerPunch;
                case KICK: return playerKick;
                case HURT: return playerHurt;
                case FALL: return playerFall;
                default: return playerIdle;
            }
        }

        /**
         * Get the correct AnimClip for an enemy based on type, state, and direction.
         * 'facingLeft' = true when enemy is to the right of the player (walks left toward them).
         */
        private AnimClip getEnemyClip(GameModel.Enemy enemy, boolean facingLeft) {
            String type = enemy.getClass().getSimpleName();
            boolean atk = (enemy.getState() == GameModel.EnemyState.ATTACK);

            if (type.equals("Wolf")) {
                return atk ? wolfAttackL : wolfWalkL;
            } else if (type.equals("Rogue")) {
                return atk ? (facingLeft ? rogueAttackR : rogueAttackL)
                          : (facingLeft ? rogueWalkR : rogueWalkL);
            } else { // Goblin
                return atk ? (facingLeft ? goblinAttackR : goblinAttackL)
                          : (facingLeft ? goblinWalkR : goblinWalkL);
            }
        }

        private int getEnemyDrawYOffset(String enemyType) {
            if (enemyType.equals("Wolf")) {
                return 28;
            }
            if (enemyType.equals("Rogue")) {
                return 10;
            }
            return 0;
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
                
                // Enemies killed display (below health)
                g.setFont(new Font("Arial", Font.PLAIN, 14));
                g.setColor(Color.WHITE);
                String kills = "Kills: " + model.getEnemiesKilled();
                g.drawString(kills, 20, 100);
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

        public Animation copy() {
            Animation cloned = new Animation(new ArrayList<>(frames), msPerFrame, looping);
            cloned.currentFrameIndex = currentFrameIndex;
            cloned.elapsedTime = elapsedTime;
            return cloned;
        }
    }
    
    // ========== CHARACTER VIEW BASE CLASS ==========
    public static abstract class CharacterView {
        protected Map<Enum<?>, Animation> animationMap;
        private final Map<GameModel.GameEntity, Map<Enum<?>, Animation>> entityAnimationCache;
        private final Map<GameModel.GameEntity, Long> entityLastUpdateMs;
        
        public CharacterView() {
            this.animationMap = new HashMap<>();
            this.entityAnimationCache = new IdentityHashMap<>();
            this.entityLastUpdateMs = new IdentityHashMap<>();
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
            Enum<?> animationState = getAnimationStateForEntity(entity);
            if (animationState == null) return;

            Animation anim = getEntityAnimation(entity, animationState);
            if (anim == null) return;

            long now = System.currentTimeMillis();
            long lastUpdate = entityLastUpdateMs.getOrDefault(entity, now);
            long delta = Math.max(0, now - lastUpdate);
            entityLastUpdateMs.put(entity, now);
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
         * Get the current animation state key for the entity.
         */
        protected abstract Enum<?> getAnimationStateForEntity(GameModel.GameEntity entity);
        
        /**
         * Update animation state (call from game loop).
         */
        public void update(long deltaTimeMs, GameModel.GameEntity entity) {
            Enum<?> animationState = getAnimationStateForEntity(entity);
            Animation anim = animationState == null ? null : getEntityAnimation(entity, animationState);
            if (anim != null) {
                anim.update(deltaTimeMs);
            }
        }

        private Animation getEntityAnimation(GameModel.GameEntity entity, Enum<?> state) {
            Map<Enum<?>, Animation> cachedAnimations = entityAnimationCache.computeIfAbsent(entity, key -> new HashMap<>());
            Animation cachedAnimation = cachedAnimations.get(state);
            if (cachedAnimation != null) {
                return cachedAnimation;
            }

            Animation prototype = animationMap.get(state);
            if (prototype == null) {
                return null;
            }

            Animation copy = prototype.copy();
            cachedAnimations.put(state, copy);
            return copy;
        }
    }
    
    // ========== PLAYER VIEW ==========
    public static class PlayerView extends CharacterView {
        @Override
        protected void loadAnimations() {
            SpriteSheet sheet = new SpriteSheet(ASSETS_DIR.resolve("player-spritemap-v9.png").toString());
            int frameW = 46;
            int frameH = 50;

            animationMap.put(GameModel.PlayerState.IDLE, buildAnimation(sheet, 0, 1, frameW, frameH, 999, false));
            animationMap.put(GameModel.PlayerState.WALK, buildAnimation(sheet, 1, 6, frameW, frameH, 110, true));
            animationMap.put(GameModel.PlayerState.RUN, buildAnimation(sheet, 3, 8, frameW, frameH, 75, true));
            animationMap.put(GameModel.PlayerState.JUMP, buildAnimation(sheet, 2, 4, frameW, frameH, 130, false));
            animationMap.put(GameModel.PlayerState.FALL, buildAnimation(sheet, 0, 1, frameW, frameH, 999, false));
            animationMap.put(GameModel.PlayerState.PUNCH, buildAnimation(sheet, 2, 2, frameW, frameH, 110, false));
            animationMap.put(GameModel.PlayerState.KICK, buildAnimation(sheet, 2, 2, frameW, frameH, 110, false));
            animationMap.put(GameModel.PlayerState.HURT, buildAnimation(sheet, 0, 1, frameW, frameH, 999, false));
            animationMap.put(GameModel.PlayerState.DEAD, buildAnimation(sheet, 0, 1, frameW, frameH, 999, false));
            animationMap.put(GameModel.PlayerState.RESPAWNING, buildAnimation(sheet, 0, 1, frameW, frameH, 999, false));
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Player)) return null;
            GameModel.Player player = (GameModel.Player) entity;
            return animationMap.getOrDefault(player.getState(), animationMap.get(GameModel.PlayerState.IDLE));
        }

        @Override
        protected Enum<?> getAnimationStateForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Player)) return null;
            GameModel.Player player = (GameModel.Player) entity;
            if (player.getState() == GameModel.PlayerState.JUMP && player.isDownHeld()) {
                return GameModel.PlayerState.FALL;
            }
            return player.getState();
        }
    }
    
    // ========== GOBLIN VIEW ==========
    public static class GoblinView extends CharacterView {
        @Override
        protected void loadAnimations() {
            SpriteSheet sheet = new SpriteSheet(ASSETS_DIR.resolve("goblin.png").toString());
            int frameW = 64;
            int frameH = 64;

            animationMap.put(GameModel.EnemyState.WALK, buildAnimation(sheet, 2, 6, frameW, frameH, 120, true));
            animationMap.put(GameModel.EnemyState.ATTACK, buildAnimation(sheet, 3, 4, frameW, frameH, 130, false));
            animationMap.put(GameModel.EnemyState.HURT, buildAnimation(sheet, 2, 1, frameW, frameH, 999, false));
            animationMap.put(GameModel.EnemyState.DEAD, buildAnimation(sheet, 2, 1, frameW, frameH, 999, false));
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Goblin)) return null;
            GameModel.Goblin goblin = (GameModel.Goblin) entity;
            return animationMap.getOrDefault(goblin.getState(), animationMap.get(GameModel.EnemyState.WALK));
        }

        @Override
        protected Enum<?> getAnimationStateForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Goblin)) return null;
            return ((GameModel.Goblin) entity).getState();
        }
    }
    
    // ========== WOLF VIEW ==========
    public static class WolfView extends CharacterView {
        @Override
        protected void loadAnimations() {
            SpriteSheet sheet = new SpriteSheet(ASSETS_DIR.resolve("wolfsheet1.png").toString());
            int frameW = 64;
            int frameH = 64;

            animationMap.put(GameModel.EnemyState.WALK, buildAnimation(sheet, 1, 6, frameW, frameH, 110, true));
            animationMap.put(GameModel.EnemyState.ATTACK, buildAnimation(sheet, 2, 4, frameW, frameH, 110, false));
            animationMap.put(GameModel.EnemyState.HURT, buildAnimation(sheet, 3, 1, frameW, frameH, 999, false));
            animationMap.put(GameModel.EnemyState.DEAD, buildAnimation(sheet, 4, 1, frameW, frameH, 999, false));
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Wolf)) return null;
            GameModel.Wolf wolf = (GameModel.Wolf) entity;
            return animationMap.getOrDefault(wolf.getState(), animationMap.get(GameModel.EnemyState.WALK));
        }

        @Override
        protected Enum<?> getAnimationStateForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Wolf)) return null;
            return ((GameModel.Wolf) entity).getState();
        }
    }
    
    // ========== ROGUE VIEW ==========
    public static class RogueView extends CharacterView {
        @Override
        protected void loadAnimations() {
            SpriteSheet sheet = new SpriteSheet(ASSETS_DIR.resolve("rogue spritesheet calciumtrice.png").toString());
            int frameW = 64;
            int frameH = 64;

            animationMap.put(GameModel.EnemyState.WALK, buildAnimation(sheet, 1, 5, frameW, frameH, 110, true));
            animationMap.put(GameModel.EnemyState.ATTACK, buildAnimation(sheet, 2, 4, frameW, frameH, 120, false));
            animationMap.put(GameModel.EnemyState.HURT, buildAnimation(sheet, 3, 1, frameW, frameH, 999, false));
            animationMap.put(GameModel.EnemyState.DEAD, buildAnimation(sheet, 4, 1, frameW, frameH, 999, false));
        }
        
        @Override
        protected Animation getAnimationForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Rogue)) return null;
            GameModel.Rogue rogue = (GameModel.Rogue) entity;
            return animationMap.getOrDefault(rogue.getState(), animationMap.get(GameModel.EnemyState.WALK));
        }

        @Override
        protected Enum<?> getAnimationStateForEntity(GameModel.GameEntity entity) {
            if (!(entity instanceof GameModel.Rogue)) return null;
            return ((GameModel.Rogue) entity).getState();
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
