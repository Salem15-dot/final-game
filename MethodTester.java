public class MethodTester {
    public static void main(String[] args) {
        System.out.println("MethodTester: starting tests...");
        GameModel model = new GameModel();
        model.startLevel(1);
        GameModel.Player player = model.getPlayer();
        System.out.println("Initial player HP: " + player.getHp());
        System.out.println("Initial player lives: " + player.getLives());
        
        // Test movement
        double startX = player.getX();
        player.moveRight();
        player.update(0.016);
        double afterMoveX = player.getX();
        System.out.println("Player moved right: startX=" + startX + " after=" + afterMoveX + " moved=" + (afterMoveX - startX));
        
        // Test jump
        double beforeJumpY = player.getY();
        player.jump();
        player.update(0.016);
        System.out.println("Player state after jump: " + player.getState() + " yDelta=" + (player.getY() - beforeJumpY) + " onGround=" + (player.getY() + player.getHeight() >= GameModel.CHARACTER_GROUND_Y));
        
        // Test punch and attack window
        player.punch();
        System.out.println("Player state after punch: " + player.getState());
        System.out.println("Player attack first try: " + player.tryDealAttack());
        System.out.println("Player attack second try: " + player.tryDealAttack());
        
        // Test spawner
        GameModel.Spawner sp = model.getSpawner();
        GameModel.Enemy e = sp.spawnRandomEnemy();
        System.out.println("Spawner created enemy: " + (e == null ? "null" : e.getClass().getSimpleName()));
        
        // Test enemy damage and death
        if (e != null) {
            System.out.println("Enemy HP: " + e.getHp());
            e.takeDamage(e.getHp() + 10);
            System.out.println("Enemy isDead after lethal damage: " + e.isDead());
        }
        
        // Test attack flags
        GameModel.Goblin g = new GameModel.Goblin(100, 100);
        g.attackActiveWindow = 200;
        System.out.println("Goblin attackActive before try: " + g.isAttackActive());
        System.out.println("Goblin tryDealAttack first: " + g.tryDealAttack());
        System.out.println("Goblin tryDealAttack second: " + g.tryDealAttack());

        // Test restart cooldown helper indirectly via controller state machine.
        GameController.StateController stateController = new GameController.StateController(model, null);
        boolean firstRestart = stateController.tryRestart();
        boolean secondRestart = stateController.tryRestart();
        System.out.println("StateController restart first: " + firstRestart);
        System.out.println("StateController restart second: " + secondRestart);
        
        System.out.println("MethodTester: tests complete.");
    }
}
