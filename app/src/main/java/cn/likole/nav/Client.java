package cn.likole.nav;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

public class Client {
    private Socket socket;
    private PrintWriter printWriter;//输出
    private BufferedReader bufferedReader;
    private static String URL = "106.15.228.191";
    private static int port = 11298;
    public String msg = "";

    public Client() {
        //设置服务器IP，绑定端口
        try {
            socket = new Socket(URL,port);
            Log.d("Nav","连接完成！");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }



    public void sendMessage(){

        try {
            //向服务器发送数据
            //初始化输出流 用来向服务器传递数据
            if(socket.isOutputShutdown()){
                System.err.println("OutputStream 被关闭");
            }
            printWriter = new PrintWriter(socket.getOutputStream(),true);

            printWriter.println(msg);
            //清空缓冲区的数据流  数据流向：内存->缓冲区->文件（或输出），如果不用.flush()，可能缓冲区内部还有数据残留，.flush()会将缓冲区内部的数据强制输出
            printWriter.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public interface Listener{
        void update(String msg);
    }
    private Listener listener;
    public void setListener(Listener l){
        this.listener = l;
    }

    public void getServerMsg(){
        //接收服务器数据
        //初始化输入流 用来获取服务器下发的数据
        try {
            bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String reply = null;

            while(!((reply = bufferedReader.readLine()) ==null)){
                System.out.println("服务器发送的数据为：" + reply);
                listener.update(reply);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
