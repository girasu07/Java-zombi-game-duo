import java.awt.*;

public class Player {
    // 外部からアクセスしやすいようpublicにしていますが、
    // 本格的にはprivateにしてgetter/setterを使うのが定石です
    public int x, y;
    public int size = 20;
    public int tileSize = 20;

    public int direction = 1; // 初期は右向き

    public Color color;//追加

    public boolean isDown = false; // ダウン中かどうか
    private int revivalTimer = 0;  // 復活までのカウント
    private static final int REVIVAL_TIME = 180; // 3秒 (60FPS想定: 60回×3秒)

    //reload関連
    public int maxAmmo = 30;       // マガジンサイズ（最大弾数）
    public int currentAmmo = 30;   // 今の残弾
    public boolean isReloading = false; // リロード中かどうか
    public int reloadTimer = 0;    // リロード時間を計るタイマー
    public int reloadDuration = 60; // リロードにかかる時間

    public Player(int startX, int startY, Color c) {
        // スタート位置もマスの角にきれいに合わせる補正
        this.x = (startX / tileSize) * tileSize;
        this.y = (startY / tileSize) * tileSize;
        this.color = c;//追加
    }

    public void moveStep(boolean up, boolean down, boolean left, boolean right, int screenWidth, int screenHeight, boolean lockDirection) {
        int dx = 0;
        int dy = 0;

        if (isDown) return; // ダウン中は動けない

        if (up) dy -= 1;
        if (down) dy += 1;
        if (left) dx -= 1;
        if (right) dx += 1;

        if (!lockDirection){
            if (dy < 0) direction = 0; // 上
            if (dx > 0) direction = 1; // 右
            if (dy > 0) direction = 2; // 下
            if (dx < 0) direction = 3; // 左
        }

        int nextX = x + dx * size;
        int nextY = y + dy * size;
        // 画面外に出ないように制限
        if (nextX >= 0 && nextX <= screenWidth - size) {
            x = nextX;
        }
        if (nextY >= 0 && nextY <= screenHeight - size) {
            y = nextY;
        }
    }

    public void reload() {
    // 弾が減っていて、かつリロード中じゃなければ実行
        if (currentAmmo < maxAmmo && !isReloading) {
            isReloading = true;
            GamePanel.playSE("sound_reloading.wav");
             // リロード完了    
        }
        // System.out.println("Reloading..."); // デバッグ用
    }


    public void checkRevival() {
        if (isDown) {
            revivalTimer++;
            // 3秒経ったら復活！
            if (revivalTimer >= REVIVAL_TIME) {
                isDown = false;
                revivalTimer = 0;
                System.out.println("復活！"); // デバッグ用
            }
        }
    }

    public void draw(Graphics2D g2) {
        if (isDown) {
            g2.setColor(new Color(this.color.getRed(),this.color.getGreen(),this.color.getBlue(),50));
        } else {
            g2.setColor(this.color);//変更
        }

        g2.fillRect(x, y, tileSize, tileSize);

        g2.setColor(Color.BLACK);
            switch (direction) {
                case 0: // 上
                    g2.fillRect(x + 5, y, 10, 5);
                    break;
                case 1: // 右
                    g2.fillRect(x + tileSize - 5, y + 5, 5, 10);
                    break;
                case 2: // 下
                    g2.fillRect(x + 5, y + tileSize - 5, 10, 5);
                    break;
                case 3: // 左
                    g2.fillRect(x, y + 5, 5, 10);
                    break;
                default:
                    break;
            }
    }

    public Rectangle getBounds() {
        return new Rectangle(x, y, tileSize, tileSize);
    }
    
    // 中心座標を取得（射撃計算用）
    public double getCenterX() { return x + tileSize / 2.0; }
    public double getCenterY() { return y + tileSize / 2.0; }
}
