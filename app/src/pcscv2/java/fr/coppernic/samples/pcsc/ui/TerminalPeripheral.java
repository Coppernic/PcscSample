package fr.coppernic.samples.pcsc.ui;

import androidx.annotation.NonNull;

import fr.coppernic.sample.pcsc.BuildConfig;
import fr.coppernic.sdk.power.api.peripheral.Peripheral;
import fr.coppernic.sdk.power.impl.access.AccessPeripheral;
import fr.coppernic.sdk.power.impl.cone.ConePeripheral;
import fr.coppernic.sdk.power.impl.dummy.DummyPeripheral;
import fr.coppernic.sdk.power.impl.idplatform.IdPlatformPeripheral;
import fr.coppernic.sdk.utils.helpers.OsHelper;

public class TerminalPeripheral {
    @NonNull
    public static Peripheral getPeripheral() {
        if (OsHelper.isCone()) {
            return ConePeripheral.RFID_ELYCTIS_LF214_USB;
        } else if (OsHelper.isIdPlatform()) {
            return IdPlatformPeripheral.SMARTCARD;
        } else if (OsHelper.isAccess()){
            if (BuildConfig.ACCESS_PERIPHERAL == "SEC1210") {
                return AccessPeripheral.PCSC_MICROCHIP_SEC1210_USB;
            } else if (BuildConfig.ACCESS_PERIPHERAL == "ASK") {
                return AccessPeripheral.RFID_ASK_UCM108_GPIO;
            }
            return AccessPeripheral.RFID_HID_CK_MINI_USB;
        } else {
            return DummyPeripheral.NO_OP;
        }
    }
}