/**
 * GameModel.java
 * Contains all game state and rules. No Swing imports.
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameModel {
    private static final long LEVEL_DURATION_MS = 60000;
    
    // Game state enum
    public enum GameState {
        TITLE, PLAYING, PAUSED, LEVEL_CLEARED, GAME_OVER, VICTORY
    }
    
    // Player state enum
    public enum PlayerState {
        IDLE, CROUCH, WALK, RUN, JUMP, FALL, PUNCH, KICK, HURT, DEAD, RESPAWNING
    }
    
    // Enemy state enum
    public enum EnemyState {
        WALK, ATTACK, HURT, DEAD
    }
    
    // World bounds
    public static final int WORLD_WIDTH = 1280;
    public static final int WORLD_HEIGHT = 720;
    public static final int GROUND_Y = 550; // Fixed ground baseline
    public static final int CHARACTER_GROUND_Y = 580; // sprite feet baseline used by physics and drawing
    
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
        this.levelTimeRemaining = LEVEL_DURATION_MS;
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
            enemy.update(deltaTime, player);
        }
        
        // Update spawner
        spawner.update(deltaTime, enemies);
        
        // Update level timer
        levelTimeRemaining -= (long)(deltaTime * 1000);
        if (levelTimeRemaining < 0) {
            levelTimeRemaining = 0;
        }
        
        // Remove dead entities
        enemies.removeIf(e -> e.isDead());
        
        // Check level transitions
        checkLevelTransitions();
    }
    
    /**
     * Check win/lose/level-clear conditions.
     */
    private void checkLevelTransitions() {
        if (player.getLives() <= 0) {
            gameState = GameState.GAME_OVER;
            return;
        }

        if (levelTimeRemaining <= 0) {
            if (currentLevel >= 4) {
                gameState = GameState.VICTORY;
            } else {
                gameState = GameState.LEVEL_CLEARED;
            }
        }
    }
    
    /**
     * Apply damage from an attack to a target.
     */
    public void applyDamage(GameEntity target, int damage) {
        if (target instanceof Player) {
            ((Player) target).takeDamage(damage);
        } else if (target instanceof Enemy) {
            ((Enemy) target).takeDamage(damage);
        }
    }
    
    /**
     * Start a new level.
     */
    public void startLevel(int levelNumber) {
        this.currentLevel = levelNumber;
        this.levelTimeRemaining = LEVEL_DURATION_MS;
        this.enemies.clear();
        this.spawner = new Spawner(levelNumber);
        this.gameState = GameState.PLAYING;

        // Give each level an immediate visual enemy check-in.
        int starterEnemies = Math.min(levelNumber, 3);
        for (int i = 0; i < starterEnemies; i++) {
            Enemy spawned = spawner.spawnRandomEnemy();
            if (spawned != null) {
                spawned.setX(WORLD_WIDTH - 120 - (i * 80));
                enemies.add(spawned);
            }
        }
    }

    public void advanceToNextLevel() {
        if (currentLevel >= 4) {
            gameState = GameState.VICTORY;
            return;
        }
        startLevel(currentLevel + 1);
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
    public boolean isCrouching() { return player != null && player.isCrouching(); }
    public void resetPlayer() { this.player = new Player(); }
    
    // ========== PLAYER CLASS ==========
    public static class Player extends GameEntity {
        private static final double WALK_SPEED = 260.0;
        private static final double RUN_SPEED = 380.0;
        private static final double GRAVITY = 1600.0;
        private static final double JUMP_VELOCITY = -700.0;

        private PlayerState state;
        private int hp;
        private int maxHp = 100;
        private int lives = 3;
        private int facing = 1; // 1 = right, -1 = left
        private double velocityY;
        private boolean onGround;
        private long attackFrameWindow; // Time active attack hitbox is active (ms)
        private long attackCooldownRemaining;
        private boolean attackDelivered;
        private long attackWindowDefault;
        private long respawnTimer;
        private boolean moveInputThisFrame;
        private boolean runHeld;
        private boolean downHeld;
        private long actionStateTimer;
        private int currentAttackDamage;
        private boolean crouching;
        
        public Player() {
            super(100, CHARACTER_GROUND_Y - 100, 92, 100);
            this.state = PlayerState.IDLE;
            this.hp = maxHp;
            this.velocityY = 0;
            this.onGround = true;
            this.attackFrameWindow = 0;
            this.attackCooldownRemaining = 0;
            this.attackDelivered = false;
            this.attackWindowDefault = 220;
            this.respawnTimer = 0;
            this.moveInputThisFrame = false;
            this.runHeld = false;
            this.downHeld = false;
            this.actionStateTimer = 0;
            this.currentAttackDamage = 0;
            this.crouching = false;
        }
        
        @Override
        public void update(double deltaTime) {
            long deltaMs = (long) (deltaTime * 1000);

            if (state == PlayerState.DEAD && lives > 0) {
                respawnTimer -= deltaMs;
                if (respawnTimer <= 0) {
                    respawn();
                }
                return;
            }

            if (attackCooldownRemaining > 0) {
                attackCooldownRemaining -= deltaMs;
                if (attackCooldownRemaining < 0) {
                    attackCooldownRemaining = 0;
                }
            }

            if (attackFrameWindow > 0) {
                attackFrameWindow -= deltaMs;
                if (attackFrameWindow < 0) {
                    attackFrameWindow = 0;
                    attackDelivered = false;
                }
            }

            if (actionStateTimer > 0) {
                actionStateTimer -= deltaMs;
                if (actionStateTimer <= 0 && onGround && (state == PlayerState.PUNCH || state == PlayerState.KICK || state == PlayerState.HURT)) {
                    state = PlayerState.IDLE;
                }
            }

            if (!onGround) {
                velocityY += GRAVITY * deltaTime;
            }

            x += velocityX * deltaTime;
            y += velocityY * deltaTime;

            if (x < 0) {
                x = 0;
            }
            if (x + width > WORLD_WIDTH) {
                x = WORLD_WIDTH - width;
            }

            if (y + height >= CHARACTER_GROUND_Y) {
                y = CHARACTER_GROUND_Y - height;
                velocityY = 0;
                onGround = true;
                if (state == PlayerState.JUMP || state == PlayerState.FALL) {
                    state = PlayerState.IDLE;
                }
            } else {
                onGround = false;
                if (velocityY > 0 || downHeld) {
                    state = PlayerState.FALL;
                }
            }

            if (!moveInputThisFrame && onGround && state != PlayerState.PUNCH && state != PlayerState.KICK && state != PlayerState.HURT) {
                velocityX = 0;
                if (state != PlayerState.IDLE) {
                    state = PlayerState.IDLE;
                }
            }

            moveInputThisFrame = false;
            runHeld = false;
            downHeld = false;
        }
        
        public void moveLeft() {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            moveInputThisFrame = true;
            facing = -1;
            velocityX = runHeld ? -RUN_SPEED : -WALK_SPEED;
            if (onGround && state != PlayerState.PUNCH && state != PlayerState.KICK && state != PlayerState.HURT) {
                state = runHeld ? PlayerState.RUN : PlayerState.WALK;
            }
        }

        public void moveRight() {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            moveInputThisFrame = true;
            facing = 1;
            velocityX = runHeld ? RUN_SPEED : WALK_SPEED;
            if (onGround && state != PlayerState.PUNCH && state != PlayerState.KICK && state != PlayerState.HURT) {
                state = runHeld ? PlayerState.RUN : PlayerState.WALK;
            }
        }

        public void setRunHeld(boolean runHeld) {
            this.runHeld = runHeld;
        }

        public void setDownHeld(boolean downHeld) {
            this.downHeld = downHeld;
        }

        public void jump() {
            if (!onGround || state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            velocityY = JUMP_VELOCITY;
            onGround = false;
            state = PlayerState.JUMP;
        }

        public void punch() {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            if (attackCooldownRemaining > 0) {
                return;
            }
            state = PlayerState.PUNCH;
            attackFrameWindow = attackWindowDefault;
            actionStateTimer = 220;
            currentAttackDamage = 10;
            velocityX = 0;
            attackCooldownRemaining = 2000;
            attackDelivered = false;
        }

        public void kick() {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            if (attackCooldownRemaining > 0) {
                return;
            }
            state = PlayerState.KICK;
            attackFrameWindow = attackWindowDefault;
            actionStateTimer = 260;
            currentAttackDamage = 15;
            velocityX = 0;
            attackCooldownRemaining = 2000;
            attackDelivered = false;
        }

        public void takeDamage(int damage) {
            if (state == PlayerState.DEAD || state == PlayerState.RESPAWNING) {
                return;
            }
            hp -= damage;
            if (hp <= 0) {
                hp = 0;
                lives--;
                state = PlayerState.DEAD;
                velocityX = 0;
                attackFrameWindow = 0;
                if (lives > 0) {
                    respawnTimer = 3000;
                }
            } else {
                state = PlayerState.HURT;
                actionStateTimer = 180;
            }
        }

        public void respawn() {
            hp = maxHp;
            x = 100;
            y = CHARACTER_GROUND_Y - height;
            velocityX = 0;
            velocityY = 0;
            onGround = true;
            state = PlayerState.IDLE;
            respawnTimer = 0;
            attackCooldownRemaining = 0;
            attackFrameWindow = 0;
            attackDelivered = false;
            downHeld = false;
        }
        
        public long getRespawnRemaining() { return respawnTimer; }
        
        public boolean tryDealAttack() {
            if (attackFrameWindow > 0 && !attackDelivered) {
                attackDelivered = true;
                return true;
            }
            return false;
        }
        
        public PlayerState getState() { return state; }
        public int getHp() { return hp; }
        public int getMaxHp() { return maxHp; }
        public int getLives() { return lives; }
        public int getFacing() { return facing; }
        public boolean hasActiveAttack() { return attackFrameWindow > 0; }
        public int getCurrentAttackDamage() { return currentAttackDamage; }
        public boolean isDownHeld() { return downHeld; }
        public boolean isOnGround() { return onGround; }
        public boolean isCrouching() { return crouching; }
        public void setCrouching(boolean c) { this.crouching = c; }
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
        protected long attackActiveWindow;
        protected boolean attackDelivered;
        protected long attackActiveWindowDefault;
        protected int facing = 1; // 1 = right, -1 = left
        
        public Enemy(double x, double y, int maxHp, int damage, double speed) {
            super(x, y, 72, 72);
            this.state = EnemyState.WALK;
            this.hp = maxHp;
            this.maxHp = maxHp;
            this.damage = damage;
            this.speed = speed;
            this.attackRange = 56;
            this.attackCooldown = 2000; // 2 seconds between attacks
            this.attackCooldownRemaining = 0;
            this.attackActiveWindow = 0;
            this.attackActiveWindowDefault = 220;
        }
        
        @Override
        public void update(double deltaTime) {
            // Use update(deltaTime, player) for AI behavior.
        }

        public void update(double deltaTime, Player player) {
            if (isDead()) {
                state = EnemyState.DEAD;
                return;
            }

            long deltaMs = (long) (deltaTime * 1000);
            if (attackCooldownRemaining > 0) {
                attackCooldownRemaining -= deltaMs;
                if (attackCooldownRemaining < 0) {
                    attackCooldownRemaining = 0;
                }
            }

            if (attackActiveWindow > 0) {
                attackActiveWindow -= deltaMs;
                if (attackActiveWindow <= 0) {
                    attackActiveWindow = 0;
                    // reset delivered marker for the next attack
                    attackDelivered = false;
                }
            }

            double distanceX = player.getX() - x;
            facing = distanceX >= 0 ? 1 : -1;

            if (Math.abs(distanceX) <= attackRange) {
                state = EnemyState.ATTACK;
                velocityX = 0;
                if (attackCooldownRemaining == 0) {
                    // begin an attack window during which collision controller can apply damage exactly once
                    attackActiveWindow = this.attackActiveWindowDefault > 0 ? this.attackActiveWindowDefault : 220;
                    attackCooldownRemaining = attackCooldown;
                    attackDelivered = false;
                }
            } else {
                state = EnemyState.WALK;
                walkToward(player.getX(), deltaTime);
            }

            y = CHARACTER_GROUND_Y - height;
        }
        
        public void walkToward(double targetX, double deltaTime) {
            if (targetX > x) {
                velocityX = speed;
            } else {
                velocityX = -speed;
            }
            x += velocityX * deltaTime;
        }

        public void walkToward(double targetX) {
            // Backwards-compatible stub.
            walkToward(targetX, 0);
        }

        public void attack() {
            state = EnemyState.ATTACK;
        }

        public void takeDamage(int damage) {
            if (state == EnemyState.DEAD) {
                return;
            }
            hp -= damage;
            if (hp <= 0) {
                hp = 0;
                state = EnemyState.DEAD;
                velocityX = 0;
            } else {
                state = EnemyState.HURT;
            }
        }

        /**
         * Attempt to apply a single attack hit during the current active window.
         * Returns true only the first time it is called while the window is active.
         */
        public boolean tryDealAttack() {
            if (attackActiveWindow > 0 && !attackDelivered) {
                attackDelivered = true;
                return true;
            }
            return false;
        }

        public boolean isAttackActive() {
            return attackActiveWindow > 0;
        }
        
        public EnemyState getState() { return state; }
        public int getHp() { return hp; }
        public int getFacing() { return facing; }
        public boolean isDead() { return hp <= 0; }
    }
    
    // ========== ENEMY SUBCLASSES ==========
    public static class Goblin extends Enemy {
        public Goblin(double x, double y) {
            super(x, y, 30, 5, 100.0);
            this.width = 72;
            this.height = 72;
            this.y = CHARACTER_GROUND_Y - height;
            this.attackCooldown = 2000; // goblins are slow
            this.attackActiveWindowDefault = 220;
        }
    }
    
    public static class Wolf extends Enemy {
        public Wolf(double x, double y) {
            super(x, y, 40, 10, 170.0);
            this.width = 104;
            this.height = 80;
            this.y = CHARACTER_GROUND_Y - height;
            this.attackCooldown = 1500; // wolves faster
            this.attackActiveWindowDefault = 160;
        }
    }
    
    public static class Rogue extends Enemy {
        public Rogue(double x, double y) {
            super(x, y, 60, 15, 130.0);
            this.width = 86;
            this.height = 96;
            this.y = CHARACTER_GROUND_Y - height;
            this.attackCooldown = 1800; // rogue medium
            this.attackActiveWindowDefault = 240;
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
        private final Random random = new Random();
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
            if (level == 1) {
                spawnInterval = 3200;
                maxEnemiesOnScreen = 3;
            } else if (level == 2) {
                spawnInterval = 2800;
                maxEnemiesOnScreen = 3;
            } else if (level == 3) {
                spawnInterval = 2400;
                maxEnemiesOnScreen = 4;
            } else {
                spawnInterval = 2100;
                maxEnemiesOnScreen = 4;
            }
        }
        
        /**
         * Update spawner and add enemies to the list if conditions met.
         */
        public void update(double deltaTime, List<Enemy> activeEnemies) {
            if (activeEnemies.size() >= maxEnemiesOnScreen) {
                return;
            }

            spawnTimer += (long) (deltaTime * 1000);
            if (spawnTimer >= spawnInterval) {
                spawnTimer = 0;
                Enemy spawned = spawnRandomEnemy();
                if (spawned != null) {
                    activeEnemies.add(spawned);
                }
            }
        }
        
        /**
         * Create a random enemy based on level config.
         */
        public Enemy spawnRandomEnemy() {
            int spawnX = WORLD_WIDTH + 20;
            int roll;
            if (level == 1) {
                return new Goblin(spawnX, GROUND_Y - 72);
            }

            if (level == 2) {
                roll = random.nextInt(2);
                return (roll == 0) ? new Goblin(spawnX, GROUND_Y - 72) : new Wolf(spawnX, GROUND_Y - 80);
            }

            if (level == 3) {
                roll = random.nextInt(3);
                if (roll == 0) {
                    return new Goblin(spawnX, GROUND_Y - 72);
                }
                if (roll == 1) {
                    return new Wolf(spawnX, GROUND_Y - 80);
                }
                return new Rogue(spawnX, GROUND_Y - 96);
            }

            // Level 4: all enemy types.
            roll = random.nextInt(3);
            if (roll == 0) {
                return new Goblin(spawnX, GROUND_Y - 72);
            }
            if (roll == 1) {
                return new Wolf(spawnX, GROUND_Y - 80);
            }
            return new Rogue(spawnX, GROUND_Y - 96);
        }
    }
}
