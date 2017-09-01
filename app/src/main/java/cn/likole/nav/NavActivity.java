package cn.likole.nav;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.support.v7.app.AppCompatActivity;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.baidu.duer.dcs.R;
import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.VoiceRecognitionService;
import com.baidu.tts.client.SpeechSynthesizer;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class NavActivity extends AppCompatActivity {


    public static final int STATUS_None = 0;
    public static final int STATUS_WaitingReady = 2;
    public static final int STATUS_Ready = 3;
    public static final int STATUS_Speaking = 4;
    public static final int STATUS_Recognition = 5;
    private static final int EVENT_ERROR = 11;

    private int status = STATUS_None;
    private SpeechRecognizer speechRecognizer;
    private EventManager mWpEventManager;

    private Button btn;
    private Button btn_chart;
    private TextView tv_log;

    // 语音合成客户端
    private SpeechSynthesizer mSpeechSynthesizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nav_activity);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO
            }, 1); // requestPermissions是Activity的方法
        }

        btn = (Button) findViewById(R.id.button);
        btn_chart = (Button) findViewById(R.id.button_chart);
        tv_log = (TextView) findViewById(R.id.textView);
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this, new ComponentName(this, VoiceRecognitionService.class));

        speechRecognizer.setRecognitionListener(new myRecognitionListener());
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switch (status) {
                    case STATUS_None:
                        start();
                        btn.setText("取消");
                        status = STATUS_WaitingReady;
                        break;
                    case STATUS_WaitingReady:
                        cancel();
                        status = STATUS_None;
                        btn.setText("开始");
                        break;
                    case STATUS_Ready:
                        cancel();
                        status = STATUS_None;
                        btn.setText("开始");
                        break;
                    case STATUS_Speaking:
                        stop();
                        status = STATUS_Recognition;
                        btn.setText("识别中");
                        break;
                    case STATUS_Recognition:
                        cancel();
                        status = STATUS_None;
                        btn.setText("开始");
                        break;
                }
            }
        });

        btn_chart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
    }

    @Override
    protected void onDestroy() {
        speechRecognizer.destroy();
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 唤醒功能打开步骤
        // 1) 创建唤醒事件管理器
        mWpEventManager = EventManagerFactory.create(NavActivity.this, "wp");

        // 2) 注册唤醒事件监听器
        mWpEventManager.registerListener(new EventListener() {
            @Override
            public void onEvent(String name, String params, byte[] data, int offset, int length) {
                try {
//                    print(String.format("event: name=%s, params=%s", name, params));
                    JSONObject json = new JSONObject(params);
                    if ("wp.data".equals(name)) { // 每次唤醒成功, 将会回调name=wp.data的时间, 被激活的唤醒词在params的word字段
                        String word = json.getString("word"); // 唤醒词
                        print("唤醒成功, 唤醒词: " + word + "\r\n");
                        btn.setText("取消");
                        status = STATUS_WaitingReady;
                        start();
                    } else if ("wp.exit".equals(name)) {
                        // 唤醒已经停止
                    }
                } catch (JSONException e) {
                    throw new AndroidRuntimeException(e);
                }
            }
        });

        // 3) 通知唤醒管理器, 启动唤醒功能
        HashMap params = new HashMap();
        params.put("kws-file", "assets:///WakeUp.bin"); // 设置唤醒资源, 唤醒资源请到 http://yuyin.baidu.com/wake#m4 来评估和导出
        mWpEventManager.send("wp.start", new JSONObject(params).toString(), null, 0, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 停止唤醒监听
        mWpEventManager.send("wp.stop", null, null, 0, 0);
    }

    public void bindParams(Intent intent) {
        intent.putExtra(Constant.EXTRA_SOUND_START, R.raw.bdspeech_recognition_start);
        intent.putExtra(Constant.EXTRA_SOUND_END, R.raw.bdspeech_speech_end);
        intent.putExtra(Constant.EXTRA_SOUND_SUCCESS, R.raw.bdspeech_recognition_success);
        intent.putExtra(Constant.EXTRA_SOUND_ERROR, R.raw.bdspeech_recognition_error);
        intent.putExtra(Constant.EXTRA_SOUND_CANCEL, R.raw.bdspeech_recognition_cancel);
        intent.putExtra(Constant.EXTRA_NLU, "enable");
    }


    private void start() {
        print("点击了“开始”");
        Intent intent = new Intent();
        bindParams(intent);
        speechRecognizer.startListening(intent);
    }

    private void stop() {
        speechRecognizer.stopListening();
        print("点击了“说完了”");
    }

    private void cancel() {
        speechRecognizer.cancel();

        status = STATUS_None;
        print("点击了“取消”");
    }


    private void print(String msg) {
        Log.d("Nav", "----" + msg);
        //tv_log.setText(tv_log.getText() + "\n" + msg);
    }

    private void printRs(String msg) {
        try {
            tv_log.setText(new JSONObject(msg).toString(4));
        } catch (JSONException e) {
            e.printStackTrace();
        }
        try {
            JSONObject origin_result = new JSONObject(msg);
            JSONObject json_res = new JSONObject(origin_result.getJSONObject("content").getString("json_res"));
            JSONObject result = (JSONObject) json_res.getJSONArray("results").get(0);

            tv_log.setText(tv_log.getText() + "\n\n" + result.getJSONObject("object").getString("arrival"));
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    class myRecognitionListener implements RecognitionListener {
        @Override
        public void onReadyForSpeech(Bundle params) {
            status = STATUS_Ready;
            print("准备就绪，可以开始说话");
        }

        @Override
        public void onBeginningOfSpeech() {
            status = STATUS_Speaking;
            btn.setText("说完了");
            print("检测到用户的已经开始说话");
        }

        @Override
        public void onRmsChanged(float rmsdB) {

        }

        @Override
        public void onBufferReceived(byte[] buffer) {

        }

        @Override
        public void onEndOfSpeech() {
            status = STATUS_Recognition;
            print("检测到用户的已经停止说话");
            btn.setText("识别中");
        }

        @Override
        public void onError(int error) {
            status = STATUS_None;
            StringBuilder sb = new StringBuilder();
            switch (error) {
                case SpeechRecognizer.ERROR_AUDIO:
                    sb.append("音频问题");
                    break;
                case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                    sb.append("没有语音输入");
                    break;
                case SpeechRecognizer.ERROR_CLIENT:
                    sb.append("其它客户端错误");
                    break;
                case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                    sb.append("权限不足");
                    break;
                case SpeechRecognizer.ERROR_NETWORK:
                    sb.append("网络问题");
                    break;
                case SpeechRecognizer.ERROR_NO_MATCH:
                    sb.append("没有匹配的识别结果");
                    break;
                case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                    sb.append("引擎忙");
                    break;
                case SpeechRecognizer.ERROR_SERVER:
                    sb.append("服务端错误");
                    break;
                case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                    sb.append("连接超时");
                    break;
            }
            sb.append(":" + error);
            print("识别失败：" + sb.toString());
            btn.setText("开始");
        }

        @Override
        public void onResults(Bundle results) {
            status = STATUS_None;
            ArrayList<String> nbest = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            print("识别成功：" + Arrays.toString(nbest.toArray(new String[nbest.size()])));
            String json_res = results.getString("origin_result");
            try {
                print("origin_result=\n" + new JSONObject(json_res).toString(4));
                printRs(json_res);
            } catch (Exception e) {
                print("origin_result=[warning: bad json]\n" + json_res);
            }
            btn.setText("开始");
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            ArrayList<String> nbest = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (nbest.size() > 0) {
                print("~临时识别结果：" + Arrays.toString(nbest.toArray(new String[0])));
            }
        }

        @Override
        public void onEvent(int eventType, Bundle params) {
            switch (eventType) {
                case EVENT_ERROR:
                    String reason = params.get("reason") + "";
                    print("EVENT_ERROR, " + reason);
                    break;
                case VoiceRecognitionService.EVENT_ENGINE_SWITCH:
                    int type = params.getInt("engine_type");
                    print("*引擎切换至" + (type == 0 ? "在线" : "离线"));
                    break;
            }
        }

    }

}
