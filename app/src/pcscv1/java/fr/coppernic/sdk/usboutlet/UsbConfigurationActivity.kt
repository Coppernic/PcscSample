package fr.coppernic.sdk.usboutlet

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ftdi.j2xx.FT_Device
import fr.coppernic.sdk.power.OutletPowerManager
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
class UsbConfigurationActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "UsbConfiguration"

        // Adapt these
        private const val OUTLET_POWER_TIMEOUT_MS = 5_000L
        private const val DEVICE_ENUM_TIMEOUT_MS = 5_000L

        // Time given to the user to answer each permission dialog
        private const val PERMISSION_TIMEOUT_MS = 20_000L

        private const val POLL_DELAY_MS = 200L
    }

    private lateinit var usbManager: UsbManager

    private val usbPermissionAction by lazy {
        "$packageName.USB_PERMISSION"
    }

    private data class PendingPermission(
        val deviceId: Int, val result: CompletableDeferred<Boolean>
    )

    private var pendingPermission: PendingPermission? = null

    private val usbPermissionReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "USB permission received" + intent.action)

            if (intent.action != usbPermissionAction) {
                return
            }

            val device = getUsbDevice(intent) ?: return

            val pending = pendingPermission ?: return

            // Ignore an old/unrelated permission answer
            if (device.deviceId != pending.deviceId) {
                return
            }

            val granted = intent.getBooleanExtra(
                UsbManager.EXTRA_PERMISSION_GRANTED, false
            )

            Log.i(
                TAG, "Permission result for " + "%04X:%04X = $granted".format(
                    device.vendorId, device.productId
                )
            )

            pending.result.complete(granted)
        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.i(TAG, "Starting USB configuration")

        usbManager = getSystemService(USB_SERVICE) as UsbManager

        registerPermissionReceiver()

        lifecycleScope.launch {
            val success = configureUsb()

            setResult(
                if (success) RESULT_OK
                else RESULT_CANCELED
            )

            finish()
        }
    }

    override fun onDestroy() {
        pendingPermission?.result?.cancel()
        pendingPermission = null

        unregisterReceiver(usbPermissionReceiver)

        super.onDestroy()
    }

    private suspend fun configureUsb(): Boolean {
        try {

            if (!setupFtdi()) {
                Log.e(TAG, "Unable to setup FTDI")
                return false
            }

            if (!setupSmartCardReader()) {
                Log.e(TAG, "Unable to setup SmartCard reader")
                return false
            }

            /*
             * Everything is ready.
             */
            Log.i(TAG, "USB configuration successful")
            return true

        } catch (e: CancellationException) {
            throw e

        } catch (e: Exception) {
            Log.e(TAG, "USB configuration failed", e)
            return false
        } finally {
        }
    }

    private suspend fun powerOutlet(): Boolean {
        if (findUsbDevice(usbManager, FTDI_VID, listOf(FTDI_PID_1, FTDI_PID_2)) != null) {
            Log.d(TAG, "FTDI already powered")
            return true
        }/*
         * 1. Power the outlet.
         *
         * this call blocks until the outlet is powered
         * or until its own timeout occurs.
         */
        Log.i(TAG, "Powering FTDI outlet")

        withContext(Dispatchers.IO) {
            OutletPowerManager().powerOn(
                applicationContext, OUTLET_POWER_TIMEOUT_MS
            )
        }

        /*
         * 2. Wait for Android USB host to enumerate FTDI.
         */
        Log.i(TAG, "Waiting for FTDI")

        val ftdiDevice = waitForUsbDevice(
            FTDI_VID, listOf(FTDI_PID_1, FTDI_PID_2), DEVICE_ENUM_TIMEOUT_MS
        )

        if (ftdiDevice == null) {
            Log.e(TAG, "FTDI did not appear")
            return false
        }

        Log.i(TAG, "device detected: ${ftdiDevice.deviceName}")
        Log.i(TAG, "device is Ftdi : ${ftdiDevice.isFtdi()}")

        return true
    }

    private suspend fun setupFtdi(): Boolean {
        if (!powerOutlet()) {
            return false
        }

        val ftdiDevice = findUsbDevice(
            usbManager, FTDI_VID, listOf(FTDI_PID_1, FTDI_PID_2)
        ) ?: return false

        /*
         * 3. Get FTDI permission, only when necessary.
         */
        if (!ensureUsbPermission(ftdiDevice)) {
            Log.e(TAG, "FTDI permission not granted")
            return false
        }
        return true
    }

    private suspend fun setupSmartCardReader(): Boolean {
        if (!powerSmartCardReader()) {
            Log.e(TAG, "Unable to power smart card reader")
            return false
        }

        val sec1210USbDevice =
            findUsbDevice(usbManager, SEC1210_VID, listOf(SEC1210_PID)) ?: return false

        if (!ensureUsbPermission(sec1210USbDevice)) {
            Log.e(TAG, "SEC1210 permission not granted")
            return false
        }

        return true
    }

    /**
     * Waits for Android to enumerate a specific USB device.
     *
     * Polling here is intentional:
     * this Activity only lives for a few seconds and it makes
     * enumeration after power-on very simple.
     */
    private suspend fun waitForUsbDevice(
        vendorId: Int, productIdList: List<Int>, timeoutMs: Long
    ): UsbDevice? = withTimeoutOrNull(timeoutMs) {
        var device: UsbDevice? = null

        while (device == null) {
            device = findUsbDevice(usbManager, vendorId, productIdList)

            if (device == null) {
                delay(POLL_DELAY_MS)
            }
        }
        device
    }

    /**
     * Returns true if permission exists or if the user grants it.
     *
     * If the user ignores the permission dialog for too long,
     * this returns false and the Activity terminates.
     */
    private suspend fun ensureUsbPermission(
        device: UsbDevice
    ): Boolean {

        if (usbManager.hasPermission(device)) {
            Log.i(
                TAG, "Permission already granted for ${device.deviceName}"
            )
            return true
        }

        Log.i(
            TAG, "Requesting permission for ${device.deviceName}"
        )

        val deferred = CompletableDeferred<Boolean>()

        pendingPermission = PendingPermission(
            deviceId = device.deviceId, result = deferred
        )

        val permissionIntent = Intent(usbPermissionAction).setPackage(packageName)

        val pendingIntent = PendingIntent.getBroadcast(
            this, 0, permissionIntent, PendingIntent.FLAG_MUTABLE
        )

        usbManager.requestPermission(
            device, pendingIntent
        )

        val granted = try {
            withTimeoutOrNull(PERMISSION_TIMEOUT_MS) {
                deferred.await()
            } ?: false

        } finally {
            if (pendingPermission?.deviceId == device.deviceId) {
                pendingPermission = null
            }
        }

        if (!granted) {
            Log.w(
                TAG, "Permission denied or timed out for ${device.deviceName}"
            )
        }

        return granted
    }

    var ftDevice: FT_Device? = null

    /**
     * FTDI CBUS command that powers the smart card reader.
     */
    private suspend fun powerSmartCardReader(): Boolean = withContext(Dispatchers.IO) {
        if (findUsbDevice(usbManager, SEC1210_VID, listOf(SEC1210_PID)) != null) {
            Log.d(TAG, "Smart Card reader already powered")
            return@withContext true
        }

        if (!FtdiManager.powerSmartCardReader(context = this@UsbConfigurationActivity)) {
            Log.e(TAG, "Error while powering Smartcard reader with FTDI")
            return@withContext false
        }

        Log.i(TAG, "Waiting for SEC1210")

        val sec1210 = waitForUsbDevice(
                SEC1210_VID, listOf(SEC1210_PID), DEVICE_ENUM_TIMEOUT_MS
            )

        if (sec1210 == null) {
            Log.e(TAG, "SEC1210 did not appear")
            return@withContext false
        }

        Log.i(TAG, "SEC1210 detected: ${sec1210.deviceName}")

        return@withContext true

    }

    private fun registerPermissionReceiver() {
        ContextCompat.registerReceiver(
            this,
            usbPermissionReceiver,
            IntentFilter(usbPermissionAction),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun getUsbDevice(
        intent: Intent
    ): UsbDevice? {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(
                UsbManager.EXTRA_DEVICE, UsbDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(
                UsbManager.EXTRA_DEVICE
            )
        }
    }

}

