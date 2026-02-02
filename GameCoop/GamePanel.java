import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import javax.sound.sampled.*;

// ★ここに追加:ネットワーク部分
import java.io.*;
import java.net.*;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseListener, MouseMotionListener {

    // 画面設定
    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;
    public static final int TILE_SIZE = 40; // ★追加：マスの定義

    // ゲームループ用
    private Thread gameThread;
    private boolean isRunning = true;

    // ゲームオブジェクト
    private ArrayList<Player> players = new ArrayList<>();
    private ArrayList<Bullet> bullets = new ArrayList<>();
    private ArrayList<Enemy> enemies = new ArrayList<>();

    Player p1;
    Player p2;

    // ネットワーク用の変数を追加 
    private Socket socket;             // 通信ケーブル
    private ObjectOutputStream out;    // 送信用ポスト
    private ObjectInputStream in;      // 受信用ポスト
    private boolean isHost;            // 自分がサーバー(Host)かどうか
    private boolean isConnected = false; // つながった？

    // 入力状態
    // Player 1 (WASD + Space)
    private boolean p1Up, p1Down, p1Left, p1Right, p1Shift;
    // Player 2 (Arrows + Enter)
    private boolean p2Up, p2Down, p2Left, p2Right, p2Shift;
    // private boolean keySpace;
    private int mouseX, mouseY;

    private boolean p1triggerLock = false;
    private boolean p2triggerLock = false;

    private boolean p1Shooting = false;
    private boolean p2Shooting = false;
    private int p1shootTimer = 0;
    private int p2shootTimer = 0;
   
    private static final int SHOOT_DELAY = 20; // 連射速度（小さいほど速い）

    private int PlayermoveTimer = 0;
    private double EnemymoveTimer = 0;
    private static final int MOVE_DELAY = 10; // 何フレームごとに動くか（小さいほど速い）
    // 60FPSで動作する場合、10 = 約0.16秒に1回移動

    // スポーン管理
    private Random random = new Random();
    private int spawnTimer = 0;

    public static int totalKills = 0;

    private boolean isGameOver = false;

    int sendCounter = 0;
    int inputSendCounter = 0;

    public GamePanel() {
        // プレイヤー初期化
        players.add(new Player(100, 100, Color.GREEN)); // Player 1
        players.add(new Player(200, 100, Color.blue)); // Player 2

        p1 = players.get(0);
        p2 = players.get(1);

        this.setPreferredSize(new Dimension(WIDTH, HEIGHT));
        this.setBackground(Color.GRAY);
        this.setFocusable(true);
        playSE("sound_zombeiVoice.wav");//起動音かつ、サウンドドライバー解放

        // リスナー登録
        this.addKeyListener(this);
        this.addMouseListener(this);
        this.addMouseMotionListener(this);
    }

    //ネットワーク用のメソッド
    // --- サーバー（ホスト）として待機する設定 ---
    public void setupServer() {
        isHost = true;
        try {
            System.out.println("クライアントの接続を待っています...");
            // ポート9999番で待ち受け開始
            ServerSocket serverSocket = new ServerSocket(9999);
            
            // 相手が来るまでここでプログラムが一時停止します（フリーズしたようになります）
            socket = serverSocket.accept(); 
            
            System.out.println("接続しました！ IP: " + socket.getInetAddress());
            isConnected = true;

            // データの通り道を作る（順序重要：出力が先！）
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            startServerReceiver();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- クライアント（ゲスト）として接続する設定 ---
    public void setupClient(String ip) {
        isHost = false;
        try {
            System.out.println("サーバーに接続しています... IP: " + ip);
            // 指定されたIPの9999番ポートに電話をかける
            socket = new Socket(ip, 9999);
            
            System.out.println("接続成功！");
            isConnected = true;

            // データの通り道を作る
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            startClientReceiver();

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("接続に失敗しました");
        }
    }

    public void startGame() {
        gameThread = new Thread(this);
        gameThread.start();
    }

    @Override
    public void run() {
        // ゲームループ (簡易版: 60FPS目標)
        double drawInterval = 1000000000 / 60; // 1秒を60で割る
        double delta = 0;
        long lastTime = System.nanoTime();
        long currentTime;
    
        while (!isGameOver) {
            currentTime = System.nanoTime();
            delta += (currentTime - lastTime) / drawInterval;
            lastTime = currentTime;
            if (delta >= 1) {
                // --- 通信がつながっている場合の処理 ---
                if (isConnected) {
                    if (isHost) {
                        // 【ホストの仕事】
                        // 1. ゲームを普通に進める (敵の移動、P1の移動など)
                        updateGame();
                        
                        // 2. クライアントからの「キー入力」を受け取って、P2に反映する
                        //receiveClientInput();

                        // 3. 最新の「座標データ」をクライアントに送る
                        sendGameState();

                    } else {
                        // 【クライアントの仕事】
                        // 1. 自分の「キー入力」をホストに送る
                        sendClientInput();
                    }
                } else {
                    // 通信がつながっていない場合は普通にゲームを進める（シングルプレイ状態）
                    //updateGame();
                }
                // 画面を描画
                repaint();
                delta--;
            }
        }
    }

    private void updateGame() {
        // 1. プレイヤー更新
        p1.checkRevival();
        p2.checkRevival();

        // ★ここで全滅チェック！
        if (!isGameOver && p1.isDown && p2.isDown) {
            //isRunning = false;
            isGameOver = true;
            playSE("sound_damage.wav");
        }

        // 2. 弾の更新
        Iterator<Bullet> bit = bullets.iterator();
        while (bit.hasNext()) {
            Bullet b = bit.next();
            b.update();
            if (b.isOffScreen(WIDTH, HEIGHT)) {
                bit.remove();
            }
        }

        // 3. 敵のスポーン (約1秒ごと)
        spawnTimer++;
        if (spawnTimer > 60) {
            spawnEnemy();
            spawnTimer = 0;
        }

        p1shootTimer++; // 常にカウントアップ
        p2shootTimer++;

        if (p1Shooting && !p1.isDown && !p1.isReloading) {
            Shoot(p1, p1Shooting, 1, p1.currentAmmo); // P1さん、撃てるなら撃って！
        }
        if (p2Shooting && !p2.isDown && !p2.isReloading) {
            Shoot(p2, p2Shooting, 2, p2.currentAmmo); // P2さん、撃てるなら撃って！
        }

        if (p1.isReloading) {
            p1.reloadTimer++;
            if (p1.reloadTimer >= p1.reloadDuration) {
                p1.currentAmmo = p1.maxAmmo;
                p1.isReloading = false;
                p1.reloadTimer = 0;
            }
        }
        if (p2.isReloading) {
            p2.reloadTimer++;
            if (p2.reloadTimer >= p2.reloadDuration) {
                p2.currentAmmo = p2.maxAmmo;
                p2.isReloading = false;
                p2.reloadTimer = 0;
            }
        }
        // 4. 敵の移動と当たり判定
        
        synchronized(enemies) {
            Iterator<Enemy> eit = enemies.iterator();
            while (eit.hasNext()) {
                Enemy e = eit.next();

                // プレイヤーとの衝突
                for (Player player : players) {
                    if (!player.isDown && e.getBounds().intersects(player.getBounds())) {
            
                    //ゲーム終了ではなく、そのプレイヤーをダウンさせる
                        player.isDown = true;
                        System.out.println("Player DOWN!"); // デバッグ用
                        playSE("sound_zombeiVoice.wav");
            
                    }
                }
            
            
                    // 弾との衝突
                boolean hit = false;
                Iterator<Bullet> bulletIt = bullets.iterator();
                while (bulletIt.hasNext()) {
                    Bullet b = bulletIt.next();
                    if (e.getBounds().contains(b.x, b.y)) {
                        bulletIt.remove(); // 弾消去
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    eit.remove(); // 敵消去
                    GamePanel.totalKills++; // カウントアップ
                }
            }
        }


        // --- ★変更点3：タイマーを使った移動処理 ---
        
        // 1. タイマーを進める
        PlayermoveTimer++;
        EnemymoveTimer += 0.5;
        // 2. タイマーが溜まっている かつ キーが押されていたら移動
        if (PlayermoveTimer >= MOVE_DELAY) {
            boolean moved = false; // 動いたかどうかのチェック

            p1.moveStep(p1Up, p1Down, p1Left, p1Right, WIDTH, HEIGHT, p1Shift);
            moved = true;
            p2.moveStep(p2Up, p2Down, p2Left, p2Right, WIDTH, HEIGHT, p2Shift);
            moved = true;
            // 動いた場合のみ、タイマーをリセット（＝次は10フレーム待て）
            
           if (moved) {
                PlayermoveTimer = 0;    
            } else {
                // 動いていないなら、次はすぐに動けるようにタイマーを満タンにしておく
                PlayermoveTimer = MOVE_DELAY;
            }    
        }
        // --- 3. 敵の移動 ---
    
        if (EnemymoveTimer >= MOVE_DELAY) {
        
            for (Enemy enemy : enemies) {
                // ★さっき作ったメソッドで、ターゲットを決める
                Player target = GamePanel.getClosestPlayer(this, enemy );
            
            // ターゲットが見つかったら（誰か生きていたら）、その座標に向かう
                if (target != null) {
                    // Enemyクラスの update メソッドに、目標の x, y を渡す
                    enemy.update(target.x, target.y);
                }
            }
            EnemymoveTimer = 0;
        }
    }

    // 敵(e)から見て、一番近くにいるプレイヤーを探し出すメソッド
    private static Player getClosestPlayer(GamePanel gamePanel, Enemy e) {
        Player closest = null;
        double minDist = 999999; // 最初はめちゃくちゃ遠いことにしておく

        for (Player p : gamePanel.players) {
            // 三平方の定理で距離を計算
            double dx = p.x - e.x;
            double dy = p.y - e.y;
            double dist = Math.sqrt(dx * dx + dy * dy);

            // 「今の最短記録」より近かったら、記録更新！
            if (!p.isDown && dist < minDist) {
                minDist = dist;
                closest = p; // 「君が今のターゲット候補だ」
            }
        }
        return closest; // 一番近かった人を返す
    }

    // プレイヤーごとの射撃処理をまとめたメソッド
    private void Shoot(Player p, boolean isShooting, int playerNum, int currentAmmo) {

        // --- 1. その人のタイマーを進める ---
        // (playerNumが 1 なら p1のタイマー、それ以外なら p2のタイマーを使う)
        if (playerNum == 1) {
            p1shootTimer++;
        } else {
            p2shootTimer++;
        }

        // 今のタイマーの値を取得（判定に使うため）
        int currentTimer = (playerNum == 1) ? p1shootTimer : p2shootTimer;

        // --- 2. 発射判定 ---
        // 「撃つボタンが押されている」かつ「連射待ち時間が過ぎているなら発射！
        if (currentTimer >= SHOOT_DELAY) {
            double angle = 0;
            if (currentAmmo > 0) {
                // プレイヤーの向き(0~3)に合わせて、弾の飛ぶ角度を決める
                switch (p.direction) {
                    case 0:
                        angle = -Math.PI / 2;
                        break; // 上
                    case 1:
                        angle = 0;
                        break; // 右
                    case 2:
                        angle = Math.PI / 2;
                        break; // 下
                    case 3:
                        angle = Math.PI;
                        break; // 左
                }
            } else {
                boolean isLocked = (playerNum == 1) ? p1triggerLock : p2triggerLock;
                if (!isLocked) {
                    playSE("sound_emptyfire.wav");
                    if (playerNum == 1) {
                        p1triggerLock = true;
                    } else {
                        p2triggerLock = true;
                    }
                }   
                return; // 弾が無いなら発射しない
            }
            // 弾を生成してリストに追加
            bullets.add(new Bullet(p.getCenterX(), p.getCenterY(), angle));
            p.currentAmmo--;
            playSE("sound_fire.wav");
            // --- 3. 撃った人のタイマーだけリセット ---
            if (playerNum == 1) {
                p1shootTimer = 0;
            } else {
                p2shootTimer = 0;
            }
        }
    }

    private void spawnEnemy() {
        // 0:上, 1:左, 2:右
        int dir = random.nextInt(3);
        int ex = 0, ey = 0;
        int size = TILE_SIZE; // 敵のサイズ

        switch (dir) {
            case 0:
                ex = random.nextInt(WIDTH / size);
                ey = -size;
                break;
            case 1:
                ex = -size;
                ey = random.nextInt(HEIGHT / size);
                break;
            case 2:
                ex = WIDTH;
                ey = random.nextInt(HEIGHT / size);
                break;
        }
        enemies.add(new Enemy(ex, ey));
    }

    // --- ホスト用：状態を送る ---
    private void sendGameState() {
        // カウンターを増やす
        sendCounter++;
        // 「3回に1回」だけ送る（それ以外は無視して帰る）
        if (sendCounter < 3) { 
            return; 
        }
        sendCounter = 0; // カウンターリセット
        try {
            ArrayList<Bullet> safeBullets;
            synchronized(bullets) {
            // 送信用のコピー（分身）を作成
            safeBullets = new ArrayList<>(this.bullets);
            }
            // 現在の全情報をパック詰め
            GameState state = new GameState(p1, p2, enemies, totalKills, safeBullets, this.isGameOver);
            out.writeObject(state);
            out.reset(); // ★重要：これを忘れると古いデータが送られ続けます！
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- ホスト用：P2のキー入力を受け取る ---
    private void startServerReceiver() {
        // 新しいスレッド（係の人）を作る
        new Thread(() -> {
            while (isConnected) { // つながっている間ずっと
                try {
                    // ★ここでデータが来るまで待機します（フリーズしません）
                    ClientInput input = (ClientInput) in.readObject();
                    
                    // 受け取ったら即反映
                    this.p2Up = input.up;
                    this.p2Down = input.down;
                    this.p2Left = input.left;
                    this.p2Right = input.right;
                    this.p2Shooting = input.isShooting;
                    
                    this.p2Shooting = input.isShooting; 
                    this.p2.isReloading = input.isReloading;// リロードボタン
                    
                } catch (Exception e) {
                    e.printStackTrace();
                    isConnected = false; // エラーが出たら切断扱いにする
                }
            }
        }).start(); // スレッド開始！
    }

    // --- クライアント用：キー入力を送る ---
    // --- クライアント用：自分の入力を送る（Threadにはしない！） ---
    private void sendClientInput() {
        inputSendCounter++;
        // ★ゲスト側も「3回に1回」に制限する！
        if (inputSendCounter < 3) {
            return;
        }   
        inputSendCounter = 0;
        try {
            ClientInput input = new ClientInput();
            input.up = p2Up;
            input.down = p2Down;
            input.left = p2Left;
            input.right = p2Right;
            input.isShooting = p2Shooting; // 変数名注意(p2Shootかp2Shiftか確認)
            input.isReloading = p2.isReloading;

           
            out.writeObject(input);
            out.reset(); // ★リセット必須
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // --- クライアント用：サーバーからの座標を受け取る「受信係」 ---
    private void startClientReceiver() {
        new Thread(() -> {
            while (isConnected) {
                try {
                    // ★ここは「読む（read）」場所です！
                    // サーバーから送られてきた座標データ(GameState)を受け取る
                    GameState state = (GameState) in.readObject();
                    
                    // --- 1. プレイヤーの同期 ---
                    p1.x = state.p1x;
                    p1.y = state.p1y;
                    p1.direction = state.p1Dir;
                    p1.isDown = state.p1IsDown;
                    p1.isReloading = state.p1IsReloading;

                    p2.x = state.p2x;
                    p2.y = state.p2y;
                    p2.direction = state.p2Dir;
                    p2.isDown = state.p2IsDown;
                    p2.isReloading = state.p2IsReloading;

                    if (isHost) {
                        // ホストの場合：相手(P2)の弾数だけ受け取る
                        p2.currentAmmo = state.p2currentAmmo;
                    } else {
                        // クライアントの場合：相手(P1)の弾数だけ受け取る
                        // ★重要：自分の弾(state.p1currentAmmo)は無視して、自分の計算を信じる！
                        p1.currentAmmo = state.p1currentAmmo; 

                        // 「自分(P2)の弾数」もサーバーの計算結果に合わせる！
                        p2.currentAmmo = state.p2currentAmmo;
                    }

                    GamePanel.totalKills = state.totalKills;
                    this.bullets = state.bullets;
                    
                    // --- 2. 敵の同期 ---
                    // エラー防止のため synchronized で守る
                    synchronized(enemies) { 
                        this.enemies.clear(); // 今いる敵を全消去
                        for (int[] data : state.enemiesData) {
                            // 送られてきたデータで敵を作り直す
                            Enemy e = new Enemy(data[0], data[1]);
                            e.direction = data[2]; // 方向もセット
                            this.enemies.add(e);
                        }
                    }

                    this.isGameOver = state.isGameOver; // 箱に詰める
                    
                    // (デバッグ用) ちゃんと届いてるか確認したいならコメント外す
                    // System.out.println("座標受信完了");

                } catch (Exception e) {
                    e.printStackTrace();
                    isConnected = false;
                    break;
                }
            }
        }).start();
    }
    // --- クライアント用：座標を受け取る ---
    private void receiveGameState() {
        // 受信は startClientReceiver() スレッドでやっているので、ここは空っぽでOK
    }
       

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;

        // 各オブジェクトに「自分を描画しろ」と命令する
        p1.draw(g2);
        p2.draw(g2);

        // 銃口のガイド線
        // g2.setColor(Color.GREEN);
        // g2.drawLine((int)player.getCenterX(), (int)player.getCenterY(), mouseX,
        // mouseY);

        synchronized(enemies) { // ★ここにもロックが必要です！
            for (Enemy e : enemies) {
                if (e != null) e.draw(g2);
            }for (Bullet b : bullets)
                if (b != null) b.draw(g2);
        }

        // paintComponentの中の super.paintComponent(g); の直下に書く
        g2.setColor(Color.LIGHT_GRAY);
        for (int i = 0; i <= WIDTH; i += TILE_SIZE)
            g2.drawLine(i, 0, i, HEIGHT);
        for (int i = 0; i <= HEIGHT; i += TILE_SIZE)
            g2.drawLine(0, i, WIDTH, i);

        // ★UI表示（一番手前に描くため、最後に書く）
        g2.setFont(new Font("Arial", Font.BOLD, 40)); // フォント設定（太字、サイズ40）
        g2.setColor(Color.WHITE); // 文字色
        // 文字を描画 (表示する文字, X座標, Y座標)
        g2.drawString("KILLS: " + totalKills, 30, 50);

        g2.setFont(new Font("Arial", Font.BOLD, 20));
        g2.setColor(Color.WHITE);

       
        if (p1.isReloading) {
            // リロード中は赤文字で警告
            g2.setColor(Color.RED);
            g2.drawString("RELOADING...", p1.x, p1.y - 10); // キャラの上に表示
        } else {
            // 通常時は弾数を表示
            if (isHost) {
                g2.setColor(Color.WHITE);
                g2.drawString("Ammo: " + p1.currentAmmo + " / " + p1.maxAmmo, 650, 50);
            }
        }
        
        if (p2.isReloading) {
            // リロード中は赤文字で警告
            g2.setColor(Color.RED);
            g2.drawString("RELOADING...", p2.x, p2.y - 10); // キャラの上に表示
        } else {
            if (!isHost) {
                // 通常時は弾数を表示
                g2.setColor(Color.WHITE);
                g2.drawString("Ammo: " + p2.currentAmmo + " / " + p2.maxAmmo, 650, 50);
            }
        }
        
        if (isGameOver) {
        drawGameOverScreen(g2);
        }
    }
    
    // 音を鳴らす専用のメソッド
    public static void playSE(String fileName) {
        new Thread(() -> {
            try {
                File soundFile = new File("sounds/" + fileName);
                AudioInputStream audioIn = AudioSystem.getAudioInputStream(soundFile);
                Clip clip = AudioSystem.getClip();
                clip.open(audioIn);
                clip.start();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start(); // 
    }

    private void drawGameOverScreen(Graphics2D g2) {
    
        String text = "YOU ARE DEAD";
    
        // --- 1. フォントと色の設定 ---
        // Font(フォント名, スタイル, サイズ)
        // サイズを「80」とか「100」にすると「でかでか」になります！
        Font gameOverFont = new Font("Stencil", Font.BOLD, 90); 
        g2.setFont(gameOverFont);
        g2.setColor(new Color(255, 0, 70)); // 赤い字！

        // --- 2. 画面の真ん中に描くための計算（ちょっと難しいけどコピペでOK）---
        // 「このフォントだと、この文字は幅何ピクセルになるか？」を計算してくれる便利なやつ
        FontMetrics metrics = g2.getFontMetrics(gameOverFont);
    
        // 画面の幅の中央から、文字の幅の半分を左にずらす
        int x = (WIDTH - metrics.stringWidth(text)) / 2;
        // 画面の高さの中央（微調整込み）
        int y = HEIGHT / 2;

        // --- 3. 描画実行！ ---
        g2.drawString(text, x, y);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W)
            if(isHost) p1Up = true;
            else p2Up = true;
        if (code == KeyEvent.VK_S)
            if(isHost) p1Down = true;
            else p2Down = true;
        if (code == KeyEvent.VK_A)
            if(isHost) p1Left = true;
            else p2Left = true;
        if (code == KeyEvent.VK_D)
            if(isHost) p1Right = true;
            else p2Right = true;    

        if (code == KeyEvent.VK_SPACE) {
            if(isHost) {
                p1Shooting = true;
            }
            else{
                p2Shooting = true;
            }
        
            //Shoot(p1, p1Shooting, 1); // P1さん、撃てるなら撃って！
            //Shoot(p2, p2Shooting, 2); // P2さん、撃てるなら撃って！
        }

        if (code == KeyEvent.VK_SHIFT) {
            if(isHost) {
                if(!p1.isDown)p1Shift = true;
            }
            else{
                if(!p2.isDown)p2Shift = true;
            }
        }
        if (code == KeyEvent.VK_R) {
            if(isHost) {
                if(!p1.isDown)p1.reload();
            }
            else{
                if(!p2.isDown)p2.reload();
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int code = e.getKeyCode();
        if (code == KeyEvent.VK_W)
            if(isHost) p1Up = false;
            else p2Up = false;
        if (code == KeyEvent.VK_S)
            if(isHost) p1Down = false;
            else p2Down = false;
        if (code == KeyEvent.VK_A)
            if(isHost) p1Left = false;
            else p2Left = false;
        if (code == KeyEvent.VK_D)
            if(isHost) p1Right = false;
            else p2Right = false;

        // キーを離した瞬間、次の入力がすぐ効くようにタイマーを少し進めておくテクニック
        // （必須ではありませんが、操作感が良くなります）
        if (!p1Up && !p1Down && !p1Left && !p1Right) {
            PlayermoveTimer = MOVE_DELAY;
        }

        if (code == KeyEvent.VK_SPACE) {
            p1Shooting = false;
            p2Shooting = false;
        }

        if (code == KeyEvent.VK_SHIFT) {
            p1Shift = false;
            p2Shift = false;
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // isShooting = true;
        // shootTimer = SHOOT_DELAY; // スペースキーで発射に変更
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        // isShooting = false;
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        mouseX = e.getX();
        mouseY = e.getY();
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}
