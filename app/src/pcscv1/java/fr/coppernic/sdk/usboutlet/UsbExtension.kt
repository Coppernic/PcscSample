package fr.coppernic.sdk.usboutlet

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager

const val FTDI_VID = 0x0403
const val FTDI_PID_1 = 0x6010
const val FTDI_PID_2 = 0x6015

const val SEC1210_VID = 0x0424
const val SEC1210_PID = 0x1202

fun isUsbDeviceReady(usbManager: UsbManager, vendorId: Int, productIdList: List<Int>): Boolean {
    val device = findUsbDevice(usbManager, vendorId, productIdList) ?: return false
    return usbManager.hasPermission(device)
}


fun findUsbDevice(usbManager: UsbManager,
    vendorId: Int, productIdList: List<Int>
): UsbDevice? {
    return usbManager.deviceList.values.firstOrNull { device ->
        device.vendorId == vendorId && productIdList.contains(device.productId)
    }
}

fun getFtdiDevice(usbManager: UsbManager): UsbDevice? {
    return findUsbDevice(
        usbManager,
        FTDI_VID,
        listOf(FTDI_PID_1, FTDI_PID_2)
    )
}

fun getSmartCardReader(usbManager: UsbManager): UsbDevice? {
    return findUsbDevice(
        usbManager,
        SEC1210_VID,
        listOf(SEC1210_PID)
    )
}

fun UsbDevice.isFtdi(): Boolean {
    return (this.productId == FTDI_PID_1 || this.productId == FTDI_PID_2) && this.vendorId == FTDI_VID
}

