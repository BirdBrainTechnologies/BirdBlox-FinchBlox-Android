package com.birdbraintechnologies.birdblocks;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.util.Log;

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

    public boolean isConnected() {
        return this.connectionState == BluetoothGatt.STATE_CONNECTED;
    }

    public void disconnect() {
        btGatt.disconnect();
    }
}
