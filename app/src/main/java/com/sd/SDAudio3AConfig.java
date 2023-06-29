//***************************************************************************//
//* 版权所有  www.mediapro.cc
//*
//* 内容摘要：音频3A处理对外JAVA封装接口
//*	
//* 当前版本：V1.0		
//* 作    者：mediapro
//* 完成日期：2021-7-18
//**************************************************************************//
package com.sd;

import android.util.Log;


public class SDAudio3AConfig {
	
    final boolean mEnableSoft3A;
    final boolean mEnableAec;
    final boolean mEnableAgc;
    final boolean mEnableAns;
	final int mAecDelayMs;
	
    public SDAudio3AConfig(boolean enableSoft3A, boolean enableAec, boolean enableAgc, boolean enableAns, int aecDelayMs) {
        this.mEnableSoft3A = enableSoft3A;
        this.mEnableAec = enableAec;
        this.mEnableAgc = enableAgc;
        this.mEnableAns = enableAns;
		this.mAecDelayMs = aecDelayMs;
    }

    @Override
    public String toString() {
        return "SDAudio3AConfig{" +
                "mEnableSoft3A=" + mEnableSoft3A +
                ", mEnableAec=" + mEnableAec +
				", mAecDelayMs=" + mAecDelayMs +
                ", mEnableAgc=" + mEnableAgc +
                ", mEnableAns=" + mEnableAns + '}';
    }
}
