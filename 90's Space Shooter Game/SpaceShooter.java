import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.io.*;
import javax.sound.sampled.*;

public class SpaceShooter extends JPanel implements ActionListener, KeyListener {

    // screen
    private final int WIDTH = 640;
    private final int HEIGHT = 800;

    // game loop timer (use Swing Timer explicitly where needed)
    private javax.swing.Timer timer;

    // player
    private int playerX;
    private final int playerY = 700;
    private final int PLAYER_W = 48, PLAYER_H = 48;
    private int playerSpeed = 8;
    private int skin = 1;

    // bullets
    private java.util.List<Bullet> bullets = new ArrayList<>();
    private int shootCooldown = 0;
    private boolean rapidFire = false;

    // enemies
    private java.util.List<Enemy> enemies = new ArrayList<>();
    private int enemySpawnTimer = 0;
    private int enemySpawnRate = 60; // ticks
    private int baseEnemySpeed = 2;

    // powerups
    private java.util.List<PowerUp> powerUps = new ArrayList<>();

    // game state
    boolean paused = false;
    boolean gameOver = false;
    private int score = 0;
    private int highScore = 0;
    private int level = 1;
    private int ticks = 0;

    // powerup effects
    private boolean shieldActive = false;
    private int shieldTicks = 0;
    private boolean doubleScore = false;
    private int doubleScoreTicks = 0;

    // sounds (optional)
    private Clip bgmClip = null;

    private Random rand = new Random();

    public SpaceShooter() {
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setBackground(Color.black);
        setFocusable(true);
        addKeyListener(this);

        loadHighScore();
        resetGame();

        timer = new javax.swing.Timer(16, this); // ~60 FPS
        timer.start();

        playBackgroundMusic("bgm.wav"); // optional: put a bgm.wav in same folder
    }

    private void resetGame() {
        bullets.clear();
        enemies.clear();
        powerUps.clear();
        playerX = WIDTH / 2 - PLAYER_W / 2;
        score = 0;
        level = 1;
        gameOver = false;
        paused = false;
        ticks = 0;
        enemySpawnRate = 60;
        baseEnemySpeed = 2;
        shieldActive = false;
        rapidFire = false;
        doubleScore = false;
    }

    private void loadHighScore() {
        try {
            File f = new File("highscore_ss.dat");
            if (f.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line = br.readLine();
                if (line != null) highScore = Integer.parseInt(line.trim());
                br.close();
            }
        } catch (Exception ignored) {}
    }

    private void saveHighScore() {
        try {
            BufferedWriter bw = new BufferedWriter(new FileWriter("highscore_ss.dat"));
            bw.write(String.valueOf(highScore));
            bw.close();
        } catch (Exception ignored) {}
    }

    private void spawnEnemy() {
        int w = 40 + rand.nextInt(40);
        int x = rand.nextInt(WIDTH - w - 20) + 10;
        int hp = 1 + rand.nextInt(Math.min(3, level));
        int speed = baseEnemySpeed + level / 2 + rand.nextInt(2);
        enemies.add(new Enemy(x, -60, w, 36, hp, speed));
    }

    private void spawnPowerUp(int x, int y) {
        int type = rand.nextInt(3); // 0: rapid, 1: shield, 2: double
        powerUps.add(new PowerUp(x, y, type));
    }

    @Override
    protected void paintComponent(Graphics gg) {
        super.paintComponent(gg);
        Graphics2D g = (Graphics2D) gg;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // starfield background
        g.setColor(Color.black);
        g.fillRect(0,0,WIDTH,HEIGHT);
        g.setColor(new Color(30,30,40));
        for (int i = 0; i < 60; i++) {
            int sx = (i * 23 + ticks*2) % WIDTH;
            int sy = (i * 47 + ticks*3) % HEIGHT;
            g.fillRect(sx, sy, 2, 2);
        }

        // draw player
        drawPlayer(g);

        // bullets
        g.setColor(Color.cyan);
        for (Bullet b : bullets) g.fillRect(b.x, b.y, b.w, b.h);

        // enemies
        for (Enemy en : enemies) {
            if (en.hp == 1) g.setColor(new Color(220, 90, 90));
            else if (en.hp == 2) g.setColor(new Color(220,150,60));
            else g.setColor(new Color(200,80,200));
            g.fillRoundRect(en.x, en.y, en.w, en.h, 8, 8);

            // hp bar
            g.setColor(Color.darkGray);
            g.fillRect(en.x, en.y - 8, en.w, 4);
            g.setColor(Color.green);
            int bar = (int)((en.hp / (double)en.maxHp) * en.w);
            g.fillRect(en.x, en.y - 8, bar, 4);
        }

        // powerups
        for (PowerUp p : powerUps) {
            if (p.type == 0) g.setColor(new Color(80,200,255));
            else if (p.type == 1) g.setColor(new Color(255,220,80));
            else g.setColor(new Color(160,120,255));
            g.fillOval(p.x, p.y, 18, 18);
        }

        // HUD
        g.setColor(Color.white);
        g.setFont(new Font("Consolas", Font.BOLD, 18));
        g.drawString("Score: " + score + "   High: " + highScore + "   Level: " + level, 10, 26);

        String status = "";
        if (shieldActive) status += "[Shield] ";
        if (rapidFire) status += "[Rapid] ";
        if (doubleScore) status += "[Double] ";
        if (!status.isEmpty()) g.drawString(status, 10, 48);

        if (paused) {
            g.setColor(new Color(0,0,0,160));
            g.fillRect(0,0,WIDTH,HEIGHT);
            g.setColor(Color.white);
            g.setFont(new Font("Arial", Font.BOLD, 54));
            g.drawString("PAUSED", WIDTH/2 - 110, HEIGHT/2);
        }

        if (gameOver) {
            g.setColor(new Color(0,0,0,200));
            g.fillRect(0,0,WIDTH,HEIGHT);
            g.setColor(Color.red);
            g.setFont(new Font("Arial", Font.BOLD, 56));
            g.drawString("GAME OVER", WIDTH/2 - 170, HEIGHT/2 - 10);
            g.setFont(new Font("Arial", Font.PLAIN, 24));
            g.setColor(Color.white);
            g.drawString("Press R to Restart", WIDTH/2 - 110, HEIGHT/2 + 30);
            g.drawString("Final Score: " + score, WIDTH/2 - 90, HEIGHT/2 + 70);
        }
    }

    private void drawPlayer(Graphics2D g) {
        int cx = playerX, cy = playerY;
        // draw hull according to skin
        if (skin == 1) {
            g.setColor(Color.white);
            g.fillOval(cx, cy, PLAYER_W, PLAYER_H);
            g.setColor(Color.blue);
            g.fillOval(cx + 8, cy + 8, PLAYER_W - 16, PLAYER_H - 16);
        } else if (skin == 2) {
            g.setColor(Color.white);
            int[] xs = {cx + PLAYER_W/2, cx + PLAYER_W, cx};
            int[] ys = {cy, cy + PLAYER_H, cy + PLAYER_H};
            g.fillPolygon(xs, ys, 3);
            g.setColor(Color.cyan);
            int[] xs2 = {cx + PLAYER_W/2, cx + PLAYER_W - 6, cx + 6};
            int[] ys2 = {cy + 6, cy + PLAYER_H - 10, cy + PLAYER_H - 10};
            g.fillPolygon(xs2, ys2, 3);
        } else {
            g.setColor(Color.gray);
            g.fillRoundRect(cx, cy + 6, PLAYER_W, PLAYER_H - 12, 20, 20);
            g.setColor(Color.yellow);
            g.fillOval(cx + 10, cy + 12, PLAYER_W - 20, PLAYER_H - 24);
        }
        // shield ring
        if (shieldActive) {
            g.setColor(new Color(120,220,255,120));
            g.setStroke(new BasicStroke(4));
            g.drawOval(cx - 6, cy - 6, PLAYER_W + 12, PLAYER_H + 12);
            g.setStroke(new BasicStroke(1));
        }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!paused && !gameOver) {
            gameTick();
        }
        repaint();
    }

    private void gameTick() {
        ticks++;

        // spawn enemies periodically, faster with level
        enemySpawnTimer++;
        if (enemySpawnTimer >= Math.max(10, enemySpawnRate - level*4)) {
            enemySpawnTimer = 0;
            spawnEnemy();
        }

        // update bullets
        Iterator<Bullet> bit = bullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            b.y -= b.speed;
            if (b.y < -20) bit.remove();
        }

        // update enemies
        Iterator<Enemy> eit = enemies.iterator();
        while (eit.hasNext()) {
            Enemy en = eit.next();
            en.y += en.speed;
            if (en.y > HEIGHT + 50) {
                eit.remove();
                // penalty: lose points
                score = Math.max(0, score - 5);
            }
        }

        // update powerups
        Iterator<PowerUp> pit = powerUps.iterator();
        while (pit.hasNext()) {
            PowerUp p = pit.next();
            p.y += 2;
            if (p.y > HEIGHT + 20) pit.remove();
        }

        // bullets vs enemies
        bit = bullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            for (Enemy en : new ArrayList<>(enemies)) {
                if (rectCollision(b.x, b.y, b.w, b.h, en.x, en.y, en.w, en.h)) {
                    en.hp -= b.damage;
                    bit.remove();
                    playSound("explosion.wav", false);
                    if (en.hp <= 0) {
                        enemies.remove(en);
                        int gained = 10;
                        if (doubleScore) gained *= 2;
                        score += gained;
                        if (rand.nextInt(100) < 20) spawnPowerUp(en.x + en.w/2, en.y); // spawn powerup
                        if (score > highScore) { highScore = score; saveHighScore(); }
                    }
                    break;
                }
            }
        }

        // enemies vs player
        for (Enemy en : new ArrayList<>(enemies)) {
            if (rectCollision(en.x, en.y, en.w, en.h, playerX, playerY, PLAYER_W, PLAYER_H)) {
                if (shieldActive) {
                    // consume shield and remove enemy
                    shieldActive = false;
                    shieldTicks = 0;
                    enemies.remove(en);
                    playSound("explosion.wav", false);
                } else {
                    // game over
                    triggerGameOver();
                    return;
                }
            }
        }

        // player vs powerups
        pit = powerUps.iterator();
        while (pit.hasNext()) {
            PowerUp p = pit.next();
            if (rectCollision(p.x, p.y, 18, 18, playerX, playerY, PLAYER_W, PLAYER_H)) {
                activatePowerUp(p.type);
                pit.remove();
                playSound("powerup.wav", false);
            }
        }

        // powerup durations
        if (shieldActive) {
            shieldTicks--;
            if (shieldTicks <= 0) shieldActive = false;
        }
        if (doubleScore) {
            doubleScoreTicks--;
            if (doubleScoreTicks <= 0) doubleScore = false;
        }

        // difficulty scaling
        if (score > level * 50) {
            level++;
            baseEnemySpeed++;
            enemySpawnRate = Math.max(20, enemySpawnRate - 4);
        }

        // shooting cooldown
        if (shootCooldown > 0) shootCooldown--;
    }

    private boolean rectCollision(int x1,int y1,int w1,int h1,int x2,int y2,int w2,int h2) {
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    private void activatePowerUp(int type) {
        if (type == 0) { // rapid-fire
            rapidFire = true;
            // schedule turning off rapidFire after 10 seconds using java.util.Timer (fully-qualified)
            new java.util.Timer().schedule(new java.util.TimerTask() {
                @Override
                public void run() { rapidFire = false; }
            }, 10000);
        } else if (type == 1) { // shield
            shieldActive = true;
            shieldTicks = 600; // ticks (~10s)
        } else if (type == 2) { // double score
            doubleScore = true;
            doubleScoreTicks = 600;
        }
        playSound("powerup.wav", false);
    }

    private void triggerGameOver() {
        gameOver = true;
        stopBackgroundMusic();
        playSound("explosion.wav", false);
        timer.stop();
    }

    // Input handling
    private boolean leftPressed=false, rightPressed=false, shootingPressed=false;
    @Override
    public void keyPressed(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT) leftPressed = true;
        if (k == KeyEvent.VK_RIGHT) rightPressed = true;
        if (k == KeyEvent.VK_SPACE) shootingPressed = true;
        if (k == KeyEvent.VK_P) {
            paused = !paused;
            if (paused) stopBackgroundMusic(); else playBackgroundMusic("bgm.wav");
            if (!paused) timer.start(); else timer.stop();
        }
        if (k == KeyEvent.VK_R) {
            resetGame();
            playBackgroundMusic("bgm.wav");
            timer.setDelay(16);
            timer.start();
        }
        if (k == KeyEvent.VK_1) skin = 1;
        if (k == KeyEvent.VK_2) skin = 2;
        if (k == KeyEvent.VK_3) skin = 3;
        // immediate response: move/shoot on key pressed
        applyInputActions();
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_LEFT) leftPressed = false;
        if (k == KeyEvent.VK_RIGHT) rightPressed = false;
        if (k == KeyEvent.VK_SPACE) shootingPressed = false;
    }

    @Override public void keyTyped(KeyEvent e) {}

    private void applyInputActions() {
        // movement
        if (leftPressed) playerX = Math.max(0, playerX - playerSpeed);
        if (rightPressed) playerX = Math.min(WIDTH - PLAYER_W, playerX + playerSpeed);
        // shooting
        if (shootingPressed) attemptShoot();
    }

    private void attemptShoot() {
        int cooldownThreshold = rapidFire ? 6 : 14;
        if (shootCooldown <= 0) {
            bullets.add(new Bullet(playerX + PLAYER_W/2 - 4, playerY - 12, 8, 12, 10));
            playSound("shoot.wav", false);
            shootCooldown = cooldownThreshold;
        }
    }

    // Play sound helper (optional)
    private void playSound(String file, boolean wait) {
        try {
            File soundFile = new File(file);
            if (!soundFile.exists()) return;
            AudioInputStream audio = AudioSystem.getAudioInputStream(soundFile);
            Clip clip = AudioSystem.getClip();
            clip.open(audio);
            clip.start();
            if (wait) Thread.sleep(10);
        } catch (Exception ex) { /* ignore */ }
    }

    private void playBackgroundMusic(String file) {
        try {
            File soundFile = new File(file);
            if (!soundFile.exists()) return;
            AudioInputStream audio = AudioSystem.getAudioInputStream(soundFile);
            bgmClip = AudioSystem.getClip();
            bgmClip.open(audio);
            bgmClip.loop(Clip.LOOP_CONTINUOUSLY);
            bgmClip.start();
        } catch (Exception ex) { /* ignore */ }
    }

    private void stopBackgroundMusic() {
        if (bgmClip != null) {
            bgmClip.stop();
            bgmClip.close();
            bgmClip = null;
        }
    }

    // inner classes
    private static class Bullet {
        int x, y, w, h, speed, damage;
        Bullet(int x,int y,int w,int h,int dmg){ this.x=x;this.y=y;this.w=w;this.h=h;this.speed=12;this.damage=dmg;}
    }
    private static class Enemy {
        int x,y,w,h,hp,maxHp,speed;
        Enemy(int x,int y,int w,int h,int hp,int speed){ this.x=x;this.y=y;this.w=w;this.h=h;this.hp=hp;this.maxHp=hp;this.speed=speed;}
    }
    private static class PowerUp {
        int x,y,type;
        PowerUp(int x,int y,int t){ this.x=x; this.y=y; this.type=t;}
    }

    // main
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Space Shooter - Java");
            SpaceShooter g = new SpaceShooter();
            f.add(g);
            f.pack();
            f.setResizable(false);
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.setLocationRelativeTo(null);
            f.setVisible(true);

            // Use javax.swing.Timer for input poller (fully-qualified to avoid ambiguity)
            new javax.swing.Timer(16, ae -> {
                if (!g.paused && !g.gameOver) {
                    g.applyInputActions();
                }
            }).start();
        });
    }
}
