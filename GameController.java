/**
 * GameController.java
 * Handles input, game loop, collision detection, and state transitions.
 */

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashSet;
import java.util.Set;

public class GameController {
    
    private GameModel model;
    private GameView view;
    private KeyboardController keyboardController;
    private GameLoop gameLoop;
    private CollisionController collisionController;
    private StateController stateController;
    
    public GameController() {
        // Initialize MVC components
        this.model = new GameModel();
        this.view = new GameView(model);
        this.keyboardController = new KeyboardController();
        this.collisionController = new CollisionController();
        this.stateController = new StateController(model, view);
        
        // Setup input on game window
        view.getGameWindow().addKeyListener(keyboardController);
        view.getGameWindow().getGamePanel().addKeyListener(keyboardController);
        SwingUtilities.invokeLater(() -> view.getGameWindow().getGamePanel().requestFocusInWindow());

        // Start directly in level 1 for iterative gameplay testing.
        this.stateController.startNewGame();
        
        // Start game loop
        this.gameLoop = new GameLoop(model, view, keyboardController, collisionController, stateController);
        gameLoop.start();
    }
    
    // ========== KEYBOARD CONTROLLER ==========
    public static class KeyboardController implements KeyListener {
        private Set<Integer> heldKeys;
        private Set<Integer> pressedKeys;
        
        public KeyboardController() {
            this.heldKeys = new HashSet<>();
            this.pressedKeys = new HashSet<>();
        }
        
        @Override
        public void keyPressed(KeyEvent e) {
            int code = e.getKeyCode();
            heldKeys.add(code);
            pressedKeys.add(code);
        }
        
        @Override
        public void keyReleased(KeyEvent e) {
            int code = e.getKeyCode();
            heldKeys.remove(code);
        }
        
        @Override
        public void keyTyped(KeyEvent e) {
            // Not used
        }
        
        // ===== Intent queries =====
        public boolean isLeftHeld() { return heldKeys.contains(KeyEvent.VK_A); }
        public boolean isRightHeld() { return heldKeys.contains(KeyEvent.VK_D); }
        public boolean isRunHeld() { return heldKeys.contains(KeyEvent.VK_SHIFT); }
        public boolean isDownHeld() { return heldKeys.contains(KeyEvent.VK_S); }
        
        public boolean wasJumpPressed() {
            return consumeKey(KeyEvent.VK_SPACE) || consumeKey(KeyEvent.VK_W);
        }
        
        public boolean wasPunchPressed() {
            return consumeKey(KeyEvent.VK_J);
        }
        
        public boolean wasKickPressed() {
            return consumeKey(KeyEvent.VK_K);
        }
        
        public boolean wasPausePressed() {
            return consumeKey(KeyEvent.VK_ESCAPE) || consumeKey(KeyEvent.VK_P);
        }

        public boolean wasRestartPressed() { return consumeKey(KeyEvent.VK_R); }
        public boolean wasBuySpeedPressed() { return consumeKey(KeyEvent.VK_1); }
        public boolean wasBuyHealthPressed() { return consumeKey(KeyEvent.VK_2); }
        public boolean wasBuyDamagePressed() { return consumeKey(KeyEvent.VK_3); }
        public boolean wasBuyJumpPressed() { return consumeKey(KeyEvent.VK_4); }
        
        /**
         * Consume a key press (edge-triggered).
         * Returns true if key was pressed this frame, and removes it from pressed set.
         */
        private boolean consumeKey(int keyCode) {
            if (pressedKeys.contains(keyCode)) {
                pressedKeys.remove(keyCode);
                return true;
            }
            return false;
        }
        
        /**
         * Clear pressed keys (call after processing each frame).
         */
        public void clearPressedKeys() {
            pressedKeys.clear();
        }
    }
    
    // ========== GAME LOOP ==========
    public static class GameLoop {
        private GameModel model;
        private GameView view;
        private KeyboardController keyboardController;
        private CollisionController collisionController;
        private StateController stateController;
        private Timer timer;
        private long lastFrameTime;
        private static final int FPS = 60;
        private static final int FRAME_DELAY = 1000 / FPS; // ~16ms
        
        public GameLoop(GameModel model, GameView view,
                        KeyboardController keyboardController,
                        CollisionController collisionController,
                        StateController stateController) {
            this.model = model;
            this.view = view;
            this.keyboardController = keyboardController;
            this.collisionController = collisionController;
            this.stateController = stateController;
            this.lastFrameTime = System.currentTimeMillis();
        }
        
        /**
         * Start the game loop timer.
         */
        public void start() {
            timer = new Timer(FRAME_DELAY, e -> tick());
            timer.start();
        }
        
        /**
         * Stop the game loop.
         */
        public void stop() {
            if (timer != null) {
                timer.stop();
            }
        }
        
        /**
         * Main game loop tick.
         */
        private void tick() {
            long now = System.currentTimeMillis();
            double deltaTime = (now - lastFrameTime) / 1000.0; // Convert to seconds
            long deltaMs = Math.max(0L, now - lastFrameTime);
            lastFrameTime = now;

            stateController.tick(deltaMs);
            
            // 1. Read input intents
            readInput();
            
            // 2. Update game model
            model.update(deltaTime);
            
            // 3. Check collisions and apply damage
            collisionController.checkCollisions(model);

            // 4. Check state transitions
            // Minimal routing now; expanded screen routing comes in later steps.
            stateController.checkStateTransitions();
            
            // 5. Render
            view.render();
            
            // 6. Clear edge-triggered inputs
            keyboardController.clearPressedKeys();
        }
        
        /**
         * Process input and update player accordingly.
         */
        private void readInput() {
            GameModel.Player player = model.getPlayer();
            boolean pausePressed = keyboardController.wasPausePressed();
            boolean restartPressed = keyboardController.wasRestartPressed();

            if (pausePressed) {
                if (model.getGameState() == GameModel.GameState.PLAYING) {
                    model.pause();
                } else if (model.getGameState() == GameModel.GameState.PAUSED) {
                    model.resume();
                }
            }

            if (restartPressed) {
                stateController.tryRestart();
            }

            if (player == null || model.getGameState() != GameModel.GameState.PLAYING) {
                return;
            }
            
            // Movement
            player.setRunHeld(keyboardController.isRunHeld());
            player.setDownHeld(keyboardController.isDownHeld());
            
            // Handle crouching (S key while on ground)
            if (keyboardController.isDownHeld() && player.isOnGround()) {
                player.setCrouching(true);
            } else {
                player.setCrouching(false);
            }
            
            if (keyboardController.isLeftHeld()) {
                player.moveLeft();
            }
            if (keyboardController.isRightHeld()) {
                player.moveRight();
            }
            
            // Actions (edge-triggered)
            if (keyboardController.wasJumpPressed()) {
                player.jump();
            }
            if (keyboardController.wasPunchPressed()) {
                player.punch();
            }
            if (keyboardController.wasKickPressed()) {
                player.kick();
            }

            // Purchases: 1=Speed, 2=Health, 3=Damage, 4=Jump
            if (keyboardController.wasBuySpeedPressed()) {
                model.purchaseUpgrade(GameModel.AbilityType.SPEED);
            }
            if (keyboardController.wasBuyHealthPressed()) {
                model.purchaseUpgrade(GameModel.AbilityType.HEALTH);
            }
            if (keyboardController.wasBuyDamagePressed()) {
                model.purchaseUpgrade(GameModel.AbilityType.DAMAGE);
            }
            if (keyboardController.wasBuyJumpPressed()) {
                model.purchaseUpgrade(GameModel.AbilityType.JUMP);
            }
            
        }
    }
    
    // ========== COLLISION CONTROLLER ==========
    public static class CollisionController {
        
        /**
         * Check all collisions: player attacks vs enemies, enemy attacks vs player.
         */
        public void checkCollisions(GameModel model) {
            GameModel.Player player = model.getPlayer();
            if (player == null) return;
            
            // Check player attack hitbox vs each enemy
            if (player.hasActiveAttack()) {
                for (GameModel.Enemy enemy : model.getEnemies()) {
                    if (checkBoundingBoxOverlap(player, enemy)) {
                        if (player.tryDealAttack()) {
                            model.applyDamage(enemy, player.getCurrentAttackDamage());
                        }
                    }
                }
            }
            
            // Check enemy attack hitbox vs player
            for (GameModel.Enemy enemy : model.getEnemies()) {
                if (enemy instanceof GameModel.Enemy) {
                    GameModel.Enemy e = (GameModel.Enemy) enemy;
                    if (e.isAttackActive() && checkBoundingBoxOverlap(enemy, player)) {
                        if (e.tryDealAttack()) {
                            model.applyDamage(player, e.damage);
                        }
                    }
                }
            }
        }
        
        /**
         * Check bounding-box overlap between two entities.
         */
        private boolean checkBoundingBoxOverlap(GameModel.GameEntity a, GameModel.GameEntity b) {
            return !(a.getX() + a.getWidth() < b.getX() ||
                     b.getX() + b.getWidth() < a.getX() ||
                     a.getY() + a.getHeight() < b.getY() ||
                     b.getY() + b.getHeight() < a.getY());
        }
    }
    
    // ========== STATE CONTROLLER ==========
    public static class StateController {
        private GameModel model;
        private long restartCooldownRemainingMs;
        private static final long RESTART_COOLDOWN_MS = 5000;
        
        public StateController(GameModel model, GameView view) {
            this.model = model;
            this.restartCooldownRemainingMs = 0;
        }

        public void tick(long deltaMs) {
            if (restartCooldownRemainingMs > 0) {
                restartCooldownRemainingMs -= deltaMs;
                if (restartCooldownRemainingMs < 0) {
                    restartCooldownRemainingMs = 0;
                }
            }
        }
        
        /**
         * Handle state transitions (win/lose/level-clear).
         */
        public void checkStateTransitions() {
            if (model.getGameState() == GameModel.GameState.LEVEL_CLEARED) {
                model.advanceToNextLevel();
            }
        }
        
        /**
         * Start a new game from the title screen.
         */
        public void startNewGame() {
            model.resetPlayer();
            model.setGameState(GameModel.GameState.PLAYING);
            model.startLevel(1);
        }

        public boolean tryRestart() {
            if (restartCooldownRemainingMs > 0) {
                return false;
            }

            restartCooldownRemainingMs = RESTART_COOLDOWN_MS;
            startNewGame();
            return true;
        }
        
        /**
         * Advance to the next level.
         */
        public void nextLevel() {
            int nextLevel = model.getCurrentLevel() + 1;
            if (nextLevel > 4) {
                model.setGameState(GameModel.GameState.VICTORY);
            } else {
                model.startLevel(nextLevel);
            }
        }
        
        /**
         * Handle game over (player out of lives).
         */
        public void gameOver() {
            model.setGameState(GameModel.GameState.GAME_OVER);
        }
    }
    
    // ========== MAIN ENTRY POINT ==========
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new GameController();
        });
    }
}
