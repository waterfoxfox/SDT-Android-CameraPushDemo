package com.sd;
//和C层一致的常量定义
public class Constant {

	// 用户类型
	public interface UserType {
		//USER_TYPE_OTHER						0  //其他类型
		//USER_TYPE_AV_SEND_RECV				1  //音视频收发类型
		//USER_TYPE_AV_RECV_ONLY				2  //仅接收音视频类型
		//USER_TYPE_AV_SEND_ONLY				3  //仅发送音视频类型
		final byte USER_TYPE_OTHER = 0;
		final byte USER_TYPE_AV_SEND_RECV = 1;
		final byte USER_TYPE_AV_RECV_ONLY = 2;
		final byte USER_TYPE_AV_SEND_ONLY = 3;		
	}
	
	// 底层状况通知
	public interface SystemStatusType {	
		final byte SYS_NOTIFY_EXIT_NORMAL = 0;			// 正常退出
		final byte SYS_NOTIFY_EXIT_ABNORMAL = 1;		// 异常退出 未知原因
		final byte SYS_NOTIFY_EXIT_LOSTCONNECT = 2;		// 底层网络原因与服务器断开
		final byte SYS_NOTIFY_EXIT_KICKED = 3;			// 用户被KICKED		
		final byte SYS_NOTIFY_ONLINE_SUCCESS = 4;		// 登录成功
		final byte SYS_NOTIFY_ONLINE_FAILED = 5;		// 登录失败
		final byte SYS_NOTIFY_RECON_START = 6;			// 客户端掉线，内部开始自动重连
		final byte SYS_NOTIFY_RECON_SUCCESS = 7;		// 内部自动重连成功
		final byte SYS_NOTIFY_ONPOSITION = 8;			// 某客户端开始发布	
		final byte SYS_NOTIFY_OFFPOSITION = 9;			// 某客户端停止发布
		final byte SYS_NOTIFY_AUTO_BITRATE_REQ = 10;	// 底层请求码率自适应
		final byte SYS_NOTIFY_IDR_REQ = 11;				// 底层请求IDR
	}	
	
	// 输出到日志文件的级别
	public interface LogLevel {
		final byte LOG_LEVEL_DEBUG = 1;
		final byte LOG_LEVEL_INFO = 2;
		final byte LOG_LEVEL_WARN = 3;
		final byte LOG_LEVEL_ERROR = 4;
		final byte LOG_LEVEL_ALARM = 5;
		final byte LOG_LEVEL_FATAL = 6;
		final byte LOG_LEVEL_NONE = 7;
	}

	//码率自适应调整策略
	public interface AutoBitrateMethod {
		final byte AUTO_BITRATE_ADJUST_DISABLE = 0;
		final byte AUTO_BITRATE_ADJUST_FRAME_FIRST = 1;
		final byte AUTO_BITRATE_ADJUST_BITRATE_FIRST = 2;
	}

	//FEC冗余度方法
	public interface FecRedunMethod {
		final byte FEC_REDUN_FIX = 0;			//使用固定的冗余度
		final byte FEC_REDUN_DYNAMIC = 1;		//以用户指定的冗余度为上限，根据网络情况进行调整
	}

	//编码器网络保护级别
	public interface VideoCodecProtectLevel {
		final byte CODEC_NET_PROTECT_LEVEL_MIN = 0;
		final byte CODEC_NET_PROTECT_LEVEL_DEF = 0;
		final byte CODEC_NET_PROTECT_LEVEL_MAX = 12;		
	}
	
	//音视频编解码类型
	public interface ClientAudioCodecType {
		final byte CLIENT_AUDIO_TYPE_AAC = 0;
		final byte CLIENT_AUDIO_TYPE_G711 = 1;
		final byte CLIENT_AUDIO_TYPE_OPUS = 2;
	}

	public interface ClientVideoCodecType {
		final byte CLIENT_VIDEO_TYPE_H264 = 0;
		final byte CLIENT_VIDEO_TYPE_HEVC = 1;
	}
	
}
