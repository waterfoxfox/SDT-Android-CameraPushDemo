package com.sd;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;



public class SDCameraView {
    private static final String TAG = "SDMedia";

    private SurfaceHolder mSurfaceViewHolder = null;
    private Camera mCamera = null;

	//采集宽高
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
	//是否使能编码
    private boolean mIsEncoding;
	//是否开启闪光灯
    private boolean mIsTorchOn = false;


    //用于存放采集的一帧RGBA数据的临时区域
    private ByteBuffer mPreviewBuffer = null;
    private int mCamId = -1;
    private int mFrontCamId = -1;
    private int mBackCamId = -1;
    private int mPreviewOrientation = Configuration.ORIENTATION_PORTRAIT;
    private int mPreviewCameraType = Camera.CameraInfo.CAMERA_FACING_FRONT;

    //当采集帧率高于编码帧率时，进行丢帧处理相关
    private boolean mFrameDropMode = false;
    private int mFrameInterval = 1;
    private long mFrameCount = 0;
	//编码线程
    private Thread mEncodeThr;
    private final Object writeLock = new Object();
    //采集线程与编码线程之间的队列
    private ConcurrentLinkedQueue<byte []> mCameraBufferCache = new ConcurrentLinkedQueue<>();
    //采集回调函数，外层一般在回调中实现视频编码
    private PreviewCallback mPrevCb = null;


    public SDCameraView(SurfaceView cameraSurfaceView)
	{
        if (cameraSurfaceView != null)
        {
            mSurfaceViewHolder = cameraSurfaceView.getHolder();
            mSurfaceViewHolder.addCallback(mSurfaceCallback);
        }
    }


    private SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mSurfaceViewHolder = holder;
            if ((mCamera != null) && (mIsEncoding == true))
            {
                stopCamera();
                startCamera();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {

        }
    };


    private  Camera.PreviewCallback mPreviewCallback = new Camera.PreviewCallback() {
        @Override
        public void onPreviewFrame(byte[] byVideoData, Camera currentCamera) {

            if (mIsEncoding)
            {
                //Log.i(TAG, "onPreviewFrame get a frame.");
                mCameraBufferCache.add(byVideoData);
                synchronized (writeLock)
                {
                    writeLock.notifyAll();
                }
            }
        }
    };

    public interface PreviewCallback
    {
        void onGetNv21Frame(byte[] data, int width, int height, boolean flip, int rotateDegree);
    }





    //******************************************************************************************
    //*****对外接口******

    //指定采集回调函数，在Start接口前必须调用本接口
    public void setPreviewCallback(PreviewCallback cb) 
	{
        mPrevCb = cb;
    }

    //请求按指定宽高采集，实际宽高可能不等于请求宽高（返回值），在Start接口前必须调用本接口
    public int[] setPreviewResolution(int cameraType, int previewOrientation, int width, int height)
	{
        if ((cameraType == Camera.CameraInfo.CAMERA_FACING_FRONT) || (cameraType == Camera.CameraInfo.CAMERA_FACING_BACK))
        {
            mPreviewCameraType = cameraType;
        }
        else
        {
            Log.e(TAG, "Camera setPreviewOrientationAndType with invalid type:" + cameraType);
        }

        if ((previewOrientation == Configuration.ORIENTATION_PORTRAIT) || (previewOrientation == Configuration.ORIENTATION_LANDSCAPE))
        {
            mPreviewOrientation = previewOrientation;
        }
        else
        {
            Log.e(TAG, "Camera setPreviewOrientationAndType with invalid Orientation:" + previewOrientation);
        }

        Log.i(TAG, "Camera setPreviewOrientationAndType with type:" + mPreviewCameraType + " orientation:" + mPreviewOrientation);

	    if (mSurfaceViewHolder != null)
        {
            mSurfaceViewHolder.setFixedSize(width, height);
        }

        mCamera = mfOpenCamera();
		
        mPreviewWidth = width;
        mPreviewHeight = height;

        if (mCamera != null)
        {
            //若摄像头支持指定的宽高，则使用指定宽高，否则使用宽高比最为接近的分辨率
            Camera.Size rs = mfAdaptPreviewResolution(mCamera.new Size(width, height));
            if (rs != null)
            {
                mPreviewWidth = rs.width;
                mPreviewHeight = rs.height;
            }
        }

        Log.i(TAG, "Camera setPreviewResolution with w:" + width + " h:" + height + " Finally use w:" + mPreviewWidth + " h:" + mPreviewHeight);

        //用于存放采集的一帧RGBA数据的临时区域
        mPreviewBuffer = ByteBuffer.allocateDirect(mPreviewWidth * mPreviewHeight * 4);

        return new int[] { mPreviewWidth, mPreviewHeight };
    }

    //过程中切换摄像头
    public void switchCamera()
	{
	    if (mPreviewCameraType == Camera.CameraInfo.CAMERA_FACING_FRONT)
	    {
	        if (mBackCamId != -1)
	        {
                mPreviewCameraType = Camera.CameraInfo.CAMERA_FACING_BACK;
                mCamId = mBackCamId;
            }
	        else
            {
                Log.e(TAG, "switchCamera to back camera failed! camera not exist!");
            }
        }
	    else
        {
            if (mFrontCamId != -1)
            {
                mPreviewCameraType = Camera.CameraInfo.CAMERA_FACING_FRONT;
                mCamId = mFrontCamId;
            }
            else
            {
                Log.e(TAG, "switchCamera to front camera failed! camera not exist!");
            }
        }
    }

	//启动采集
    public boolean startCamera() 
	{
	    if ((mPreviewWidth == 0) || (mPreviewHeight == 0) || (mPreviewBuffer == null))
        {
            Log.e(TAG, "startCamera failed. should call setPreviewResolution first!");
            return false;
        }

	    //开启编码线程
        mfEnableEncoding();

        if (mCamera == null) 
		{
            mCamera = mfOpenCamera();
            if (mCamera == null) 
			{
                Log.e(TAG, "startCamera failed. open camera failed");
                return false;
            }
        }

        if (mSurfaceViewHolder == null)
        {
            Log.e(TAG, "startCamera failed. mSurfaceViewHolder is null");
            return false;
        }

        Camera.Parameters params = mCamera.getParameters();
        Camera.Size pictureSize = mfAdaptPictureResolution(mCamera.new Size(mPreviewWidth, mPreviewHeight));
        if (pictureSize != null)
        {
            Log.i(TAG, "Camera use picture size1:" + pictureSize.width + " x " + pictureSize.height);
            params.setPictureSize(pictureSize.width, pictureSize.height);
        }
        else
        {
            Log.i(TAG, "Camera use picture size2:" + mPreviewWidth + " x " + mPreviewHeight);
            params.setPictureSize(mPreviewWidth, mPreviewHeight);
        }
        Log.i(TAG, "Camera use preview size:" + mPreviewWidth + " x " + mPreviewHeight);
        params.setPreviewSize(mPreviewWidth, mPreviewHeight);
		
        //寻找支持的最接近外层编码要求的帧率
        int[] range = mfAdaptFpsRange(SDEncoder.getEncFrameRate(), params.getSupportedPreviewFpsRange());
        params.setPreviewFpsRange(range[0], range[1]);
        Log.i(TAG, "Camera request fps:" + SDEncoder.getEncFrameRate() + " Finally use fps:" + range[0] + "~" + range[1]);
		
        //若实际帧率超出期望帧率一定程度，则需要进行丢帧处理
        mfCalcFrameDropInterval(SDEncoder.getEncFrameRate(), range[0] / 1000);
        mFrameCount = 0;

        params.setPreviewFormat(ImageFormat.NV21);
        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        params.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
        params.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);

        try 
		{
            //某些特定设备上设置自动对焦可能出现异常
            List<String> supportedFocusModes = params.getSupportedFocusModes();
            if (supportedFocusModes != null && !supportedFocusModes.isEmpty()) 
			{
                if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) 
				{
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } 
				else if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) 
				{
                    params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    mCamera.autoFocus(null);
                } 
				else 
				{
                    params.setFocusMode(supportedFocusModes.get(0));
                }
            }
            Log.i(TAG, "Camera setFocusMode success.");
        } 
		catch (Exception e) 
		{
            //仅当做警告，不影响采集
            Log.w(TAG, "Camera setFocusMode failed.");
        }

        List<String> supportedFlashModes = params.getSupportedFlashModes();
        if (supportedFlashModes != null && !supportedFlashModes.isEmpty()) 
		{
            if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) 
			{
                if (mIsTorchOn) 
				{
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                }
            } 
			else 
			{
                params.setFlashMode(supportedFlashModes.get(0));
            }
        }

        int previewRotation = 90;
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCamId, info);
        if (mPreviewOrientation == Configuration.ORIENTATION_PORTRAIT)
        {
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            {
                previewRotation = info.orientation % 360;
                previewRotation = (360 - previewRotation) % 360;  // compensate the mirror
            }
            else
            {
                previewRotation = (info.orientation + 360) % 360;
            }
        }
        else if (mPreviewOrientation == Configuration.ORIENTATION_LANDSCAPE)
        {
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT)
            {
                previewRotation = (info.orientation + 90) % 360;
                previewRotation = (360 - previewRotation) % 360;  // compensate the mirror
            }
            else
            {
                previewRotation = (info.orientation + 270) % 360;
            }
        }
        mCamera.setDisplayOrientation(previewRotation);
        Log.i(TAG, "mCamera.setDisplayOrientation:" + previewRotation);

        try 
		{
            mCamera.setParameters(params);
        } 
		catch (Exception e) 
		{
            Log.e(TAG, "Camera setParameters failed. may not support the request w:" + mPreviewWidth + " h:" + mPreviewHeight);
            e.printStackTrace();
            return false;
        }

        try 
		{
            mCamera.setPreviewDisplay(mSurfaceViewHolder);
            mCamera.setPreviewCallback(mPreviewCallback);
        } 
		catch (IOException e) 
		{
            Log.e(TAG, "Camera setPreviewTexture failed.");
            e.printStackTrace();
            return false;
        }
        mCamera.startPreview();

        return true;
    }

    public void stopCamera() 
	{
		//停止编码线程
        mfDisableEncoding();

		//停止采集
        if (mCamera != null) 
		{
            try {
                mCamera.setPreviewCallback(null);
                mCamera.setPreviewDisplay(null);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
        }
    }


    //******************************************************************************************
    //*****内部函数******

    //启动编码线程
    private void mfEnableEncoding()
    {
        mFrameCount = 0;
        mEncodeThr = new Thread(new Runnable() {
            @Override
            public void run()
            {
                while (!Thread.interrupted())
                {
                    while (!mCameraBufferCache.isEmpty())
                    {
                        byte picture[] = mCameraBufferCache.poll();
                        if (picture != null)
                        {
                            mPreviewBuffer.rewind();
                            mPreviewBuffer.put(picture);
                            if (mPrevCb != null)
                            {
                                mFrameCount++;

                                int rotateDegree = 0;
                                if (mPreviewOrientation == Configuration.ORIENTATION_LANDSCAPE)
                                {
                                    rotateDegree = 0;
                                }
                                else
                                {
                                    rotateDegree = mCamId == mFrontCamId ? 270 : 90;
                                }

                                //丢帧处理
                                if (mFrameDropMode == false)
                                {
                                    if ((mFrameCount % mFrameInterval) == 0)
                                    {
                                        mPrevCb.onGetNv21Frame(mPreviewBuffer.array(), mPreviewWidth, mPreviewHeight, mCamId == mFrontCamId ? true:false, rotateDegree);
                                    }
                                }
                                else
                                {
                                    if ((mFrameCount % mFrameInterval) != 0)
                                    {
                                        mPrevCb.onGetNv21Frame(mPreviewBuffer.array(), mPreviewWidth, mPreviewHeight, mCamId == mFrontCamId ? true:false, rotateDegree);
                                    }
                                }

                            }
                        }
                    }
                    // Waiting for next frame
                    synchronized (writeLock)
                    {
                        try
                        {
                            // isEmpty() may take some time, so we set timeout to detect next frame
                            writeLock.wait(500);
                        }
                        catch (InterruptedException ie)
                        {
                            mEncodeThr.interrupt();
                        }
                    }
                }
            }
        });

        mEncodeThr.start();
        mIsEncoding = true;
        Log.i(TAG, "Camera enableEncoding success.");
    }

    //停止编码线程
    private void mfDisableEncoding()
    {
        mIsEncoding = false;

        if (mEncodeThr != null)
        {
            mEncodeThr.interrupt();
            try
            {
                mEncodeThr.join();
            }
            catch (InterruptedException e)
            {
                e.printStackTrace();
                mEncodeThr.interrupt();
            }
            mEncodeThr = null;
        }
        Log.i(TAG, "Camera disableEncoding success.");

        mCameraBufferCache.clear();
    }


    private Camera mfOpenCamera() 
	{
        Camera camera;
        if (mCamId < 0) 
		{
            Camera.CameraInfo info = new Camera.CameraInfo();
            int numCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numCameras; i++) 
			{
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) 
				{
                    mBackCamId = i;
                } 
				else if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) 
				{
                    mFrontCamId = i;
                }
            }
			
            if ((mFrontCamId != -1) && (mPreviewCameraType == Camera.CameraInfo.CAMERA_FACING_FRONT))
			{
                mCamId = mFrontCamId;
            }
			else if ((mBackCamId != -1) && (mPreviewCameraType == Camera.CameraInfo.CAMERA_FACING_BACK))
			{
                mCamId = mBackCamId;
            } 
			else 
			{
				Log.e(TAG, "Cannot found request:" + (mPreviewCameraType == Camera.CameraInfo.CAMERA_FACING_FRONT ? "front camera!":"back camera!"));
                mCamId = mFrontCamId != -1 ? mFrontCamId:mBackCamId;
            }

			if (mCamId < 0)
            {
                Log.e(TAG, "None camera is usable! mfOpenCamera failed!");
                return null;
            }
        }
        camera = Camera.open(mCamId);
        return camera;
    }

    //若摄像头支持指定的宽高，则使用指定宽高，否则使用宽高比和面积接近的分辨率
    private Camera.Size mfAdaptPreviewResolution(Camera.Size resolution) 
	{
        float diffRatioMin = 100f;
        float targetRatio = resolution.width > resolution.height ? ((float) resolution.width / (float) resolution.height) :  ((float) resolution.height / (float) resolution.width);
        int diffAreaMin = Integer.MAX_VALUE;
        int targetArea = resolution.width * resolution.height;

        Camera.Size bestRatioSize = null;
        Camera.Size bestAreaSize = null;

        for (Camera.Size size : mCamera.getParameters().getSupportedPreviewSizes()) 
		{
            if (size.equals(resolution)) 
			{
                return size;
            }

            float currRatio = size.width > size.height ? ((float) size.width / (float) size.height) :  ((float) size.height / (float) size.width);
            float diffRatio = Math.abs(currRatio - targetRatio);
            //Log.i(TAG, "Camera support preview:" + size.width + "x" + size.height + " diff ratio:" + diffRatio + " target:" + resolution.width + "x" + resolution.height);
            if (diffRatio < 0.01f)
            {
                int diffArea = Math.abs((size.width * size.height) - targetArea);
                if (diffArea < diffAreaMin)
                {
                    diffAreaMin = diffArea;
                    bestAreaSize = size;
                }
            }
            if (diffRatio < diffRatioMin)
			{
                diffRatioMin = diffRatio;
                bestRatioSize = size;
            }
        }
        return bestAreaSize != null ? bestAreaSize:bestRatioSize;
    }

    //若摄像头支持指定的宽高，则使用指定宽高，否则使用宽高比和面积接近的分辨率
    private Camera.Size mfAdaptPictureResolution(Camera.Size resolution)
    {
        float diffRatioMin = 100f;
        float targetRatio = resolution.width > resolution.height ? ((float) resolution.width / (float) resolution.height) :  ((float) resolution.height / (float) resolution.width);
        int diffAreaMin = Integer.MAX_VALUE;
        int targetArea = resolution.width * resolution.height;

        Camera.Size bestRatioSize = null;
        Camera.Size bestAreaSize = null;

        for (Camera.Size size : mCamera.getParameters().getSupportedPictureSizes())
        {
            if (size.equals(resolution))
            {
                return size;
            }

            float currRatio = size.width > size.height ? ((float) size.width / (float) size.height) :  ((float) size.height / (float) size.width);
            float diffRatio = Math.abs(currRatio - targetRatio);
            //Log.i(TAG, "Camera support picture:" + size.width + "x" + size.height + " diff ratio:" + diffRatio + " target:" + resolution.width + "x" + resolution.height);
            if (diffRatio < 0.01f)
            {
                int diffArea = Math.abs((size.width * size.height) - targetArea);
                if (diffArea < diffAreaMin)
                {
                    diffAreaMin = diffArea;
                    bestAreaSize = size;
                }
            }
            if (diffRatio < diffRatioMin)
            {
                diffRatioMin = diffRatio;
                bestRatioSize = size;
            }
        }
        return bestAreaSize != null ? bestAreaSize:bestRatioSize;
    }

    //若帧率支持，则直接使用该帧率，否则返回默认帧率
    private int[] mfAdaptFpsRange(int expectedFps, List<int[]> fpsRanges) 
	{
        expectedFps *= 1000;
        int[] closestRange = fpsRanges.get(0);
        int measure = Math.abs(closestRange[0] - expectedFps) + Math.abs(closestRange[1] - expectedFps);
        for (int[] range : fpsRanges) 
		{
            if (range[0] <= expectedFps && range[1] >= expectedFps) 
			{
                int curMeasure = Math.abs(range[0] - expectedFps) + Math.abs(range[1] - expectedFps);
                if (curMeasure < measure) 
				{
                    closestRange = range;
                    measure = curMeasure;
                }
            }
        }
        return closestRange;
    }

    //视情况决定是否进行丢帧处理
    private void mfCalcFrameDropInterval(int targetFps, int realFps) 
	{
        if (targetFps >= realFps) 
		{
            mFrameDropMode = false;
            mFrameInterval = 1;
        } 
		else 
		{
            double div = realFps / (double)targetFps;
            if (div >= 2.0) 
			{
                //隔几帧取一帧模式
                mFrameDropMode = false;
                mFrameInterval = (int)(div + 0.5);
            } 
			else 
			{
                //隔几帧丢一帧模式
                mFrameDropMode = true;
                mFrameInterval = (int)(realFps / (double)(realFps - targetFps) + 0.5);
            }
        }
    }


}
