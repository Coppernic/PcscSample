package fr.coppernic.samples.pcsc.ui;

import androidx.annotation.NonNull;
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
            return ConePeripheral.PCSC_MICROCHIP_SEC1210_USB;
        } else if (OsHelper.isAccess()){
            return AccessPeripheral.PCSC_MICROCHIP_SEC1210_USB;
        } else if (OsHelper.isIdPlatform()){
            return IdPlatformPeripheral.SMARTCARD;
        } else {
            return DummyPeripheral.NO_OP;
        }
    }
}
