import javax.swing.JFrame;

public class Main {
    public static void main(String[] args) {
        JFrame frame = new JFrame("Zombie Hanting");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // ゲーム画面（パネル）を作成してフレームに乗せる
        GamePanel gamePanel = new GamePanel();
        frame.add(gamePanel);
        frame.pack(); // パネルのサイズに合わせてウィンドウを調整
        
        frame.setLocationRelativeTo(null); // 画面中央に表示
        frame.setVisible(true);
        
        // ゲームループ開始
        gamePanel.startGame();
    }
}
