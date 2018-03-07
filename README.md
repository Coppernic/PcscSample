# PcscSample
Sample application for PC/SC reader on C-One e-ID

## Prerequisites

CpcSystemServices shall be installed on your device.
Please install the last version available on FDroid available on www.coppernic.fr/fdroid.apk


## Set up

### build.gradle

```groovy
repositories {
    jcenter()
    maven { url 'https://artifactory.coppernic.fr/artifactory/libs-release' }
}


dependencies {
// [...]
    // Coppernic
    implementation 'fr.coppernic.sdk.cpcutils:CpcUtilsLib:6.14.0'
    implementation 'fr.coppernic.sdk.core:CpcCore:1.3.0'
    implementation 'fr.coppernic.sdk.pcsc:CpcPcsc:1.5.0'
// [...]
}

```

### Power management

 * Implements power listener

```java

  private final PowerListener powerListener = new PowerListener() {
        @Override
        public void onPowerUp(CpcResult.RESULT result, Peripheral peripheral) {
            if (peripheral == ConePeripheral.RFID_ELYCTIS_LF214_USB &&
                    (result == CpcResult.RESULT.NOT_CONNECTED || result == CpcResult.RESULT.OK)) {
                Timber.d("RFID reader powered on");
                ConePeripheral.PCSC_GEMALTO_CR30_USB.on(MainActivity.this);
            } else if (peripheral == ConePeripheral.PCSC_GEMALTO_CR30_USB && result == CpcResult.RESULT.OK) {
                Timber.d("Smart Card reader powered on");
                swConnect.setEnabled(true);
                showMessage(getString(R.string.pcsc_explanation));
                updateSpinner();
            }
            else{
                //Error while powering fingerprint
            }
        }

        @Override
        public void onPowerDown(CpcResult.RESULT result, Peripheral peripheral) {
           //FP reader power off
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
