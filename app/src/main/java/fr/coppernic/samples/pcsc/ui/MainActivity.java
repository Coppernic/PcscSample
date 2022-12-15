package fr.coppernic.samples.pcsc.ui;

import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.weiwangcn.betterspinner.library.material.MaterialBetterSpinner;

import java.util.ArrayList;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import fr.coppernic.sample.pcsc.BuildConfig;
import fr.coppernic.sample.pcsc.R;
import fr.coppernic.samples.pcsc.reader.PcscReader;
import fr.coppernic.sdk.pcsc.ApduResponse;
import fr.coppernic.sdk.power.PowerManager;
import fr.coppernic.sdk.power.api.PowerListener;
import fr.coppernic.sdk.power.api.peripheral.Peripheral;
import fr.coppernic.sdk.utils.core.CpcBytes;
import fr.coppernic.sdk.utils.core.CpcResult;
import fr.coppernic.sdk.utils.core.CpcResult.RESULT;
import fr.coppernic.sdk.utils.ui.TextAppender;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private static final String SMART_CARD_PERMISSION = "fr.coppernic.permission.SMART_CARD";
    private static final String RFID_PERMISSION = "fr.coppernic.permission.RFID";
    private static final int REQUEST_PERMISSION_CODE = 29;

    FloatingActionButton fab;
    Toolbar toolbar;
    MaterialBetterSpinner spReader;
    EditText etResult;
    SwitchCompat swConnect;
    EditText etApdu;

    private MenuItem itemClear;
    private PcscReader reader;

    private final PowerListener powerListener = new PowerListener() {
        @Override
        public void onPowerUp(RESULT result, Peripheral peripheral) {
            if ((peripheral == TerminalPeripheral.getPeripheral())
                    && ((result == RESULT.NOT_CONNECTED) || (result == RESULT.OK))) {
                Timber.d("Smart Card reader powered on");
                swConnect.setEnabled(true);
                //Wait for USB device to be ready
                SystemClock.sleep(1000);
                showMessage(getString(R.string.pcsc_explanation));
                updateSpinner();
            } else {
                Timber.e("Result %s, Peripheral %s", result.toString(), peripheral.toString());
                showMessage(getString(R.string.power_error));
            }
        }

        @Override
        public void onPowerDown(RESULT result, Peripheral peripheral) {
            Timber.d("RFID reader powered off");
        }
    };
    private final TextView.OnEditorActionListener editorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
            boolean handled = false;
            if (i == EditorInfo.IME_ACTION_SEND) {
                sendApdu();
                handled = true;
            }
            return handled;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initTitle();

        fab = findViewById(R.id.fab);
        toolbar = findViewById(R.id.toolbar);
        spReader = findViewById(R.id.spReader);
        etResult = findViewById(R.id.etResult);
        swConnect = findViewById(R.id.swConnect);
        etApdu = findViewById(R.id.etApdu);

        swConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connectCard();
            }
        });

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendApdu();
            }
        });

        etResult.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                showBinIfNotEmpty();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        setSupportActionBar(toolbar);

        reader = new PcscReader(getApplicationContext());
        //Init empty spinner
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<String>());
        spReader.setAdapter(arrayAdapter);
        etResult.clearFocus();

        etApdu.setOnEditorActionListener(editorActionListener);
    }

    @Override
    protected void onStart() {
        Timber.d("onStart");
        super.onStart();
        showFAB(false);
        PowerManager.get().registerListener(powerListener);
        powerOn(true);
        swConnect.setEnabled(true);
        updateSpinner();

//        if (!checkPermissions()) {
//            requestPermissions();
//        } else {
//            powerOn(true);
//        }
    }

    @Override
    protected void onStop() {
        Timber.d("onStop");
        if (reader.isConnected()) {
            reader.disconnect();
        }
        powerOn(false);
        PowerManager.get().unregisterListener(powerListener);
        super.onStop();
    }

//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
//        switch (requestCode) {
//            case REQUEST_PERMISSION_CODE: {
//                // If request is cancelled, the result arrays are empty.
//                if (grantResults.length > 0
//                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    powerOn(true);
//                } else {
//                    // For this sample, we ask permission again
//                    requestPermissions();
//                }
//            }
//        }
//    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        itemClear = menu.findItem(R.id.action_clear);
        showBinIfNotEmpty();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear:
                clear();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    void connectCard() {
        if (!reader.isConnected()) {//Connect reader
            RESULT result;
            if ((result = reader.connect(spReader.getText().toString())) != RESULT.OK) {
                addLog(getString(R.string.errorConnectingCard) + result.toString());
                swConnect.setChecked(false);
            } else {
                addLog(getString(R.string.cardDetected));
                addLog(getString(R.string.atr) + reader.getAtr());
                showFAB(true);
            }
        } else {//Disconnect
            reader.disconnect();
            addLog(getString(R.string.disconnected));
            showFAB(false);
        }
    }

    void sendApdu() {
        try {
            addLog(getString(R.string.dataSend) + etApdu.getText().toString());
            ApduResponse response = reader.sendApdu(etApdu.getText().toString());
            if (response.getStatus() != null) {
                addLog(getString(R.string.status) +
                        CpcBytes.byteArrayToString(response.getStatus(), response.getStatus().length));
            } else {
                addLog(getString(R.string.noStatus));
            }
            if (response.getData() != null) {
                addLog(getString(R.string.dataReceived) +
                        CpcBytes.byteArrayToString(response.getData(), response.getData().length));
            } else {
                addLog(getString(R.string.noData));
            }
        } catch (CpcResult.ResultException e) {
            addLog(e.getResult().toString() + e.getMessage());
        }
    }

    void showBinIfNotEmpty() {
        if (itemClear != null) {
            if (etResult.getText().toString().isEmpty()) {
                itemClear.setVisible(false);
            } else {
                itemClear.setVisible(true);
            }
        }
    }

    private void clear() {
        etResult.setText("");
    }

    private void initTitle() {
        setTitle(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
    }

    private void addLog(String data) {
        etResult.post(new TextAppender(etResult, data + System.getProperty("line.separator")));
    }

    private void powerOn(boolean on) {
        PowerManager.get().power(this, TerminalPeripheral.getPeripheral(), on);
    }

    private void showFAB(boolean value) {
        if (value) {
            fab.show();
        } else {
            fab.hide();
        }
        etApdu.setEnabled(value);
        swConnect.setChecked(value);
    }

    private void showMessage(String message) {
        Snackbar.make(this.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    private void updateSpinner() {
        ArrayList<String> deviceList = reader.listReaders();
        if (deviceList != null) {
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line,
                    deviceList);
            spReader.setAdapter(arrayAdapter);
        }
    }

//    private boolean checkPermissions() {
//        if (CpcOs.isConeN()) {
//            boolean scPermission = ContextCompat.checkSelfPermission(this, SMART_CARD_PERMISSION) == PackageManager.PERMISSION_GRANTED;
//            boolean rfidPermission = ContextCompat.checkSelfPermission(this, RFID_PERMISSION) == PackageManager.PERMISSION_GRANTED;
//
//            return scPermission && rfidPermission;
//        } else {
//            return true;
//        }
//    }
//
//    private void requestPermissions() {
//        if (ActivityCompat.shouldShowRequestPermissionRationale(this,
//                SMART_CARD_PERMISSION) || ActivityCompat.shouldShowRequestPermissionRationale(this,
//                RFID_PERMISSION)) {
//            // For this sample we do not display rationale, we just ask for permission if not granted
//            ActivityCompat.requestPermissions(this,
//                    new String[]{SMART_CARD_PERMISSION, RFID_PERMISSION},
//                    REQUEST_PERMISSION_CODE);
//        } else {
//            // No explanation needed; request the permission
//            ActivityCompat.requestPermissions(this,
//                    new String[]{SMART_CARD_PERMISSION, RFID_PERMISSION},
//                    REQUEST_PERMISSION_CODE);
//        }
//    }
}
