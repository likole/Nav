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

    SocketThread(AbstractSlamwarePlatform abstractSlamwarePlatform, NavActivity activity){
        this.abstractSlamwarePlatform=abstractSlamwarePlatform;
        this.activity=activity;
    }

    @Override
    public void run() {
        client=new Client();
        Log.d("Nav","socket启动完毕");
        client.setListener(new Client.Listener(){

            @Override
            public void update(String msg) {
                Log.d("Nav",msg);
                if("l".equals(msg)) abstractSlamwarePlatform.moveBy(MoveDirection.TURN_LEFT);
                else if("r".equals(msg)) abstractSlamwarePlatform.moveBy(MoveDirection.TURN_RIGHT);
                else if("fd".equals(msg)) abstractSlamwarePlatform.moveBy(MoveDirection.FORWARD);
                else if("bk".equals(msg)) abstractSlamwarePlatform.moveBy(MoveDirection.BACKWARD);
                else if("reset".equals(msg)) {
                    abstractSlamwarePlatform.clearMap();
                    abstractSlamwarePlatform.setPose(new Pose(new Location(0,0,0),new Rotation(0)));
                }else{
                    try{
                        int x= Integer.parseInt(msg.substring(0,msg.indexOf(':')));
                        int y= Integer.parseInt(msg.substring(msg.indexOf(':')+1));
                        abstractSlamwarePlatform.moveTo(new Location(x,y,0));
                    }catch (Exception e) {
                       activity.showMessage("ERROR");
                        activity.speak("沈鹏杰臭傻逼");
                    }
                }



            }
        });

        client.getServerMsg();

    }
}
