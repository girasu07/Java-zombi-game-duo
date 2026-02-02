import java.awt.*;
import java.io.Serializable;

public class Bullet implements Serializable {
    public double x, y;
    public double dx, dy;
    public int size = 6;
    public double speed = 10.0;

    public Bullet (double startX, double startY, double angle){
        this.x = startX;
        this.y = startY;
        this.dx = Math.cos(angle) * speed;
        this.dy = Math.sin(angle) * speed;
    }

    public void update() {
        x += dx;
        y += dy;
    }

    public void draw(Graphics2D g2) {
        g2.setColor(Color.YELLOW);
        g2.fillOval((int)x, (int)y, size, size);
    }
    
    // 画面外に出たかどうかの判定
    public boolean isOffScreen(int width, int height) {
        return x < 0 || x > width || y < 0 || y > height;
    }
}