package com.tencent.trtcplugin;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;

import com.tencent.live.beauty.custom.ITXCustomBeautyProcesserFactory;
import com.tencent.live.beauty.custom.ITXCustomBeautyProcesser;
import static com.tencent.live.beauty.custom.TXCustomBeautyDef.TXCustomBeautyBufferType;
import static com.tencent.live.beauty.custom.TXCustomBeautyDef.TXCustomBeautyPixelFormat;
import static com.tencent.live.beauty.custom.TXCustomBeautyDef.TXCustomBeautyVideoFrame;

import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.tencent.liteav.audio.TXAudioEffectManager;
import com.tencent.liteav.beauty.TXBeautyManager;
import com.tencent.liteav.device.TXDeviceManager;
import com.tencent.liteav.basic.log.TXCLog;
import com.tencent.trtc.TRTCCloud;
import com.tencent.trtc.TRTCCloudDef;
import com.tencent.trtc.TRTCCloudListener;
import com.tencent.trtcplugin.listener.CustomTRTCCloudListener;
import com.tencent.trtcplugin.listener.ProcessVideoFrame;
import com.tencent.trtcplugin.util.CommonUtil;
import com.tencent.trtcplugin.view.TRTCCloudVideoPlatformView;
import com.tencent.trtcplugin.view.TRTCCloudVideoSurfaceView;
import com.tencent.trtcplugin.view.CustomRenderVideoFrame;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.platform.PlatformViewRegistry;

import io.flutter.view.TextureRegistry;

/**
 * ???????????????-?????????????????????????????????????????????
 */
public class TRTCCloudPlugin implements FlutterPlugin, MethodCallHandler {
    /**
     * methodChannel??????
     */
    private static final String CHANNEL_SIGN = "trtcCloudChannel";
    private static final String TAG = "TRTCCloudFlutter";
    private FlutterPlugin.FlutterAssets flutterAssets;
    private TRTCCloud trtcCloud;
    private Context trtcContext;
    private TXDeviceManager txDeviceManager;
    private TXBeautyManager txBeautyManager;
    private TXAudioEffectManager txAudioEffectManager;
    private CustomTRTCCloudListener trtcListener;
    // ??????????????? ??????????????????
    private static ITXCustomBeautyProcesserFactory sProcesserFactory;
    private ITXCustomBeautyProcesser    mCustomBeautyProcesser;

    private TextureRegistry textureRegistry;
    private Map<String, TextureRegistry.SurfaceTextureEntry> surfaceMap = new HashMap<>();
    private Map<String, CustomRenderVideoFrame> renderMap = new HashMap<>();
    private SurfaceTexture localSufaceTexture;
    private CustomRenderVideoFrame localCustomRender;
    private PlatformViewRegistry platformRegistry;
    private BinaryMessenger trtcMessenger;

    public TRTCCloudPlugin() {
    }

    private TRTCCloudPlugin(
            BinaryMessenger messenger,
            Context context,
            MethodChannel channel,
            PlatformViewRegistry registry,
            FlutterPlugin.FlutterAssets flutterAssets, TextureRegistry textureRegistrya) {
        this.trtcContext = context;
        this.flutterAssets = flutterAssets;
        this.trtcListener = new CustomTRTCCloudListener(channel);
        this.platformRegistry = registry;
        this.trtcMessenger = messenger;
        this.textureRegistry = textureRegistrya;
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        final MethodChannel channel = new MethodChannel(
                flutterPluginBinding.getBinaryMessenger(),
                CHANNEL_SIGN);
        channel.setMethodCallHandler(new TRTCCloudPlugin(
                flutterPluginBinding.getBinaryMessenger(),
                flutterPluginBinding.getApplicationContext(),
                channel,
                flutterPluginBinding.getPlatformViewRegistry(),
                flutterPluginBinding.getFlutterAssets(), flutterPluginBinding.getTextureRegistry()));

    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    }

    public static void registerWith(Registrar registrar) {
        if (registrar.activity() == null) {
            return;
        }
        final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL_SIGN);
        channel.setMethodCallHandler(new TRTCCloudPlugin());

    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        TXCLog.i(TAG, "|method=" + call.method + "|arguments=" + call.arguments);
        try {
            Method method = TRTCCloudPlugin.class.getDeclaredMethod(call.method,MethodCall.class,Result.class);
            method.invoke(this,call,result);
        } catch (NoSuchMethodException e) {
            TXCLog.e(TAG, "|method=" + call.method + "|arguments=" + call.arguments + "|error=" + e);
        } catch (IllegalAccessException e) {
            TXCLog.e(TAG, "|method=" + call.method + "|arguments=" + call.arguments + "|error=" + e);
        } catch (Exception e) {
            TXCLog.e(TAG, "|method=" + call.method + "|arguments=" + call.arguments + "|error=" + e);
        }
    }

    /**
     * ?????? TRTCCloud ??????
     */
    private void sharedInstance(MethodCall call, Result result) {
        // ???????????????
        trtcCloud = TRTCCloud.sharedInstance(trtcContext);
        platformRegistry.registerViewFactory(
                TRTCCloudVideoPlatformView.SIGN,
                new TRTCCloudVideoPlatformView(trtcContext, trtcMessenger));
        platformRegistry.registerViewFactory(
                TRTCCloudVideoSurfaceView.SIGN,
                new TRTCCloudVideoSurfaceView(trtcContext, trtcMessenger));
        trtcCloud.setListener(trtcListener);
        result.success(null);
    }

    /**
     * ?????? TRTCCloud ??????
     */
    private void destroySharedInstance(MethodCall call, Result result) {
        TRTCCloud.destroySharedInstance();
        trtcCloud = null;
        surfaceMap.clear();
        renderMap.clear();
        localCustomRender = null;
        localSufaceTexture = null;
        result.success(null);
    }

    /**
     * ????????????
     */
    private void enterRoom(MethodCall call, Result result) {
        //???????????????2147483647????????????????????????????????????
        //TRTCCloudDef.TRTCParams trtcP = new Gson().fromJson(param, TRTCCloudDef.TRTCParams.class);
        TRTCCloudDef.TRTCParams trtcP = new TRTCCloudDef.TRTCParams();
        trtcP.sdkAppId = CommonUtil.getParam(call, result, "sdkAppId");
        trtcP.userId = CommonUtil.getParam(call, result, "userId");
        trtcP.userSig = CommonUtil.getParam(call, result, "userSig");
        String roomId = CommonUtil.getParam(call, result, "roomId");
        trtcP.roomId = (int) (Long.parseLong(roomId) & 0xFFFFFFFF);
        trtcP.strRoomId = CommonUtil.getParam(call, result, "strRoomId");
        trtcP.role = CommonUtil.getParam(call, result, "role");
        trtcP.streamId = CommonUtil.getParam(call, result, "streamId");
        trtcP.userDefineRecordId = CommonUtil.getParam(call, result, "userDefineRecordId");
        trtcP.privateMapKey = CommonUtil.getParam(call, result, "privateMapKey");
        trtcP.businessInfo = CommonUtil.getParam(call, result, "businessInfo");

        int scene = CommonUtil.getParam(call, result, "scene");
        trtcCloud.callExperimentalAPI("{\"api\": \"setFramework\", \"params\": {\"framework\": 7}}");
        trtcCloud.enterRoom(trtcP, scene);
        result.success(null);
    }
    
    /**
     * ????????????
     */
    private void exitRoom(MethodCall call, Result result) {
        trtcCloud.exitRoom();
        surfaceMap.clear();
        renderMap.clear();
        localCustomRender = null;
        localSufaceTexture = null;
        result.success(null);
    }

    /**
     * ????????????
     */
    private void connectOtherRoom(MethodCall call, Result result) {
        String param = CommonUtil.getParam(call, result, "param");
        trtcCloud.ConnectOtherRoom(param);
        result.success(null);
    }

    /**
     * ??????????????????
     */
    private void disconnectOtherRoom(MethodCall call, Result result) {
        trtcCloud.DisconnectOtherRoom();
        result.success(null);
    }

    /**
     * ??????????????????????????????????????????TRTC_APP_SCENE_LIVE ??? TRTC_APP_SCENE_VOICE_CHATROOM???
     */
    private void switchRole(MethodCall call, Result result) {
        int role = CommonUtil.getParam(call, result, "role");
        trtcCloud.switchRole(role);
        result.success(null);
    }

    /**
     * ????????????????????????????????????????????????????????????????????????
     */
    private void setDefaultStreamRecvMode(MethodCall call, Result result) {
        boolean autoRecvAudio = CommonUtil.getParam(call, result, "autoRecvAudio");
        boolean autoRecvVideo = CommonUtil.getParam(call, result, "autoRecvVideo");
        trtcCloud.setDefaultStreamRecvMode(autoRecvAudio, autoRecvVideo);
        result.success(null);
    }

    /**
     * ????????????
     */
    private void switchRoom(MethodCall call, Result result) {
        String config = CommonUtil.getParam(call, result, "config");
        trtcCloud.switchRoom(new Gson().fromJson(config, TRTCCloudDef.TRTCSwitchRoomConfig.class));
        result.success(null);
    }

    /**
     * ??????????????????????????? CDN ??????
     */
    private void startPublishing(MethodCall call, Result result) {
        String streamId = CommonUtil.getParam(call, result, "streamId");
        int streamType = CommonUtil.getParam(call, result, "streamType");
        trtcCloud.startPublishing(streamId, streamType);
        result.success(null);
    }

    /**
     * ??????????????????????????? CDN ??????
     */
    private void stopPublishing(MethodCall call, Result result) {
        trtcCloud.stopPublishing();
        result.success(null);
    }

    /**
     * ??????????????????????????? CDN ??????
     */
    private void startPublishCDNStream(MethodCall call, Result result) {
        String param = CommonUtil.getParam(call, result, "param");
        trtcCloud.startPublishCDNStream(new Gson().fromJson(param, TRTCCloudDef.TRTCPublishCDNParam.class));
        result.success(null);
    }

    /**
     * ?????????????????????????????????
     */
    private void stopPublishCDNStream(MethodCall call, Result result) {
        trtcCloud.stopPublishCDNStream();
        result.success(null);
    }

    /**
     * ?????????????????????????????????
     */
    private void setMixTranscodingConfig(MethodCall call, Result result) {
        String config = CommonUtil.getParam(call, result, "config");
        if(config == "null") {
            trtcCloud.setMixTranscodingConfig(null);
        } else {
            trtcCloud.setMixTranscodingConfig(new Gson().fromJson(config, TRTCCloudDef.TRTCTranscodingConfig.class));
        }
        result.success(null);
    }

    /**
     * ?????????????????????????????????
     */
    private void stopLocalPreview(MethodCall call, Result result) {
        trtcCloud.stopLocalPreview();
        result.success(null);
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????
     */
    private void stopRemoteView(MethodCall call, Result result) {
        String userId = CommonUtil.getParam(call, result, "userId");
        int streamType = CommonUtil.getParam(call, result, "streamType");
        trtcCloud.stopRemoteView(userId, streamType);
        result.success(null);
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????????????????
     */
    private void stopAllRemoteView(MethodCall call, Result result) {
        trtcCloud.stopAllRemoteView();
        result.success(null);
    }

    /**
     * ??????/??????????????????????????????????????????
     */
    private void muteRemoteAudio(MethodCall call, Result result) {
        String userId = CommonUtil.getParam(call, result, "userId");
        boolean mute = CommonUtil.getParam(call, result, "mute");
        trtcCloud.muteRemoteAudio(userId, mute);
        result.success(null);
    }

    /**
     * ??????/?????????????????????????????????
     */
    private void muteAllRemoteAudio(MethodCall call, Result result) {
        boolean mute = CommonUtil.getParam(call, result, "mute");
        trtcCloud.muteAllRemoteAudio(mute);
        result.success(null);
    }

    /**
     * ???????????????????????????????????????
     */
    private void setRemoteAudioVolume(MethodCall call, Result result) {
        String userId = CommonUtil.getParam(call, result, "userId");
        int volume = CommonUtil.getParam(call, result, "volume");
        trtcCloud.setRemoteAudioVolume(userId, volume);
        result.success(null);
    }

    /**
     * ?????? SDK ???????????????
     */
    private void setAudioCaptureVolume(MethodCall call, Result result) {
        int volume = CommonUtil.getParam(call, result, "volume");
        trtcCloud.setAudioCaptureVolume(volume);
        result.success(null);
    }

    /**
     * ?????? SDK ???????????????
     */
    private void getAudioCaptureVolume(MethodCall call, Result result) {
        result.success(trtcCloud.getAudioCaptureVolume());
    }

    /**
     * ?????? SDK ???????????????
     */
    private void setAudioPlayoutVolume(MethodCall call, Result result) {
        int volume = CommonUtil.getParam(call, result, "volume");
        trtcCloud.setAudioPlayoutVolume(volume);
        result.success(null);
    }

    /**
     * ?????? SDK ???????????????
     */
    private void getAudioPlayoutVolume(MethodCall call, Result result) {
        result.success(trtcCloud.getAudioPlayoutVolume());
    }

    /**
     * ????????????????????????????????????
     */
    private void startLocalAudio(MethodCall call, Result result) {
        int quality = CommonUtil.getParam(call, result, "quality");
        trtcCloud.startLocalAudio(quality);
        result.success(null);
    }

    /**
     * ????????????????????????????????????
     */
    private void stopLocalAudio(MethodCall call, Result result) {
        trtcCloud.stopLocalAudio();
        result.success(null);
    }

    /**
     * ??????/????????????????????????????????????
     */
    private void muteRemoteVideoStream(MethodCall call, Result result) {
        String userId = CommonUtil.getParam(call, result, "userId");
        boolean mute = CommonUtil.getParam(call, result, "mute");
        trtcCloud.muteRemoteVideoStream(userId, mute);
        result.success(null);
    }

    /**
     * ??????/?????????????????????????????????
     */
    private void muteAllRemoteVideoStreams(MethodCall call, Result result) {
        boolean mute = CommonUtil.getParam(call, result, "mute");
        trtcCloud.muteAllRemoteVideoStreams(mute);
        result.success(null);
    }

    /**
     * ?????????????????????????????????
     * ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private void setVideoEncoderParam(MethodCall call, Result result) {
        String param = CommonUtil.getParam(call, result, "param");
        trtcCloud.setVideoEncoderParam(new Gson().fromJson(param, TRTCCloudDef.TRTCVideoEncParam.class));
        result.success(null);
    }

    public static void register(ITXCustomBeautyProcesserFactory processerFactory) {
        sProcesserFactory = processerFactory;
    }

    public static ITXCustomBeautyProcesserFactory getBeautyProcesserFactory() {
        return sProcesserFactory;
    }

    private static int convertTRTCPixelFormat(TXCustomBeautyPixelFormat format) {
        switch (format) {
            case TXCustomBeautyPixelFormatUnknown:
                return TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_UNKNOWN;
            case TXCustomBeautyPixelFormatI420:
                return TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_I420;
            case TXCustomBeautyPixelFormatTexture2D:
                return TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D;
            default:
                return TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_UNKNOWN;
        }
    }

    private static int convertTRTCBufferType(TXCustomBeautyBufferType type) {
        switch (type) {
            case TXCustomBeautyBufferTypeUnknown:
                return TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_UNKNOWN;
            case TXCustomBeautyBufferTypeByteBuffer:
                return TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_BUFFER;
            case TXCustomBeautyBufferTypeByteArray:
                return TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_BYTE_ARRAY;
            case TXCustomBeautyBufferTypeTexture:
                return TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE;
            default:
                return TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_UNKNOWN;
        }
    }

    /**
     * ??????/??????????????????????????????
     * enable true: ??????; false: ????????????????????????: false
     *
     * @return ?????????
     */
    public void enableCustomVideoProcess(MethodCall call, MethodChannel.Result result) {
        boolean enable = CommonUtil.getParam(call, result, "enable");
        ITXCustomBeautyProcesserFactory processerFactory = TRTCCloudPlugin.getBeautyProcesserFactory();
        mCustomBeautyProcesser = processerFactory.createCustomBeautyProcesser();
        TXCustomBeautyBufferType bufferType = mCustomBeautyProcesser.getSupportedBufferType();
        TXCustomBeautyPixelFormat pixelFormat = mCustomBeautyProcesser.getSupportedPixelFormat();
        if(enable) {
            ProcessVideoFrame processVideo =  new ProcessVideoFrame(mCustomBeautyProcesser);
            int ret = trtcCloud.setLocalVideoProcessListener(convertTRTCPixelFormat(pixelFormat), convertTRTCBufferType(bufferType), processVideo);
            result.success(ret);
        } else {
            int ret = trtcCloud.setLocalVideoProcessListener(convertTRTCPixelFormat(pixelFormat), convertTRTCBufferType(bufferType), null);
            // processerFactory.destroyCustomBeautyProcesser();
            mCustomBeautyProcesser = null;
            result.success(ret);
        }
    }

    /**
     * ?????????????????????????????????
     * ??????????????? SDK ???????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private void setNetworkQosParam(MethodCall call, Result result) {
        String param = CommonUtil.getParam(call, result, "param");
        trtcCloud.setNetworkQosParam(new Gson().fromJson(param, TRTCCloudDef.TRTCNetworkQosParam.class));
        result.success(null);
    }

    /**
     * ????????????????????????????????????
     */
    private void setLocalRenderParams(MethodCall call, Result result) {
        String param = CommonUtil.getParam(call, result, "param");
        trtcCloud.setLocalRenderParams(new Gson().fromJson(param, TRTCCloudDef.TRTCRenderParams.class));
        result.success(null);
    }

    /**
     * ????????????????????????????????????
     */
    private void setRemoteRenderParams(MethodCall call, Result result) {
        String userId = CommonUtil.getParam(call, result, "userId");
        int streamType = CommonUtil.getParam(call, result, "streamType");
        String param = CommonUtil.getParam(call, result, "param");
        trtcCloud.setRemoteRenderParams(
                userId,
                streamType,
                new Gson().fromJson(param, TRTCCloudDef.TRTCRenderParams.class));
        result.success(null);
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private void setVideoEncoderRotation(MethodCall call, Result result) {
        int rotation = CommonUtil.getParam(call, result, "rotation");
        trtcCloud.setVideoEncoderRotation(rotation);
        result.success(null);
    }

    /**
     * ?????????????????????????????????????????????
     */
    private void setVideoEncoderMirror(MethodCall call, Result result) {
        boolean mirror = CommonUtil.getParam(call, result, "mirror");
        trtcCloud.setVideoEncoderMirror(mirror);
        result.success(null);
    }

    /**
     * ????????????????????????????????????
     */
    private void setGSensorMode(MethodCall call, Result result) {
        int mode = CommonUtil.getParam(call, result, "mode");
        trtcCloud.setGSensorMode(mode);
        result.success(null);
    }

    /**
     * ???????????????????????????????????????
     */
    private void enableEncSmallVideoStream(MethodCall call, Result result) {
        boolean enable = CommonUtil.getParam(call, result, "enable");
        String smallVideoEncParam = CommonUtil.getParam(call, result, "smallVideoEncParam");
        int value = trtcCloud.enableEncSmallVideoStream(
                enable,
                new Gson().fromJson(smallVideoEncParam, TRTCCloudDef.TRTCVideoEncParam.class));
        result.success(value);
    }

    /**
     * ?????????????????? uid ???????????????????????????
     */
    private void setRemoteVideoStreamType(MethodCall call, Result result) {
        String userId = CommonUtil.getParam(call, result, "userId");
        int streamType = CommonUtil.getParam(call, result, "streamType");
        int value = trtcCloud.setRemoteVideoStreamType(userId, streamType);
        result.success(value);
    }

//    private Integer getBitmapPixelDataMemoryPtr(JNIEnv *env, jclass clazz, jobject bitmap) {
//        AndroidBitmapInfo bitmapInfo;
//        int ret;
//        if ((ret = AndroidBitmap_getInfo(env, bitmap, &bitmapInfo)) < 0) {
//            LOGE("AndroidBitmap_getInfo() failed ! error=%d", ret);
//            return 0;
//        }
//        // ?????? bitmap ????????????????????? native ????????????
//        void *addPtr;
//        if ((ret = AndroidBitmap_lockPixels(env, bitmap, &addPtr)) < 0) {
//            LOGE("AndroidBitmap_lockPixels() failed ! error=%d", ret);
//            return 0;
//        }
//        //unlock???????????????????????????????????????bitmap?????????
//        AndroidBitmap_unlockPixels(env, bitmap);
//        return (jlong)addPtr;
//    }

    /**
     * ??????????????????
     */
    private void snapshotVideo(MethodCall call, final Result result) {
        String userId = CommonUtil.getParamCanBeNull(call, result, "userId");
        int streamType = CommonUtil.getParam(call, result, "streamType");
        final String path = CommonUtil.getParam(call, result, "path");

        trtcCloud.snapshotVideo(userId, streamType, new TRTCCloudListener.TRTCSnapshotListener() {
            @Override
            public void onSnapshotComplete(Bitmap bitmap) {
                try {
                    String[] pathArr = path.split("\\.");
                    Bitmap.CompressFormat bitComp = Bitmap.CompressFormat.PNG;
                    if (pathArr[pathArr.length - 1].equals("jpg")) {
                        bitComp = Bitmap.CompressFormat.JPEG;
                    } else if (pathArr[pathArr.length - 1].equals("webp")) {
                        bitComp = Bitmap.CompressFormat.WEBP;
                    }
                    FileOutputStream fos = new FileOutputStream(path);
                    boolean isSuccess = bitmap.compress(bitComp, 100, fos);
                    if (isSuccess) {
                        trtcListener.onSnapshotComplete(0, "success", path);
                    } else {
                        trtcListener.onSnapshotComplete(-101,"bitmap compress failed", null);
                    }

                } catch (FileNotFoundException e) {
                    TXCLog.e(TAG,"|method=snapshotVideo|error=" + e);
                    trtcListener.onSnapshotComplete(-102,e.toString(), null);
                } catch (Exception e) {
                    TXCLog.e(TAG,"|method=snapshotVideo|error=" + e);
                    trtcListener.onSnapshotComplete(-103,e.toString(), null);
                }
            }
        });

        result.success(null);
    }

    // ??????????????????????????????????????????
    private void setLocalVideoRenderListener(MethodCall call, final Result result) {
        boolean isFront = CommonUtil.getParam(call, result, "isFront");
        trtcCloud.startLocalPreview(isFront, null);
        TextureRegistry.SurfaceTextureEntry surfaceEntry = textureRegistry.createSurfaceTexture();
        SurfaceTexture surfaceTexture = surfaceEntry.surfaceTexture();
        String userId = CommonUtil.getParam(call, result, "userId");
        int streamType = CommonUtil.getParam(call, result, "streamType");
        int width = CommonUtil.getParam(call, result, "width");
        int height = CommonUtil.getParam(call, result, "height");
        surfaceTexture.setDefaultBufferSize(width, height);
        CustomRenderVideoFrame customRender =  new CustomRenderVideoFrame(userId, streamType);
        int pixelFormat = TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D;
        int bufferType = TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE;
        trtcCloud.setLocalVideoRenderListener(pixelFormat, bufferType, customRender);
        customRender.start(surfaceTexture, width, height);
        surfaceMap.put(Long.toString(surfaceEntry.id()), surfaceEntry);
        renderMap.put(Long.toString(surfaceEntry.id()), customRender);
        localSufaceTexture = surfaceTexture;
        localCustomRender = customRender;
        result.success(surfaceEntry.id());
    }

    private void updateLocalVideoRender(MethodCall call, final Result result) {
        int width = CommonUtil.getParam(call, result, "width");
        int height = CommonUtil.getParam(call, result, "height");
        localSufaceTexture.setDefaultBufferSize(width, height);
        localCustomRender.updateSize(width, height);
        result.success(null);
    }

    // ??????????????????
    private void startLocalPreview(MethodCall call, final Result result) {
        boolean isFront = CommonUtil.getParam(call, result, "isFront");
        trtcCloud.startLocalPreview(isFront, null);
        result.success(null);
    }

    // ????????????????????????
    private void startRemoteView(MethodCall call, final Result result) {
        String userId = CommonUtil.getParam(call, result, "userId");
        int streamType = CommonUtil.getParam(call, result, "streamType");
        trtcCloud.startRemoteView(userId, streamType, null);
        result.success(null);
    }

    // ??????????????????????????????????????????
    private void setRemoteVideoRenderListener(MethodCall call, final Result result) {
        String userId = CommonUtil.getParam(call, result, "userId");
        int streamType = CommonUtil.getParam(call, result, "streamType");
        int width = CommonUtil.getParam(call, result, "width");
        int height = CommonUtil.getParam(call, result, "height");
        trtcCloud.startRemoteView(userId, streamType, null);
        TextureRegistry.SurfaceTextureEntry surfaceEntry = textureRegistry.createSurfaceTexture();
        SurfaceTexture surfaceTexture = surfaceEntry.surfaceTexture();
        surfaceTexture.setDefaultBufferSize(width, height);
        CustomRenderVideoFrame customRender =  new CustomRenderVideoFrame(userId, streamType);
        int pixelFormat = TRTCCloudDef.TRTC_VIDEO_PIXEL_FORMAT_Texture_2D;
        int bufferType = TRTCCloudDef.TRTC_VIDEO_BUFFER_TYPE_TEXTURE;
        trtcCloud.setRemoteVideoRenderListener(userId, pixelFormat, bufferType, customRender);
        customRender.start(surfaceTexture, width, height);
        surfaceMap.put(Long.toString(surfaceEntry.id()), surfaceEntry);
        renderMap.put(Long.toString(surfaceEntry.id()), customRender);
        result.success(surfaceEntry.id());
    }

    private void updateRemoteVideoRender(MethodCall call, final Result result) {
        int width = CommonUtil.getParam(call, result, "width");
        int height = CommonUtil.getParam(call, result, "height");
        int textureID = CommonUtil.getParam(call, result, "textureID");
        TextureRegistry.SurfaceTextureEntry surfaceEntry = surfaceMap.get(String.valueOf(textureID));
        CustomRenderVideoFrame surfaceRender = renderMap.get(String.valueOf(textureID));
        localSufaceTexture.setDefaultBufferSize(width, height);
        if (surfaceEntry != null) {
            surfaceEntry.surfaceTexture().setDefaultBufferSize(width, height);
        }
        if (surfaceRender != null) {
            surfaceRender.updateSize(width, height);
        }
        result.success(null);
    }

    private void unregisterTexture(MethodCall call, final Result result) {
        int textureID = CommonUtil.getParam(call, result, "textureID");
        TextureRegistry.SurfaceTextureEntry surfaceEntry = surfaceMap.get(String.valueOf(textureID));
        CustomRenderVideoFrame surfaceRender = renderMap.get(String.valueOf(textureID));
        if (surfaceEntry != null) {
            surfaceEntry.release();
            surfaceMap.remove(String.valueOf(textureID));
        }
        if (surfaceRender != null) {
            surfaceRender.stop();
            renderMap.remove(String.valueOf(textureID));
        }
        result.success(null);
    }

    /**
     * ??????/???????????????????????????
     */
    private void muteLocalAudio(MethodCall call, Result result) {
        boolean mute = CommonUtil.getParam(call, result, "mute");
        trtcCloud.muteLocalAudio(mute);
        result.success(null);
    }

    /**
     * ??????/?????????????????????????????????
     */
    private void muteLocalVideo(MethodCall call, Result result) {
        boolean mute = CommonUtil.getParam(call, result, "mute");
        trtcCloud.muteLocalVideo(mute);
        result.success(null);
    }

    /**
     * ???????????????????????????????????????????????????
     */
    private void setVideoMuteImage(MethodCall call, final Result result) {
        String type = CommonUtil.getParam(call, result, "type");
        final String imageUrl = CommonUtil.getParamCanBeNull(call, result, "imageUrl");
        final int fps = CommonUtil.getParam(call, result, "fps");
        if (imageUrl == null) {
            trtcCloud.setVideoMuteImage(null, fps);
        } else {
            if (type.equals("network")) {
                new Thread() {
                    @Override
                    public void run() {
                        try {
                            URL url = new URL(imageUrl);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            connection.setDoInput(true);
                            connection.connect();
                            InputStream input = connection.getInputStream();
                            Bitmap myBitmap = BitmapFactory.decodeStream(input);
                            trtcCloud.setVideoMuteImage(myBitmap, fps);
                        } catch (IOException e) {
                            TXCLog.e(TAG, "|method=setVideoMuteImage|error=" + e);
                        }
                    }
                }.start();
            } else {
                try {
                    String path = flutterAssets.getAssetFilePathByName(imageUrl);
                    AssetManager mAssetManger = trtcContext.getAssets();
                    InputStream mystream = mAssetManger.open(path);
                    Bitmap myBitmap = BitmapFactory.decodeStream(mystream);
                    trtcCloud.setVideoMuteImage(myBitmap, fps);
                } catch (Exception e) {
                    TXCLog.e(TAG, "|method=setVideoMuteImage|error=" + e);
                }
            }
        }
        result.success(null);
    }

    /**
     * ?????????????????????
     */
    private void setAudioRoute(MethodCall call, Result result) {
        int route = CommonUtil.getParam(call, result, "route");
        trtcCloud.setAudioRoute(route);
        result.success(null);
    }

    /**
     * ???????????????????????????
     */
    private void enableAudioVolumeEvaluation(MethodCall call, Result result) {
        int intervalMs = CommonUtil.getParam(call, result, "intervalMs");
        trtcCloud.enableAudioVolumeEvaluation(intervalMs);
        result.success(null);
    }

    /**
     * ???????????????
     */
    private void startAudioRecording(MethodCall call, Result result) {
        String param = CommonUtil.getParam(call, result, "param");
        int value = trtcCloud.startAudioRecording(
                new Gson().fromJson(param, TRTCCloudDef.TRTCAudioRecordingParams.class));
        result.success(value);
    }

    /**
     * ???????????????
     */
    private void stopAudioRecording(MethodCall call, Result result) {
        trtcCloud.stopAudioRecording();
        result.success(null);
    }

    /**
     * ???????????????????????????
     */
    private void startLocalRecording(MethodCall call, Result result) {
        String param = CommonUtil.getParam(call, result, "param");
       trtcCloud.startLocalRecording(
                new Gson().fromJson(param, TRTCCloudDef.TRTCLocalRecordingParams.class));
        result.success(null);
    }

    /**
     * ???????????????
     */
    private void stopLocalRecording(MethodCall call, Result result) {
        trtcCloud.stopLocalRecording();
        result.success(null);
    }

    /**
     * ?????????????????????????????????????????????
     */
    private void setSystemVolumeType(MethodCall call, Result result) {
        int type = CommonUtil.getParam(call, result, "type");
        trtcCloud.setSystemVolumeType(type);
        result.success(null);
    }

    /**
     * ??????????????????????????????
     */
    private void isFrontCamera(MethodCall call, Result result) {
        result.success(txDeviceManager.isFrontCamera());
    }

    /**
     * ??????????????????
     */
    private void switchCamera(MethodCall call, Result result) {
        boolean isFrontCamera = CommonUtil.getParam(call, result, "isFrontCamera");
        result.success(txDeviceManager.switchCamera(isFrontCamera));
    }

    /**
     * ?????????????????????????????????
     */
    private void getCameraZoomMaxRatio(MethodCall call, Result result) {
        result.success(txDeviceManager.getCameraZoomMaxRatio());
    }

    /**
     * ??????????????????????????????????????????
     */
    private void setCameraZoomRatio(MethodCall call, Result result) {
        String value = CommonUtil.getParam(call, result, "value");
        float ratioValue = Float.parseFloat(value);
        result.success(txDeviceManager.setCameraZoomRatio(ratioValue));
    }

    /**
     * ????????????????????????????????????
     */
    private void enableCameraAutoFocus(MethodCall call, Result result) {
        boolean enable = CommonUtil.getParam(call, result, "enable");
        result.success(txDeviceManager.enableCameraAutoFocus(enable));
    }

    /**
     * ?????????????????????????????????????????????
     */
    private void isAutoFocusEnabled(MethodCall call, Result result) {
        result.success(txDeviceManager.isAutoFocusEnabled());
    }

    /**
     * ???????????????
     */
    private void enableCameraTorch(MethodCall call, Result result) {
        boolean enable = CommonUtil.getParam(call, result, "enable");
        result.success(txDeviceManager.enableCameraTorch(enable));
    }

    /**
     * ????????????????????????
     */
    private void setCameraFocusPosition(MethodCall call, Result result) {
        int x = CommonUtil.getParam(call, result, "x");
        int y = CommonUtil.getParam(call, result, "y");
        txDeviceManager.setCameraFocusPosition(x, y);
        result.success(null);
    }

    //????????????????????????
    private void getDeviceManager(MethodCall call, Result result) {
        txDeviceManager = trtcCloud.getDeviceManager();
    }

    //????????????????????????
    private void getBeautyManager(MethodCall call, Result result) {
        txBeautyManager = trtcCloud.getBeautyManager();
    }

    //????????????????????? TXAudioEffectManager
    private void getAudioEffectManager(MethodCall call, Result result) {
        txAudioEffectManager = trtcCloud.getAudioEffectManager();
    }

    //??????????????????
    private void startScreenCapture(MethodCall call, Result result) {
        int streamType = CommonUtil.getParam(call, result, "streamType");
        String encParams = CommonUtil.getParam(call, result, "encParams");
        trtcCloud.startScreenCapture(streamType, new Gson().fromJson(encParams, TRTCCloudDef.TRTCVideoEncParam.class),null);
        result.success(null);
    }

    //??????????????????
    private void stopScreenCapture(MethodCall call, Result result) {
        trtcCloud.stopScreenCapture();
        result.success(null);
    }

    //??????????????????
    private void pauseScreenCapture(MethodCall call, Result result) {
        trtcCloud.pauseScreenCapture();
        result.success(null);
    }

    //??????????????????
    private void resumeScreenCapture(MethodCall call, Result result) {
        trtcCloud.resumeScreenCapture();
        result.success(null);
    }

    /**
     * ????????????
     */
    private void setWatermark(MethodCall call, Result result) {
        final String imageUrl = CommonUtil.getParam(call, result, "imageUrl");
        String type = CommonUtil.getParam(call, result, "type");
        final int streamType = CommonUtil.getParam(call, result, "streamType");
        String xStr = CommonUtil.getParam(call, result, "x");
        final float x = Float.parseFloat(xStr);
        String yStr = CommonUtil.getParam(call, result, "y");
        final float y = Float.parseFloat(yStr);
        String widthStr = CommonUtil.getParam(call, result, "width");
        final float width = Float.parseFloat(widthStr);
        if (type.equals("network")) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        URL url = new URL(imageUrl);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setDoInput(true);
                        connection.connect();
                        InputStream input = connection.getInputStream();
                        Bitmap myBitmap = BitmapFactory.decodeStream(input);
                        trtcCloud.setWatermark(myBitmap, streamType, x, y, width);
                    } catch (IOException e) {
                        TXCLog.e(TAG,"|method=setWatermark|error=" + e);
                    }
                }
            }.start();
        } else {
            try {
                Bitmap myBitmap;
                //???????????????sdcard??????
                if (imageUrl.startsWith("/")) {
                    myBitmap = BitmapFactory.decodeFile(imageUrl);
                } else {
                    String path = flutterAssets.getAssetFilePathByName(imageUrl);
                    AssetManager mAssetManger = trtcContext.getAssets();
                    InputStream mystream = mAssetManger.open(path);
                    myBitmap = BitmapFactory.decodeStream(mystream);
                }
                trtcCloud.setWatermark(myBitmap, streamType, x, y, width);
            } catch (Exception e) {
                TXCLog.e(TAG,"|method=setWatermark|error=" + e);
            }
        }
        result.success(null);
    }

    /**
     * ?????????????????????????????????????????????
     */
    private void sendCustomCmdMsg(MethodCall call, Result result) {
        int cmdID = CommonUtil.getParam(call, result, "cmdID");
        String data = CommonUtil.getParam(call, result, "data");
        boolean reliable = CommonUtil.getParam(call, result, "reliable");
        boolean ordered = CommonUtil.getParam(call, result, "ordered");
        boolean value = trtcCloud.sendCustomCmdMsg(cmdID, data.getBytes(), reliable, ordered);
        result.success(value);
    }

    /**
     * ???????????????????????????????????????????????????
     */
    private void sendSEIMsg(MethodCall call, Result result) {
        String data = CommonUtil.getParam(call, result, "data");
        int repeatCount = CommonUtil.getParam(call, result, "repeatCount");
        boolean value = trtcCloud.sendSEIMsg(data.getBytes(), repeatCount);
        result.success(value);
    }

    /**
     * ???????????????????????????????????????????????????????????????????????????????????????
     */
    private void startSpeedTest(MethodCall call, Result result) {
        int sdkAppId = CommonUtil.getParam(call, result, "sdkAppId");
        String userId = CommonUtil.getParam(call, result, "userId");
        String userSig = CommonUtil.getParam(call, result, "userSig");
        trtcCloud.startSpeedTest(sdkAppId,userId,userSig);
        result.success(null);
    }

    /**
     * ?????????????????????
     */
    private void stopSpeedTest(MethodCall call, Result result) {
        trtcCloud.stopSpeedTest();
        result.success(null);
    }

    /**
     * ?????? SDK ????????????
     */
    private void getSDKVersion(MethodCall call, Result result) {
        result.success(trtcCloud.getSDKVersion());
    }

    /**
     * ?????? Log ????????????
     */
    private void setLogLevel(MethodCall call, Result result) {
        int level = CommonUtil.getParam(call, result, "level");
        trtcCloud.setLogLevel(level);
        result.success(null);
    }

    /**
     * ????????????????????????????????????
     */
    private void setConsoleEnabled(MethodCall call, Result result) {
        boolean enabled = CommonUtil.getParam(call, result, "enabled");
        TRTCCloud.setConsoleEnabled(enabled);
        result.success(null);
    }

    /**
     * ????????????????????????
     */
    private void setLogDirPath(MethodCall call, Result result) {
        String path = CommonUtil.getParam(call, result, "path");
        TRTCCloud.setLogDirPath(path);
        result.success(null);
    }

    /**
     * ??????????????? Log ??????????????????
     */
    private void setLogCompressEnabled(MethodCall call, Result result) {
        boolean enabled = CommonUtil.getParam(call, result, "enabled");
        TRTCCloud.setLogCompressEnabled(enabled);
        result.success(null);
    }

    /**
     * ???????????????
     * ????????????????????????????????????????????????view???????????????
     */
    private void showDebugView(MethodCall call, Result result) {
        int mode = CommonUtil.getParam(call, result, "mode");
        trtcCloud.showDebugView(mode);
        result.success(null);
    }

    // ??????????????? API ??????
    private void callExperimentalAPI(MethodCall call, Result result) {
        String jsonStr = CommonUtil.getParam(call, result, "jsonStr");
        trtcCloud.callExperimentalAPI(jsonStr);
        result.success(null);
    }

    /**
     * ??????????????????
     */
    private void setBeautyStyle(MethodCall call, Result result) {
        int beautyStyle = CommonUtil.getParam(call, result, "beautyStyle");
        txBeautyManager.setBeautyStyle(beautyStyle);
        result.success(null);
    }

    /**
     * ??????????????????????????????
     */
    private void setFilter(MethodCall call, final Result result) {
        String type = CommonUtil.getParam(call, result, "type");
        final String imageUrl = CommonUtil.getParam(call, result, "imageUrl");
        if (type.equals("network")) {
            new Thread() {
                @Override
                public void run() {
                    try {
                        URL url = new URL(imageUrl);
                        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                        connection.setDoInput(true);
                        connection.connect();
                        InputStream input = connection.getInputStream();
                        Bitmap myBitmap = BitmapFactory.decodeStream(input);
                        txBeautyManager.setFilter(myBitmap);
                    } catch (IOException e) {
                        TXCLog.e(TAG,"|method=setFilter|error=" + e);
                    }
                }
            }.start();
        } else {
            try {
                Bitmap myBitmap;
                //???????????????sdcard??????
                if (imageUrl.startsWith("/")) {
                    myBitmap = BitmapFactory.decodeFile(imageUrl);
                } else {
                    String path = flutterAssets.getAssetFilePathByName(imageUrl);
                    AssetManager mAssetManger = trtcContext.getAssets();
                    InputStream mystream = mAssetManger.open(path);
                    myBitmap = BitmapFactory.decodeStream(mystream);
                }
                txBeautyManager.setFilter(myBitmap);

            } catch (Exception e) {
                TXCLog.e(TAG,"|method=setFilter|error=" + e);
            }
        }
        result.success(null);
    }

    /**
     * ??????????????????
     */
    private void setFilterStrength(MethodCall call, Result result) {
        String strength = CommonUtil.getParam(call, result, "strength");
        float strengthFloat = Float.parseFloat(strength);
        txBeautyManager.setFilterStrength(strengthFloat);
        result.success(null);
    }

    /**
     * ??????????????????
     */
    private void setBeautyLevel(MethodCall call, Result result) {
        int beautyLevel = CommonUtil.getParam(call, result, "beautyLevel");
        txBeautyManager.setBeautyLevel(beautyLevel);
        result.success(null);
    }

    /**
     * ??????????????????
     */
    private void setWhitenessLevel(MethodCall call, Result result) {
        int whitenessLevel = CommonUtil.getParam(call, result, "whitenessLevel");
        txBeautyManager.setWhitenessLevel(whitenessLevel);
        result.success(null);
    }

    /**
     * ?????????????????????
     */
    private void enableSharpnessEnhancement(MethodCall call, Result result) {
        boolean enable = CommonUtil.getParam(call, result, "enable");
        txBeautyManager.enableSharpnessEnhancement(enable);
        result.success(null);
    }

    /**
     * ??????????????????
     */
    private void setRuddyLevel(MethodCall call, Result result) {
        int ruddyLevel = CommonUtil.getParam(call, result, "ruddyLevel");
        txBeautyManager.setRuddyLevel(ruddyLevel);
        result.success(null);
    }

    /**
     * ????????????
     */
    private void enableVoiceEarMonitor(MethodCall call, Result result) {
        boolean enable = CommonUtil.getParam(call, result, "enable");
        txAudioEffectManager.enableVoiceEarMonitor(enable);
        result.success(null);
    }

    /**
     * ?????????????????????
     */
    private void setVoiceEarMonitorVolume(MethodCall call, Result result) {
        int volume = CommonUtil.getParam(call, result, "volume");
        txAudioEffectManager.setVoiceEarMonitorVolume(volume);
        result.success(null);
    }

    /**
     * ??????????????????????????????KTV??????????????????????????????????????????...???
     */
    private void setVoiceReverbType(MethodCall call, Result result) {
        int type = CommonUtil.getParam(call, result, "type");
        TXAudioEffectManager.TXVoiceReverbType reverbType =
                TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_0;
        switch (type) {
            case 0:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_0;
                break;
            case 1:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_1;
                break;
            case 2:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_2;
                break;
            case 3:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_3;
                break;
            case 4:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_4;
                break;
            case 5:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_5;
                break;
            case 6:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_6;
                break;
            case 7:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_7;
                break;
            default:
                reverbType = TXAudioEffectManager.TXVoiceReverbType.TXLiveVoiceReverbType_0;
                break;
        }
        txAudioEffectManager.setVoiceReverbType(reverbType);
        result.success(null);
    }

    /**
     * ?????????????????????????????????????????????????????????????????????...???
     */
    private void setVoiceChangerType(MethodCall call, Result result) {
        int type = CommonUtil.getParam(call, result, "type");
        TXAudioEffectManager.TXVoiceChangerType changerType =
                TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_0;
        switch (type) {
            case 0:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_0;
                break;
            case 1:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_1;
                break;
            case 2:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_2;
                break;
            case 3:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_3;
                break;
            case 4:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_4;
                break;
            case 5:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_5;
                break;
            case 6:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_6;
                break;
            case 7:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_7;
                break;
            case 8:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_8;
                break;
            case 9:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_9;
                break;
            case 10:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_10;
                break;
            case 11:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_11;
                break;
            default:
                changerType = TXAudioEffectManager.TXVoiceChangerType.TXLiveVoiceChangerType_0;
                break;
        }
        txAudioEffectManager.setVoiceChangerType(changerType);
        result.success(null);
    }
    
    /**
     * ????????????????????????????????????
     */
    private void setVoiceCaptureVolume(MethodCall call, Result result) {
        int volume = CommonUtil.getParam(call, result, "volume");
        txAudioEffectManager.setVoiceCaptureVolume(volume);
        result.success(null);
    }

    /**
     * ?????????????????????????????????????????????
     */
    private void setMusicObserver(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        txAudioEffectManager.setMusicObserver(id, new TXAudioEffectManager.TXMusicPlayObserver() {
            @Override
            public void onStart(int i, int i1) {
                trtcListener.onMusicObserverStart(i, i1);
            }

            @Override
            public void onPlayProgress(int i, long l, long l1) {
                trtcListener.onMusicObserverPlayProgress(i, l,l1);
            }

            @Override
            public void onComplete(int i, int i1) {
                trtcListener.onMusicObserverComplete(i, i1);
            }
        });
        result.success(null);
    }


    /**
     * ????????????????????????
     */
    private void startPlayMusic(MethodCall call, Result result) {
        String musicParam = CommonUtil.getParam(call, result, "musicParam");
        TXAudioEffectManager.AudioMusicParam audioMusicParam =
                new Gson().fromJson(musicParam, TXAudioEffectManager.AudioMusicParam.class);
        boolean isSuccess = txAudioEffectManager.startPlayMusic(audioMusicParam);
        result.success(isSuccess);
        txAudioEffectManager.setMusicObserver(audioMusicParam.id, new TXAudioEffectManager.TXMusicPlayObserver() {
            @Override
            public void onStart(int i, int i1) {
                trtcListener.onMusicObserverStart(i, i1);
            }

            @Override
            public void onPlayProgress(int i, long l, long l1) {
                trtcListener.onMusicObserverPlayProgress(i, l,l1);
            }

            @Override
            public void onComplete(int i, int i1) {
                trtcListener.onMusicObserverComplete(i, i1);
            }
        });
    }

    /**
     * ????????????????????????
     */
    private void stopPlayMusic(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        txAudioEffectManager.stopPlayMusic(id);
        result.success(null);
    }

    /**
     * ????????????????????????
     */
    private void pausePlayMusic(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        txAudioEffectManager.pausePlayMusic(id);
        result.success(null);
    }

    /**
     * ????????????????????????
     */
    private void resumePlayMusic(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        txAudioEffectManager.resumePlayMusic(id);
        result.success(null);
    }

    /**
     * ????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private void setMusicPublishVolume(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        int volume = CommonUtil.getParam(call, result, "volume");
        txAudioEffectManager.setMusicPublishVolume(id, volume);
        result.success(null);
    }

    /**
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????
     */
    private void setMusicPlayoutVolume(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        int volume = CommonUtil.getParam(call, result, "volume");
        txAudioEffectManager.setMusicPlayoutVolume(id, volume);
        result.success(null);
    }

    /**
     * ?????????????????????????????????????????????????????????
     */
    private void setAllMusicVolume(MethodCall call, Result result) {
        int volume = CommonUtil.getParam(call, result, "volume");
        txAudioEffectManager.setAllMusicVolume(volume);
        result.success(null);
    }

    /**
     * ?????????????????????????????????
     */
    private void setMusicPitch(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        String pitchParam = CommonUtil.getParam(call, result, "pitch");
        float pitch = Float.parseFloat(pitchParam);
        txAudioEffectManager.setMusicPitch(id, pitch);
        result.success(null);
    }

    /**
     * ?????????????????????????????????
     */
    private void setMusicSpeedRate(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        String speedRateParam = CommonUtil.getParam(call, result, "speedRate");
        float speedRate = Float.parseFloat(speedRateParam);
        txAudioEffectManager.setMusicSpeedRate(id, speedRate);
        result.success(null);
    }

    /**
     * ????????????????????????????????????????????????????????????
     */
    private void getMusicCurrentPosInMS(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        result.success(txAudioEffectManager.getMusicCurrentPosInMS(id));
    }

    /**
     * ??????????????????????????????????????????????????????
     */
    private void seekMusicToPosInMS(MethodCall call, Result result) {
        int id = CommonUtil.getParam(call, result, "id");
        int pts = CommonUtil.getParam(call, result, "pts");
        txAudioEffectManager.seekMusicToPosInMS(id, pts);
        result.success(null);
    }

    /**
     * ??????????????????????????????????????????????????????
     */
    private void getMusicDurationInMS(MethodCall call, Result result) {
        String path = CommonUtil.getParamCanBeNull(call, result, "path");
        result.success(txAudioEffectManager.getMusicDurationInMS(path));
    }
}