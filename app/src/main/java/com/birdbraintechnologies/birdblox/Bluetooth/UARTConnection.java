package com.birdbraintechnologies.birdblox.Bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.birdbraintechnologies.birdblox.MainWebView.bbxEncode;
import static com.birdbraintechnologies.birdblox.MainWebView.runJavascript;

/**
 * Represents a UART connection established via Bluetooth Low Energy. Communicates using the RX and
 * TX lines.
 *
 * @author Terence Sun (tsun1215)
 */
public class UARTConnection extends BluetoothGattCallback {
    private static final String TAG = UARTConnection.class.getName();
    private static final int MAX_RETRIES = 100;

    /* Latches to handle serialization of async reads/writes */
    private CountDownLatch startLatch = new CountDownLatch(1);
    private CountDownLatch doneLatch = new CountDownLatch(1);
    private CountDownLatch resultLatch = new CountDownLatch(1);

    /* UUIDs for the communication lines */
    private UUID uartUUID, txUUID, rxUUID, rxConfigUUID;

    private List<RXDataListener> rxListeners = new ArrayList<>();

    private int connectionState;
    private BluetoothGatt btGatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;

    private BluetoothDevice bluetoothDevice;
    private byte[] G4Response;

    /**
     * Initializes a UARTConnection. This needs to know the context the Bluetooth connection is
     * being made from (Activity, Service, etc)
     *
     * @param context  Context that the connection is begin made from
     * @param device   Device to connect to
     * @param settings Settings for connecting via UART
     */
    public UARTConnection(final Context context, final BluetoothDevice device, UARTSettings settings) {
        this.uartUUID = settings.getUARTServiceUUID();
        this.txUUID = settings.getTxCharacteristicUUID();
        this.rxUUID = settings.getRxCharacteristicUUID();
        this.rxConfigUUID = settings.getRxConfig();

        this.bluetoothDevice = device;

        establishUARTConnection(context, device);
        // TODO: Handle failure to establish UART connection
    }

    /**
     * Sends a byte array to the device across TX
     *
     * @param bytes byte array to send
     * @return True on success, false otherwise
     */
    synchronized public boolean writeBytes(byte[] bytes) {
        try {
            startLatch = new CountDownLatch(1);
            doneLatch = new CountDownLatch(1);

            tx.setValue(bytes);
            boolean res;
            int retryCount = 0;
            while (!(res = btGatt.writeCharacteristic(tx))) {
                if (retryCount > MAX_RETRIES) {
                    break;
                }
                retryCount++;
            }

            // Wait for operation to complete
            startLatch.countDown();
            try {
                doneLatch.await();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error: " + e);
                return false;
            }

            return res;
        } catch (Exception e) {
        }
        return false;
    }

    /**
     * Sends a byte array across TX, expecting a response on the RX line. Returns the response.
     *
     * @param bytes Byte array to send to the device
     * @return Response from the device
     */
    synchronized public byte[] writeBytesWithResponse(byte[] bytes) {
        try {
            startLatch = new CountDownLatch(1);
            doneLatch = new CountDownLatch(1);
            resultLatch = new CountDownLatch(1);

            tx.setValue(bytes);
            boolean success;
            int retryCount = 0;
            while (!(success = btGatt.writeCharacteristic(tx))) {
                if (retryCount > MAX_RETRIES) {
                    break;
                }
                retryCount++;
            }
            if (success) {
                // Wait for a successful write and a response
                startLatch.countDown();
                try {
                    doneLatch.await();
                    resultLatch.await();
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error: " + e);
                    return new byte[]{};
                }

                // Retrieve and return response
                byte[] res = rx.getValue();
                return Arrays.copyOf(res, res.length);
            }
        } catch (Exception e) {

        }
        Log.e(TAG, "Unable to write bytes to tx");
        return new byte[]{};
    }

    /**
     * Establishes a UARTConnection by connecting to the device and registering a characteristic
     * notification on the RX line
     *
     * @param context Context that this connection is being made in
     * @param device  Bluetooth device being connected to
     * @return True if a connection was successfully established, false otherwise
     */
    private boolean establishUARTConnection(Context context, BluetoothDevice device) {
        // Connect to device
        this.btGatt = device.connectGatt(context, false, this);

        // Initialize serialization
        startLatch.countDown();
        try {
            if (!doneLatch.await(30000, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            // TODO: Handle error
            return false;
        }

        // Enable RX notification
        if (!btGatt.setCharacteristicNotification(rx, true)) {
            Log.e(TAG, "Unable to set characteristic notification");
            return false;
        }
        BluetoothGattDescriptor descriptor = rx.getDescriptor(rxConfigUUID);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        if (!btGatt.writeDescriptor(descriptor)) {
            Log.e(TAG, "Unable to set descriptor");
            return false;
        }
        Log.d(TAG, "Successfully established connection to " + device);
        return true;
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        connectionState = newState;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                gatt.discoverServices();
//                G4Response = this.writeBytesWithResponse("G4".getBytes());
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(gatt.getDevice().getAddress()) + "', true);");
            } else {
                runJavascript("CallbackManager.robot.updateStatus('" + bbxEncode(gatt.getDevice().getAddress()) + "', false);");
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            tx = gatt.getService(uartUUID).getCharacteristic(txUUID);
            rx = gatt.getService(uartUUID).getCharacteristic(rxUUID);
            // Notify that the setup process is completed
            doneLatch.countDown();
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
            Log.v(TAG, "Successfully wrote " + Arrays.toString(characteristic.getValue()) + " to TX");
        } else {
            Log.e(TAG, "Error writing " + Arrays.toString(characteristic.getValue()) + " to TX");
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
        Log.v(TAG, "Got response " + Arrays.toString(newValue) + " from RX");
        for (RXDataListener l : rxListeners) {
            l.onRXData(newValue);
        }

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
     * Returns whether or not this connection is being established currently
     *
     * @return True if connecting, false otherwise
     */
    public boolean isConnecting() {
        return this.connectionState == BluetoothGatt.STATE_CONNECTING;
    }

    /**
     * Disconnects and closes the connection with the device
     */
    public void disconnect() {
        btGatt.disconnect();
        btGatt.close();
        this.bluetoothDevice = null;
    }

    /**
     * @return
     */
    public BluetoothDevice getBLEDevice() {
        return this.bluetoothDevice;
    }

    /**
     * @return
     */
    public byte[] getG4Response() {
        return this.G4Response;
    }

    public void addRxDataListener(RXDataListener l) {
        rxListeners.add(l);
    }

    public void removeRxDataListener(RXDataListener l) {
        rxListeners.remove(l);
    }

    /**
     * Listener for new data coming in on RX
     */
    public interface RXDataListener {
        /**
         * Called when new data arrives on the RX line
         *
         * @param newData Data that arrived
         */
        void onRXData(byte[] newData);
    }
}
