package com.mediapro.camera_send_demo;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v7.app.AppCompatActivity;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import java.text.DecimalFormat;
import java.util.List;

import com.sd.Constant;
import com.sd.Constant.LogLevel;
import com.sd.SDAudio3AConfig;
import com.sd.SDInterfaceCameraPublisher;
import com.sd.SDInterfacePlayer;
import com.sd.SDInterface;

import org.webrtc.SurfaceViewRenderer;


import static com.sd.Constant.SystemStatusType.SYS_NOTIFY_EXIT_KICKED;
import static com.sd.Constant.SystemStatusType.SYS_NOTIFY_RECON_START;
import static com.sd.Constant.SystemStatusType.SYS_NOTIFY_RECON_SUCCESS;
import static com.sd.Constant.SystemStatusType.SYS_NOTIFY_ONPOSITION;
import static com.sd.Constant.SystemStatusType.SYS_NOTIFY_OFFPOSITION;
import static com.sd.Constant.SystemStatusType.SYS_NOTIFY_AUTO_BITRATE_REQ;

import static com.sd.Constant.FecRedunMethod.FEC_REDUN_FIX;
import static com.sd.Constant.ClientVideoCodecType.CLIENT_VIDEO_TYPE_H264;
import static com.sd.Constant.ClientAudioCodecType.CLIENT_AUDIO_TYPE_AAC;
import static com.sd.Constant.ClientAudioCodecType.CLIENT_AUDIO_TYPE_G711;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "SDMedia";

    private Button btnPublish = null;
    private Button btnSwitchCamera = null;
    private Button btnSwitchEncoder = null;


    //专用于播放音频的播放器
    private SDInterfacePlayer mPlayerAudio = null;


    //采集窗口
    private SurfaceView mSurfaceViewCamera = null;
    //主接口类
    private SDInterface mInterface = null;

    private SharedPreferences sharedPreferences;
    private UserSetting mUserSet;
    private boolean mPublishStart = false;
    private boolean mLoginSuccess = false;

    // 相机采集分辨率
    private int mCameraCapWidth = 360;
    private int mCameraCapHeight = 640;

    // 外层指定编码分辨率
    private int mEncodeWidth = 360;
    private int mEncodeHeight = 640;

    //外层指定编码码率
    private int mEncodeBitrate = 300*1000;

    //日志文件存放路径
    private String mLogfileDir = "/sdcard/mediapro/";


    //来自底层消息的处理
    private final Handler mHandler = new Handler(){

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what)
            {
                case SYS_NOTIFY_EXIT_KICKED:
                    //账号在其他位置登录，停止音视频发送
                    stopPublishAndPlay();
                    offLineProcess();
                    Toast.makeText(MainActivity.this, "账号在其他位置登录，与服务器断开连接", Toast.LENGTH_SHORT).show();
                    break;
                case SYS_NOTIFY_RECON_START:
                    Toast.makeText(MainActivity.this, "网络超时，开始重连服务器...", Toast.LENGTH_SHORT).show();
                    break;
                case SYS_NOTIFY_RECON_SUCCESS:
                    Toast.makeText(MainActivity.this, "连服务器成功", Toast.LENGTH_SHORT).show();
                    break;
                case SYS_NOTIFY_ONPOSITION:
                    long uid_on = (long)msg.arg1;
                    int on_position = msg.arg2;
                    Toast.makeText(MainActivity.this, uid_on + " 加入房间，位置：" + on_position, Toast.LENGTH_SHORT).show();
                    break;
                case SYS_NOTIFY_OFFPOSITION:
                    long uid_off = (long)msg.arg1;
                    int off_position = msg.arg2;
                    Toast.makeText(MainActivity.this, uid_off + " 离开房间，位置：" + off_position, Toast.LENGTH_SHORT).show();
                    break;
                case SYS_NOTIFY_AUTO_BITRATE_REQ:
                    int frame_drop_interval = msg.arg1;
                    float bitrate_ratio = msg.arg2 / 100.0f;

                    DecimalFormat df =new DecimalFormat("#0.00");
                    String result = df.format(bitrate_ratio);
                    Toast.makeText(MainActivity.this, "丢帧间隔：" + frame_drop_interval + " 码率比率：" + result, Toast.LENGTH_SHORT).show();

                    //丢帧间隔
                    SDInterfaceCameraPublisher.Inst().changeVideoFrameDropInterval(frame_drop_interval);
                    //新的码率
                    int nBitrate = (int)(mEncodeBitrate * bitrate_ratio);
                    SDInterfaceCameraPublisher.Inst().changeVideoBitrate(nBitrate);
                    //TBD：视情况调整分辨率
                    break;
                default:
                    break;
            }
        }

    };

    //设置页面变更设置后，重启生效
    BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            Toast.makeText(getApplicationContext(), "设置变更在重启后生效", Toast.LENGTH_LONG).show();
        }
    };


    //初始化界面资源
    private void initView() {

        btnPublish = (Button) findViewById(R.id.publish);
        btnSwitchCamera = (Button) findViewById(R.id.swCam);
        btnSwitchEncoder = (Button) findViewById(R.id.swEnc);

        mSurfaceViewCamera = (SurfaceView)findViewById(R.id.sv_camera);


        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnPublish.getText().toString().contentEquals("发送")) {
                    startPublishAndPlay();
                } else if (btnPublish.getText().toString().contentEquals("停止")) {
                    new AlertDialog.Builder(MainActivity.this)
                            .setMessage(getString(R.string.stop_send_video_audio))
                            .setCancelable(false)
                            .setPositiveButton(android.R.string.ok, (dialog, which) ->
                                    stopPublishAndPlay())
                            .setNegativeButton(android.R.string.cancel, null)
                            .create()
                            .show();

                }
            }
        });

        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mLoginSuccess == true)
                {
                    SDInterfaceCameraPublisher.Inst().changeCameraFace();
                }
            }
        });

        btnSwitchEncoder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnSwitchEncoder.getText().toString().contentEquals("硬编码")) {
                    btnSwitchEncoder.setText("软编码");
                    SDInterfaceCameraPublisher.Inst().setToSoftwareEncoder();
                } else if (btnSwitchEncoder.getText().toString().contentEquals("软编码")) {
                    btnSwitchEncoder.setText("硬编码");
                    SDInterfaceCameraPublisher.Inst().setToHardwareEncoder();
                }
            }
        });

    }

    //初始化基础API、推送\播放API，请在initView之后调用
    private void initAvResource()
    {
        sharedPreferences = getSharedPreferences("UserInfo", 0);

        mUserSet = new UserSetting();
        mUserSet.UserId = sharedPreferences.getInt(UserSettingKey.UserIdKey, 0);
        mUserSet.ServerIp = sharedPreferences.getString(UserSettingKey.ServerIpKey, "47.106.195.225");
        mUserSet.RoomId = sharedPreferences.getInt(UserSettingKey.RoomIdKey, 888);
        mUserSet.UpPosition = sharedPreferences.getInt(UserSettingKey.SendPositionKey, 0);
        mUserSet.Framerate = sharedPreferences.getInt(UserSettingKey.FramerateKey, 30);
        mUserSet.Bitrate = sharedPreferences.getString(UserSettingKey.BitrateKey, "400kbps");
        mUserSet.Resolution = sharedPreferences.getString(UserSettingKey.ResolutionKey, "360*640");
        mUserSet.FecRedunRatio = sharedPreferences.getInt(UserSettingKey.FecRedunKey, 30);
        mUserSet.EnableNack = sharedPreferences.getBoolean(UserSettingKey.EnableNackKey, true);
        mUserSet.EnableAutoBitrate = sharedPreferences.getBoolean(UserSettingKey.EnableAutoBitrateKey, true);

        mUserSet.AecDelay = sharedPreferences.getInt(UserSettingKey.AecDelayKey, 40);
        mUserSet.EnableAec = sharedPreferences.getBoolean(UserSettingKey.EnableAecKey, true);
        mUserSet.EnableAgc = sharedPreferences.getBoolean(UserSettingKey.EnableAgcKey, true);
        mUserSet.EnableAns = sharedPreferences.getBoolean(UserSettingKey.EnableAnsKey, true);
        mUserSet.EnableSoft3A = sharedPreferences.getBoolean(UserSettingKey.EnableSoft3AcKey, true);
        mUserSet.UseHwEncode = sharedPreferences.getBoolean(UserSettingKey.EnableHwEncode, true);
        mUserSet.UseHwDecode = sharedPreferences.getBoolean(UserSettingKey.EnableHwDecode, true);

        if (mUserSet.UseHwEncode == false)
        {
            btnSwitchEncoder.setText("软编码");
        }
        else {
            btnSwitchEncoder.setText("硬编码");
        }

        String[] array = mUserSet.Resolution.split("\\*");
        if (array.length > 1) {
            mEncodeWidth =  Integer.parseInt(array[0]);
            mEncodeHeight = Integer.parseInt(array[1]);
        }
        String[] arrBitrate = mUserSet.Bitrate.split("kbps");
        if (arrBitrate.length > 0)
        {
            mEncodeBitrate = Integer.parseInt(arrBitrate[0]) * 1000;
        }

        //基础API
        mInterface = new SDInterface(mHandler);
        // 初始化系统，指定服务器IP地址、本地客户端输出日志文件级别和存放路径
        int ret = mInterface.SDsysinit(mUserSet.ServerIp, mLogfileDir, LogLevel.LOG_LEVEL_INFO);
        if(0 != ret)
        {
            Log.e(TAG, "SDsysinit failed return:" + ret);
            Toast.makeText(this, "初始化音视频资源返回错误编码:" + ret, Toast.LENGTH_LONG).show();
        }

        //播放API
        //decoderMode = 0(软解码)  1(硬解码)  2(硬解码优先)
        int decoderMode = mUserSet.UseHwDecode == true ? 2 : 0;

        boolean bPlayAudioOnJava = false;
        //若开启软件AEC，则需要开启在JAVA层播放远端音频
        if ((mUserSet.EnableSoft3A == true) && (mUserSet.EnableAec == true))
        {
            bPlayAudioOnJava = true;
        }

        mPlayerAudio = new SDInterfacePlayer();
        mPlayerAudio.Init(this, null, true, false, bPlayAudioOnJava, decoderMode);

        //推送API
		SDInterfaceCameraPublisher.Inst().setLogOutput(mLogfileDir, LogLevel.LOG_LEVEL_INFO);
        SDAudio3AConfig audio3AConfig = new SDAudio3AConfig(mUserSet.EnableSoft3A, mUserSet.EnableAec, mUserSet.EnableAgc, mUserSet.EnableAns, mUserSet.AecDelay);
        SDInterfaceCameraPublisher.Inst().Init(mInterface, mSurfaceViewCamera, false, false, audio3AConfig);
    }

    //反初始化基础API、推送\播放API
    private void uninitAvResource()
    {
        mPlayerAudio.Destroy();
        SDInterfaceCameraPublisher.Inst().Destroy();
        mInterface.SDsysexit();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        //关闭屏幕旋转
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR);

        //初始化相关资源
        initView();

        //设置为扬声器输出模式
        AudioManager audioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        Boolean bWired = checkIsWired();
        if (bWired == true)
        {
            audioManager.setSpeakerphoneOn(false);
        }
        else
        {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
        }

        initAvResource();

        //登录服务器
        onLineProcess();

        //开始发布
        startPublishAndPlay();

        IntentFilter filter = new IntentFilter(UserSettingActivity.action);
        registerReceiver(mBroadcastReceiver, filter);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_userSettings) {
            // popu user serttig activity
            Intent intent = new Intent(MainActivity.this, UserSettingActivity.class);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        final Button btn = (Button) findViewById(R.id.publish);
        btn.setEnabled(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mBroadcastReceiver);

        //停止播放
        stopPublishAndPlay();

        //下线服务器
        offLineProcess();

        //回收资源
        uninitAvResource();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

    }

    private Boolean checkIsWired() {
        AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            AudioDeviceInfo[] devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
            for (AudioDeviceInfo device : devices) {
                int deviceType = device.getType();
                if (deviceType == AudioDeviceInfo.TYPE_WIRED_HEADSET || deviceType == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || deviceType ==  AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || deviceType ==  AudioDeviceInfo.TYPE_BLUETOOTH_SCO){
                    return true;
                }
            }
        } else {
            return am.isWiredHeadsetOn() || am.isBluetoothScoOn() || am.isBluetoothA2dpOn();
        }
        return  false;
    }


    private void startPublishAndPlay()
    {
        if (mLoginSuccess == true)
        {
            boolean useHwEncode = false;
            if (btnSwitchEncoder.getText().toString().contentEquals("硬编码"))
            {
                Toast.makeText(getApplicationContext(), "使用硬编码", Toast.LENGTH_SHORT).show();
                SDInterfaceCameraPublisher.Inst().setToHardwareEncoder();
                useHwEncode = true;
            } else {
                Toast.makeText(getApplicationContext(), "使用软编码", Toast.LENGTH_SHORT).show();
                SDInterfaceCameraPublisher.Inst().setToSoftwareEncoder();
                useHwEncode = false;
            }
            sharedPreferences.edit()
                    .putBoolean(UserSettingKey.EnableHwEncode, useHwEncode)
                    .commit();

            //混音时播放0号位置音频(服务器开启混音模式时，混音音频从0号位置下发)
            //非混音模式下，按业务设计播放对应位置音频
            mPlayerAudio.startPlay(0, 0, 0);

            //开始推送
            boolean bRet = SDInterfaceCameraPublisher.Inst().startPublish();
            if (bRet == false)
            {
                Toast.makeText(getApplicationContext(), "摄像头or麦克风设备打开失败", Toast.LENGTH_SHORT).show();
                mPublishStart = false;
            }
            else
            {
                btnPublish.setText("停止");
                btnSwitchEncoder.setEnabled(false);
                mPublishStart = true;
            }

        }
    }

    private void stopPublishAndPlay()
    {
        if (mPublishStart == true)
        {
            //停止推送
            SDInterfaceCameraPublisher.Inst().stopPublish();
            //停止播放
            mPlayerAudio.stopPlay();

            btnPublish.setText("发送");
            btnSwitchEncoder.setEnabled(true);

            mPublishStart = false;
        }
    }

    //登录服务器
    private int onLineProcess()
    {
        //根据码率选择合适的FEC参数GROUP
        int nGroupSize = 16;
        if (mEncodeBitrate >= 1500*1000)
        {
            nGroupSize = 28;
        }		
        else if (mEncodeBitrate >= 900*1000)
        {
            nGroupSize = 28;
        }
        else if (mEncodeBitrate >= 600*1000)
        {
            nGroupSize = 22;
        }

        //设置传输参数，未调用则使用默认值
        mInterface.SDSetTransParams(FEC_REDUN_FIX, mUserSet.FecRedunRatio, nGroupSize, mUserSet.EnableNack == true ? 1:0, 200);
        Log.i(TAG, "SDSetTransParams FEC redun:" + mUserSet.FecRedunRatio);

        //发送端Smooth处理
        mInterface.SDSetVideoFrameRateForSmoother(mUserSet.Framerate);

        //设置获取在线用户ID列表的回调周期（单位秒）
        //mInterface.SDEnableUserListCallback(3);

        //设置码率自适应是否启用
        if (mUserSet.EnableAutoBitrate == true)
        {
            mInterface.SDEnableAutoBitrateSupport(Constant.AutoBitrateMethod.AUTO_BITRATE_ADJUST_BITRATE_FIRST);
        }
        else
        {
            mInterface.SDEnableAutoBitrateSupport(Constant.AutoBitrateMethod.AUTO_BITRATE_ADJUST_DISABLE);
        }

        //本DEMO使用的音视频编码类型
        mInterface.SDSetVideoAudioCodecType(CLIENT_VIDEO_TYPE_H264, CLIENT_AUDIO_TYPE_AAC);
        //mInterface.SDSetVideoAudioCodecType(CLIENT_VIDEO_TYPE_H264, CLIENT_AUDIO_TYPE_G711);

        //本DEMO固定使用服务器3号域
        int nDomainId = 3;
        int ret = mInterface.SDOnlineUser(mUserSet.RoomId, mUserSet.UserId, Constant.UserType.USER_TYPE_AV_SEND_RECV, nDomainId);
        if (ret != 0)
        {
            Toast.makeText(this, "SDOnlineUser失败:" + ret, Toast.LENGTH_LONG).show();
            return ret;
        }
        else
        {
		    //本DEMO仅接收音频
			mInterface.SDSetAvDownMasks(0xFFFFFFFF, 0x0);	
            
			ret = mInterface.SDOnPosition((byte)mUserSet.UpPosition);
            if (ret != 0) {
                mInterface.SDOfflineUser();
                Toast.makeText(this, "SDOnPosition失败:" + ret, Toast.LENGTH_LONG).show();
                return ret;
            }

            Log.i(TAG, "音视频发送位置:" + mUserSet.UpPosition);
        }

        mLoginSuccess = true;

        SDInterfaceCameraPublisher.Inst().setPreviewParams(Camera.CameraInfo.CAMERA_FACING_FRONT, Configuration.ORIENTATION_PORTRAIT, mCameraCapWidth, mCameraCapHeight);
        SDInterfaceCameraPublisher.Inst().setVideoEncParams(mEncodeBitrate, mUserSet.Framerate, mEncodeWidth, mEncodeHeight);
        SDInterfaceCameraPublisher.Inst().setAudioEncParams(2, 44100, CLIENT_AUDIO_TYPE_AAC);
        //SDInterfaceCameraPublisher.Inst().setAudioEncParams(1, 8000, CLIENT_AUDIO_TYPE_G711);

        Log.i(TAG, "setOutputResolution Width = " + mEncodeWidth + " Height =" + mEncodeHeight);
        Log.i(TAG, "setVideoEncParams Bitrate = " + mEncodeBitrate + " Framerate = " + mUserSet.Framerate);

        return ret;
    }

    //下线服务器并停止音视频
    protected void offLineProcess()
    {
        mLoginSuccess = false;
        mInterface.SDOfflineUser();

    }


}
