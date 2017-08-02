package com.birdbraintechnologies.birdblox.Bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.birdbraintechnologies.birdblox.Util.NamingHandler;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.mainWebViewContext;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;


/**
 * Represents a connection established via Bluetooth Low Energy to a MelodySmart BTLE Adapter.
 * Communicates using the dataBus line.
 *
 * @author Terence Sun (tsun1215)
 */
public class MelodySmartConnection extends BluetoothGattCallback {
    private static final String TAG = MelodySmartConnection.class.getName();
    private static final int MAX_RETRIES = 100;
    private static final int AWAIT_MAX = 5000;
    private static final String OK_RESPONSE = "OK";
    private static final String FAIL_RESPONSE = "FAIL";

    /* UUIDs for the communication lines */
    private UUID uartUUID, dataBusUUID, configUUID;

    /* Latches to handle serialization of async reads/writes */
    private CountDownLatch startLatch = new CountDownLatch(1);
    private CountDownLatch doneLatch = new CountDownLatch(1);
    private CountDownLatch resultLatch = new CountDownLatch(1);

    private int connectionState;
    private BluetoothGatt btGatt;
    private BluetoothGattCharacteristic dataBus;

    private BluetoothDevice bluetoothDevice;

    /**
     * Initializes a UARTConnection. This needs to know the context the Bluetooth connection is
     * being made from (Activity, Service, etc)
     *
     * @param context  Context that the connection is begin made from
     * @param device   Device to connect to
     * @param settings Settings for connecting via UART
     */
    public MelodySmartConnection(Context context, BluetoothDevice device, UARTSettings settings) {
        this.uartUUID = settings.getUARTServiceUUID();
        this.dataBusUUID = settings.getTxCharacteristicUUID();
        this.configUUID = settings.getRxConfig();

        establishConnection(context, device);
        // TODO: Handle failure to establish UART connection

        runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(btGatt.getDevice().getAddress()) + "', true);");
    }

    /**
     * Establishes a connection by connecting to the device and registering a characteristic
     * notification on the dataBus line
     *
     * @param context Context that this connection is being made in
     * @param device  Bluetooth device being connected to
     * @return True if a connection was successfully established, false otherwise
     */
    private boolean establishConnection(Context context, final BluetoothDevice device) {
        // Connect to device
        this.btGatt = device.connectGatt(context, false, this);

        // Initialize serialization
        startLatch.countDown();
        try {
            if (!doneLatch.await(AWAIT_MAX, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Timed out waiting for initialization.");
                new Handler(mainWebViewContext.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        String FLName = NamingHandler.GenerateName(mainWebViewContext.getApplicationContext(), device.getAddress());
                        Toast.makeText(mainWebViewContext, "Connection to Flutter " + FLName + " timed out.", Toast.LENGTH_LONG).show();
                    }
                });
                return false;
            }
        } catch (InterruptedException e) {
            // TODO: Handle error
            return false;
        }

        // Enable Data notification
        if (!btGatt.setCharacteristicNotification(dataBus, true)) {
            Log.e(TAG, "Unable to set characteristic notification");
            return false;
        }
        BluetoothGattDescriptor descriptor = dataBus.getDescriptor(configUUID);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

        boolean res;
        int retryCount = 0;
        while (!(res = btGatt.writeDescriptor(descriptor))) {
            if (retryCount > MAX_RETRIES) {
                Log.e(TAG, "Unable to set dataBus notification descriptor");
                break;
            }
            retryCount++;
        }

        this.bluetoothDevice = device;

        return res;
    }


    /**
     * Sends a byte array to the device across the data bus
     *
     * @param bytes byte array to send
     * @return True on success, false otherwise
     */
    synchronized public boolean writeBytes(byte[] bytes) {
        byte[] responseBytes = writeBytesWithResponse(bytes);
        String response = new String(responseBytes);
        if (response.equals(OK_RESPONSE)) {
            return true;
        } else {
            Log.e(TAG, "Expected OK, received response " + response);
            return false;
        }
    }

    /**
     * Sends a byte array across data bus, expecting a response on the same line. Returns the
     * response.
     *
     * @param bytes Byte array to send to the device
     * @return Response from the device
     */
    synchronized public byte[] writeBytesWithResponse(byte[] bytes) {
        startLatch = new CountDownLatch(1);
        doneLatch = new CountDownLatch(1);
        resultLatch = new CountDownLatch(1);

        dataBus.setValue(bytes);
        boolean success;
        int retryCount = 0;
        while (!(success = btGatt.writeCharacteristic(dataBus))) {
            if (retryCount > MAX_RETRIES) {
                break;
            }
            retryCount++;
        }
        if (success) {
            // Wait for a successful write and a response
            startLatch.countDown();
            try {
                if (!doneLatch.await(AWAIT_MAX, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Error waiting for a write callback");
                    return new byte[]{};
                }
                if (!resultLatch.await(AWAIT_MAX, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Error waiting for a response callback");
                    return new byte[]{};
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error: " + e);
                return new byte[]{};
            }

            // Retrieve and return response
            byte[] res = dataBus.getValue();
            // TODO: Potential data race -> change to blocking queue
            return Arrays.copyOf(res, res.length);
        }
        Log.e(TAG, "Unable to write bytes");
        return new byte[]{};
    }


    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        connectionState = newState;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices();
            } else {
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(gatt.getDevice().getAddress()) + "', false);");
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            dataBus = gatt.getService(uartUUID).getCharacteristic(dataBusUUID);
            // Notify that the setup process is completed
            doneLatch.countDown();
        } else {
            runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(gatt.getDevice().getAddress()) + "', false);");
        }
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        // For serializing write operations
        try {
            startLatch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error: " + e);
        }

        if (status == BluetoothGatt.GATT_SUCCESS) {
            Log.v(TAG, "Successfully wrote " + Arrays.toString(characteristic.getValue()));
        } else {
            Log.e(TAG, "Error writing " + Arrays.toString(characteristic.getValue()));
        }

        // TODO: Inidcate write success/failure to main thread

        // For serializing write operations
        doneLatch.countDown();
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        // For serializing read operations
        try {
            startLatch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error: " + e);
        }
        byte[] newValue = characteristic.getValue();
        Log.v(TAG, "Got response " + Arrays.toString(newValue));

        // TODO: Inidcate write success/failure to main thread

        // For serializing read operations
        resultLatch.countDown();
    }


    /**
     * Returns whether or not this connection is connected
     *
     * @return True if connected, false otherwise
     */
    public boolean isConnected() {
        return this.connectionState == BluetoothGatt.STATE_CONNECTED;
    }

    /**
     * Disconnects and closes the connection with the device
     */
    public void disconnect() {
        btGatt.disconnect();
        btGatt.close();
    }

    /**
     * @return
     */
    public BluetoothDevice getBLEDevice() {
        return this.bluetoothDevice;
    }
}
