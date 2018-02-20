package fr.coppernic.sample.pcsc.ui;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.weiwangcn.betterspinner.library.material.MaterialBetterSpinner;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;
import fr.coppernic.sample.pcsc.R;
import fr.coppernic.sample.pcsc.reader.PcscReader;
import fr.coppernic.sdk.pcsc.ApduResponse;
import fr.coppernic.sdk.power.PowerManager;
import fr.coppernic.sdk.power.api.PowerListener;
import fr.coppernic.sdk.power.api.peripheral.Peripheral;
import fr.coppernic.sdk.power.impl.cone.ConePeripheral;
import fr.coppernic.sdk.utils.core.CpcBytes;
import fr.coppernic.sdk.utils.core.CpcResult;
import fr.coppernic.sdk.utils.ui.TextAppender;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    @BindView(R.id.fab)
    FloatingActionButton fab;
    @BindView(R.id.tvMessage)
    TextView tvMessage;
    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.spReader)
    MaterialBetterSpinner spReader;
    @BindView(R.id.etResult)
    EditText etResult;
    @BindView(R.id.swConnect)
    SwitchCompat swConnect;
    @BindView(R.id.imgBtnClear)
    ImageButton imgBtnClear;
    @BindView(R.id.btnSend)
    Button btnSend;
    @BindView(R.id.etApdu)
    EditText etApdu;

    PcscReader reader;

    private final PowerListener powerListener = new PowerListener() {
        @Override
        public void onPowerUp(CpcResult.RESULT result, Peripheral peripheral) {
            if (peripheral == ConePeripheral.RFID_ELYCTIS_LF214_USB && result == CpcResult.RESULT.NOT_CONNECTED) {
                Timber.d("RFID reader powered on");
                ConePeripheral.PCSC_GEMALTO_CR30_USB.on(MainActivity.this);
            } else if (peripheral == ConePeripheral.PCSC_GEMALTO_CR30_USB && result == CpcResult.RESULT.OK) {
                Timber.d("Smart Card reader powered on");
                swConnect.setEnabled(true);
                showMessage(getString(R.string.pcsc_explanation));
                updateSpinner();
            } else {
                showMessage(getString(R.string.power_error));
            }
        }

        @Override
        public void onPowerDown(CpcResult.RESULT result, Peripheral peripheral) {
            Timber.d("Fp reader powered off");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            String versionName = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            setTitle(getString(R.string.app_name) + " " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        reader = new PcscReader(getApplicationContext());
        //Init empty spinner
        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, new ArrayList<String>());
        spReader.setAdapter(arrayAdapter);
        showBinIfNotEmpty();
        etResult.clearFocus();

        etApdu.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                boolean handled = false;
                if (i == EditorInfo.IME_ACTION_SEND) {
                    sendApdu();
                    handled = true;
                }
                return handled;
            }
        });
    }

    @Override
    protected void onStart() {
        Timber.d("onStart");
        super.onStart();
        showFAB(false);
        swConnect.setEnabled(false);
        PowerManager.get().registerListener(powerListener);
        showMessage(getString(R.string.wait_RFID_powered));
        powerOn(true);
    }

    @Override
    protected void onStop() {
        Timber.d("onStop");
        if (reader.isConnected()) {
            reader.disconnect();
            swConnect.setChecked(false);
        }
        powerOn(false);
        PowerManager.get().unregisterListener(powerListener);
        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        //getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }


    @OnClick(R.id.fab)
    void readCard() {
        if (reader.isConnected()) {
            addLog(getString(R.string.atr) + reader.getAtr());
        } else {
            showMessage(getString(R.string.connectReaderFirst));
        }
    }

    @OnClick(R.id.swConnect)
    void connectCard() {
        if (!reader.isConnected()) {//Connect reader
            CpcResult.RESULT result;
            if ((result = reader.connect(spReader.getText().toString())) != CpcResult.RESULT.OK) {
                addLog(getString(R.string.errorConnectingCard) + result.toString());
                swConnect.setChecked(false);
            } else {
                addLog(getString(R.string.cardDetected));
                showFAB(true);
            }
        } else {//Disconnect
            reader.disconnect();
            showFAB(false);
        }
    }

    @OnClick(R.id.imgBtnClear)
    void clear() {
        etResult.setText("");
    }

    @OnClick(R.id.btnSend)
    void sendApdu() {
        try {
            addLog(getString(R.string.dataSend) + etApdu.getText().toString());
            ApduResponse response = reader.sendApdu(etApdu.getText().toString());
            if (response.getStatus() != null) {
                addLog(getString(R.string.status) + CpcBytes.byteArrayToString(response.getStatus(), response.getStatus().length));
            } else {
                addLog(getString(R.string.noStatus));
            }
            if (response.getData() != null) {
                addLog(getString(R.string.dataReceived) + CpcBytes.byteArrayToString(response.getData(), response.getData().length));
            } else {
                addLog(getString(R.string.noData));
            }
        } catch (CpcResult.ResultException e) {
            addLog(e.getResult().toString() + e.getMessage());
        }
    }


    @OnTextChanged(R.id.etResult)
    void showBinIfNotEmpty() {
        if (etResult.getText().toString().isEmpty())
            imgBtnClear.setVisibility(View.INVISIBLE);
        else
            imgBtnClear.setVisibility(View.VISIBLE);
    }

    public void showMessage(String value) {
        tvMessage.setText(value);
    }

    public void addLog(String data) {
        etResult.post(new TextAppender(etResult, data + System.getProperty("line.separator")));
    }

    private void powerOn(boolean on) {
        if (on) {
            ConePeripheral.RFID_ELYCTIS_LF214_USB.on(this);
        } else {
            ConePeripheral.RFID_ELYCTIS_LF214_USB.off(this);
        }
    }

    public void showFAB(boolean value) {
        if (value) {
            fab.show();
            showMessage(getString(R.string.pressButton));

        } else {
            fab.hide();
            showMessage(getString(R.string.pcsc_explanation));
        }
        btnSend.setEnabled(value);
    }

    public void updateSpinner() {
        ArrayList<String> deviceList = reader.listReaders();
        if (deviceList != null) {
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this,
                    android.R.layout.simple_dropdown_item_1line, deviceList);
            spReader.setAdapter(arrayAdapter);
        }
    }
}
