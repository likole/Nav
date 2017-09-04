package cn.likole.nav;

import com.slamtec.slamware.AbstractSlamwarePlatform;
import com.slamtec.slamware.robot.Location;

/**
 * Created by likole on 9/4/17.
 */

public class SlamThread extends Thread {
    AbstractSlamwarePlatform abstractSlamwarePlatform;
    private float x;
    private float y;

    SlamThread(AbstractSlamwarePlatform abstractSlamwarePlatform, float x, float y) {
        this.abstractSlamwarePlatform = abstractSlamwarePlatform;
        this.x = x;
        this.y = y;
    }

    @Override
    public void run() {
        while (true) {
            Location location = abstractSlamwarePlatform.getLocation();
            float nx = location.getX();
            float ny = location.getY();
            float c;

            if ((c = (float) (Math.hypot(nx - x, ny - y))) > 1) {
                abstractSlamwarePlatform.moveTo(new Location(nx + (x - nx) / c, ny + (y - ny) / c, 0));
                try {
                    sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                interrupt();
            }
        }
    }
}
