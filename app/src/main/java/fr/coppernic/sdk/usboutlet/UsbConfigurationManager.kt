package fr.coppernic.sdk.usboutlet

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

class UsbConfigurationManager(
    context: Context
) {


    companion object {

        // Replace with your real values
        const val FTDI_VENDOR_ID = 0x0403
        const val FTDI_PRODUCT_ID = 0x6001

        const val SEC1210_VENDOR_ID = 0x0000
        const val SEC1210_PRODUCT_ID = 0x0000

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

    /**
     * Returns true if the final USB device required by the application
     * is present and permission has already been granted.
     */
    fun isReady(): Boolean {
        val reader = getSmartCardReader()
            ?: return false

        return usbManager.hasPermission(reader)
    }

    fun isFtdiReady(): Boolean {
        val ftdi = getFtdiDevice()
            ?: return false

        return usbManager.hasPermission(ftdi)
    }

    fun isSmartCardReaderReady(): Boolean {
        val reader = getSmartCardReader()
            ?: return false

        return usbManager.hasPermission(reader)
    }

    fun getFtdiDevice(): UsbDevice? {
        return findUsbDevice(
            FTDI_VENDOR_ID,
            FTDI_PRODUCT_ID
        )
    }

    fun getSmartCardReader(): UsbDevice? {
        return findUsbDevice(
            SEC1210_VENDOR_ID,
            SEC1210_PRODUCT_ID
        )
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    private fun findUsbDevice(
        vendorId: Int,
        productId: Int
    ): UsbDevice? {
        return usbManager.deviceList.values.firstOrNull {
            it.vendorId == vendorId &&
                it.productId == productId
        }
    }
}
