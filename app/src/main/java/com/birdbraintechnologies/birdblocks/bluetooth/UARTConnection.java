package com.birdbraintechnologies.birdblocks.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Represents a UART connection established via Bluetooth Low Energy. Communicates using the RX and
 * TX lines.
 *
 * @author Terence Sun (tsun1215)
 */
public class UARTConnection extends BluetoothGattCallback {
    private static final String TAG = UARTConnection.class.getName();

    /* Latches to handle serialization of async reads/writes */
    private CountDownLatch startLatch = new CountDownLatch(1);
    private CountDownLatch doneLatch = new CountDownLatch(1);
    private CountDownLatch resultLatch = new CountDownLatch(1);

    /* UUIDs for the communication lines */
    private UUID uartUUID, txUUID, rxUUID, rxConfigUUID;

    private int connectionState;
    private BluetoothGatt btGatt;
    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;


    /**
     * Initializes a UARTConnection. This needs to know the context the bluetooth connection is
     * being made from (Activity, Service, etc)
     *
     * @param context  Context that the connection is begin made from
     * @param device   Device to connect to
     * @param settings Settings for connecting via UART
     */
    public UARTConnection(Context context, BluetoothDevice device, UARTSettings settings) {
        this.uartUUID = settings.getUARTServiceUUID();
        this.txUUID = settings.getTxCharacteristicUUID();
        this.rxUUID = settings.getRxCharacteristicUUID();
        this.rxConfigUUID = settings.getRxConfig();

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
        startLatch = new CountDownLatch(1);
        doneLatch = new CountDownLatch(1);

        tx.setValue(bytes);
        boolean res = btGatt.writeCharacteristic(tx);

        // Wait for operation to complete
        startLatch.countDown();
        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error: " + e);
            return false;
        }

        return res;
    }

    /**
     * Sends a byte array across TX, expecting a response on the RX line. Returns the response.
     *
     * @param bytes Byte array to send to the device
     * @return Response from the device
     */
    synchronized public byte[] writeBytesWithResponse(byte[] bytes) {
        startLatch = new CountDownLatch(1);
        doneLatch = new CountDownLatch(1);
        resultLatch = new CountDownLatch(1);

        tx.setValue(bytes);
        boolean success = btGatt.writeCharacteristic(tx);
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
            doneLatch.await();
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
            Log.d(TAG, "Successfully wrote " + Arrays.toString(characteristic.getValue()) + " to TX");
        } else {
            Log.d(TAG, "Error writing " + Arrays.toString(characteristic.getValue()) + " to TX");
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

        Log.d(TAG, "Got response " + Arrays.toString(characteristic.getValue()) + " from RX");

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
}
