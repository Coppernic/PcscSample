package fr.coppernic.sdk.usboutlet

import android.content.Context
import android.content.Context.USB_SERVICE
import android.hardware.usb.UsbManager
import android.util.Log
import com.ftdi.j2xx.D2xxManager
import com.ftdi.j2xx.FT_Device

const val SET_GPIO_OUTPUT_ALL_HIGH = 0xF0

const val SET_GPIO_OUTPUT_ALL_LOG = 0xFF

const val SET_BIT_MODE = 0x20
const val FTDI_CONFIGURATION_DELAY = 2000L


object FtdiManager {

    private var ftDevice: FT_Device? = null
    private const val TAG = "FtdiManager"

    fun powerSmartCardReader(context: Context): Boolean {
        val usbManager = context.applicationContext.getSystemService(USB_SERVICE) as UsbManager

        val ftdiUsbDevice =
            findUsbDevice(usbManager, FTDI_VID, listOf(FTDI_PID_1, FTDI_PID_2)) ?: return false


        try {
            val d2xxManager = D2xxManager.getInstance(context)
            val count = d2xxManager.createDeviceInfoList(context)

            if (count == 0) {
                Log.e(TAG, "D2xx createDeviceInfoList() count == 0")
                return false
            }

            ftDevice = d2xxManager.openByUsbDevice(
                context, ftdiUsbDevice
            )

            if (ftDevice == null) {
                Log.e(TAG, "D2xx openByUsbDevice() failed")
                return false
            }

            ftDevice?.apply {
                resetDevice()
                setBaudRate(9600)
                setDataCharacteristics(
                    D2xxManager.FT_DATA_BITS_8,
                    D2xxManager.FT_STOP_BITS_1,
                    D2xxManager.FT_PARITY_NONE
                )
            }

            val mask = computeFtdiGpioOutputMask(false, false, false, true)
            ftDevice?.setBitMode(mask, SET_BIT_MODE.toByte())

            return true
        } catch (e: Exception) {
            Log.e(TAG, "FTDI communication failed", e)
            return false
        }
    }


    private fun computeFtdiGpioOutputMask(
        gpio0: Boolean, gpio1: Boolean, gpio2: Boolean, gpio3: Boolean
    ): Byte {

        val direction = 0xF shl 4
        var values = 0
        if (!gpio0) values = values or (1 shl 0)
        if (!gpio1) values = values or (1 shl 1)
        if (!gpio2) values = values or (1 shl 2)
        if (!gpio3) values = values or (1 shl 3)

        return (direction or values).toByte()
    }

}
