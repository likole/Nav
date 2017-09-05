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
    public boolean exit = false;

    SlamThread(AbstractSlamwarePlatform abstractSlamwarePlatform, float x, float y) {
        this.abstractSlamwarePlatform = abstractSlamwarePlatform;
        this.x = x;
        this.y = y;
    }

    public void setExit(boolean exit) {
        this.exit = exit;
    }

    @Override
    public void run() {
        while (true) {
            if (exit) {
                try {
                    abstractSlamwarePlatform.getCurrentAction().cancel();
                    break;
                } catch (Exception e) {

                }
            }
            Location location = abstractSlamwarePlatform.getLocation();
            float nx = location.getX();
            float ny = location.getY();
            float c = (float) (Math.hypot(nx - x, ny - y));

            if (c > 1) {
                float a = nx + (x - nx) / c;
                float b = ny + (y - ny) / c;
                abstractSlamwarePlatform.moveTo(new Location(a, b, 0));
                try {
                    sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } else {
                abstractSlamwarePlatform.moveTo(new Location(x, y, 0));
                break;
            }
        }
    }
}
