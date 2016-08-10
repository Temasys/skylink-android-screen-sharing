package skylink.temasys.com.sg.skylinkshare;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import sg.com.temasys.skylink.sdk.rtc.SkylinkConfig;
import sg.com.temasys.skylink.sdk.listener.DataTransferListener;
import sg.com.temasys.skylink.sdk.listener.LifeCycleListener;
import sg.com.temasys.skylink.sdk.listener.RemotePeerListener;
import sg.com.temasys.skylink.sdk.rtc.SkylinkConnection;

/**
 * Simple application demonstrating the use of
 * Android Media Projection API and Skylink for Android
 *
 * Media projection code referred from
 * https://github.com/mtsahakis/MediaProjectionDemo
 */
public class MainActivity extends Activity
        implements RemotePeerListener, DataTransferListener, LifeCycleListener {

    private static final String TAG = MainActivity.class.getName();

    private static final String APP_KEY = "";
    private static final String APP_SECRET = "";
    private static final String ROOM_NAME = "SkylinkShare";

    private static final int REQUEST_CODE = 1;
    private static final int TIME_OUT = 60;

    private int displayWidth;
    private int displayHeight;
    private int imagesProduced;

    private MediaProjectionManager projectionManager;
    private ImageReader imageReader;
    private MediaProjection mediaProjection;
    private Handler handler;

    private SkylinkConnection skylinkConnection;
    private String currentRemotePeerId;
    private boolean projectionStarted;
    private boolean connected;
    private ImageView imgViewer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imgViewer = (ImageView) findViewById(R.id.image_view);

        projectionManager = (MediaProjectionManager)
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);

        // Use the device id as the username
        String deviceId = Settings.Secure.getString(getApplicationContext().getContentResolver(),
                Settings.Secure.ANDROID_ID);

        //Initialize skylinkConnection and connect to the room
        if(APP_KEY.isEmpty()||APP_SECRET.isEmpty()){
            showInitializationFailedAlert();
            return;
        }

        initializeSkylinkConnection();
        skylinkConnection.connectToRoom(APP_SECRET, ROOM_NAME, deviceId);

        // Start capture handling thread
        new Thread() {
            @Override
            public void run() {
                Looper.prepare();
                handler = new Handler();
                Looper.loop();
            }
        }.start();
    }

    private void showInitializationFailedAlert() {
        AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
        alertBuilder.setMessage(R.string.dialog_init_fail).setPositiveButton(
                getString(R.string.ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        finish();
                    }
                });

        alertBuilder.show();
    }

    @Override
    public void onDestroy() {
        //close the connection when the fragment is detached, so the streams are not open.
        if (skylinkConnection != null && connected) {
            skylinkConnection.disconnectFromRoom();
            skylinkConnection.setLifeCycleListener(null);
            skylinkConnection.setMediaListener(null);
            skylinkConnection.setRemotePeerListener(null);
            connected = false;
        }

        super.onDestroy();
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

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {

            if (projectionStarted) {
                stopProjection();
                item.setTitle("Share Screen");
            } else {
                startProjection();
                item.setTitle("Stop Sharing");
            }

            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Initializes the skylink connection
     */
    private void initializeSkylinkConnection() {
        if (skylinkConnection == null) {
            skylinkConnection = SkylinkConnection.getInstance();
            // The app_key and app_secret is obtained from the temasys developer console.
            skylinkConnection.init(APP_KEY, getSkylinkConfig(), this);
            // Set listeners to receive callbacks when events are triggered
            skylinkConnection.setRemotePeerListener(this);
            skylinkConnection.setDataTransferListener(this);
            skylinkConnection.setLifeCycleListener(this);
        }
    }

    /**
     * Returns the skylink config
     *
     * @return
     */
    private SkylinkConfig getSkylinkConfig() {
        SkylinkConfig config = new SkylinkConfig();
        // AudioVideo config options can be NO_AUDIO_NO_VIDEO, AUDIO_ONLY, VIDEO_ONLY, AUDIO_AND_VIDEO;
        config.setAudioVideoSendConfig(SkylinkConfig.AudioVideoConfig.NO_AUDIO_NO_VIDEO);
        config.setHasDataTransfer(true);
        config.setTimeout(TIME_OUT);
        return config;
    }

    /**
     * Requests to start projection
     */
    public void startProjection() {
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE);
    }

    /**
     * Request to stop projection
     */
    public void stopProjection() {
        projectionStarted = false;

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaProjection != null) {
                    mediaProjection.stop();
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {

            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            if (mediaProjection != null) {

                projectionStarted = true;

                // Initialize the media projection
                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int density = metrics.densityDpi;
                int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;

                Display display = getWindowManager().getDefaultDisplay();
                Point size = new Point();
                display.getSize(size);

                displayWidth = size.x;
                displayHeight = size.y;

                imageReader = ImageReader.newInstance(displayWidth, displayHeight
                        , PixelFormat.RGBA_8888, 2);
                mediaProjection.createVirtualDisplay("screencap",
                        displayWidth, displayHeight, density,
                        flags, imageReader.getSurface(), null, handler);
                imageReader.setOnImageAvailableListener(new ImageAvailableListener(), handler);
            }
        }
    }


    private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            FileOutputStream fos = null;
            Bitmap bitmap = null;

            ByteArrayOutputStream stream = null;

            try {
                image = imageReader.acquireLatestImage();
                if (image != null) {
                    Image.Plane[] planes = image.getPlanes();
                    ByteBuffer buffer = planes[0].getBuffer();
                    int pixelStride = planes[0].getPixelStride();
                    int rowStride = planes[0].getRowStride();
                    int rowPadding = rowStride - pixelStride * displayWidth;

                    // create bitmap
                    bitmap = Bitmap.createBitmap(displayWidth + rowPadding / pixelStride,
                            displayHeight, Bitmap.Config.ARGB_8888);
                    bitmap.copyPixelsFromBuffer(buffer);

                    if (skylinkConnection != null && !TextUtils.isEmpty(currentRemotePeerId)) {
                        stream = new ByteArrayOutputStream();
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 5, stream);
                        skylinkConnection.sendData(currentRemotePeerId, stream.toByteArray());
                        Log.d(TAG, "sending data to peer :" + currentRemotePeerId);
                    }

                    imagesProduced++;
                    Log.e(TAG, "captured image: " + imagesProduced);
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }

                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                }

                if (bitmap != null) {
                    bitmap.recycle();
                }

                if (image != null) {
                    image.close();
                }
            }
        }
    }

    /**
     * LifeCycleListener implementation
     */
    @Override
    public void onConnect(boolean isSuccessful, String message) {
        showToast("onConnect " + isSuccessful);
        connected = isSuccessful;
    }

    @Override
    public void onWarning(int errorCode, String message) {
        showToast("onWarning " + message);
    }

    @Override
    public void onDisconnect(int errorCode, String message) {
        showToast("onDisconnect " + message);
    }

    @Override
    public void onReceiveLog(String message) {
        showToast("onReceiveLog " + message);
    }

    @Override
    public void onLockRoomStatusChange(String remotePeerId, boolean locked) {
        showToast("onLockRoomStatusChange " + remotePeerId + " locked " + locked);
    }

    /**
     * RemotePeerListener implementation
     */

    @Override
    public void onRemotePeerJoin(String remotePeerId, Object userData, boolean hasDataChannel) {

        if (!TextUtils.isEmpty(this.currentRemotePeerId)) {
            showToast("A remote peer is already in the room");
            return;
        }

        this.currentRemotePeerId = remotePeerId;
        showToast("onRemotePeerJoin " + remotePeerId);
    }

    @Override
    public void onRemotePeerUserDataReceive(String remotePeerId, Object userData) {
        showToast("onRemotePeerUserDataReceive " + remotePeerId);
    }

    @Override
    public void onOpenDataConnection(String remotePeerId) {
        showToast("onOpenDataConnection " + remotePeerId);
    }

    @Override
    public void onRemotePeerLeave(String remotePeerId, String message) {
        if (remotePeerId.equals(this.currentRemotePeerId)) {
            this.currentRemotePeerId = null;
        }
        showToast("onRemotePeerLeave " + remotePeerId);
    }

    /**
     * DataTransferListener implementation
     */
    @Override
    public void onDataReceive(String remotePeerId, final byte[] data) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "onDataReceive: " + data.length);
                if (data != null && data.length != 0) {
                    Bitmap bm = BitmapFactory.decodeByteArray(data, 0, data.length);
                    Log.d(TAG, "Set Image : " + bm.toString());
                    imgViewer.setImageBitmap(bm);
                }
            }
        });
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
    }

}
