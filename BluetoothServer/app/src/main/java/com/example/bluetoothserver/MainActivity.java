package com.example.bluetoothserver;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private TextView btDetails;
    private TextView statusConnect;
    private TextView receivedLabel;
    private TextView toBeSentLabel;
    private EditText sentContent;
    private ScrollView receivedScrollView;
    private LinearLayout receivedContainer;
    private Button acceptConnectionButton;
    private Button closeConnectionButton;
    private Button sendButton;
    private TextView btMac;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothServerManager bluetoothServerManager;
    private boolean bluetoothReady;
    private Handler bluetoothHandler;

    private final ActivityResultLauncher<String> bluetoothPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    checkBluetoothState();
                } else {
                    setBluetoothUnavailable("Permiso de Bluetooth denegado");
                }
            });

    private final ActivityResultLauncher<Intent> enableBluetoothLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    setBluetoothReady();
                } else {
                    setBluetoothUnavailable("Bluetooth no activado");
                }
            });

    private final ActivityResultLauncher<String> advertisePermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    requestDiscoverability();
                } else {
                    Toast.makeText(this, "Hace falta permiso para hacer visible el servidor",
                            Toast.LENGTH_LONG).show();
                    statusConnect.setText("Permiso BLUETOOTH_ADVERTISE denegado");
                    acceptConnectionButton.setEnabled(bluetoothReady);
                    closeConnectionButton.setEnabled(false);
                }
            });

    private final ActivityResultLauncher<Intent> discoverabilityLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() >= 0 && bluetoothReady) {
                    beginListeningForClient();
                } else {
                    statusConnect.setText("Visibilidad Bluetooth rechazada");
                    acceptConnectionButton.setEnabled(bluetoothReady);
                    closeConnectionButton.setEnabled(false);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        btDetails = findViewById(R.id.bt_details);
        btMac = findViewById(R.id.bt_mac);
        statusConnect = findViewById(R.id.status_connect);
        receivedLabel = findViewById(R.id.receivedLabel);
        toBeSentLabel = findViewById(R.id.outgoingMessageLabel);
        sentContent = findViewById(R.id.outgoingMessageContent);
        receivedScrollView = findViewById(R.id.receivedMessagesScrollView);
        receivedContainer = (LinearLayout) receivedScrollView.getChildAt(0);
        acceptConnectionButton = findViewById(R.id.ack_connect);
        closeConnectionButton = findViewById(R.id.can_connect);
        sendButton = findViewById(R.id.send_data);

        bluetoothHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message message) {
                String text = (String) message.obj;
                if (message.arg1 == 0) {
                    appendReceivedMessage(text);
                } else {
                    updateServerState(text);
                }
            }
        };

        acceptConnectionButton.setEnabled(false);
        closeConnectionButton.setEnabled(false);
        sendButton.setEnabled(false);
        sentContent.setEnabled(false);
        statusConnect.setText("NOT CONNECTED");

        acceptConnectionButton.setOnClickListener(view -> startBluetoothServer());
        closeConnectionButton.setOnClickListener(view -> stopBluetoothServer());
        sendButton.setOnClickListener(view -> sendMessage());

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBluetoothPermissionAndState();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void checkBluetoothPermissionAndState() {
        if (bluetoothAdapter == null) {
            setBluetoothUnavailable("Este dispositivo no admite Bluetooth");
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }
        checkBluetoothState();
    }

    private void checkBluetoothState() {
        if (bluetoothAdapter == null) {
            setBluetoothUnavailable("Este dispositivo no admite Bluetooth");
        } else if (bluetoothAdapter.isEnabled()) {
            setBluetoothReady();
        } else {
            enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        }
    }

    private void setBluetoothReady() {
        bluetoothReady = true;
        btDetails.setText("BT Name: " + bluetoothAdapter.getName());
        btMac.setText("BT MAC: " + bluetoothAdapter.getAddress());
        if (bluetoothServerManager == null) {
            statusConnect.setText("NOT CONNECTED");
            acceptConnectionButton.setEnabled(true);
            closeConnectionButton.setEnabled(false);
            sendButton.setEnabled(false);
        }
    }

    private void setBluetoothUnavailable(String message) {
        bluetoothReady = false;
        btDetails.setText(message);
        statusConnect.setText(message);
        acceptConnectionButton.setEnabled(false);
        closeConnectionButton.setEnabled(false);
        sendButton.setEnabled(false);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void startBluetoothServer() {
        if (!bluetoothReady || bluetoothAdapter == null || bluetoothServerManager != null) {
            return;
        }
        acceptConnectionButton.setEnabled(false);
        statusConnect.setText("Solicitando visibilidad Bluetooth...");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE)
                != PackageManager.PERMISSION_GRANTED) {
            advertisePermissionLauncher.launch(Manifest.permission.BLUETOOTH_ADVERTISE);
            return;
        }
        requestDiscoverability();
    }

    private void requestDiscoverability() {
        Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
        discoverabilityLauncher.launch(intent);
    }

    private void beginListeningForClient() {
        if (!bluetoothReady || bluetoothAdapter == null || bluetoothServerManager != null) {
            return;
        }
        closeConnectionButton.setEnabled(true);
        sendButton.setEnabled(false);
        sentContent.setEnabled(false);
        statusConnect.setText("Starting Bluetooth server...");
        bluetoothServerManager = new BluetoothServerManager(bluetoothAdapter, bluetoothHandler);
        bluetoothServerManager.start();
    }

    private void stopBluetoothServer() {
        if (bluetoothServerManager != null) {
            bluetoothServerManager.cancel();
            bluetoothServerManager = null;
        }
        statusConnect.setText("NOT CONNECTED");
        acceptConnectionButton.setEnabled(bluetoothReady);
        closeConnectionButton.setEnabled(false);
        sendButton.setEnabled(false);
        sentContent.setEnabled(false);
    }

    private void updateServerState(String state) {
        if (bluetoothServerManager == null) {
            return;
        }
        statusConnect.setText(state);
        String normalized = state.toUpperCase();
        boolean connected = normalized.startsWith("CONNECTED:");
        boolean waiting = normalized.startsWith("WAITING FOR CONNECTION");
        boolean failed = normalized.startsWith("SOCKET ERROR")
                || normalized.startsWith("BLUETOOTH NOT SUPPORTED");

        sendButton.setEnabled(connected);
        sentContent.setEnabled(connected);
        closeConnectionButton.setEnabled(bluetoothServerManager != null);

        if (failed) {
            bluetoothServerManager = null;
            acceptConnectionButton.setEnabled(bluetoothReady);
            closeConnectionButton.setEnabled(false);
            sendButton.setEnabled(false);
            sentContent.setEnabled(false);
        } else if (waiting || connected) {
            acceptConnectionButton.setEnabled(false);
        }
    }

    private void appendReceivedMessage(String text) {
        TextView messageView = new TextView(this);
        messageView.setText(text);
        receivedContainer.addView(messageView);
        receivedScrollView.post(() -> receivedScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void sendMessage() {
        if (bluetoothServerManager == null) {
            return;
        }
        String text = sentContent.getText().toString();
        if (text.trim().isEmpty()) {
            Toast.makeText(this, "Escribe un mensaje", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            bluetoothServerManager.write(text);
        } catch (IOException e) {
            Toast.makeText(this, "No se pudo enviar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (bluetoothServerManager != null) {
            bluetoothServerManager.cancel();
            bluetoothServerManager = null;
        }
        super.onDestroy();
    }
}
