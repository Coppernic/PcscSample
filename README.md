# PcscSample
Sample application for PC/SC reader on C-One2 e-ID and Access-ER HID.

## Prerequisites

CpcSystemServices shall be installed on your device.
Please install the last version available on FDroid available on www.coppernic.fr/fdroid.apk


## Set up

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
    implementation 'fr.coppernic.sdk.core:CpcCore:2.1.12'
    implementation 'fr.coppernic.sdk.pcsc:CpcPcsc:1.5.4'
// [...]
}

```

### Power management

 * Define used peripheral

 Each terminal (C-One2 e-ID, Access-ER HID, IdPlatform), can contain one or more Pcsc reader.
 Check their availability in technical specifications.

https://www.coppernic.fr/c-one-2-e-id/
https://www.coppernic.fr/access-er/
https://www.coppernic.fr/access-er-e-id/
https://www.coppernic.fr/id-platform/

C-One2 e-ID contains two peripherals available with PCSC library :
- Contactless card reader RFID Elyctis reader
- Contact card reader

Access-ER HID contains one peripheral available with PCSC library :
- Contactless card reader HID OMNIKEY 5127 CK-Mini

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

Current source code is using gradle flavors for different use cases :
- **hiddev** to use HID peripherals
- **smartcard** to use SmartCard peripherals
- **normal** to use legacy PcscSample code (default peripheral)

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

### Reader initialization

#### Create reader object
 * Declare a Scard object and instantiate

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
