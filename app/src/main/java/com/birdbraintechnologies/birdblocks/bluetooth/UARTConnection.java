package com.birdbraintechnologies.birdblocks.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

/**
 * Created by tsun on 2/13/17.
 */

public class UARTConnection extends BluetoothGattCallback {
    private static final String TAG = UARTConnection.class.getName();
    private int connectionState;
    private CountDownLatch startLatch = new CountDownLatch(1);
    private CountDownLatch doneLatch = new CountDownLatch(1);
    private UUID uartUUID, txUUID, rxUUID;
    private BluetoothGatt btGatt;

    private BluetoothGattCharacteristic tx;
    private BluetoothGattCharacteristic rx;


    public UARTConnection(Context context, BluetoothDevice device, UARTSettings settings) {
        this.uartUUID = settings.getUARTServiceUUID();
        this.txUUID = settings.getTxCharacteristicUUID();
        this.rxUUID = settings.getRxCharacteristicUUID();

        establishUARTConnection(context, device);
    }

    synchronized public boolean writeBytes(byte[] bytes) {
        startLatch = new CountDownLatch(1);
        doneLatch = new CountDownLatch(1);

        // Serialize callback
        tx.setValue(bytes);
        startLatch.countDown();
        boolean res = btGatt.writeCharacteristic(tx);
        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "Error: " + e);
        }
        return res;
    }

    private boolean establishUARTConnection(Context context, BluetoothDevice device) {
        this.btGatt = device.connectGatt(context, false, this);
        startLatch.countDown();
        try {
            doneLatch.await();
        } catch (InterruptedException e) {
            // TODO: Handle error
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

    public boolean isConnected() {
        return this.connectionState == BluetoothGatt.STATE_CONNECTED;
    }

    public void disconnect() {
        btGatt.disconnect();
        btGatt.close();
    }
}
