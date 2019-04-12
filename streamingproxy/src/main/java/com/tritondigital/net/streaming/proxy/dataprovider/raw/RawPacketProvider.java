package com.tritondigital.net.streaming.proxy.dataprovider.raw;

import com.tritondigital.net.streaming.proxy.dataprovider.DataProvider;
import com.tritondigital.net.streaming.proxy.dataprovider.Packet;
import com.tritondigital.net.streaming.proxy.decoder.AudioConfig;
import com.tritondigital.net.streaming.proxy.utils.Log;

 /**
  * <p>This class is used to provide the data exactly as it is received. It does not alter the packet structure at all,
  * it simply stores the decoded packets to retransmit them to the server when needed,
  *
  * <p> This class is typically used when the Proxy connects to a Stream, takes the MetaData but do not modify the audio
  * data at all, because it is already in a format that the client / player supports.
  */
 public class RawPacketProvider extends DataProvider {
     public final String TAG = "RawPacketProvider";

     /** Tells if the AudioSpecificConfig is ready, thus if getting the SDPConfig will block or not.*/
     boolean mAudioConfigReady = false;

     /** Used to block the method that returns SDP Config if the AudioSpecificConfig is not ready.*/
     final Object mAudioConfigReadyLock = new Object();

     /** Used to unblock the thread. Set to true to force the blocking loop to break, then reset to true so the next time that getSdpConfig is called, it will block. */
     private volatile boolean     mBlockUntilAudioConfigReadyEnabled = true;

     /**
      * Interface to be implemented in order to be notified about the important changes of state of the server.
      * Calls to this listener may come from any thread, so thread safety should be considered when designing the implementation.
      */
     public interface StateChangedListener {
         /** Provider can produce the Audio mime type response without blocking, it has received all the necessary data. */
         void onProviderAudioConfigReady();
     }


     protected AudioConfig mAudioConfig;
     private StateChangedListener mStateChangedListener;


     @Override
     public void onAudioConfigDecoded(AudioConfig audioConfig) {
         mAudioConfig = audioConfig;
         notifyListenerAudioConfigReady();
     }


     @Override
     public void onAudioDataDecoded(byte[] audioData, int audioDataLength, int timestamp) {
         // Proceed with the creation of the RTP packet with the default header and enough room for the payload.
         RawPacket rawPacket = (RawPacket) getFreePacket();
         rawPacket.setPayload(audioData, audioDataLength);

         // Packet ready, add to queue
         enqueuePacket(rawPacket);
     }


     @Override
     protected Packet createEmptyPacket() {
         return new RawPacket(512);
     }


     /**
      * Returns the mime-type string (audio/aac, audio/mpeg) to be sent by the HTTP server in response to a GET request.
      */
     public String getMimeType() {
         // Block until AudioConfig is ready.
         synchronized (mAudioConfigReadyLock) {
             mBlockUntilAudioConfigReadyEnabled = true; // Set to true, may be set to false on a secondary thread to unblock this one.

             try {
                 while (mBlockUntilAudioConfigReadyEnabled && !mAudioConfigReady) {
                     Log.i(TAG, "Waiting for AudioConfig to be received");
                     mAudioConfigReadyLock.wait();
                 }
             } catch (InterruptedException e) {
                 return null;
             }

         }

         String mimeType;
         switch (mAudioConfig.getMediaType()) {
             case AAC: mimeType = "audio/aac";  break;
             case MP3: mimeType = "audio/mpeg"; break;
             default:  mimeType = null;         break;
         }

         return mimeType;
     }


     /**
      * In addition to the default stop,
      * Resets the Audio Config state to make sure to block the next time that this provider is used, until the Audio Config
      * is ready again.
      */
     @Override
     public void stop() {
         super.stop();

         stopBlockingUntilAudioConfigReady();
         synchronized (mAudioConfigReadyLock) {
             mAudioConfigReady = false;
         }
     }


     /**
      * Unblock the thread waiting on a call to blockUntilReady.
      */
     public void stopBlockingUntilAudioConfigReady() {
         synchronized (mAudioConfigReadyLock) {
             mBlockUntilAudioConfigReadyEnabled = false;
             mAudioConfigReadyLock.notify();
         }
     }


     /**
      * Tells if the Audio Config strings can be used without blocking.
      * It is assumed that once the AudioConfig has been received, all mandatory information
      * are available. Thus, this method returns false until the AudioConfig has been received from the
      * protocol container decoder.
      */
     public boolean isAudioConfigReady() {
         synchronized (mAudioConfigReadyLock) {
             return mAudioConfigReady;
         }
     }


     /**
      * Sets the listener to be notified by any state change on this provider.
      */
     public void setStateChangedListener(StateChangedListener stateChangedListener) {
         mStateChangedListener = stateChangedListener;
     }

     /**
      * Notifies listener that the Audio Config can now be used without blocking.
      * Called by the subclasses to indicate that the provider has received enough data and is ready to be used as the source for streaming.
      */
     protected void notifyListenerAudioConfigReady() {
         // Unblock SDP config getter
         synchronized (mAudioConfigReadyLock) {
             mAudioConfigReady = true;
             mAudioConfigReadyLock.notify();
         }

         if (mStateChangedListener != null) {
             mStateChangedListener.onProviderAudioConfigReady();
         }
     }
 }
