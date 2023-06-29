package com.sd;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.util.Log;
import android.view.SurfaceView;

import com.sd.Constant;
import static com.sd.Constant.ClientAudioCodecType.CLIENT_AUDIO_TYPE_AAC;
import static com.sd.Constant.ClientAudioCodecType.CLIENT_AUDIO_TYPE_G711;

public class SDInterfaceCameraPublisher {
    private static final String TAG = "SDMedia";

	//音频采集相关
    private AudioRecord mAudioRecord = null;
    private Thread mAudioCapEncThr = null;


    private AcousticEchoCanceler mAec = null;
    private AutomaticGainControl mAgc = null;
    private NoiseSuppressor mAns = null;

    private boolean mEnableAec = false;
    private boolean mEnableAgc = false;
    private boolean mEnableAns = false;

	//摄像头本地渲染
    private SDCameraView mCameraView;

	//音视频编码器
    private SDEncoder mEncoder = null;

    private boolean mSendAudioOnly = false;
    private boolean mSendVideoOnly = false;

    private boolean mInited = false;
    private boolean mStarted = false;

    //视频采集丢帧间隔
    private int mVideoFrameDropInterval = 0;
    private int mVideoFrameCount = 0;


    //单实例
    private static class SingletonHolder 
	{
        private static final SDInterfaceCameraPublisher INSTANCE = new SDInterfaceCameraPublisher();
    }

    public static final SDInterfaceCameraPublisher Inst() 
	{
        return SingletonHolder.INSTANCE;
    }


    public SDInterfaceCameraPublisher() 
	{
        mEncoder = new SDEncoder(false, false);
    }


    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////      底层回调实现



	//////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////      对外接口

    //设置日志输出接口，Init接口之前调用生效
    public void setLogOutput(String log_output_dir, byte log_output_level)
    {
        mEncoder.setLogOutput(log_output_dir, log_output_level);
    }

    //初始化摄像头麦克风互动模块
    public void Init(SDInterface sdInterface, SurfaceView view, boolean sendAudioOnly, boolean sendVideoOnly, SDAudio3AConfig audio3AConfig)
	{
        mInited = true;
        mEncoder.setSendInterface(sdInterface);
        mSendAudioOnly = sendAudioOnly;
        mSendVideoOnly = sendVideoOnly;

        if (audio3AConfig != null)
        {
            mEnableAec = audio3AConfig.mEnableAec;
            mEnableAgc = audio3AConfig.mEnableAgc;
            mEnableAns = audio3AConfig.mEnableAns;

        }


        if (mSendAudioOnly == false)
		{
            mCameraView = new SDCameraView(view);
			//注册视频采集回调函数
            mCameraView.setPreviewCallback(new SDCameraView.PreviewCallback() {
                @Override
                public void onGetNv21Frame(byte[] data, int width, int height, boolean flip, int rotateDegree)
				{
                    if ((mSendAudioOnly == false) && (mEncoder != null)) 
					{
					    //按码率自适应请求，进行间隔丢帧
					    boolean shouldSkip = false;
                        mVideoFrameCount++;
					    if (mVideoFrameDropInterval == 0)
                        {
                            shouldSkip = false;
                        }
                        else
                        {
                            if ((mVideoFrameCount % mVideoFrameDropInterval) == 0)
                            {
                                shouldSkip = true;
                            }
                        }

					    if (shouldSkip == false)
                        {
                            mEncoder.onGetNv21Frame(data, width, height, flip, rotateDegree);
                        }
                    }
                }
            });
        }
        else 
		{
            Log.i(TAG, "Init SDInterfaceCameraPublisher with audio only mode!");
        }
    }

    public void Destroy() 
	{

    }



	//开始发布
    public boolean startPublish() 
	{
        if (mInited == false) 
		{
            Log.e(TAG, "startPublish failed should Init first.");
            return false;
        }

        if (mSendVideoOnly == false)
        {
            //启动音频采集编码(一体线程)
            if (!mfStartAudioCap())
            {
                mfStopAudio();
                return false;
            }
        }


        if (mSendAudioOnly == false)
        {
            //开始视频采集线程
            if (!mCameraView.startCamera())
            {
                Log.e(TAG, "startPublish failed. camera start failed!");
                mfStopAudio();
                return false;
            }

        }

        //启动编码器，在未启动前，采集送入的数据将被丢弃
        boolean bRet = mEncoder.start();
        if (bRet == false)
        {
            Log.e(TAG, "startPublish failed. encode start failed!");
            mfStopAudio();
            if (mSendAudioOnly == false)
            {
                mCameraView.stopCamera();
            }
        }
        else
        {
            mStarted = true;
        }
        return bRet;
    }

	//停止发布
    public void stopPublish() 
	{
        mStarted = false;
        if (mInited == false) 
		{
            Log.e(TAG, "stopPublish failed should Init first.");
            return;
        }

        mfStopAudio();

        if (mSendAudioOnly == false)
        {
            mCameraView.stopCamera();
        }

        mEncoder.stop();
    }

	//使用软编码
    public void setToSoftwareEncoder()
	{
        mEncoder.switchToSoftEncoder();
    }
	
	//使用硬编码
    public void setToHardwareEncoder()
	{
        mEncoder.switchToHardEncoder();
    }

    //指定期望的采集宽高
    public boolean setPreviewParams(int cameraType, int previewOrientation, int width, int height)
	{
        if (mInited == false) 
		{
            Log.e(TAG, "setPreviewParams failed should Init first.");
            return false;
        }

        if (mSendAudioOnly == true)
        {
            Log.e(TAG, "setPreviewParams failed, current state is audio only mode.");
            return false;
        }
        //请求设置的采集宽高 不一定 等于实际支持和使用的采集宽高
        int resolution[] = mCameraView.setPreviewResolution(cameraType, previewOrientation, width, height);
        return true;
    }



    //设置视频编码参数
    public boolean setVideoEncParams(int nBitrate, int nFramerate, int nWidth, int nHeight)
	{
        if (mInited == false) 
		{
            Log.e(TAG, "setVideoEncParams failed should Init first.");
            return false;
        }
		
        if (mSendAudioOnly == true)
        {
            return false;
        }

        mEncoder.setOutputResolution(nWidth, nHeight);
        mEncoder.setVideoEncParams(nBitrate, nFramerate);
        return true;
    }

    //设置音频采集、编码声道、采样率、编码标准(0-AAC 1-G711 2-OPUS)
    public boolean setAudioEncParams(int nChannels, int nSampleRate, int nCodecType)
    {
        if (mInited == false)
        {
            Log.e(TAG, "setAudioEncParams failed should Init first.");
            return false;
        }
        mEncoder.setAudioEncParams(nChannels, nSampleRate, nCodecType);
        return true;
    }

    //过程中：切换前后置摄像头
    public boolean changeCameraFace()
	{

        if (mInited == false) 
		{
            Log.e(TAG, "changeCameraFace failed should Init first.");
            return false;
        }

        if (mSendAudioOnly == true)
        {
            return false;
        }
        mCameraView.stopCamera();
        mCameraView.switchCamera();
        boolean bRet = mCameraView.startCamera();
        if (bRet == false)
        {
            Log.e(TAG, "changeCameraFace failed. camera start failed!");
            return false;
        }
        return true;
    }

    //过程中：请求通过丢帧等方式降低码率，实现码率自适应
    public void changeVideoFrameDropInterval(int frameDropInterval)
    {
        mVideoFrameDropInterval = frameDropInterval;
        Log.i(TAG, "set video frame drop interval:" + frameDropInterval);
    }

    //过程中：设置视频编码碼率
    public boolean changeVideoBitrate(int nBitrate)
    {
        if (mInited == false)
        {
            Log.e(TAG, "setVideoBitrate failed should Init first.");
            return false;
        }

        if (mSendAudioOnly == true)
        {
            return false;
        }

        mEncoder.changeVideoBitrate(nBitrate);
        return true;
    }


    //获得AEC开启情况
    public boolean isAecEnable()
    {
        return mEnableAec;
    }

    //获得ANS开启情况
    public boolean isAnsEnable()
    {
        return mEnableAns;
    }

    //获得AGC开启情况
    public boolean isAgcEnable()
    {
        return mEnableAgc;
    }

    //获得当前硬件or软件AEC情况
    public boolean isSoftware3A()
    {
        return false;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////      内部函数
    //创建音频渲染器
    private AudioTrack mfCreateAudioTrack()
    {
        int audioChannels = mEncoder.getAudioEncChannelNum();
        int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        if (audioChannels == 1)
        {
            channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
        }
        else
        {
            channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;
        }
        int minBufferSize = AudioTrack.getMinBufferSize(mEncoder.getAudioEncSamplerate(), channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, mEncoder.getAudioEncSamplerate(), channelConfig, AudioFormat.ENCODING_PCM_16BIT, minBufferSize, AudioTrack.MODE_STREAM);

        if (audioTrack.getState() == AudioTrack.STATE_INITIALIZED)
        {
            Log.i(TAG, "Audio render create success with sample rate:" + mEncoder.getAudioEncSamplerate() + "  channels:" + audioChannels);
        }
        else {
            Log.e(TAG, "Audio render create failed with sample rate:" + mEncoder.getAudioEncSamplerate() + "  channels:" + audioChannels);
            audioTrack.release();
            audioTrack = null;
        }

        return audioTrack;
    }

    //创建音频采集器
    private AudioRecord mfCreateAudioRecord()
    {
        int audioChannels = mEncoder.getAudioEncChannelNum();
        int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        if (audioChannels == 1)
        {
            channelConfig = AudioFormat.CHANNEL_IN_MONO;
        }
        else
        {
            channelConfig = AudioFormat.CHANNEL_IN_STEREO;
        }
        int pcmBufSize = AudioRecord.getMinBufferSize(mEncoder.getAudioEncSamplerate(), channelConfig, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord audioRecord = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, mEncoder.getAudioEncSamplerate(), channelConfig, AudioFormat.ENCODING_PCM_16BIT, pcmBufSize * 2);
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED)
        {
            Log.i(TAG, "create audio capture success with sample rate:" + mEncoder.getAudioEncSamplerate() + " channel:" + audioChannels + " buf size:" + pcmBufSize);
        }
        else
        {
            Log.e(TAG, "create audio capture failed with sample rate:" + mEncoder.getAudioEncSamplerate() + " channel:" + audioChannels + " buf size:" + pcmBufSize);
            audioRecord.release();
            audioRecord = null;
        }
        return audioRecord;
    }

    //创建音频采集模块并开始采集编码线程
    private boolean mfStartAudioCap()
    {
        mAudioRecord = mfCreateAudioRecord();
        if (mAudioRecord == null)
        {
            Log.e(TAG, "startAudio failed create audio cap failed.");
            return false;
        }

        {
            //硬件AEC则开启
            if (mEnableAec == true)
            {
                if (AcousticEchoCanceler.isAvailable())
                {
                    mAec = AcousticEchoCanceler.create(mAudioRecord.getAudioSessionId());
                    if (mAec != null)
                    {
                        mAec.setEnabled(true);
                        if (mAec.getEnabled() == true)
                        {
                            Log.i(TAG, "HW AEC is available!");
                        }
                        else
                        {
                            Log.w(TAG, "HW AEC enable is failed!");
                            mEnableAec = false;
                        }
                    }
                    else
                    {
                        Log.w(TAG, "HW AEC is unavailable!");
                        mEnableAec = false;
                    }
                }
                else
                {
                    Log.w(TAG, "HW AEC is unavailable!");
                    mEnableAec = false;
                }
            }

            if (mEnableAns == true)
            {
                if (NoiseSuppressor.isAvailable())
                {
                    mAns = NoiseSuppressor.create(mAudioRecord.getAudioSessionId());
                    if (mAns != null)
                    {
                        mAns.setEnabled(true);
                        if (mAns.getEnabled() == true)
                        {
                            Log.i(TAG, "HW ANS is available!");
                        }
                        else
                        {
                            Log.w(TAG, "HW ANS enable is failed!");
                            mEnableAns = false;
                        }
                    }
                    else
                    {
                        Log.w(TAG, "HW ANS is unavailable!");
                        mEnableAns = false;
                    }
                }
                else
                {
                    Log.w(TAG, "HW ANS is unavailable!");
                    mEnableAns = false;
                }
            }

            if (mEnableAgc == true)
            {
                if (AutomaticGainControl.isAvailable())
                {
                    mAgc = AutomaticGainControl.create(mAudioRecord.getAudioSessionId());
                    if (mAgc != null)
                    {
                        mAgc.setEnabled(true);
                        if (mAgc.getEnabled() == true)
                        {
                            Log.i(TAG, "HW AGC is available!");
                        }
                        else
                        {
                            Log.w(TAG, "HW AGC enable is failed!");
                            mEnableAgc = false;
                        }
                    }
                    else
                    {
                        Log.w(TAG, "HW AGC is unavailable!");
                        mEnableAgc = false;
                    }
                }
                else
                {
                    Log.w(TAG, "HW AGC is unavailable!");
                    mEnableAgc = false;
                }
            }

        }


        //音频采集编码一体线程
        mAudioCapEncThr = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

                mAudioRecord.startRecording();
                int audioChannels = mEncoder.getAudioEncChannelNum();
                int audioSamplerate = mEncoder.getAudioEncSamplerate();
				int audioCodecType = mEncoder.getAudioCodecType();


                //AAC编码每帧1024个sample， 字节数：1024 * 2（short to byte）* channels
                int aduioFrameSize = audioChannels == 2 ? 4096:2048;
				
				if (audioCodecType == CLIENT_AUDIO_TYPE_G711)
				{
	                //G711推荐单次处理指定时长数据(20ms)
                    aduioFrameSize = audioChannels * (audioSamplerate / 100) * 2 * 2;				
				}


                byte[] pcmBuffer = new byte[aduioFrameSize];
                while (!Thread.interrupted())
                {
                    int size = mAudioRecord.read(pcmBuffer, 0, pcmBuffer.length);
                    if (size > 0)
                    {

                        mEncoder.onGetPcmFrame(pcmBuffer, size);

                    }
                }
            }
        });
        mAudioCapEncThr.start();
        return true;
    }


    //停止音频采集编码\音频渲染
    private void mfStopAudio()
    {
        //先停止音频采集编码线程
        if (mAudioCapEncThr != null)
        {
            mAudioCapEncThr.interrupt();
            try
            {
                mAudioCapEncThr.join();
            }
            catch (InterruptedException e)
            {
                mAudioCapEncThr.interrupt();
            }
            mAudioCapEncThr = null;
        }

        if (mAudioRecord != null)
        {
            mAudioRecord.setRecordPositionUpdateListener(null);
            mAudioRecord.stop();
            mAudioRecord.release();
            mAudioRecord = null;
        }


        if (mAec != null)
        {
            mAec.setEnabled(false);
            mAec.release();
            mAec = null;
        }

        if (mAgc != null)
        {
            mAgc.setEnabled(false);
            mAgc.release();
            mAgc = null;
        }

    }

}
