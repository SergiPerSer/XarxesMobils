package com.example.myfirstapp2026;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.view.View;
import android.os.Bundle;
import android.widget.*;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import android.bluetooth.BluetoothAdapter;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import android.os.Bundle;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    public static Boolean bluetoothActive = false;
    public static int velocity;
    Button conButton;
    Button desButton;
    Button forButton;
    Button backButton;
    Button leftButton;
    Button turnleftButton;
    Button rightButton;
    Button turnrightButton;
    TextView robotStatus;
    TextView velValue;
    Button stopButton;
    LinearLayout discoveredDevices;
    SeekBar velBar;
    private BluetoothSocket bluetoothSocket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private final ExecutorService connectionExecutor = Executors.newSingleThreadExecutor();
    private static final String COMMAND_FORWARD = "w";
    private static final String COMMAND_BACKWARD = "s";
    private static final String COMMAND_TURN_LEFT = "a";
    private static final String COMMAND_TURN_RIGHT = "d";
    private static final String COMMAND_LATERAL_LEFT = "q";
    private static final String COMMAND_LATERAL_RIGHT = "e";
    private static final String COMMAND_STOP = "0";
    private boolean receiverRegistered = false;
    private ActivityResultLauncher<Intent> enableBluetoothLauncher;
    private ActivityResultLauncher<String> bluetoothPermissionLauncher;
    private ActivityResultLauncher<String> discoveryPermissionLauncher;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ArrayList<BluetoothDevice> deviceList = new ArrayList<BluetoothDevice>();
    public BluetoothAdapter bluetooth = BluetoothAdapter.getDefaultAdapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Inicializa las vistas aquí
        conButton = findViewById(R.id.conButton);
        desButton = findViewById(R.id.desButton);
        forButton = findViewById(R.id.forButton);
        backButton = findViewById(R.id.backButton);
        leftButton = findViewById(R.id.leftButton);
        rightButton = findViewById(R.id.rightButton);
        turnleftButton = findViewById(R.id.turnleftButton);
        turnrightButton = findViewById(R.id.turnrightButton);
        stopButton = findViewById(R.id.stopButton);
        robotStatus = findViewById(R.id.robotStatus);
        velBar = findViewById(R.id.velBar);
        velValue = findViewById(R.id.velValue);
        updateVelocityLabel(velBar.getProgress());
        velBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                updateVelocityLabel(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // No hace falta ninguna acción al empezar a mover la barra.
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // La etiqueta ya refleja el valor actual de la barra.
            }
        });

        discoveredDevices = findViewById(R.id.discoveredDevices);

        forButton.setOnClickListener(view -> moveForward());
        backButton.setOnClickListener(view -> moveBackward());
        turnleftButton.setOnClickListener(view -> turnLeft());
        turnrightButton.setOnClickListener(view -> turnRight());
        leftButton.setOnClickListener(view -> moveLaterallyLeft());
        rightButton.setOnClickListener(view -> moveLaterallyRight());
        stopButton.setOnClickListener(view -> stopRobot());
        desButton.setOnClickListener(view -> disconnectFromRobot());

        enableBluetoothLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (bluetooth != null && bluetooth.isEnabled()) {
                        checkBluetooth();
                        startDiscovery();
                    } else {
                        robotStatus.setText("Bluetooth sigue desactivado");
                    }
                });

        // Prepara la solicitud del permiso Bluetooth
        bluetoothPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {
                            if (granted) {
                                checkBluetooth();
                                if (bluetooth != null && bluetooth.isEnabled()) {
                                    startDiscovery();
                                }
                            } else {
                                robotStatus.setText("Permiso Bluetooth denegado");
                            }
                        });
        discoveryPermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {
                            if (granted) {
                                startDiscovery();
                            } else {
                                Toast.makeText(
                                        this,
                                        "Se necesita permiso para buscar dispositivos Bluetooth",
                                        Toast.LENGTH_SHORT
                                ).show();
                            }
                        });
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    if (hasLocationPermission()) {
                        startDiscovery();
                    } else {
                        robotStatus.setText("Permiso de ubicación denegado");
                        Toast.makeText(
                                this,
                                "Android requiere permiso de ubicación para buscar Bluetooth en esta versión.",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
        // Este listener solo aplica el espacio de las barras del sistema
        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main),
                (v, insets) -> {
                    Insets systemBars =
                            insets.getInsets(WindowInsetsCompat.Type.systemBars());

                    v.setPadding(
                            systemBars.left,
                            systemBars.top,
                            systemBars.right,
                            systemBars.bottom);

                    return insets;
                });
    }
    public void onClickConnectButton(View view) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }

        if (bluetooth == null) {
            checkBluetooth();
            return;
        }
        if (!bluetooth.isEnabled()) {
            requestEnableBluetooth();
            return;
        }

        checkBluetooth();
        startDiscovery();
    }
    private void requestEnableBluetooth() {
        robotStatus.setText("Solicitando activar Bluetooth...");
        enableBluetoothLauncher.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
    }

    public void requestDiscoveryPermission(){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            discoveryPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN);
        }
    }

    private void checkBluetooth() {

        if (bluetooth == null) {
            robotStatus.setText("Este dispositivo no tiene Bluetooth");
            return;
        }

        try {
            if (!bluetooth.isEnabled()) {
                robotStatus.setText("Bluetooth está desactivado");
                bluetoothActive = false;
                return;
            }

            robotStatus.setText("Bluetooth activado: " + bluetooth.getName());
            bluetoothActive = true;
        } catch (SecurityException e) {
            robotStatus.setText("No se concedió el permiso Bluetooth");
        }
    }
    private boolean requiresLocationPermissionForDiscovery() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M;
    }

    private boolean hasLocationPermission() {
        if (!requiresLocationPermissionForDiscovery()) {
            return true;
        }
        boolean fineGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarseGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        return fineGranted || coarseGranted;
    }

    private void requestLocationPermission() {
        if (!requiresLocationPermissionForDiscovery()) {
            return;
        }
        locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
        });
    }

    private boolean hasDiscoveryPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    == PackageManager.PERMISSION_GRANTED;
        }

        return true;
    }
    private void startDiscovery(){
        if (requiresLocationPermissionForDiscovery() && !hasLocationPermission()) {
            requestLocationPermission();
            return;
        }
        if(!hasDiscoveryPermission()){
            requestDiscoveryPermission();
            return;
        }
        deviceList.clear();
        discoveredDevices.removeAllViews();
        addDeviceMessage("Buscando dispositivos...");

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                    this,
                    discoveryResult,
                    filter,
                    ContextCompat.RECEIVER_EXPORTED
            );
            receiverRegistered = true;
        }
        if (bluetooth.isDiscovering()) {
            Log.d("MyFirstAPP", "La búsqueda ya está en curso");
            return;
        }

        boolean ok = bluetooth.startDiscovery();
        Log.d("MyFirstAPP", "startDiscovery devolvió: " + ok);
        if(!ok){
            Toast.makeText(this,"Could not start discovery", Toast.LENGTH_SHORT).show();
        }
    }
    private void addDeviceMessage(String message) {
        TextView row = new TextView(this);
        row.setText(message);
        row.setTextSize(16);
        row.setPadding(12, 10, 12, 10);
        discoveredDevices.addView(row);
    }

    private void addDiscoveredDevice(BluetoothDevice device, String name) {
        Button row = new Button(this);
        row.setAllCaps(false);
        row.setText(name + "\n" + device.getAddress() + " — toca para conectar");
        row.setOnClickListener(view -> connectBD(device, name, row));
        discoveredDevices.addView(row);
    }

    private void connectBD(BluetoothDevice device, String name, Button deviceRow) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            showConnectionStatus("Falta el permiso Bluetooth para conectar.", Color.rgb(183, 28, 28));
            bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
            return;
        }

        deviceRow.setEnabled(false);
        deviceRow.setText("Conectando con " + name + "…");
        showConnectionStatus("Conectando con " + name + "…", Color.rgb(245, 124, 0));

        connectionExecutor.execute(() -> {
            BluetoothSocket newSocket = null;
            try {
                if (bluetooth != null && bluetooth.isDiscovering()) {
                    bluetooth.cancelDiscovery();
                }
                newSocket = device.createRfcommSocketToServiceRecord(
                        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
                newSocket.connect();
                InputStream newInputStream = newSocket.getInputStream();
                OutputStream newOutputStream = newSocket.getOutputStream();
                BluetoothSocket connectedSocket = newSocket;

                runOnUiThread(() -> {
                    closeCurrentSocket();
                    bluetoothSocket = connectedSocket;
                    inputStream = newInputStream;
                    outputStream = newOutputStream;
                    deviceRow.setEnabled(true);
                    deviceRow.setText("Conectado: " + name);
                    deviceRow.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(46, 125, 50)));
                    showConnectionStatus("Conexión establecida con " + name, Color.rgb(46, 125, 50));
                });
            } catch (Exception e) {
                Log.e("ERROR: connect", "No se pudo conectar con " + name, e);
                if (newSocket != null) {
                    try {
                        newSocket.close();
                    } catch (IOException closeError) {
                        Log.w("ERROR: connect", "No se pudo cerrar el socket fallido", closeError);
                    }
                }
                runOnUiThread(() -> {
                    deviceRow.setEnabled(true);
                    deviceRow.setText(name + " — conexión fallida. Toca para reintentar.");
                    deviceRow.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(198, 40, 40)));
                    showConnectionStatus(
                            "No se pudo conectar con " + name
                                    + ". Comprueba que esté emparejado y que admita Bluetooth clásico SPP.",
                            Color.rgb(183, 28, 28));
                    Toast.makeText(this, "Conexión Bluetooth fallida", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateVelocityLabel(int value) {
        velValue.setText("Velocidad: " + value);
    }

    private void moveForward() {
        sendRobotCommand(COMMAND_FORWARD, "Avanzando");
        setRobotVelocity(1);
    }

    private void moveBackward() {
        sendRobotCommand(COMMAND_BACKWARD, "Retrocediendo");
        setRobotVelocity(1);
    }

    private void turnLeft() {
        sendRobotCommand(COMMAND_TURN_LEFT, "Girando a la izquierda");
        setRobotVelocity(1);
    }

    private void turnRight() {
        sendRobotCommand(COMMAND_TURN_RIGHT, "Girando a la derecha");
        setRobotVelocity(1);
    }

    private void moveLaterallyLeft() {
        sendRobotCommand(COMMAND_LATERAL_LEFT, "Desplazándose lateralmente a la izquierda");
        setRobotVelocity(1);

    }

    private void moveLaterallyRight() {
        sendRobotCommand(COMMAND_LATERAL_RIGHT, "Desplazándose lateralmente a la derecha");
        setRobotVelocity(1);

    }

    private void setRobotVelocity(int velocity) {
        sendRobotCommand(String.valueOf(velocity), "Velocidad establecida: " + velocity);
    }

    private void stopRobot() {
        sendRobotCommand(COMMAND_STOP, "Orden de parada enviada");
    }

    private void sendRobotCommand(String command, String description) {
        if (bluetoothSocket == null || !bluetoothSocket.isConnected() || outputStream == null) {
            showConnectionStatus("No hay un robot conectado.", Color.rgb(183, 28, 28));
            Toast.makeText(this, "Conecta primero con un dispositivo", Toast.LENGTH_SHORT).show();
            return;
        }

        connectionExecutor.execute(() -> {
            try {
                OutputStream stream = outputStream;
                stream.write((command + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                stream.flush();
                runOnUiThread(() -> showConnectionStatus(
                        description, Color.rgb(46, 125, 50)));
            } catch (IOException | SecurityException e) {
                Log.e("Bluetooth", "No se pudo enviar el comando " + command, e);
                runOnUiThread(() -> showConnectionStatus(
                        "Error al enviar la orden. La conexión puede haberse perdido.",
                        Color.rgb(183, 28, 28)));
            }
        });
    }

    private void disconnectFromRobot() {
        BluetoothSocket socketToClose = bluetoothSocket;
        if (socketToClose == null) {
            showConnectionStatus("No hay un robot conectado.", Color.rgb(117, 117, 117));
            return;
        }

        showConnectionStatus("Desconectando…", Color.rgb(245, 124, 0));
        connectionExecutor.execute(() -> {
            try {
                if (outputStream != null && socketToClose.isConnected()) {
                    outputStream.write((COMMAND_STOP + "\n")
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    outputStream.flush();
                }
            } catch (IOException | SecurityException e) {
                Log.w("Bluetooth", "No se pudo enviar la orden de parada al desconectar", e);
            } finally {
                try {
                    socketToClose.close();
                } catch (IOException e) {
                    Log.w("Bluetooth", "No se pudo cerrar la conexión Bluetooth", e);
                }
                runOnUiThread(() -> {
                    if (bluetoothSocket == socketToClose) {
                        bluetoothSocket = null;
                        inputStream = null;
                        outputStream = null;
                    }
                    showConnectionStatus("Desconectado", Color.rgb(117, 117, 117));
                });
            }
        });
    }

    private void showConnectionStatus(String message, int color) {
        robotStatus.setText(message);
        robotStatus.setTextColor(color);
    }

    private void closeCurrentSocket() {
        if (bluetoothSocket != null) {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                Log.w("Bluetooth", "No se pudo cerrar el socket anterior", e);
            }
            bluetoothSocket = null;
            inputStream = null;
            outputStream = null;
        }
    }

    @Override
    protected void onDestroy() {
        closeCurrentSocket();
        connectionExecutor.shutdownNow();
        if (receiverRegistered) {
            unregisterReceiver(discoveryResult);
            receiverRegistered = false;
        }
        super.onDestroy();
    }


    BroadcastReceiver discoveryResult = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Log.d("MyFirstAPP", "Búsqueda iniciada");

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                Log.d("MyFirstAPP", "Búsqueda terminada");
                if (deviceList.isEmpty()) {
                    addDeviceMessage("No se encontraron dispositivos.");
                }

            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice remoteDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                String name = intent.getStringExtra(BluetoothDevice.EXTRA_NAME);
                int rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                if (remoteDevice != null) {
                    boolean alreadyListed = false;
                    for (BluetoothDevice device : deviceList) {
                        if (device.getAddress().equals(remoteDevice.getAddress())) {
                            alreadyListed = true;
                            break;
                        }
                    }
                    if (!alreadyListed) {
                        deviceList.add(remoteDevice);
                        String deviceName = name != null ? name : "Dispositivo sin nombre";
                        addDiscoveredDevice(remoteDevice, deviceName);
                    }
                }

                Log.d("MyFirstAPP", "Encontrado: " + name);
                Log.d("MyFirstAPP", "RSSI: " + rssi + " dBm");
            }
        }
    };

}