package com.birdbraintechnologies.birdblocks.bluetooth;

import java.util.UUID;

/**
 * Created by tsun on 2/13/17.
 */

public class UARTSettings {
    private UUID uart;
    private UUID tx;
    private UUID rx;
    private UUID rxConfig;


    protected UARTSettings(UUID uartServiceId, UUID txCharId, UUID rxCharId, UUID rxConfigId) {
        this.uart = uartServiceId;
        this.tx = txCharId;
        this.rx = rxCharId;
        this.rxConfig = rxConfigId;
    }

    public UUID getRxCharacteristicUUID() {
        return rx;
    }

    public UUID getUARTServiceUUID() {
        return uart;
    }

    public UUID getTxCharacteristicUUID() {
        return tx;
    }

    public UUID getRxConfig() {
        return rxConfig;
    }

    public static class Builder {
        private UUID uart, tx, rx, rxConfig;

        public Builder setUARTServiceUUID(UUID id) {
            this.uart = id;
            return this;
        }

        public Builder setTxCharacteristicUUID(UUID id) {
            this.tx = id;
            return this;
        }

        public Builder setRxCharacteristicUUID(UUID id) {
            this.rx = id;
            return this;
        }

        public Builder setRxConfigUUID(UUID id) {
            this.rxConfig = id;
            return this;
        }

        public UARTSettings build() {
            return new UARTSettings(uart, tx, rx, rxConfig);
        }
    }
}
