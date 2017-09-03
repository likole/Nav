package cn.likole.nav;

import android.content.Context;
import android.util.Log;

import com.slamtec.slamware.AbstractSlamwarePlatform;
import com.slamtec.slamware.action.MoveDirection;

/**
 * Created by likole on 9/3/17.
 */

public class SocketThread extends Thread {

    Client client;
    Context context;
    AbstractSlamwarePlatform abstractSlamwarePlatform;

    SocketThread(AbstractSlamwarePlatform abstractSlamwarePlatform, Context context){
        this.abstractSlamwarePlatform=abstractSlamwarePlatform;
        this.context=context;
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
                if("r".equals(msg)) abstractSlamwarePlatform.moveBy(MoveDirection.TURN_RIGHT);

            }
        });

        client.getServerMsg();

    }
}
