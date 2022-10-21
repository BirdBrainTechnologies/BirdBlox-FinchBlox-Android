package com.birdbraintechnologies.birdblox.Bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.birdbraintechnologies.birdblox.httpservice.RequestHandlers.RobotRequestHandler.deviceGatt;

import androidx.core.app.ActivityCompat;

/**
 * Represents a UART connection established via Bluetooth Low Energy. Communicates using the RX and
 * TX lines.
 *
 * @author Terence Sun (tsun1215)
 */
public class UARTConnection extends BluetoothGattCallback {
    private static final String TAG = UARTConnection.class.getSimpleName();
    private static final int MAX_RETRIES = 100;
    private static final int CONNECTION_TIMEOUT_IN_SECS = 15;

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

    public Context context;
    private BluetoothDevice bluetoothDevice;

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

        this.context = context;
        this.bluetoothDevice = device;

        if (!establishUARTConnection(context, device)) {
            disconnect();
        }
        // TODO: Handle failure to establish UART connection
    }

    /**
     * Sends a byte array to the device across TX
     *
     * @param bytes byte array to send
     * @return True on success, false otherwise
     */
    synchronized public boolean writeBytes(byte[] bytes) {
        //Log.d(TAG, "writing value " + bytes[0]);
        if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
            Log.e(TAG, "Trying to write bytes without bluetooth connect permissions");
            return false;
        }
        try {
            startLatch = new CountDownLatch(1);
            doneLatch = new CountDownLatch(1);

            tx.setValue(bytes);
            boolean res;
            int retryCount = 0;
            while (!(res = btGatt.writeCharacteristic(tx))) {
                //Log.d(TAG, "retrying to write " + bytes[0]);
                if (retryCount > MAX_RETRIES) {
                    Log.e(TAG, "failed to write " + bytes[0] + " after " + MAX_RETRIES + " attempts.");
                    break;
                }
                retryCount++;
            }

            // Wait for operation to complete
            startLatch.countDown();
            try {
                doneLatch.await(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Log.e(TAG, "Error: " + e);
                return false;
            }
            //Log.d(TAG, "returning " + res);
            return res;
        } catch (Exception e) {
            Log.e(TAG, "Error writing: " + e.getMessage());
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
        if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
            Log.e(TAG, "Trying to write bytes with response without bluetooth connect permissions");
            return new byte[]{};
        }
        try {
            startLatch = new CountDownLatch(1);
            doneLatch = new CountDownLatch(1);
            resultLatch = new CountDownLatch(1);

            tx.setValue(bytes);
            boolean success;
            int retryCount = 0;
            while (!(success = btGatt.writeCharacteristic(tx))) {
                if (retryCount > MAX_RETRIES) {
                    Log.e(TAG, "writeCharacteristic(tx) failed to write " + Arrays.toString(bytes) + " after " + MAX_RETRIES + " tries.");
                    break;
                }
                retryCount++;
            }
            if (success) {
                // Wait for a successful write and a response
                startLatch.countDown();
                try {
                    doneLatch.await(100, TimeUnit.MILLISECONDS);
                    resultLatch.await(100, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Error: " + e);
                    return new byte[]{};
                }

                // Retrieve and return response
                byte[] res = rx.getValue();
                if (res != null) {
                    return Arrays.copyOf(res, res.length);
                } else {
                    Log.e(TAG, "writeBytesWithResponse: no response received.");
                    return new byte[]{};
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "writeBytesWithResponse " + e.getMessage());
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
    private boolean establishUARTConnection(Context context, final BluetoothDevice device) {
        if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
            Log.e(TAG, "Trying to connect without bluetooth connect permissions");
            return false;
        }
        // Connect to device

        if (deviceGatt.get(device.getAddress()) == null) {
            this.btGatt = device.connectGatt(context, false, this);
            deviceGatt.put(device.getAddress(), this.btGatt);

            // Initialize serialization
            startLatch.countDown();
            try {
                if (!doneLatch.await(CONNECTION_TIMEOUT_IN_SECS, TimeUnit.SECONDS)) {
                    return false;
                }
            } catch (InterruptedException e) {
                Log.e(TAG, "Error while trying to establish UART connection: " + e.toString());
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
        } else {
            return true;
        }
    }


    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        Log.d(TAG, "onConnectionStateChange to " + newState);
        connectionState = newState;
        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                        != PackageManager.PERMISSION_GRANTED) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
                    Log.e(TAG, "Recieved STATE_CONNECTED without bluetooth connect permissions");
                    return;
                }
                gatt.discoverServices();
            }
        }
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            tx = gatt.getService(uartUUID).getCharacteristic(txUUID);
            rx = gatt.getService(uartUUID).getCharacteristic(rxUUID);
            // Notify that the setup process is completed
            if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
                Log.e(TAG, "Discovered services without bluetooth connect permissions");
                return;
            }
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED);
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
            //Log.v(TAG, "Successfully wrote " + Arrays.toString(characteristic.getValue()) + " to TX");
        } else {
            Log.e(TAG, "Error writing " + Arrays.toString(characteristic.getValue()) + " to TX");
        }

        // TODO: Indicate write success/failure to main thread

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
        //Log.v(TAG, "Got response " + Arrays.toString(newValue) + " from RX");
        for (RXDataListener l : rxListeners) {
            l.onRXData(newValue);
        }

        // TODO: Indicate write success/failure to main thread

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
        if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
            Log.e(TAG, "Trying to disconnect without bluetooth connect permissions");
        } else {
            btGatt.disconnect();
            btGatt.close();
        }
        btGatt = null;
        this.bluetoothDevice = null;
    }

    /**
     * Disconnects and closes the connection with the device
     */
    public void forceDisconnect() {
        if ((ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) && (Build.VERSION.SDK_INT > Build.VERSION_CODES.R)) {
            Log.e(TAG, "Trying to force disconnect without bluetooth connect permissions");
        } else {
            btGatt.disconnect();
            btGatt.close();
        }
        this.bluetoothDevice = null;
    }

    /**
     * @return
     */
    public BluetoothDevice getBLEDevice() {
        return this.bluetoothDevice;
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