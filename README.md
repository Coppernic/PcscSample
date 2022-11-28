# PcscSample
Sample application for PC/SC reader on C-One e-ID and Access-ER.

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

 Each terminal (COne, Access-ER, IdPlatform), can contain one or more Pcsc reader.
 Check their availability in technical specifications.

https://www.coppernic.fr/c-one-2-e-id/
https://www.coppernic.fr/access-er/
https://www.coppernic.fr/access-er-e-id/
https://www.coppernic.fr/id-platform/

```java

    private Peripheral getPeripheral() {
        if (OsHelper.isCone()) {
            return ConePeripheral.RFID_ELYCTIS_LF214_USB;
//            return ConePeripheral.PCSC_GEMALTO_CR30_USB;
//            return ConePeripheral.PCSC_MICROCHIP_SEC1210_USB;
        } else if (OsHelper.isIdPlatform()) {
            return IdPlatformPeripheral.SMARTCARD;
        } else if (OsHelper.isAccess()){
            return AccessPeripheral.RFID_HID_CK_MINI_USB;
        } else {
            return DummyPeripheral.NO_OP;
        }
    }

```

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
