package fr.coppernic.samples.pcsc.ui;

import static fr.coppernic.sdk.power.OutletPowerManager.ACTION_USB_PERMISSION;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.weiwangcn.betterspinner.library.material.MaterialBetterSpinner;

import java.util.ArrayList;

import fr.coppernic.sample.pcsc.BuildConfig;
import fr.coppernic.sample.pcsc.R;
import fr.coppernic.samples.pcsc.reader.PcscReader;
import fr.coppernic.sdk.pcsc.ApduResponse;
import fr.coppernic.sdk.power.OutletPowerManager;
import fr.coppernic.sdk.utils.core.CpcBytes;
import fr.coppernic.sdk.utils.core.CpcResult;
import fr.coppernic.sdk.utils.core.CpcResult.RESULT;
import fr.coppernic.sdk.utils.ui.TextAppender;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    FloatingActionButton fab;
    Toolbar toolbar;
    MaterialBetterSpinner spReader;
    EditText etResult;
    SwitchCompat swConnect;
    EditText etApdu;

    private MenuItem itemClear;
    private PcscReader reader = null;
    private OutletPowerManager manager = new OutletPowerManager();

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

    //region Lifecycle
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

    @Override
    protected void onResume() {
        Timber.d("onResume");
        super.onResume();
        updateSpinner();
    }
    //endregion

    //region Menu
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
    //endregion

    //region PCSC
    public PcscReader getInstance(Context context) {
        if (reader != null) {
            return reader;
        }
        reader = new PcscReader(context);
        return reader;
    }

    private void startPcscReader() {
        reader = getInstance(getApplicationContext());
        updateSpinner();
    }

    private void stopPcscReader() {
        if (reader != null && reader.isConnected()) {
            reader.disconnect();
        }
    }
    //endregion


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


    // region Methods
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

    void connectCard() {
        if (reader != null) {
            if (!reader.isConnected()) {//Connect reader
                RESULT result = null;
                try {
                    result = reader.connect(spReader.getText().toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
                if (result != RESULT.OK) {
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
        } else {
            showMessage(getString(R.string.pcscReaderNotInitialized));
        }
    }

    void sendApdu() {
        try {
            String command = etApdu.getText().toString();
            addLog(getString(R.string.dataSend) + command);
            ApduResponse response = reader.sendApdu(CpcBytes.parseHexStringToArray(command));
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
            itemClear.setVisible(!etResult.getText().toString().isEmpty());
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
        if (reader == null) {
            Timber.d("PcscReader not initialized");
            return;
        }
        ArrayList<String> deviceList = reader.listReaders();
        if (deviceList != null) {
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line,
                    deviceList);
            spReader.setAdapter(arrayAdapter);
        }
    }
//endregion
}
