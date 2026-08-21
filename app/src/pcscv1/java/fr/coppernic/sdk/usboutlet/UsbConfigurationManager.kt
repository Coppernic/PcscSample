package fr.coppernic.sdk.usboutlet

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

class UsbConfigurationManager(
    context: Context
) {

   companion object {

        fun createConfigurationIntent(context: Context): Intent {
            return Intent(
                context,
                UsbConfigurationActivity::class.java
            )
        }
    }

    private val usbManager =
        context.applicationContext
            .getSystemService(Context.USB_SERVICE) as UsbManager

    fun isSmartCardReaderReady(): Boolean {
        val reader = getSmartCardReader(usbManager)
            ?: return false

        return usbManager.hasPermission(reader)
    }

 }
