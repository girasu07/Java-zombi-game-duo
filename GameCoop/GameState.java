import java.io.Serializable;
import java.util.ArrayList;
import java.awt.Color;

// "Serializable" は「このクラスを通信で送れる形式に変換していいよ」という許可証です
public class GameState implements Serializable {
    
    // --- プレイヤー情報 ---
    public int p1x, p1y, p1Dir;
    public boolean p1IsDown;
    
    public int p2x, p2y, p2Dir;
    public boolean p2IsDown;

    public int totalKills;
    public int p1currentAmmo;
    public int p2currentAmmo;

    public boolean p1IsReloading; 
    public boolean p2IsReloading;

    // --- 敵の情報（数が多いので簡略化したクラスを作ってリストにするのが一般的ですが、一旦座標配列で） ---
    public ArrayList<int[]> enemiesData;
    public ArrayList<Bullet> bullets;

    public boolean isGameOver;
    
    // コンストラクタ（データをセットする）
    public GameState(Player p1, Player p2, ArrayList<Enemy> enemies, int totalKills, ArrayList<Bullet> bullets, boolean isGameOver) {
        this.p1x = p1.x;
        this.p1y = p1.y;
        this.p1Dir = p1.direction;
        this.p1IsDown = p1.isDown;

        this.p2x = p2.x;
        this.p2y = p2.y;
        this.p2Dir = p2.direction;
        this.p2IsDown = p2.isDown;

        this.totalKills = totalKills;
        this.p1currentAmmo = p1.currentAmmo;
        this.p2currentAmmo = p2.currentAmmo;

        this.p1IsReloading = p1.isReloading;
        this.p2IsReloading = p2.isReloading;
        
        this.bullets = bullets;
        this.enemiesData = new ArrayList<>();
        for (Enemy e : enemies) {
            // 敵1体につき、{x, y} というデータをリストに追加
            this.enemiesData.add(new int[]{e.x, e.y, e.direction});
        }
        this.isGameOver = isGameOver; // 箱に詰める   
    }
}