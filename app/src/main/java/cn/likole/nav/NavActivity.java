package cn.likole.nav;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.RecognitionListener;
import android.speech.SpeechRecognizer;
import android.support.v7.app.AppCompatActivity;
import android.util.AndroidRuntimeException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.baidu.duer.dcs.androidapp.DcsSampleMainActivity;
import com.baidu.speech.EventListener;
import com.baidu.speech.EventManager;
import com.baidu.speech.EventManagerFactory;
import com.baidu.speech.VoiceRecognitionService;
import com.baidu.tts.client.SpeechError;
import com.baidu.tts.client.SpeechSynthesizer;
import com.baidu.tts.client.SpeechSynthesizerListener;
import com.baidu.tts.client.TtsMode;
import com.slamtec.slamware.AbstractSlamwarePlatform;
import com.slamtec.slamware.action.MoveDirection;
import com.slamtec.slamware.discovery.DeviceManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import static com.slamtec.slamware.robot.SystemParameters.SYSPARAM_ROBOT_SPEED;
import static com.slamtec.slamware.robot.SystemParameters.SYSVAL_ROBOT_SPEED_HIGH;

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

    private AbstractSlamwarePlatform slamwarePlatform;
    private SocketThread socketThread;

    //-----语音合成-----
    private SpeechSynthesizer mSpeechSynthesizer;
    private String mSampleDirPath;
    private static final String SAMPLE_DIR_NAME = "baiduTTS";
    private static final String SPEECH_FEMALE_MODEL_NAME = "bd_etts_speech_female.dat";
    private static final String SPEECH_MALE_MODEL_NAME = "bd_etts_speech_male.dat";
    private static final String TEXT_MODEL_NAME = "bd_etts_text.dat";
    private static final String LICENSE_FILE_NAME = "temp_license";
    private static final String ENGLISH_SPEECH_FEMALE_MODEL_NAME = "bd_etts_speech_female_en.dat";
    private static final String ENGLISH_SPEECH_MALE_MODEL_NAME = "bd_etts_speech_male_en.dat";
    private static final String ENGLISH_TEXT_MODEL_NAME = "bd_etts_text_en.dat";
    //-----语音合成-----

    private String message;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nav_activity);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{
                    Manifest.permission.RECORD_AUDIO
            }, 1);
        }

        print("权限请求完成");

        btn = (Button) findViewById(R.id.button);
        btn_chart = (Button) findViewById(R.id.button_chart);
        tv_log = (TextView) findViewById(R.id.textView);

        print("界面绑定完成");

        //连接小车
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    SharedPreferences preferences=getSharedPreferences("Nav", Context.MODE_PRIVATE);
                    slamwarePlatform = DeviceManager.connect(preferences.getString("ip","192.168.11.1"), 1445);
                    slamwarePlatform.setSystemParameter(SYSPARAM_ROBOT_SPEED, SYSVAL_ROBOT_SPEED_HIGH);
                    socketThread = new SocketThread(slamwarePlatform, NavActivity.this);
                    socketThread.start();
                    changeMessage("已成功连接到小车");
                } catch (Exception e) {
                    changeMessage("无法连接到小车");
                }
            }
        }).start();

        print("启动小车OK");

        //-----语音合成-----
//        initialEnv();
        initialTts();
        //-----语音合成-----

        print("语音合成OK");

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

        print("语音识别OK");

        btn_chart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(NavActivity.this, DcsSampleMainActivity.class);
                startActivity(intent);
                finish();
            }
        });

        print("模式切换OK");

        handler = new Handler();
        handler.postDelayed(messageUpdate, 100);

    }

    @Override
    protected void onDestroy() {
        speechRecognizer.destroy();
        this.mSpeechSynthesizer.release();
        if (socketThread != null) socketThread.interrupt();
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
//        try {
//            tv_log.setText(new JSONObject(msg).toString(4));
//        } catch (JSONException e) {
//            e.printStackTrace();
//        }
        try {
            JSONObject origin_result = new JSONObject(msg);
            JSONObject json_res = new JSONObject(origin_result.getJSONObject("content").getString("json_res"));
            JSONObject result = (JSONObject) json_res.getJSONArray("results").get(0);
            String dest = result.getJSONObject("object").getString("arrival");
            speak("即将前往" + result.getJSONObject("object").getString("arrival"));
            showMessage("即将前往" + dest);
            message = "正在前往" + dest;
            slamwarePlatform.moveBy(MoveDirection.FORWARD);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void showMessage(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_log.setText(msg);
            }
        });
    }

    public void changeMessage(final String msg) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tv_log.setText(msg);
            }
        });
        message = msg;
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
            if (json_res.contains("进入聊天模式")) {
                Intent intent = new Intent(NavActivity.this, DcsSampleMainActivity.class);
                startActivity(intent);
                finish();
            }

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


    //====================
    // 上面应该是..语音识别吧= =
    //=====================


    //====================
    //以下为语音合成的东东= =
    //=====================


    private void initialEnv() {
        if (mSampleDirPath == null) {
            String sdcardPath = Environment.getExternalStorageDirectory().toString();
            mSampleDirPath = sdcardPath + "/" + SAMPLE_DIR_NAME;
        }
        makeDir(mSampleDirPath);
        copyFromAssetsToSdcard(false, SPEECH_FEMALE_MODEL_NAME, mSampleDirPath + "/" + SPEECH_FEMALE_MODEL_NAME);
        copyFromAssetsToSdcard(false, SPEECH_MALE_MODEL_NAME, mSampleDirPath + "/" + SPEECH_MALE_MODEL_NAME);
        copyFromAssetsToSdcard(false, TEXT_MODEL_NAME, mSampleDirPath + "/" + TEXT_MODEL_NAME);
        copyFromAssetsToSdcard(false, LICENSE_FILE_NAME, mSampleDirPath + "/" + LICENSE_FILE_NAME);
        copyFromAssetsToSdcard(false, "english/" + ENGLISH_SPEECH_FEMALE_MODEL_NAME, mSampleDirPath + "/"
                + ENGLISH_SPEECH_FEMALE_MODEL_NAME);
        copyFromAssetsToSdcard(false, "english/" + ENGLISH_SPEECH_MALE_MODEL_NAME, mSampleDirPath + "/"
                + ENGLISH_SPEECH_MALE_MODEL_NAME);
        copyFromAssetsToSdcard(false, "english/" + ENGLISH_TEXT_MODEL_NAME, mSampleDirPath + "/"
                + ENGLISH_TEXT_MODEL_NAME);
    }

    private void makeDir(String dirPath) {
        File file = new File(dirPath);
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    /**
     * 将sample工程需要的资源文件拷贝到SD卡中使用（授权文件为临时授权文件，请注册正式授权）
     *
     * @param isCover 是否覆盖已存在的目标文件
     * @param source
     * @param dest
     */
    private void copyFromAssetsToSdcard(boolean isCover, String source, String dest) {
        File file = new File(dest);
        if (isCover || (!isCover && !file.exists())) {
            InputStream is = null;
            FileOutputStream fos = null;
            try {
                is = getResources().getAssets().open(source);
                String path = dest;
                fos = new FileOutputStream(path);
                byte[] buffer = new byte[1024];
                int size = 0;
                while ((size = is.read(buffer, 0, 1024)) >= 0) {
                    fos.write(buffer, 0, size);
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    if (is != null) {
                        is.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void initialTts() {
        this.mSpeechSynthesizer = SpeechSynthesizer.getInstance();
        this.mSpeechSynthesizer.setContext(this);
        this.mSpeechSynthesizer.setSpeechSynthesizerListener(new mySpeechSynthesizerListener());
//        // 文本模型文件路径 (离线引擎使用)
//        this.mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_TEXT_MODEL_FILE, mSampleDirPath + "/"
//                + TEXT_MODEL_NAME);
//        // 声学模型文件路径 (离线引擎使用)
//        this.mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_SPEECH_MODEL_FILE, mSampleDirPath + "/"
//                + SPEECH_FEMALE_MODEL_NAME);
//        // 本地授权文件路径,如未设置将使用默认路径.设置临时授权文件路径，LICENCE_FILE_NAME请替换成临时授权文件的实际路径，仅在使用临时license文件时需要进行设置，如果在[应用管理]中开通了正式离线授权，不需要设置该参数，建议将该行代码删除（离线引擎）
//        // 如果合成结果出现临时授权文件将要到期的提示，说明使用了临时授权文件，请删除临时授权即可。
//        this.mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_TTS_LICENCE_FILE, mSampleDirPath + "/"
//                + LICENSE_FILE_NAME);
        // 请替换为语音开发者平台上注册应用得到的App ID (离线授权)

        // 发音人（在线引擎），可用参数为0,1,2,3。。。（服务器端会动态增加，各值含义参考文档，以文档说明为准。0--普通女声，1--普通男声，2--特别男声，3--情感男声。。。）
        this.mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_SPEAKER, "0");
        // 设置Mix模式的合成策略
        this.mSpeechSynthesizer.setParam(SpeechSynthesizer.PARAM_MIX_MODE, SpeechSynthesizer.MIX_MODE_DEFAULT);
        // 授权检测接口(只是通过AuthInfo进行检验授权是否成功。)
//        // AuthInfo接口用于测试开发者是否成功申请了在线或者离线授权，如果测试授权成功了，可以删除AuthInfo部分的代码（该接口首次验证时比较耗时），不会影响正常使用（合成使用时SDK内部会自动验证授权）
//        AuthInfo authInfo = this.mSpeechSynthesizer.auth(TtsMode.MIX);
//
//        if (authInfo.isSuccess()) {
//            toPrint("auth success");
//        } else {
//            String errorMsg = authInfo.getTtsError().getDetailMessage();
//            toPrint("auth failed errorMsg=" + errorMsg);
//        }

        // 初始化tts
        mSpeechSynthesizer.initTts(TtsMode.MIX);
//        // 加载离线英文资源（提供离线英文合成功能）
//        int result =
//                mSpeechSynthesizer.loadEnglishModel(mSampleDirPath + "/" + ENGLISH_TEXT_MODEL_NAME, mSampleDirPath
//                        + "/" + ENGLISH_SPEECH_FEMALE_MODEL_NAME);
//        toPrint("loadEnglishModel result=" + result);
    }


    public void speak(String text) {
        int result = this.mSpeechSynthesizer.speak(text);
        if (result < 0) {
            toPrint("error,please look up error code in doc or URL:http://yuyin.baidu.com/docs/tts/122 ");
        }
    }


    class mySpeechSynthesizerListener implements SpeechSynthesizerListener {
        /*
     * @param arg0
     */
        @Override
        public void onSynthesizeStart(String utteranceId) {
            toPrint("onSynthesizeStart utteranceId=" + utteranceId);
        }

        /**
         * 合成数据和进度的回调接口，分多次回调
         *
         * @param utteranceId
         * @param data        合成的音频数据。该音频数据是采样率为16K，2字节精度，单声道的pcm数据。
         * @param progress    文本按字符划分的进度，比如:你好啊 进度是0-3
         */
        @Override
        public void onSynthesizeDataArrived(String utteranceId, byte[] data, int progress) {
            // toPrint("onSynthesizeDataArrived");
        }

        /**
         * 合成正常结束，每句合成正常结束都会回调，如果过程中出错，则回调onError，不再回调此接口
         *
         * @param utteranceId
         */
        @Override
        public void onSynthesizeFinish(String utteranceId) {
            toPrint("onSynthesizeFinish utteranceId=" + utteranceId);
        }

        /**
         * 播放开始，每句播放开始都会回调
         *
         * @param utteranceId
         */
        @Override
        public void onSpeechStart(String utteranceId) {
            toPrint("onSpeechStart utteranceId=" + utteranceId);
        }

        /**
         * 播放进度回调接口，分多次回调
         *
         * @param utteranceId
         * @param progress    文本按字符划分的进度，比如:你好啊 进度是0-3
         */
        @Override
        public void onSpeechProgressChanged(String utteranceId, int progress) {
            // toPrint("onSpeechProgressChanged");
            // mHandler.sendMessage(mHandler.obtainMessage(UI_CHANGE_INPUT_TEXT_SELECTION, progress, 0));
        }

        /**
         * 播放正常结束，每句播放正常结束都会回调，如果过程中出错，则回调onError,不再回调此接口
         *
         * @param utteranceId
         */
        @Override
        public void onSpeechFinish(String utteranceId) {
            toPrint("onSpeechFinish utteranceId=" + utteranceId);
        }

        /**
         * 当合成或者播放过程中出错时回调此接口
         *
         * @param utteranceId
         * @param error       包含错误码和错误信息
         */
        @Override
        public void onError(String utteranceId, SpeechError error) {
            toPrint("onError error=" + "(" + error.code + ")" + error.description + "--utteranceId=" + utteranceId);
        }
    }


    private void toPrint(String s) {
        Log.d("Nav", s);
    }


    //====================
    //上面的是语音合成部分
    //=====================

    //定时更新消息
    private Runnable messageUpdate = new Runnable() {
        @Override
        public void run() {
            showMessage(message);
            handler.postDelayed(messageUpdate, 5000);
        }
    };
}
