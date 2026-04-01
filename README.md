# PcscSample

Sample application for PC/SC reader designed for C-One2 e-ID, IdPlatform, Access-ER HID, Access-ER e-ID Smartcard and HMD Fusion

## Prerequisites

Be aware that CoreServices shall be installed on Coppernic devices only, HMD devices *do not support* CoreService and should not be installed on the later.

Please install the lastest version of CopperApps on Coppernic devices (except for HMD devices), available at www.coppernic.fr/CopperApps.apk

## Set up

### PCSC Coppernic library

There are 2 implementations of the PCSC in Coppernic libraries: PCSC v1 and PCSC v2.

- PCSC v1 is compatible with all the devices (Coppernic and HMD)
- PCSC v2 must only be used on an Coppernic OS version later than 20240813 with CoreService installed (version 3.3.0 or later).

**PCSC v1** has a wide compatibility range but requires a card on the reader in order to establish a connection to the reader.

**PCSC v2** is compatible with the newest Coppernic OS and has to be coupled with CoreService. It allows to communicate with the reader without a card on the reader.


### build.gradle

```groovy
repositories {
    jcenter()
    maven { url 'https://nexus.coppernic.fr/repository/libs-release' }
}


dependencies {
// [...]
    // Coppernic
    implementation 'fr.coppernic.sdk.cpcutils:CpcUtilsLib:6.19.1'
    implementation "fr.coppernic.sdk.core:CpcCore:3.8.0"
    pcscv1Implementation 'fr.coppernic.sdk.pcsc:CpcPcsc:1.6.0'
    pcscv2Implementation "fr.coppernic.sdk.pcsc2:CpcCore:3.7.0"
// [...]
}

```

### Power management


Current source code is using gradle flavors for different use cases :
- **pcscv1**: to use a PC/SC peripherals on the the HMD Fusion devices, or on the Coppernic devices.

Here, the source code shown in `pcscv1` variant only covers the HMD devices (but can also be used with Coppernic devices with the dedicated CoreService power function as shown in `pcscv2` variant)
- **pcscv2**: to use a PC/SC peripherals on the latest Coppernic devices only.



#### Coppernic device (does NOT include HMD devices)
The Coppernic devices MUST have CoreService service to be installed to control the power of the peripherals at GPIO level and manage the USB permissions automatically.


Implementation of the Coppernic librarie are *required* on Coppernic devices:
```groovy
dependencies {
// [...]
    // Coppernic
    implementation 'fr.coppernic.sdk.cpcutils:CpcUtilsLib:6.19.1'
    implementation "fr.coppernic.sdk.core:CpcCore:3.8.0"
// [...]
}

```

 Each Coppernic terminal (C-One2 e-ID, Access-ER HID, IdPlatform, Access-ER e-ID Smartcard), can contain one or more Pcsc reader.
 Check their availability in technical specifications.

- https://www.coppernic.fr/c-one-2-e-id/
- https://www.coppernic.fr/access-er/
- https://www.coppernic.fr/access-er-e-id/
- https://www.coppernic.fr/id-platform/

C-One2 e-ID contains two peripherals available with PCSC library :
- Contactless card reader RFID Elyctis reader
- Contact card reader

Access-ER HID contains one peripheral available with PCSC library :
- Contactless card reader HID OMNIKEY 5127 CK-Mini

Access-ER e-ID Smartcard :
- Contact card reader & SAM reader

IdPlatform :
- Contact card reader & SAM reader


```java
    private Peripheral getPeripheral() {
        if (OsHelper.isCone()) {
            return ConePeripheral.RFID_ELYCTIS_LF214_USB; // Contactless card reader RFID Elyctis reader
//            return ConePeripheral.PCSC_GEMALTO_CR30_USB; // Default contact card reader
//            return ConePeripheral.PCSC_MICROCHIP_SEC1210_USB; // New contact card reader available on latest terminals. Contact Coppernic support for informations
        } else if (OsHelper.isIdPlatform()) {
            return IdPlatformPeripheral.SMARTCARD;
        } else if (OsHelper.isAccess()){
            return AccessPeripheral.RFID_HID_CK_MINI_USB; // Contactless card reader HID OMNIKEY 5127 CK-Mini
        } else {
            return DummyPeripheral.NO_OP;
        }
    }
```

A specific file is defined for each flavor to define which peripheral is used in : fr/coppernic/samples/pcsc/ui/TerminalPeripheral.java

* Implements power listener

```java

  private final PowerListener powerListener = new PowerListener() {
        @Override
        public void onPowerUp(CpcResult.RESULT result, Peripheral peripheral) {
            if ((peripheral == getPeripheral())
                    && ((result == RESULT.NOT_CONNECTED) || (result == RESULT.OK))) {
                Timber.d("Smart Card reader powered on");
                // (...)
                else{
                // Error while powering peripheral
            }
        }

        @Override
        public void onPowerDown(CpcResult.RESULT result, Peripheral peripheral) {
           // peripheral powered off
        }
    };

```

 * Register the listener

```java
@Override
    protected void onStart() {
// [...]
        PowerManager.get().registerListener(powerListener);
// [...]
    }
```

 * Power reader on

```java
// Powers on RFID reader
PowerManager.get().power(this, ConePeripheral.RFID_ELYCTIS_LF214_USB, true);
// The listener will be called with the result
```

 * Power off when you are done

```java
// Powers off RFID reader
PowerManager.get().power(this, ConePeripheral.RFID_ELYCTIS_LF214_USB, false);
// The listener will be called with the result
```

 * unregister listener resources

```java
@Override
    protected void onStop() {
// [...]
        PowerManager.get().unregisterListener(powerListener);
// [...]
    }
```

#### HMD device

The HMD devices have an OS not developed by Coppernic and CoreServices are not compatible with it.
The power management (through pogo pins) is done through dedicated proprietary Intents and require a precise USB authorisation management.

https://www.coppernic.fr/hmd-coppernic/


The CpcpCore library contains some helpers in OutletPowerManager class to launch the intents.
Implementation of the Coppernic libraries are _completely optional on HMD devices_.


Optional helpers for HMD devices:
```groovy
dependencies {
// [...]
    // Coppernic
    implementation 'fr.coppernic.sdk.cpcutils:CpcUtilsLib:6.19.1'
    implementation "fr.coppernic.sdk.core:CpcCore:3.8.0"
// [...]
}
```

* Implements power

HMD OS has a built in way to power on or off the pogo pins with Intents:

```kotlin
private const val ACTION_TURN_ON_VBUS = "action.vbus.authentic.on"
private const val ACTION_TURN_OFF_VBUS = "action.vbus.authentic.off"
private const val EXTRA_FORCE_BYPASS = "force_bypass"

Intent(ACTION_TURN_ON_VBUS).apply {
    putExtra(EXTRA_FORCE_BYPASS, true)
}.also {
    context.sendBroadcast(it)
}
```


But you can also use the helpers from OutletPowerManager class located in CpcCore:

```java
private OutletPowerManager manager = new OutletPowerManager();

void powerOn() {
    if (!manager.isPowerOn(getApplicationContext())) {
        Coroutines coroutines = new Coroutines();
        manager.powerOn(MainActivity.this, 10000L, coroutines.getContinuation(
            (res, err) -> {
                try {
                    Thread.sleep(500L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                manager.getUsbPermissions(MainActivity.this);
            }
        ));
    }
}
```


* Manage USB permission and USB devices

Carefully manage the USB device and USB permission like any regular USB device.

```java
    @Override
    protected void onStart() {
        Timber.d("onStart");
        super.onStart();
        showFAB(false);

        // Register USB receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter, RECEIVER_EXPORTED);

        powerOn();
    }

    @Override
    protected void onStop() {
        Timber.d("onStop");
        // Unregister USB receiver
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            e.printStackTrace();
        }
        stopPcscReader();
        super.onStop();
    }

    //region USB
    private BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equalsIgnoreCase(action)) {
                swConnect.setEnabled(true);
                startPcscReader();
            }
        }
    };
    //endregion
```

### Reader initialization

You have *2 versions* of the PCSCReader class: one in the `pcscv1` variant and on in the `pcscv2` variant.
Both of them share a very close method interface but are not 100% compatible.


#### Create reader object
 * Instantiate a `PcscReader` object (which contains a Scard object) or directly a Scard object

```java
   // PCSC
    private Scard sCard = null;
    sCard = new Scard();

```
 * List reader

```java
     ArrayList<String> deviceList = new ArrayList<>();
     CpcResult.RESULT result = sCard.establishContext(context);
     if (result == CpcResult.RESULT.OK) {
        result = sCard.listReaders(deviceList);
     }

```

### Read card

 * Connect to card and get ATR

```java
    CpcResult.RESULT result = sCard.connect(readerName, 0, 0);
    sCard.getAtr();
```

### Send PC/SC APDU command

 * Send APDU command

```java
    String apduCommand = "FFCA000000"//get Data
    byte[] apdu = CpcBytes.parseHexStringToArray(apduCommand);
    ApduResponse apduResponse = new ApduResponse();
    CpcResult.RESULT res = sCard.transmit(
        new ProtocolControlInformation(ProtocolControlInformation.Protocol.T0)
        , apdu
        , new ProtocolControlInformation(ProtocolControlInformation.Protocol.T0)
        , apduResponse);

    if (res != CpcResult.RESULT.OK) {
       //Error sending APDU
    }else{
      //get your response in apduResponse
    }
```
