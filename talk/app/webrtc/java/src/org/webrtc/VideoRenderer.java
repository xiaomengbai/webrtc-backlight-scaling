/*
 * libjingle
 * Copyright 2013 Google Inc.
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

package org.webrtc;

import java.nio.ByteBuffer;

import android.util.Log;
/**
 * Java version of VideoRendererInterface.  In addition to allowing clients to
 * define their own rendering behavior (by passing in a Callbacks object), this
 * class also provides a createGui() method for creating a GUI-rendering window
 * on various platforms.
 */
public class VideoRenderer {

  /** Java version of cricket::VideoFrame. */
  public static class I420Frame {
    public final int width;
    public final int height;
    public final int[] yuvStrides;
    public final ByteBuffer[] yuvPlanes;
    public final boolean yuvFrame;
    public Object textureObject;
    public int textureId;

    public final static int MAX_0_LUM = 0;
    public final static int MAX_2_LUM = 1;
    public final static int MAX_5_LUM = 2;
    public final static int MAX_10_LUM = 3;
    public final static int MAX_LUM_LVL = 4;
    public final static int MAX_LUM_CAND_NR = 4;
    public int[] maxLumCandStPos;
    public byte[][] lumCands;

    public int maxY;
    public int adjustLum;
    public int idx;
    public boolean isScan;
    public float yScaleFactor = 1.0f;

    private void setLumCandStPos(int width){
      maxLumCandStPos = new int[]{width + 1, width * 2 + 1, width * 3 + 1, width * 4 + 1};
    }

    {
      lumCands = new byte[MAX_LUM_LVL][MAX_LUM_CAND_NR];
    }

    private void extractCand(){
      for(int i = 0; i < MAX_LUM_LVL; i++){
        for(int j = 0; j < MAX_LUM_CAND_NR; j++){
          // max luminances are neighboring
          lumCands[i][j] = yuvPlanes[0].get(maxLumCandStPos[i] + j);
        }
        //Log.d("EXTRACT THE MAX LUMINANCE", "L" + i + ": " + Arrays.toString(lumCands[i]));
      }

    }

    public int getMaxLum(int level){
      int maxLum = 0;
      for(int i = 0; i < MAX_LUM_CAND_NR; i++)
        maxLum += (int)(lumCands[level][i] & 0xff);
      maxLum /= MAX_LUM_CAND_NR;
      return maxLum;
    }
    /**
     * Construct a frame of the given dimensions with the specified planar
     * data.  If |yuvPlanes| is null, new planes of the appropriate sizes are
     * allocated.
     */
    public I420Frame(
        int width, int height, int[] yuvStrides, ByteBuffer[] yuvPlanes) {
      this.width = width;
      this.height = height;
      this.yuvStrides = yuvStrides;
      if (yuvPlanes == null) {
        yuvPlanes = new ByteBuffer[3];
        yuvPlanes[0] = ByteBuffer.allocateDirect(yuvStrides[0] * height);
        yuvPlanes[1] = ByteBuffer.allocateDirect(yuvStrides[1] * height / 2);
        yuvPlanes[2] = ByteBuffer.allocateDirect(yuvStrides[2] * height / 2);
      }
      this.yuvPlanes = yuvPlanes;
      this.yuvFrame = true;

      // =MBX=
      setLumCandStPos(width);
    }

    /**
     * Construct a texture frame of the given dimensions with data in SurfaceTexture
     */
    public I420Frame(
        int width, int height, Object textureObject, int textureId) {
      this.width = width;
      this.height = height;
      this.yuvStrides = null;
      this.yuvPlanes = null;
      this.textureObject = textureObject;
      this.textureId = textureId;
      this.yuvFrame = false;

      // =MBX=
      setLumCandStPos(width);
    }

    /**
     * Copy the planes out of |source| into |this| and return |this|.  Calling
     * this with mismatched frame dimensions or frame type is a programming
     * error and will likely crash.
     */
    public I420Frame copyFrom(I420Frame source) {
      if (source.yuvFrame && yuvFrame) {
        if (width != source.width || height != source.height) {
          throw new RuntimeException("Mismatched dimensions!  Source: " +
              source.toString() + ", destination: " + toString());
        }
        nativeCopyPlane(source.yuvPlanes[0], width, height,
            source.yuvStrides[0], yuvPlanes[0], yuvStrides[0]);
        nativeCopyPlane(source.yuvPlanes[1], width / 2, height / 2,
            source.yuvStrides[1], yuvPlanes[1], yuvStrides[1]);
        nativeCopyPlane(source.yuvPlanes[2], width / 2, height / 2,
            source.yuvStrides[2], yuvPlanes[2], yuvStrides[2]);

        // =MBX=
        extractCand();
        // =MBX=
        return this;
      } else if (!source.yuvFrame && !yuvFrame) {
        textureObject = source.textureObject;
        textureId = source.textureId;
        return this;
      } else {
        throw new RuntimeException("Mismatched frame types!  Source: " +
            source.toString() + ", destination: " + toString());
      }
    }

    public I420Frame copyFrom(byte[] yuvData) {
      if (yuvData.length < width * height * 3 / 2) {
        throw new RuntimeException("Wrong arrays size: " + yuvData.length);
      }
      if (!yuvFrame) {
        throw new RuntimeException("Can not feed yuv data to texture frame");
      }
      int planeSize = width * height;
      ByteBuffer[] planes = new ByteBuffer[3];
      planes[0] = ByteBuffer.wrap(yuvData, 0, planeSize);
      planes[1] = ByteBuffer.wrap(yuvData, planeSize, planeSize / 4);
      planes[2] = ByteBuffer.wrap(yuvData, planeSize + planeSize / 4,
          planeSize / 4);
      for (int i = 0; i < 3; i++) {
        yuvPlanes[i].position(0);
        yuvPlanes[i].put(planes[i]);
        yuvPlanes[i].position(0);
        yuvPlanes[i].limit(yuvPlanes[i].capacity());
      }

      // =MBX=
      extractCand();
      // =MBX=

      return this;
    }

    public byte getYData(int pos){
        return (yuvPlanes[0].array())[pos];
    }

    public byte[] getYData(){
        return yuvPlanes[0].array();
    }

    public void setMaxY(int max){
        maxY = max;
    }

    public int getMaxY(){
        return maxY;
    }


    @Override
    public String toString() {
        return idx + ": " + width + "x" + height + ":" + yuvStrides[0] + ":" + yuvStrides[1] +
            ":" + yuvStrides[2] + ", isScan: " + isScan + ", maxY: " + maxY + ", adjustLum: " + adjustLum + "\n";
    }
  }

  // Helper native function to do a video frame plane copying.
  private static native void nativeCopyPlane(ByteBuffer src, int width,
      int height, int srcStride, ByteBuffer dst, int dstStride);

  /** The real meat of VideoRendererInterface. */
  public static interface Callbacks {
    public void setSize(int width, int height);
    public void renderFrame(I420Frame frame);

    public boolean isSizeSet();
    public boolean isSizeUpdated();

    public int getWidth();
    public int getHeight();
  }

  // |this| either wraps a native (GUI) renderer or a client-supplied Callbacks
  // (Java) implementation; so exactly one of these will be non-0/null.
  final long nativeVideoRenderer;
  private final Callbacks callbacks;

  public static VideoRenderer createGui(int x, int y) {
    long nativeVideoRenderer = nativeCreateGuiVideoRenderer(x, y);
    if (nativeVideoRenderer == 0) {
      return null;
    }
    return new VideoRenderer(nativeVideoRenderer);
  }

  public VideoRenderer(Callbacks callbacks) {
    nativeVideoRenderer = nativeWrapVideoRenderer(callbacks);
    this.callbacks = callbacks;
  }

  private VideoRenderer(long nativeVideoRenderer) {
    this.nativeVideoRenderer = nativeVideoRenderer;
    callbacks = null;
  }

  public void dispose() {
    if (callbacks == null) {
      freeGuiVideoRenderer(nativeVideoRenderer);
    } else {
      freeWrappedVideoRenderer(nativeVideoRenderer);
    }
  }

  private static native long nativeCreateGuiVideoRenderer(int x, int y);
  private static native long nativeWrapVideoRenderer(Callbacks callbacks);

  private static native void freeGuiVideoRenderer(long nativeVideoRenderer);
  private static native void freeWrappedVideoRenderer(long nativeVideoRenderer);
}
