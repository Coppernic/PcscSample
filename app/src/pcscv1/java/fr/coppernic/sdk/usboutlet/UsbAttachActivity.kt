package fr.coppernic.sdk.usboutlet

import android.app.Activity
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log

class UsbAttachActivity : Activity() {
    private val TAG = "UsbAttachActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val device =
            intent.getParcelableExtra<UsbDevice?>(UsbManager.EXTRA_DEVICE)

        if (device != null) {
            val manager =
                getSystemService(USB_SERVICE) as UsbManager

            Log.d(
                TAG,
                "USB attached: " + device.deviceName +
                    ", permission=" + manager.hasPermission(device)
            )
        }

        finish()
    }
}
