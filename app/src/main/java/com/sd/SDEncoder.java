package com.sd;

import android.content.res.Configuration;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sd.SDInterface;
import com.sd.Constant;

import static com.sd.Constant.VideoCodecProtectLevel.CODEC_NET_PROTECT_LEVEL_DEF;
import static com.sd.Constant.VideoCodecProtectLevel.CODEC_NET_PROTECT_LEVEL_MIN;
import static com.sd.Constant.ClientAudioCodecType.CLIENT_AUDIO_TYPE_AAC;
import static com.sd.Constant.ClientAudioCodecType.CLIENT_AUDIO_TYPE_G711;


public class SDEncoder {
    private static final String TAG = "SDMedia";

    //在RK3399上硬编码码流中IDR前放置了SEI，因此插入SPS PPS时需要考虑
    private static final boolean SUPPORT_RK3399 = true;
    //软编码模式下支持对原图进行剪裁以保持与编码宽高比一致
    private static final boolean SUPPORT_CUT_TO_KEEPRATIO = true;

    private static final String VCODEC = "video/avc";
    private static final String ACODEC = "audio/mp4a-latm";
    private static String X264PRESET = "veryfast";

	
    private static int mOutWidth = 640;
    private static int mOutHeight = 360;

    private static int mVideoBitrate = 300*1000;
    private static int mFramerate = 30;
    //IDR间隔时间，单位秒
    private static int mIdrIntervalSec = 2;

    //音频采样率、声道数、编码码率、编码标准
    private static int mSamperate = 44100;
    private static int mChannelnum = 2;
    private static final int mAacBitrate = 32000;
	private static int mAudioCodecType = CLIENT_AUDIO_TYPE_AAC;

    //音频采样率对应的索引（ADTS头封装所需），采样率与索引的映射关系如下
    /*
    *             96000, 88200, 64000, 48000, 44100, 32000,
    *             24000, 22050, 16000, 12000, 11025, 8000, 7350
    *             索引依次从0递增，44100的索引为4
    * */
    private int ASAMPLERATE_INDEX_ADTS = 4;
    //音频通道对应的索引（ADTS头封装所需），通道数与索引的映射关系如下
    /*
        0: Defined in AOT Specifc Config
        1: 1 channel: front-center
        2: 2 channels: front-left, front-right
        3: 3 channels: front-center, front-left, front-right
        4: 4 channels: front-center, front-left, front-right, back-center
        5: 5 channels: front-center, front-left, front-right, back-left, back-right
        6: 6 channels: front-center, front-left, front-right, back-left, back-right, LFE-channel
        7: 8 channels: front-center, front-left, front-right, side-left, side-right, back-left, back-right, LFE-channel
        8-15: Reserved
    */
    private int ACHANNEL_INDEX_ADTS = 2;


    private MediaCodecInfo mMediaCodecInfo;
    private MediaCodec mVideoEncoder;
    private MediaCodec mAudioEncoder;
    private MediaCodec.BufferInfo mVideoBuffInfo = new MediaCodec.BufferInfo();
    private MediaCodec.BufferInfo mAudioBuffInfo = new MediaCodec.BufferInfo();

    //默认优先硬编码
    private boolean mRequestHwEncoder = true;
    private boolean mCanSoftEncode = false;
    private boolean mCanHardwareEncode = false;

	//是否支持G711音频编码
	private boolean mSupportG711Encode = false;
	
    private long mPresentTimeUs;

    private int mVideoColorFormat = 0;

    //基础API类
    private SDInterface mInterface = null;

    private byte[] mSpsPps = null;
    private byte[] mFrameStreamBuff = null;
    private boolean mStarted = false;

    private Surface mSurface = null;
    private boolean mSurfaceMode = false;
    private boolean mAsyncMode = false;
    private AsyncEncodeCallback mAsyncCallback = null;


    // Y, U (Cb) and V (Cr)
    // yuv420                     yuv yuv yuv yuv
    // yuv420p (planar)   yyyy*2 uu vv
    // yuv420sp(semi-planner)   yyyy*2 uv uv
    // I420 -> YUV420P   yyyy*2 uu vv
    // YV12 -> YUV420P   yyyy*2 vv uu
    // NV12 -> YUV420SP  yyyy*2 uv uv
    // NV21 -> YUV420SP  yyyy*2 vu vu
    // NV16 -> YUV422SP  yyyy uv uv
    // YUY2 -> YUV422SP  yuyv yuyv

    public SDEncoder(boolean bSurfaceMode, boolean bAsyncMode)
	{
        mSurfaceMode = bSurfaceMode;
        mAsyncMode = bAsyncMode;
    }
	
	//********************************************对外接口************************************************************/
	//设置日志输出接口
	public void setLogOutput(String log_output_dir, int log_output_level)
	{
		setEncoderLogOutput(log_output_dir, log_output_level);
	}
	
	//设置码流输出接口
    public void setSendInterface(SDInterface sendInterface)
	{
        mInterface = sendInterface;
    }

    //獲取輸入surface
    public Surface getSurface(){
        return mSurface;
    }
	
    //设置切换到软编码
    public void switchToSoftEncoder() 
	{
        mRequestHwEncoder = false;
    }

    //设置切换到硬编码（内部视情况响应）
    public void switchToHardEncoder() 
	{
        mRequestHwEncoder = true;
    }

	//设置视频编码分辨率
    public void setOutputResolution(int width, int height) 
	{
        mOutWidth = width;
        mOutHeight = height;
        if (mOutWidth % 2 != 0 || mOutHeight % 2 != 0)
		{
            Log.w(TAG, String.format("Enc width(%d) height(%d) is not 2x, will use software enc.", mOutWidth, mOutHeight));
            mCanHardwareEncode = false;
        }
    }

	//设置视频编码码率、帧率
    public void setVideoEncParams(int nBitrate, int nFramerate) 
	{
        mVideoBitrate = nBitrate;
        mFramerate = nFramerate;
        X264PRESET = "veryfast";
    }

    //允许动态切换码率
    public void changeVideoBitrate(int nBitrate)
    {
        if ((mRequestHwEncoder == false) || (mCanHardwareEncode == false))
        {
            Log.i(TAG, String.format("Change SW video enc bitrate to:%d bps", nBitrate));
            setEncoderBitrate(nBitrate);
        }
        else
        {
            Log.i(TAG, String.format("Change HW video enc bitrate to:%d bps", nBitrate));
            if (Build.VERSION.SDK_INT >= 19) {
                setVideoBitrateOnFly(nBitrate);
            }
            else{
                Log.w(TAG, String.format("changeVideoBitrate failed, version not support"));
            }
        }
    }

	//软编码状态下允许动态调整编码器抗弱网级别
    public void changeVideoNetworkProtectLevel(int nLevel)
    {
        if ((mRequestHwEncoder == false) || (mCanHardwareEncode == false))
        {
            Log.i(TAG, String.format("Change video codec network protect level to:%d", nLevel));
            setEncoderNetworkProtectLevel(nLevel);
        }
    }


    //允许动态IDR请求
    public void requestEncIdrFrame()
    {
        if ((mRequestHwEncoder == false) || (mCanHardwareEncode == false))
        {
            Log.i(TAG, String.format("Request SW video enc IDR frame"));
			requestEncoderIdr();
        }
        else
        {
            Log.i(TAG, String.format("Request HW video enc IDR frame"));
            if (Build.VERSION.SDK_INT >= 19) {
                setIdrFrameOnFly();
            }
            else{
                Log.w(TAG, String.format("requestEncIdrFrame failed, version not support"));
            }
        }
    }

    //软编码状态下允许动态切换分辨率
    public void changeOutputResolution(int width, int height)
    {
        if ((mRequestHwEncoder == false) || (mCanHardwareEncode == false))
        {
            Log.i(TAG, String.format("Change video enc resolution to:%dx%d", width, height));
            setEncoderResolution(width, height, SUPPORT_CUT_TO_KEEPRATIO == true ? 1:0);
        }
    }


	//设置音频编码声道、采样率、编码标准(0-AAC 1-G711 2-OPUS)
    public void setAudioEncParams(int nChannelNum, int nSamplerate, int nCodecType)
	{
        if (nChannelNum == 1)
		{
            ACHANNEL_INDEX_ADTS = 1;

        }
        else if (nChannelNum == 2)
		{
            ACHANNEL_INDEX_ADTS = 2;
        }
        else
        {
            Log.w(TAG, String.format("Audio Enc channel(%d) invalid. will use 2 channel instead.", nChannelNum));
            ACHANNEL_INDEX_ADTS = 2;
            nChannelNum = 2;
        }
        mChannelnum = nChannelNum;

        if (nSamplerate == 8000)
        {
            ASAMPLERATE_INDEX_ADTS = 11;
        }
        else if (nSamplerate == 16000)
        {
            ASAMPLERATE_INDEX_ADTS = 8;
        }
        else if (nSamplerate == 32000)
        {
            ASAMPLERATE_INDEX_ADTS = 5;
        }
        else if (nSamplerate == 44100)
        {
            ASAMPLERATE_INDEX_ADTS = 4;
        }
        else if (nSamplerate == 48000)
        {
            ASAMPLERATE_INDEX_ADTS = 3;
        }		
        else
        {
            Log.w(TAG, String.format("Audio Enc sample rate(%d) invalid. will use 44100 instead.", nSamplerate));
            ASAMPLERATE_INDEX_ADTS = 4;
            nSamplerate = 44100;
        }
        mSamperate = nSamplerate;
		
		mAudioCodecType = nCodecType;
		//G711仅支持8KHZ 1CH
		if ((mAudioCodecType == CLIENT_AUDIO_TYPE_G711) && ((mSamperate != 8000) || (mChannelnum != 1)))
		{
	        Log.w(TAG, String.format("Audio G711 Enc sample rate(%d) channel(%d) invalid. will use 8KHZ 1CH instead.", mSamperate, mChannelnum));
            mSamperate = 8000;
            mChannelnum = 1;		
		}
    }

    //获得设置的音频采样率
    public int getAudioEncSamplerate()
    {
        return mSamperate;
    }

    //获得设置的音频声道数
    public int getAudioEncChannelNum()
    {
        return mChannelnum;
    }
	
	//获得设置的音频编码类型
	public int getAudioCodecType()
	{
		return mAudioCodecType;
	}

	//视频编码器是否可用
    public boolean isEnabled() 
	{
        return mCanHardwareEncode || mCanSoftEncode;
    }
	
	//获取当前配置帧率
    public static int getEncFrameRate() 
	{
        return mFramerate;
    }
	
    //创建所需音视频编解码器
    public boolean start() 
	{
        //码流临时存放区
        mFrameStreamBuff = new byte[mOutWidth * mOutHeight];

        //SPS PPS存放区
        mSpsPps = null;

        //初始化时间戳
        mPresentTimeUs = System.nanoTime() / 1000;

		//通过native接口设置软编码相关参数
        setEncoderResolution(mOutWidth, mOutHeight, SUPPORT_CUT_TO_KEEPRATIO == true ? 1:0);
        setEncoderFps(mFramerate);
        setEncoderGop(mIdrIntervalSec);
        setEncoderBitrate(mVideoBitrate);
        setEncoderPreset(X264PRESET);
		setEncoderNetworkProtectLevel(CODEC_NET_PROTECT_LEVEL_MIN);

        //创建X264软编码器
        mCanSoftEncode = SoftEncoderOpen();
        if (!mCanSoftEncode) 
		{
            Log.e(TAG, "create software video enc failed.");
            return false;
        }

        //尝试创建H264硬编码器
        try {
            //硬编码对宽高有对齐要求
            if ((mOutWidth % 2 != 0 || mOutHeight % 2 != 0))
			{
                Log.w(TAG, String.format("Enc width(%d) height(%d) is not 2x, will use software enc.", mOutWidth, mOutHeight));
                mCanHardwareEncode = false;
                mVideoEncoder = null;
            }
            else
            {
                //是否有所需的H264硬编码器，目前使用类型而非名字创建硬编码
                mMediaCodecInfo = mfGetSupportHwVideoEncInfo(null);
                if (mMediaCodecInfo == null)
                {
                    Log.w(TAG, "hw enc is not support, cannot found h264 hw enc");
                    mCanHardwareEncode = false;
                    mVideoEncoder = null;
                }
                else
                {
                    //硬编码器的输入格式是否符合要求
                    if (mfIsHwVideoFormatSupport())
                    {
                        mVideoEncoder = MediaCodec.createByCodecName(mMediaCodecInfo.getName());
                        if (mVideoEncoder != null)
                        {
                            mCanHardwareEncode = true;
                        }
                        else
                        {
                            Log.w(TAG, "create video hw encoder failed. will use sw encode");
                            mCanHardwareEncode = false;
                        }
                    }
                    else
                    {
                        Log.w(TAG, "hw enc format is not support. will use sw encode");
                        mCanHardwareEncode = false;
                        mVideoEncoder = null;
                    }
                }
            }

        } catch (IOException e) {
            Log.w(TAG, "create video hw encoder failed. will use sw encode");
            mVideoEncoder = null;
            mCanHardwareEncode = false;
        }

        if (mCanHardwareEncode)
        {
            //配置硬编码器
            MediaFormat videoFormat = MediaFormat.createVideoFormat(VCODEC, mOutWidth, mOutHeight);
            videoFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, mVideoBitrate);
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFramerate);
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mIdrIntervalSec);
            videoFormat.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR);
            if (mSurfaceMode == true)
            {
                videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
                if (mAsyncMode == true)
                {
                    mVideoEncoder.setCallback(mVideoCodecCallback);
                }
            }
            else
            {
                videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mVideoColorFormat);
            }

            mVideoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

            if (mSurfaceMode == true)
            {
                mSurface = mVideoEncoder.createInputSurface();
                if (mSurface == null)
                {
                    Log.w(TAG, "create video hw encoder createInputSurface failed!!!!!!");
                }
            }
        }

        //创建音频硬编码器，目前未加入音频软编码支持
        try {
            mAudioEncoder = MediaCodec.createEncoderByType(ACODEC);
            if (mAudioEncoder == null)
            {
                Log.e(TAG, "create audio hw encoder failed.");
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "create audio hw encoder failed.");
            e.printStackTrace();
            return false;
        }
        //配置硬编码器
        MediaFormat audioFormat = MediaFormat.createAudioFormat(ACODEC, mSamperate, mChannelnum);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, mAacBitrate);
		audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        mAudioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        //启动相关编码器
        mAudioEncoder.start();
        if (mCanHardwareEncode)
        {
            Log.i(TAG, "start audio hw enc with sample rate:" + mSamperate + "  channel:"+ mChannelnum + " and video hw enc success, with  width:" + mOutWidth + " height:" + mOutHeight);
            mVideoEncoder.start();
        } 
		else 
		{
            Log.i(TAG, "start audio hw enc with sample rate:" + mSamperate + "  channel:"+ mChannelnum + " and video sw enc success, with  width:" + mOutWidth + " height:" + mOutHeight);
        }
		
		//创建G711软编码
		mSupportG711Encode = G711EncoderOpen();
        if (!mSupportG711Encode) 
		{
            Log.e(TAG, "create software g711 enc failed.");
        }
	
        mStarted = true;
        return true;
    }

    public void stop() 
	{
        mStarted = false;
        if (mCanSoftEncode) 
		{
            SoftEncoderClose();
            mCanSoftEncode = false;
        }
		
		if (mSupportG711Encode)
		{
			G711EncoderClose();
			mSupportG711Encode = false;
		}

        if (mAudioEncoder != null) 
		{
            Log.i(TAG, "stop audio hw encoder");
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mVideoEncoder != null) 
		{
			Log.i(TAG, "stop video hw encoder");
			mVideoEncoder.stop();
			mVideoEncoder.release();
			mVideoEncoder = null;
        }
    }

    //对外提供的状态回调
    public void setAsyncEncodeCallback(AsyncEncodeCallback callback)
    {
        mAsyncCallback = callback;
    }

    //异步硬编码模式时对外的回调通知
    public interface AsyncEncodeCallback
    {
        void onEncodedOneFrame();
    }


    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void setVideoBitrateOnFly(int bitrate) {
        if (mStarted == true) {
            Bundle bundle = new Bundle();
            bundle.putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, bitrate);
            try {
                if (mVideoEncoder != null) {
                    mVideoEncoder.setParameters(bundle);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "encoder need be running", e);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void setIdrFrameOnFly() {
        if (mStarted == true) {
            Bundle bundle = new Bundle();
            bundle.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0);
            try {
                if (mVideoEncoder != null) {
                    mVideoEncoder.setParameters(bundle);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "encoder need be running", e);
            }
        }
    }

    //异步硬编码(surface模式)
    private MediaCodec.Callback mVideoCodecCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int outputBufferIndex, MediaCodec.BufferInfo bufferInfo) {
            if (mStarted == false)
            {
                return;
            }
            int pos = 0;

            if (outputBufferIndex >= 0)
            {
                ByteBuffer outputBuffer = mVideoEncoder.getOutputBuffer(outputBufferIndex);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (mSpsPps != null)
                {
                    ByteBuffer streamBuffer = ByteBuffer.wrap(outData);
                    boolean isNewFrame = (streamBuffer.getInt() == 0x00000001) ? true:false;

                    if (isNewFrame == true)
                    {
                        if(((outData[4] & 0x1f) == 0x5) || ((outData[4] & 0x1f) == 0x6))
                        {
                            System.arraycopy(mSpsPps, 0,  mFrameStreamBuff, pos, mSpsPps.length);
                            pos += mSpsPps.length;
                        }
                    }

                    System.arraycopy(outData, 0,  mFrameStreamBuff, pos, outData.length);
                    pos += outData.length;

                }
                else
                {
                    //初次保存SPS PPS
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001)
                    {
                        mSpsPps = new byte[outData.length];
                        System.arraycopy(outData, 0, mSpsPps, 0, outData.length);
                    }
                    else
                    {
                        Log.w(TAG, "sps pps nalu invalid");
                    }
                }

                mVideoEncoder.releaseOutputBuffer(outputBufferIndex, false);
            }

            //输出视频码流
            if (mInterface != null && pos > 0)
            {
                //传入时间戳0，使用SDK内部自行维护的时间戳
                mInterface.SDSendVideoStreamData(mFrameStreamBuff, pos, 0);

                if (mAsyncCallback != null)
                {
                    mAsyncCallback.onEncodedOneFrame();
                }
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {

        }
    };

    //软编码时，C层回调本函数输出视频码流
    private void onSoftEncodedData(byte[] es, long pts, boolean isIdrFrame, boolean isKeyFrame) 
	{
        if (mInterface != null)
        {
            //传入时间戳0，使用SDK内部自行维护的时间戳
            //mInterface.SDSendVideoStreamData(es, es.length, 0);
			mInterface.SDSendVideoStreamDataWithKeyInfo(es, es.length, isKeyFrame == true ? 1:0, 0);
        }
    }

    //输出音频AAC码流
    private void onEncodedAacFrame(ByteBuffer es, MediaCodec.BufferInfo bi) 
	{
        //MediaCodec编码得到的AAC音频流为RAM格式，底层要求输入为ADTS格式
        //在此进行ADTS封装，加入7字节ADTS头
        byte[] buffer = new byte[bi.size + 7];
        mfAddADTStoPacket(buffer, bi.size + 7);
        es.get(buffer, 7 , bi.size);

        if (mInterface != null)
        {
            //传入时间戳0，使用SDK内部自行维护的时间戳
            mInterface.SDSendAudioStreamData(buffer, bi.size + 7, 0);
        }
    }
	
	//输出音频G711码流，C层回调本函数输出
    private void onG711EncodedData(byte[] es, long pts) 
	{
        if (mInterface != null)
        {
            //传入时间戳0，使用SDK内部自行维护的时间戳
			mInterface.SDSendAudioStreamData(es, es.length, 0);
        }
    }	
	
    //JNI调用C层X264软编码处理
    private void mfSoftwareNV21Encode(byte[] data, int width, int height, boolean flip, int rotateDegree, long pts)
	{
        //SoftEncodeRGBA(data, width, height, true, 180, pts);
        SoftEncodeNV21(data, width, height, flip, rotateDegree, pts);
    }

    private void mfSoftwareEncodeWithStride(ByteBuffer data, int width, int height, int stride, long pts)
    {
        SoftEncodeRGBAWithStride(data, width, height, stride, pts);
    }

    //同步硬编码处理
    private void mfHardwareEncode(byte[] yuv420, long pts) 
	{
        int pos = 0;

        try {
            ByteBuffer[] inputBuffers = mVideoEncoder.getInputBuffers();
            ByteBuffer[] outputBuffers = mVideoEncoder.getOutputBuffers();
            int inputBufferIndex = mVideoEncoder.dequeueInputBuffer(-1);
            if (inputBufferIndex >= 0)
            {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                inputBuffer.put(yuv420);
                mVideoEncoder.queueInputBuffer(inputBufferIndex, 0, yuv420.length, pts, 0);
            }

            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mVideoEncoder.dequeueOutputBuffer(bufferInfo,0);

            while (outputBufferIndex >= 0)
            {
                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (mSpsPps != null)
                {
                    ByteBuffer streamBuffer = ByteBuffer.wrap(outData);
                    boolean isNewFrame = (streamBuffer.getInt() == 0x00000001) ? true:false;
                    if (pos != 0)
                    {
                        if (isNewFrame == true)
                        {
                            //输出视频码流
                            if (mInterface != null)
                            {
                                //传入时间戳0，使用SDK内部自行维护的时间戳
                                mInterface.SDSendVideoStreamData(mFrameStreamBuff, pos, 0);
                            }
                            pos = 0;
                        }

                    }

                    if (isNewFrame == true)
                    {
                        if((outData[4] & 0x1f) == 0x5)
                        {
                            System.arraycopy(mSpsPps, 0,  mFrameStreamBuff, pos, mSpsPps.length);
                            pos += mSpsPps.length;
                        }
                    }

                    System.arraycopy(outData, 0,  mFrameStreamBuff, pos, outData.length);
                    pos += outData.length;

                }
                else
                {
                    //初次保存SPS PPS
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001)
                    {
                        mSpsPps = new byte[outData.length];
                        System.arraycopy(outData, 0, mSpsPps, 0, outData.length);
                    }
                    else
                    {
						Log.w(TAG, "sps pps nalu invalid");
                        return ;
                    }
                }

                mVideoEncoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mVideoEncoder.dequeueOutputBuffer(bufferInfo, 0);
            }

        } catch (Throwable t) {
            t.printStackTrace();
        }

        //输出视频码流
        if (mInterface != null && pos > 0)
		{
            //传入时间戳0，使用SDK内部自行维护的时间戳
            mInterface.SDSendVideoStreamData(mFrameStreamBuff, pos, 0);
        }
    }

    //同步硬编码处理(surface模式)
    public void onHardwareSurfaceEncode()
    {
        int pos = 0;

        try {
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int outputBufferIndex = mVideoEncoder.dequeueOutputBuffer(bufferInfo,0);

            while (outputBufferIndex >= 0)
            {
                ByteBuffer outputBuffer = mVideoEncoder.getOutputBuffer(outputBufferIndex);
                byte[] outData = new byte[bufferInfo.size];
                outputBuffer.get(outData);

                if (mSpsPps != null)
                {
                    ByteBuffer streamBuffer = ByteBuffer.wrap(outData);
                    boolean isNewFrame = (streamBuffer.getInt() == 0x00000001) ? true:false;
                    if (pos != 0)
                    {
                        if (isNewFrame == true)
                        {
                            //输出视频码流
                            if (mInterface != null)
                            {
                                //传入时间戳0，使用SDK内部自行维护的时间戳
                                mInterface.SDSendVideoStreamData(mFrameStreamBuff, pos, 0);
                            }
                            pos = 0;
                        }

                    }

                    if (isNewFrame == true)
                    {
                        if(((outData[4] & 0x1f) == 0x5) || ((outData[4] & 0x1f) == 0x6))
                        {
                            System.arraycopy(mSpsPps, 0,  mFrameStreamBuff, pos, mSpsPps.length);
                            pos += mSpsPps.length;
                        }
                    }

                    System.arraycopy(outData, 0,  mFrameStreamBuff, pos, outData.length);
                    pos += outData.length;

                }
                else
                {
                    //初次保存SPS PPS
                    ByteBuffer spsPpsBuffer = ByteBuffer.wrap(outData);
                    if (spsPpsBuffer.getInt() == 0x00000001)
                    {
                        mSpsPps = new byte[outData.length];
                        System.arraycopy(outData, 0, mSpsPps, 0, outData.length);
                    }
                    else
                    {
                        Log.w(TAG, "sps pps nalu invalid");
                        return ;
                    }
                }

                mVideoEncoder.releaseOutputBuffer(outputBufferIndex, false);
                outputBufferIndex = mVideoEncoder.dequeueOutputBuffer(bufferInfo, 0);
            }


        } catch (Throwable t) {
            t.printStackTrace();
        }

        //输出视频码流
        if (mInterface != null && pos > 0)
        {
            //传入时间戳0，使用SDK内部自行维护的时间戳
            mInterface.SDSendVideoStreamData(mFrameStreamBuff, pos, 0);
        }
    }

    //外部音频采集回调函数
    public void onGetPcmFrame(byte[] data, int size) 
	{
        //入口保护，还未启动前不响应外部请求
        if (mStarted == false) 
		{
            return;
        }
		
		if (mAudioCodecType == CLIENT_AUDIO_TYPE_AAC)
		{
			//AAC音频硬编码
			ByteBuffer[] inBuffers = mAudioEncoder.getInputBuffers();
			ByteBuffer[] outBuffers = mAudioEncoder.getOutputBuffers();

			int inBufferIndex = mAudioEncoder.dequeueInputBuffer(-1);
			if (inBufferIndex >= 0) 
			{
				ByteBuffer bb = inBuffers[inBufferIndex];
				bb.clear();
				bb.put(data, 0, size);
				long pts = System.nanoTime() / 1000 - mPresentTimeUs;
				mAudioEncoder.queueInputBuffer(inBufferIndex, 0, size, pts, 0);
			}

			for (; ; ) 
			{
				int outBufferIndex = mAudioEncoder.dequeueOutputBuffer(mAudioBuffInfo, 0);
				if (outBufferIndex >= 0) 
				{
					ByteBuffer bb = outBuffers[outBufferIndex];
					onEncodedAacFrame(bb, mAudioBuffInfo);
					mAudioEncoder.releaseOutputBuffer(outBufferIndex, false);
				} 
				else 
				{
					break;
				}
			}
		}
		else if (mAudioCodecType == CLIENT_AUDIO_TYPE_G711)
		{
			long pts = System.nanoTime() / 1000 - mPresentTimeUs;
			G711EncodePCM(data, size, pts);
		}
    }

    //外部视频采集回调函数
    public void onGetNv21Frame(byte[] data, int width, int height, boolean flip, int rotateDegree)
	{
        //入口保护，还未启动前不响应外部请求
        if (mStarted == false) 
		{
            return;
        }
		
        //在创建软编码或硬编码器之后再响应外部编码请求
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        if ((mRequestHwEncoder == true) && (mCanHardwareEncode == true))
        {
            byte[] processedData = mfNV21TransAndScale(data, width, height, flip, rotateDegree);
            if (processedData != null) 
			{
				//硬编码
                mfHardwareEncode(processedData, pts);
            } 
			else 
			{
				//色度空间不支持硬编码时，转向软编码
                Log.w(TAG, String.format("HW encode mfNV21TransAndScale failed, will try SW encode!!!"));
                mCanHardwareEncode = false;
            }
        }
        else if (mCanSoftEncode == true)
        {
			//软编码
            mfSoftwareNV21Encode(data, width, height, flip, rotateDegree, pts);
        }
		else
		{
			Log.w(TAG, String.format("hw and sw encode are not usable. encode failed"));
		}
    }


    //外部视频采集回调函数
    public void onGetRgbaFrameWithStride(ByteBuffer data, int width, int height, int stride)
    {
        //入口保护，还未启动前不响应外部请求
        if (mStarted == false)
        {
            return;
        }

        //在创建软编码或硬编码器之后再响应外部编码请求
        long pts = System.nanoTime() / 1000 - mPresentTimeUs;
        if ((mRequestHwEncoder == true) && (mCanHardwareEncode == true))
        {
            //long start = System.currentTimeMillis();
            byte[] processedData = mfRgbTransAndScaleWithStride(data, width, height, stride);
            //long end = System.currentTimeMillis();
            //Log.w(TAG, String.format("HW encode mfRgbTransAndScale cost " + (end - start)));
            if (processedData != null)
            {
                //硬编码
                //start = System.currentTimeMillis();
                mfHardwareEncode(processedData, pts);
                //end = System.currentTimeMillis();
                //Log.w(TAG, String.format("HW encode mfHardwareEncode cost " + (end - start)));
            }
            else
            {
                //色度空间不支持硬编码时，转向软编码
                Log.w(TAG, String.format("HW encode mfRgbTransAndScale failed, will try SW encode!!!"));
                mCanHardwareEncode = false;
            }
        }
        else if (mCanSoftEncode == true)
        {
            //软编码
            //long start = System.currentTimeMillis();
            mfSoftwareEncodeWithStride(data, width, height, stride, pts);
            //long end = System.currentTimeMillis();
            //Log.w(TAG, String.format("SW encode mfSoftwareEncodeWithStride cost " + (end - start)));
        }
        else
        {
            Log.w(TAG, String.format("hw and sw encode are not usable. encode failed"));
        }
    }


    //通过C层完成颜色空间转换以及缩放到编码分辨率
    private byte[] mfNV21TransAndScale(byte[] data, int width, int height, boolean flip, int rotateDegree)
	{
        switch (mVideoColorFormat) 
		{
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                //Log.w(TAG, "mfNV21TransAndScale to  COLOR_FormatYUV420Planar");
                return NV21ToI420(data, width, height, mOutWidth, mOutHeight, flip, rotateDegree);
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                //Log.w(TAG, "mfNV21TransAndScale to  COLOR_FormatYUV420SemiPlanar");
                return NV21ToNV12(data, width, height, mOutWidth, mOutHeight, flip, rotateDegree);
            default:
                Log.w(TAG, String.format("hw encode input color format:%d is not support, change to sw encode.", mVideoColorFormat));
                return null;
        }
    }

    private byte[] mfRgbTransAndScaleWithStride(ByteBuffer data, int width, int height, int stride)
    {
        switch (mVideoColorFormat)
        {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                return RGBAToI420WithStride(data, width, height, stride, mOutWidth, mOutHeight);
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                return RGBAToNV12WithStride(data, width, height, stride, mOutWidth, mOutHeight);
            default:
                Log.w(TAG, String.format("hw encode input color format:%d is not support, change to sw encode.", mVideoColorFormat));
                return null;
        }
    }

    //获得支持的H264类型硬编码器信息，若指定了名字则要求名字匹配
    private MediaCodecInfo mfGetSupportHwVideoEncInfo(String name) 
	{
        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) 
		{
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) 
			{
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) 
			{
                if (types[j].equalsIgnoreCase(VCODEC)) 
				{
                    Log.i(TAG, String.format("mVideoEncoder %s types: %s", mci.getName(), types[j]));
                    if (name == null) 
					{
                        return mci;
                    }

                    if (mci.getName().contains(name)) 
					{
                        return mci;
                    }
                }
            }
        }
        return null;
    }

    //判断编码器是否支持指定的色度空间
    private boolean mfIsHwVideoFormatSupport() 
	{
        boolean bSupportFormat = false;
        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = mMediaCodecInfo.getCapabilitiesForType(VCODEC);
        for (int i = 0; i < cc.colorFormats.length; i++) 
		{
            int cf = cc.colorFormats[i];
            Log.i(TAG, String.format("mVideoEncoder %s supports color fomart 0x%x(%d)", mMediaCodecInfo.getName(), cf, cf));

            if (cf == cc.COLOR_FormatYUV420Planar || cf == cc.COLOR_FormatYUV420SemiPlanar)
			{
                matchedColorFormat = cf;
                bSupportFormat = true;

                if (cf == cc.COLOR_FormatYUV420SemiPlanar)
				{
				    //优先YUV420SemiPlanar
                    break;
                }
            }
        }

        /*
        for (int i = 0; i < cc.profileLevels.length; i++)
		{
            MediaCodecInfo.CodecProfileLevel pl = cc.profileLevels[i];
            Log.i(TAG, String.format("mVideoEncoder %s support profile %d, level %d", mMediaCodecInfo.getName(), pl.profile, pl.level));
        }
        */
        Log.i(TAG, "mVideoEncoder final use:" + matchedColorFormat);
        mVideoColorFormat = matchedColorFormat;
        return bSupportFormat;
    }

    /**
     *  Add ADTS header at the beginning of each and every AAC packet.
     *  This is needed as MediaCodec encoder generates a packet of raw
     *  AAC data.
     *
     *  Note the packetLen must count in the ADTS header itself.
     **/
    private void mfAddADTStoPacket(byte[] packet, int packetLen) {
        int profile = 2;  //AAC LC
        //39=MediaCodecInfo.CodecProfileLevel.AACObjectELD;
        int freqIdx = ASAMPLERATE_INDEX_ADTS;
        int chanCfg = ACHANNEL_INDEX_ADTS;

        // fill in ADTS data
        packet[0] = (byte)0xFF;
        packet[1] = (byte)0xF9;
        packet[2] = (byte)(((profile-1)<<6) + (freqIdx<<2) +(chanCfg>>2));
        packet[3] = (byte)(((chanCfg&3)<<6) + (packetLen>>11));
        packet[4] = (byte)((packetLen&0x7FF) >> 3);
        packet[5] = (byte)(((packetLen&7)<<5) + 0x1F);
        packet[6] = (byte)0xFC;
    }
	
	//初始化日志模块
	private native void setEncoderLogOutput(String log_output_dir, int log_output_level);
	//本接口支持过程中调用
    private native void setEncoderResolution(int outWidth, int outHeight, int use_cut_keep_ratio);
    private native void setEncoderFps(int fps);
    private native void setEncoderGop(int gop);
	//本接口支持过程中调用
    private native void setEncoderBitrate(int bitrate);
    private native void setEncoderPreset(String preset);
	//本接口支持过程中调用
	private native void setEncoderNetworkProtectLevel(int network_protect_level);
	//本接口支持过程中调用
	private native void requestEncoderIdr();

	
    private native int SoftEncodeRGBA(byte[] frame, int width, int height, boolean flip, int rotate, long pts);
    private native int SoftEncodeNV21(byte[] frame, int width, int height, boolean flip, int rotate, long pts);
    private native int SoftEncodeRGBAWithStride(ByteBuffer frame, int width, int height, int stride, long pts);
    private native boolean SoftEncoderOpen();
    private native void SoftEncoderClose();

    private native int G711EncodePCM(byte[] frame, int frame_size, long pts);
    private native boolean G711EncoderOpen();
    private native void G711EncoderClose();

    private native byte[] NV21ToI420(byte[] frame, int src_width, int src_height, int dst_width, int dst_height, boolean flip, int rotate);
    private native byte[] NV21ToNV12(byte[] frame, int src_width, int src_height, int dst_width, int dst_height, boolean flip, int rotate);
	private native byte[] RGBAToI420(byte[] frame, int src_width, int src_height, int dst_width, int dst_height, boolean flip, int rotate);
    private native byte[] RGBAToNV12(byte[] frame, int src_width, int src_height, int dst_width, int dst_height, boolean flip, int rotate);
    private native byte[] RGBAToI420WithStride(ByteBuffer frame, int width, int height, int stride, int dst_width, int dst_height);
    private native byte[] RGBAToNV12WithStride(ByteBuffer frame, int width, int height, int stride, int dst_width, int dst_height);

    static {
        System.loadLibrary("TerminalSdkEnc");
    }
}
