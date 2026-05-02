/**
 * GameModel.java
 * Contains all game state and rules. No Swing imports.
 */

import java.util.ArrayList;
import java.util.List;

public class GameModel {
    
    // Game state enum
    public enum GameState {
        TITLE, PLAYING, PAUSED, LEVEL_CLEARED, GAME_OVER, VICTORY
    }
    
    // Player state enum
    public enum PlayerState {
        IDLE, WALK, RUN, JUMP, PUNCH, KICK, HURT, DEAD, RESPAWNING
    }
    
    // Enemy state enum
    public enum EnemyState {
        WALK, ATTACK, HURT, DEAD
    }
    
    // World bounds
    public static final int WORLD_WIDTH = 1280;
    public static final int WORLD_HEIGHT = 720;
    public static final int GROUND_Y = 550; // Fixed ground baseline
    
    private GameState gameState;
    private int currentLevel;
    private long levelTimeRemaining; // milliseconds
    private Player player;
    private List<Enemy> enemies;
    private Spawner spawner;
    
    /**
     * Initialize game model with defaults.
     */
    public GameModel() {
        this.gameState = GameState.TITLE;
        this.currentLevel = 1;
        this.levelTimeRemaining = 60000; // 60 seconds
        this.player = new Player();
        this.enemies = new ArrayList<>();
        this.spawner = new Spawner(currentLevel);
    }
    
    /**
     * Update game state for one tick (~16ms at 60 FPS).
     */
    public void update(double deltaTime) {
        if (gameState != GameState.PLAYING) {
            return;
        }
        
        // Update player
        if (player != null) {
            player.update(deltaTime);
        }
        
        // Update enemies
        for (Enemy enemy : enemies) {
            enemy.update(deltaTime);
        }
        
        // Update spawner
        spawner.update(deltaTime, enemies);
        
        // Update level timer
        levelTimeRemaining -= (long)(deltaTime * 1000);
        
        // Remove dead entities
        enemies.removeIf(e -> e.isDead());
        
        // Check level transitions
        checkLevelTransitions();
    }
    
    /**
     * Check win/lose/level-clear conditions.
     */
    private void checkLevelTransitions() {
        // TODO: Implement transition logic
    }
    
    /**
     * Apply damage from an attack to a target.
     */
    public void applyDamage(GameEntity target, int damage) {
        // TODO: Implement collision-based damage
    }
    
    /**
     * Start a new level.
     */
    public void startLevel(int levelNumber) {
        this.currentLevel = levelNumber;
        this.levelTimeRemaining = 60000;
        this.enemies.clear();
        this.spawner = new Spawner(levelNumber);
        this.gameState = GameState.PLAYING;
    }
    
    /**
     * Pause the game.
     */
    public void pause() {
        if (gameState == GameState.PLAYING) {
            gameState = GameState.PAUSED;
        }
    }
    
    /**
     * Resume from pause.
     */
    public void resume() {
        if (gameState == GameState.PAUSED) {
            gameState = GameState.PLAYING;
        }
    }
    
    // ===== Getters =====
    public GameState getGameState() { return gameState; }
    public void setGameState(GameState state) { this.gameState = state; }
    public int getCurrentLevel() { return currentLevel; }
    public long getLevelTimeRemaining() { return levelTimeRemaining; }
    public Player getPlayer() { return player; }
    public List<Enemy> getEnemies() { return enemies; }
    public Spawner getSpawner() { return spawner; }
    
    // ========== PLAYER CLASS ==========
    public static class Player extends GameEntity {
        private PlayerState state;
        private int hp;
        private int maxHp = 100;
        private int lives = 3;
        private int facing = 1; // 1 = right, -1 = left
        private double velocityY;
        private boolean onGround;
        private long attackFrameWindow; // Time active attack hitbox is active (ms)
        private long respawnTimer;
        
        public Player() {
            super(100, GROUND_Y, 50, 50); // x, y, w, h (placeholder)
            this.state = PlayerState.IDLE;
            this.hp = maxHp;
            this.velocityY = 0;
            this.onGround = true;
            this.attackFrameWindow = 0;
            this.respawnTimer = 0;
        }
        
        @Override
        public void update(double deltaTime) {
            // TODO: Update position, velocity, state, animation
        }
        
        public void moveLeft() { /* TODO */ }
        public void moveRight() { /* TODO */ }
        public void jump() { /* TODO */ }
        public void punch() { /* TODO */ }
        public void kick() { /* TODO */ }
        public void takeDamage(int damage) { /* TODO */ }
        public void respawn() { /* TODO */ }
        
        public PlayerState getState() { return state; }
        public int getHp() { return hp; }
        public int getMaxHp() { return maxHp; }
        public int getLives() { return lives; }
        public int getFacing() { return facing; }
        public boolean hasActiveAttack() { return attackFrameWindow > 0; }
    }
    
    // ========== ENEMY BASE CLASS ==========
    public static abstract class Enemy extends GameEntity {
        protected EnemyState state;
        protected int hp;
        protected int maxHp;
        protected int damage;
        protected double speed;
        protected double attackRange;
        protected long attackCooldown;
        protected long attackCooldownRemaining;
        protected int facing = 1; // 1 = right, -1 = left
        
        public Enemy(double x, double y, int maxHp, int damage, double speed) {
            super(x, y, 40, 40); // Default size (override per enemy type)
            this.state = EnemyState.WALK;
            this.hp = maxHp;
            this.maxHp = maxHp;
            this.damage = damage;
            this.speed = speed;
            this.attackRange = 50; // Placeholder
            this.attackCooldown = 1000; // 1 second between attacks
            this.attackCooldownRemaining = 0;
        }
        
        @Override
        public void update(double deltaTime) {
            // TODO: Walk toward player, attack on cooldown
        }
        
        public void walkToward(double targetX) { /* TODO */ }
        public void attack() { /* TODO */ }
        public void takeDamage(int damage) { /* TODO */ }
        
        public EnemyState getState() { return state; }
        public int getHp() { return hp; }
        public int getFacing() { return facing; }
        public boolean isDead() { return hp <= 0; }
    }
    
    // ========== ENEMY SUBCLASSES ==========
    public static class Goblin extends Enemy {
        public Goblin(double x, double y) {
            super(x, y, 30, 5, 1.0);
        }
    }
    
    public static class Wolf extends Enemy {
        public Wolf(double x, double y) {
            super(x, y, 40, 10, 2.5);
        }
    }
    
    public static class Rogue extends Enemy {
        public Rogue(double x, double y) {
            super(x, y, 60, 15, 1.5);
        }
    }
    
    // ========== GAME ENTITY BASE CLASS ==========
    public static abstract class GameEntity {
        protected double x, y;
        protected double width, height;
        protected double velocityX;
        
        public GameEntity(double x, double y, double width, double height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.velocityX = 0;
        }
        
        public abstract void update(double deltaTime);
        
        public double getX() { return x; }
        public double getY() { return y; }
        public double getWidth() { return width; }
        public double getHeight() { return height; }
        public void setX(double x) { this.x = x; }
        public void setY(double y) { this.y = y; }
    }
    
    // ========== SPAWNER CLASS ==========
    public static class Spawner {
        private int level;
        private long spawnInterval; // milliseconds
        private long spawnTimer;
        private int maxEnemiesOnScreen;
        
        public Spawner(int level) {
            this.level = level;
            this.spawnInterval = 3000; // 3 seconds, tunable per level
            this.spawnTimer = 0;
            this.maxEnemiesOnScreen = 4;
            configureLevelSpawning();
        }
        
        /**
         * Configure spawning rules based on level.
         */
        private void configureLevelSpawning() {
            // TODO: Set enemy types, intervals, caps per level
        }
        
        /**
         * Update spawner and add enemies to the list if conditions met.
         */
        public void update(double deltaTime, List<Enemy> activeEnemies) {
            // TODO: Check interval and spawn cap, create new enemies
        }
        
        /**
         * Create a random enemy based on level config.
         */
        private Enemy spawnRandomEnemy() {
            // TODO: Instantiate appropriate enemy type
            return null;
        }
    }
}
