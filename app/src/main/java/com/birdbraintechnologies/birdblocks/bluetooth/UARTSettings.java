package com.birdbraintechnologies.birdblocks.bluetooth;

import java.util.UUID;

/**
 * Created by tsun on 2/13/17.
 */

public class UARTSettings {
    private UUID uart, tx, rx;


    protected UARTSettings(UUID uartServiceId, UUID txCharId, UUID rxCharId) {
        this.uart = uartServiceId;
        this.tx = txCharId;
        this.rx = rxCharId;
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

    public static class Builder {
        private UUID uart, tx, rx;

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

        public UARTSettings build() {
            return new UARTSettings(uart, tx, rx);
        }
    }
}
