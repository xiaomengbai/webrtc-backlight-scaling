/*
 * libjingle
 * Copyright 2015 Google Inc.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  1. Redistributions of source code must retain the above copyright notice,
 *     this list of conditions and the following disclaimer.
 *  2. Redistributions in binary form must reproduce the above copyright notice,
 *     this list of conditions and the following disclaimer in the documentation
 *     and/or other materials provided with the distribution.
 *  3. The name of the author may not be used to endorse or promote products
 *     derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
 * EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.appspot.apprtc;

import org.appspot.apprtc.AppRTCClient.RoomConnectionParameters;
import org.appspot.apprtc.AppRTCClient.SignalingParameters;
import org.appspot.apprtc.PeerConnectionClient.PeerConnectionParameters;
import org.appspot.apprtc.util.LooperExecutor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import org.webrtc.VideoRendererGui.ScalingType;

// MBX
import org.webrtc.VideoRendererGui.frameProcess;
import org.webrtc.VideoRenderer.I420Frame;
import android.content.Context;
import java.util.Arrays;
import android.os.Handler;
import java.util.HashMap;
import android.view.Window;
import android.view.WindowManager;
import java.util.ArrayList;

//import android.renderscript.*;
// MBX

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */
public class CallActivity extends Activity
    implements AppRTCClient.SignalingEvents,
      PeerConnectionClient.PeerConnectionEvents,
      CallFragment.OnCallEvents
// MBX
    , VideoRendererGui.frameProcess
// MBX
{

  public static final String EXTRA_ROOMID =
      "org.appspot.apprtc.ROOMID";
  public static final String EXTRA_LOOPBACK =
      "org.appspot.apprtc.LOOPBACK";
  public static final String EXTRA_HWCODEC =
      "org.appspot.apprtc.HWCODEC";
  public static final String EXTRA_VIDEO_BITRATE =
      "org.appspot.apprtc.VIDEO_BITRATE";
  public static final String EXTRA_VIDEO_WIDTH =
      "org.appspot.apprtc.VIDEO_WIDTH";
  public static final String EXTRA_VIDEO_HEIGHT =
      "org.appspot.apprtc.VIDEO_HEIGHT";
  public static final String EXTRA_VIDEO_FPS =
      "org.appspot.apprtc.VIDEO_FPS";
  public static final String EXTRA_VIDEOCODEC =
      "org.appspot.apprtc.VIDEOCODEC";
  public static final String EXTRA_CPUOVERUSE_DETECTION =
      "org.appspot.apprtc.CPUOVERUSE_DETECTION";
  public static final String EXTRA_DISPLAY_HUD =
      "org.appspot.apprtc.DISPLAY_HUD";
  public static final String EXTRA_CMDLINE =
      "org.appspot.apprtc.CMDLINE";
  public static final String EXTRA_RUNTIME =
      "org.appspot.apprtc.RUNTIME";
  private static final String TAG = "CallRTCClient";
  // Peer connection statistics callback period in ms.
  private static final int STAT_CALLBACK_PERIOD = 1000;
  // Local preview screen position before call is connected.
  private static final int LOCAL_X_CONNECTING = 0;
  private static final int LOCAL_Y_CONNECTING = 0;
  private static final int LOCAL_WIDTH_CONNECTING = 100;
  private static final int LOCAL_HEIGHT_CONNECTING = 100;
  // Local preview screen position after call is connected.
  private static final int LOCAL_X_CONNECTED = 72;
  private static final int LOCAL_Y_CONNECTED = 72;
  private static final int LOCAL_WIDTH_CONNECTED = 25;
  private static final int LOCAL_HEIGHT_CONNECTED = 25;
  // Remote video screen position
  private static final int REMOTE_X = 0;
  private static final int REMOTE_Y = 0;
  private static final int REMOTE_WIDTH = 100;
  private static final int REMOTE_HEIGHT = 100;

  private PeerConnectionClient peerConnectionClient = null;
  private AppRTCClient appRtcClient;
  private SignalingParameters signalingParameters;
  private AppRTCAudioManager audioManager = null;
  private VideoRenderer.Callbacks localRender;
  private VideoRenderer.Callbacks remoteRender;
  private ScalingType scalingType;
  private Toast logToast;
  private boolean commandLineRun;
  private int runTimeMs;
  private boolean activityRunning;
  private RoomConnectionParameters roomConnectionParameters;
  private PeerConnectionParameters peerConnectionParameters;
  private boolean hwCodecAcceleration;
  private String videoCodec;
  private boolean iceConnected;
  private boolean isError;
  private boolean callControlFragmentVisible = true;

  // Controls
  private GLSurfaceView videoView;
  CallFragment callFragment;

  // MBX
  /* scriptRS comment out
  private RenderScript mRS;
  private ScriptC_script mScript;
  private Allocation Hist2D;
  private Allocation Hist;
  private Allocation YPanel;
  private Allocation maxValue;
  private Allocation stepsAlloc;
  private Allocation stepSizeAlloc;
  int[] maxL = new int[1];
  private Script.LaunchOptions lo;
  private int steps;
  private int stepSize;
  */
  private Handler mHandler;
  private HashMap<Integer, Double> backlightMap;
  private Double updatedBacklight;
  private Double accBacklight;
  private Integer accTimes;
  private static boolean funcEnable = true;

    {
      backlightMap = new HashMap<Integer, Double>();
      accBacklight = 0.0;
      accTimes = 0;
    }
  // MBX


  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Thread.setDefaultUncaughtExceptionHandler(
        new UnhandledExceptionHandler(this));

    // Set window styles for fullscreen-window size. Needs to be done before
    // adding content.
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_FULLSCREEN
        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    setContentView(R.layout.activity_call);

    iceConnected = false;
    signalingParameters = null;
    scalingType = ScalingType.SCALE_ASPECT_FILL;

    // Create UI controls.
    videoView = (GLSurfaceView) findViewById(R.id.glview_call);
    callFragment = new CallFragment();

    // Create video renderers.
    VideoRendererGui.setView(videoView, new Runnable() {
      @Override
      public void run() {
        createPeerConnectionFactory();
      }
    });
    remoteRender = VideoRendererGui.create(
        REMOTE_X, REMOTE_Y,
        REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false, this);
    localRender = VideoRendererGui.create(
        LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
        LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true, this);

    //=MBX=
    /*
    mRS = RenderScript.create(this);
    mScript = new ScriptC_script(mRS);
    lo = new Script.LaunchOptions();
    steps = -1;
    stepSize = -1;

    YPanel = null;

    Hist = Allocation.createSized(mRS, Element.I32(mRS), 256);
    mScript.set_hist(Hist);

    maxValue = Allocation.createSized(mRS, Element.I32(mRS), 1);
    mScript.set_maxValue(maxValue);
    stepsAlloc = Allocation.createSized(mRS, Element.I32(mRS), 1);
    mScript.set_stepsAlloc(stepsAlloc);
    stepSizeAlloc = Allocation.createSized(mRS, Element.I32(mRS), 1);
    mScript.set_stepSizeAlloc(stepSizeAlloc);
    */

    mHandler = new Handler();
    //=MBX=


    // Show/hide call control fragment on view click.
    videoView.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        toggleCallControlFragmentVisibility();
      }
    });

    // Get Intent parameters.
    final Intent intent = getIntent();
    Uri roomUri = intent.getData();
    if (roomUri == null) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Didn't get any URL in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }
    String roomId = intent.getStringExtra(EXTRA_ROOMID);
    if (roomId == null || roomId.length() == 0) {
      logAndToast(getString(R.string.missing_url));
      Log.e(TAG, "Incorrect room ID in intent!");
      setResult(RESULT_CANCELED);
      finish();
      return;
    }
    boolean loopback = intent.getBooleanExtra(EXTRA_LOOPBACK, false);
    hwCodecAcceleration = intent.getBooleanExtra(EXTRA_HWCODEC, true);
    if (intent.hasExtra(EXTRA_VIDEOCODEC)) {
      videoCodec = intent.getStringExtra(EXTRA_VIDEOCODEC);
    } else {
      videoCodec = PeerConnectionClient.VIDEO_CODEC_VP8; // use VP8 by default.
    }
    peerConnectionParameters = new PeerConnectionParameters(
        intent.getIntExtra(EXTRA_VIDEO_WIDTH, 0),
        intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 0),
        intent.getIntExtra(EXTRA_VIDEO_FPS, 0),
        intent.getIntExtra(EXTRA_VIDEO_BITRATE, 0),
        intent.getBooleanExtra(EXTRA_CPUOVERUSE_DETECTION, true));
    commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);
    runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);

    // Create connection client and connection parameters.
    appRtcClient = new WebSocketRTCClient(this, new LooperExecutor());
    roomConnectionParameters = new RoomConnectionParameters(
        roomUri.toString(), roomId, loopback);

    // Send intent arguments to fragment.
    callFragment.setArguments(intent.getExtras());
    // Activate call fragment and start the call.
    getFragmentManager().beginTransaction()
        .add(R.id.call_fragment_container, callFragment).commit();
    startCall();

    // For command line execution run connection for <runTimeMs> and exit.
    if (commandLineRun && runTimeMs > 0) {
      videoView.postDelayed(new Runnable() {
        public void run() {
          disconnect();
        }
      }, runTimeMs);
    }
  }

  // Activity interfaces
  @Override
  public void onPause() {
    super.onPause();
    videoView.onPause();
    activityRunning = false;
    if (peerConnectionClient != null) {
      peerConnectionClient.stopVideoSource();
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    videoView.onResume();
    activityRunning = true;
    if (peerConnectionClient != null) {
      peerConnectionClient.startVideoSource();
    }
  }

  @Override
  protected void onDestroy() {
    disconnect();
    super.onDestroy();
    if (logToast != null) {
      logToast.cancel();
    }
    activityRunning = false;
  }

  // CallFragment.OnCallEvents interface implementation.
  @Override
  public void onCallHangUp() {
    disconnect();
  }

  @Override
  public void onCameraSwitch() {
    if (peerConnectionClient != null) {
      peerConnectionClient.switchCamera();
    }
  }

  @Override
  public void onVideoScalingSwitch(ScalingType scalingType) {
    this.scalingType = scalingType;
    updateVideoView();
  }

  // Helper functions.
  private void toggleCallControlFragmentVisibility() {
    if (!iceConnected || !callFragment.isAdded()) {
      return;
    }
    // Show/hide call control fragment
    callControlFragmentVisible = !callControlFragmentVisible;
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (callControlFragmentVisible) {
      ft.show(callFragment);
    } else {
      ft.hide(callFragment);
    }
    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    ft.commit();
  }

  private void updateVideoView() {
    VideoRendererGui.update(remoteRender,
        REMOTE_X, REMOTE_Y,
        REMOTE_WIDTH, REMOTE_HEIGHT, scalingType);
    if (iceConnected) {
      VideoRendererGui.update(localRender,
          LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
          LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
          ScalingType.SCALE_ASPECT_FIT);
    } else {
      VideoRendererGui.update(localRender,
          LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
          LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType);
    }
  }

  private void startCall() {
    if (appRtcClient == null) {
      Log.e(TAG, "AppRTC client is not allocated for a call.");
      return;
    }
    // Start room connection.
    logAndToast(getString(R.string.connecting_to,
        roomConnectionParameters.roomUrl));
    appRtcClient.connectToRoom(roomConnectionParameters);

    // Create and audio manager that will take care of audio routing,
    // audio modes, audio device enumeration etc.
    audioManager = AppRTCAudioManager.create(this, new Runnable() {
        // This method will be called each time the audio state (number and
        // type of devices) has been changed.
        @Override
        public void run() {
          onAudioManagerChangedState();
        }
      }
    );
    // Store existing audio settings and change audio mode to
    // MODE_IN_COMMUNICATION for best possible VoIP performance.
    Log.d(TAG, "Initializing the audio manager...");
    audioManager.init();
  }

  // Should be called from UI thread
  private void callConnected() {
    // Update video view.
    updateVideoView();
    // Enable statistics callback.
    peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
  }

  private void onAudioManagerChangedState() {
    // TODO(henrika): disable video if AppRTCAudioManager.AudioDevice.EARPIECE
    // is active.
  }

  // Create peer connection factory when EGL context is ready.
  private void createPeerConnectionFactory() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          peerConnectionClient = new PeerConnectionClient();
          peerConnectionClient.createPeerConnectionFactory(CallActivity.this,
              videoCodec, hwCodecAcceleration,
              VideoRendererGui.getEGLContext(), CallActivity.this);
        }
        if (signalingParameters != null) {
          Log.w(TAG, "EGL context is ready after room connection.");
          onConnectedToRoomInternal(signalingParameters);
        }
      }
    });
  }

  // Disconnect from remote resources, dispose of local resources, and exit.
  private void disconnect() {
    if (appRtcClient != null) {
      appRtcClient.disconnectFromRoom();
      appRtcClient = null;
    }
    if (peerConnectionClient != null) {
      peerConnectionClient.close();
      peerConnectionClient = null;
    }
    if (audioManager != null) {
      audioManager.close();
      audioManager = null;
    }
    if (iceConnected && !isError) {
      setResult(RESULT_OK);
    } else {
      setResult(RESULT_CANCELED);
    }
    finish();
  }

  private void disconnectWithErrorMessage(final String errorMessage) {
    if (commandLineRun || !activityRunning) {
      Log.e(TAG, "Critical error: " + errorMessage);
      disconnect();
    } else {
      new AlertDialog.Builder(this)
          .setTitle(getText(R.string.channel_error_title))
          .setMessage(errorMessage)
          .setCancelable(false)
          .setNeutralButton(R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
              dialog.cancel();
              disconnect();
            }
          }).create().show();
    }
  }

  // Log |msg| and Toast about it.
  private void logAndToast(String msg) {
    Log.d(TAG, msg);
    if (logToast != null) {
      logToast.cancel();
    }
    logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    logToast.show();
  }

  // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
  // All callbacks are invoked from websocket signaling looper thread and
  // are routed to UI thread.
  private void onConnectedToRoomInternal(final SignalingParameters params) {
    signalingParameters = params;
    if (peerConnectionClient == null) {
      Log.w(TAG, "Room is connected, but EGL context is not ready yet.");
      return;
    }
    logAndToast("Creating peer connection...");
    peerConnectionClient.createPeerConnection(
        localRender, remoteRender,
        signalingParameters, peerConnectionParameters);

    if (signalingParameters.initiator) {
      logAndToast("Creating OFFER...");
      // Create offer. Offer SDP will be sent to answering client in
      // PeerConnectionEvents.onLocalDescription event.
      peerConnectionClient.createOffer();
    } else {
      if (params.offerSdp != null) {
        peerConnectionClient.setRemoteDescription(params.offerSdp);
        logAndToast("Creating ANSWER...");
        // Create answer. Answer SDP will be sent to offering client in
        // PeerConnectionEvents.onLocalDescription event.
        peerConnectionClient.createAnswer();
      }
      if (params.iceCandidates != null) {
        // Add remote ICE candidates from room.
        for (IceCandidate iceCandidate : params.iceCandidates) {
          peerConnectionClient.addRemoteIceCandidate(iceCandidate);
        }
      }
    }
  }

  @Override
  public void onConnectedToRoom(final SignalingParameters params) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        onConnectedToRoomInternal(params);
      }
    });
  }

  @Override
  public void onRemoteDescription(final SessionDescription sdp) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
          return;
        }
        logAndToast("Received remote " + sdp.type + " ...");
        peerConnectionClient.setRemoteDescription(sdp);
        if (!signalingParameters.initiator) {
          logAndToast("Creating ANSWER...");
          // Create answer. Answer SDP will be sent to offering client in
          // PeerConnectionEvents.onLocalDescription event.
          peerConnectionClient.createAnswer();
        }
      }
    });
  }

  @Override
  public void onRemoteIceCandidate(final IceCandidate candidate) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG,
              "Received ICE candidate for non-initilized peer connection.");
          return;
        }
        peerConnectionClient.addRemoteIceCandidate(candidate);
      }
    });
  }

  @Override
  public void onChannelClose() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("Remote end hung up; dropping PeerConnection");
        disconnect();
      }
    });
  }

  @Override
  public void onChannelError(final String description) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!isError) {
          isError = true;
          disconnectWithErrorMessage(description);
        }
      }
    });
  }

  // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
  // Send local peer connection SDP and ICE candidates to remote party.
  // All callbacks are invoked from peer connection client looper thread and
  // are routed to UI thread.
  @Override
  public void onLocalDescription(final SessionDescription sdp) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (appRtcClient != null) {
          logAndToast("Sending " + sdp.type + " ...");
          if (signalingParameters.initiator) {
            appRtcClient.sendOfferSdp(sdp);
          } else {
            appRtcClient.sendAnswerSdp(sdp);
          }
        }
      }
    });
  }

  @Override
  public void onIceCandidate(final IceCandidate candidate) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (appRtcClient != null) {
          appRtcClient.sendLocalIceCandidate(candidate);
        }
      }
    });
  }

  @Override
  public void onIceConnected() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("ICE connected");
        iceConnected = true;
        callConnected();
      }
    });
  }

  @Override
  public void onIceDisconnected() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("ICE disconnected");
        iceConnected = false;
        disconnect();
      }
    });
  }

  @Override
  public void onPeerConnectionClosed() {
  }

  @Override
  public void onPeerConnectionStatsReady(final StatsReport[] reports) {
    final double brightness = accBacklight / (accTimes + 1.0);
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!isError && iceConnected) {
          callFragment.updateEncoderStatistics(reports, brightness);
        }
      }
    });
  }

  @Override
  public void onPeerConnectionError(final String description) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!isError) {
          isError = true;
          disconnectWithErrorMessage(description);
        }
      }
    });
  }

  // MBX
  @Override
  public void updateBacklight(ArrayList<Integer> idx, ArrayList<Double> bl){
    if(funcEnable){
      if(idx.size() != bl.size()){
        Log.w(TAG, "indices array size is not as same as the backlight's");
        return;
      }

      synchronized(this){
        for(int i = 0; i < idx.size(); i++)
          backlightMap.put(idx.get(i), bl.get(i));
      }
    }
  }

  @Override
  public void clearBacklight(){
    if(funcEnable){
      synchronized(this){
        backlightMap.clear();
      }
    }
  }

  @Override
  public void tryScaleBacklight(int index){
    Double b;

    if(funcEnable){
      synchronized(this){
        b = backlightMap.get(index);
      }

      if(b != null){
        final Double backlight = b;

        accTimes++;
        accBacklight += backlight;
        updatedBacklight = b;

        mHandler.post(new Runnable(){

            @Override
            public void run() {
//                        Log.d(TAG, "update the backlight to " + backlight);
              Window w = getWindow();
              WindowManager.LayoutParams lp = w.getAttributes();
              lp.screenBrightness = backlight.floatValue();
              w.setAttributes(lp);
            }
          });
      }
    }
  }

  @Override
  public int scanMaxY(I420Frame frame){

    return 255;
    /* comment renderscipt out
    if(!remoteRender.isSizeSet()){
      Log.d(TAG, "scan failed! scanning before size is set");
      return -1;
    }
    if(remoteRender.isSizeUpdated()){

      int[] stepsTemp = new int[1];
      int[] stepSizeTemp = new int[1];

      steps = remoteRender.getHeight();
      stepSize = remoteRender.getWidth();
      stepsTemp[0] = steps;
      stepSizeTemp[0] = stepSize;
      Log.d(TAG, "video size is updated! steps: " + steps + ", stepsize: " + stepSize);
      mScript.set_steps(steps);
      mScript.set_stepSize(stepSize);
      mScript.set_YWidth(stepSize);
      mScript.set_YHeight(steps);

      stepsAlloc.copyFrom(stepsTemp);
      stepSizeAlloc.copyFrom(stepSizeTemp);

      Type.Builder TBhist2D = new Type.Builder(mRS, Element.I32(mRS)).setX(256).setY(steps);
      Hist2D = Allocation.createTyped(mRS, TBhist2D.create(), Allocation.USAGE_SCRIPT);
      mScript.set_hist2D(Hist2D);

      Type.Builder TBYPanel = new Type.Builder(mRS, Element.U8(mRS)).setX(stepSize).setY(steps);
      YPanel = Allocation.createTyped(mRS, TBYPanel.create(), Allocation.USAGE_SCRIPT);
      mScript.set_yPanel(YPanel);

    }

    if(steps == -1 || stepSize == -1){
      Log.d(TAG, "scan frame before *step* and *stepSize* are set");
      return -1;
    }

    if(YPanel == null){
      Log.e(TAG, "YPanel is null!!!!! why!!!!");
      return -1;
    }
    //     Log.d(TAG, "step: " + mScript.get_steps() + ", stepSize: " + mScript.get_stepSize());
    byte[] ydata = frame.getYData();
    //      Log.d(TAG, "ydata(size: " + steps + "*" + stepSize + ", " + ydata.length + ") : " + ydata[0] + ", " + ydata[1]);

    YPanel.copyFromUnchecked(ydata);
    //      Log.d(TAG, "after copying");

    int[] hist = new int[256];
    int sum;

    lo.setX(0,1).setY(0,steps);
    mScript.forEach_hist1111(YPanel, lo);
    mScript.forEach_hist2(Hist);

    // // Hist.copyTo(hist);
    // // sum = 0;
    // // for(int e : hist)
    // //     sum += e;

    mScript.invoke_setTouchRoof();
    maxValue.copyTo(maxL);

    //      Log.d(TAG, "Hist[" + sum + "]: " + Arrays.toString(hist));
    frame.setMaxY(maxL[0]);
     comment renderscript out end */

    /*
      mScript.invoke_histclr();

      Hist.copyTo(hist);
      sum = 0;
      for(int e : hist)
      sum += e;
      Log.d(TAG, "Hist[" + sum + "]: " + Arrays.toString(hist));
      mScript.forEach_histnew(YPanel);

      // mScript.invoke_setTouchRoof();
      // maxValue.copyTo(maxL);

      Hist.copyTo(hist);
      sum = 0;
      for(int e : hist)
      sum += e;
      Log.d(TAG, "Hist[" + sum + "]: " + Arrays.toString(hist));

    */

    // frame.setMaxY(maxL[0]);
    // return maxL[0];
  }
  // MBX

}