package cn.likole.nav;

import android.util.Log;

import com.slamtec.slamware.AbstractSlamwarePlatform;
import com.slamtec.slamware.action.MoveDirection;
import com.slamtec.slamware.robot.Location;
import com.slamtec.slamware.robot.Pose;
import com.slamtec.slamware.robot.Rotation;

/**
 * Created by likole on 9/3/17.
 */

public class SocketThread extends Thread {

    Client client;
    NavActivity activity;
    AbstractSlamwarePlatform abstractSlamwarePlatform;
    SlamThread slamThread;

    SocketThread(AbstractSlamwarePlatform abstractSlamwarePlatform, NavActivity activity) {
        this.abstractSlamwarePlatform = abstractSlamwarePlatform;
        this.activity = activity;
    }

    @Override
    public void run() {
        client = new Client();
        Log.d("Nav", "socket启动完毕");

        client.setListener(new Client.Listener() {

            @SuppressWarnings("deprecation")
            @Override
            public void update(String msg) {
                Log.d("Nav", msg);
                switch (msg) {

                    case "l":
                        abstractSlamwarePlatform.moveBy(MoveDirection.TURN_LEFT);
                        break;

                    case "r":
                        abstractSlamwarePlatform.moveBy(MoveDirection.TURN_RIGHT);
                        break;

                    case "fd":
                        abstractSlamwarePlatform.moveBy(MoveDirection.FORWARD);
                        break;

                    case "bk":
                        abstractSlamwarePlatform.moveBy(MoveDirection.BACKWARD);
                        break;

                    case "reset":
                        clear();
                        abstractSlamwarePlatform.clearMap();
                        abstractSlamwarePlatform.setPose(new Pose(new Location(0, 0, 0), new Rotation(0)));
                        break;

                    case "ok":
                        activity.changeMessage("已到达目的地");
                        activity.speak("已到达目的地,本次导航结束");
                        break;

                    case "clear":
                        clear();
                        break;

                    default:
                        try {
                            int x = Integer.parseInt(msg.substring(0, msg.indexOf(':')));
                            int y = Integer.parseInt(msg.substring(msg.indexOf(':') + 1));
                            clear();
//                        abstractSlamwarePlatform.moveTo(new Location(x,y,0));

                            slamThread = new SlamThread(abstractSlamwarePlatform, x, y);
                            slamThread.start();

                        } catch (Exception e) {
                            activity.showMessage("未知错误");
                            activity.speak("未知错误");
                        }
                }
            }
        });

        client.getServerMsg();
    }

    public void clear() {
        if (slamThread != null) {
            slamThread.setExit(true);
            slamThread=null;
        }
    }
}
