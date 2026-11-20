package com.example.bluetoothserver;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BluetoothServerManager extends Thread {
    private static final String TAG = "BluetoothServerManager";
    private static final String SERVICE_NAME = "BTROBOTSERVERSIM";
    private static final UUID SERVICE_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private final BluetoothAdapter bluetoothAdapter;
    private final Handler handlerNetworkExecutorResult;
    private volatile boolean running = true;
    private BluetoothServerSocket bluetoothServerSocket;
    private BluetoothSocket connectedSocket;
    private InputStream inputStream;
    private OutputStream outputStream;

    public BluetoothServerManager(BluetoothAdapter bluetoothAdapter,
                                  Handler handlerNetworkExecutorResult) {
        super("BluetoothServerManager");
        this.bluetoothAdapter = bluetoothAdapter;
        this.handlerNetworkExecutorResult = handlerNetworkExecutorResult;
    }

    @Override
    public void run() {
        if (bluetoothAdapter == null) {
            showSocketStateMessage("BLUETOOTH NOT SUPPORTED");
            return;
        }

        try {
            bluetoothServerSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(
                    SERVICE_NAME, SERVICE_UUID);
            while (running) {
                showSocketStateMessage("WAITING FOR CONNECTION");
                BluetoothSocket socket = bluetoothServerSocket.accept();
                if (!running) {
                    closeQuietly(socket);
                    break;
                }
                connectedSocket = socket;
                showSocketStateMessage("CONNECTED: " + socket.getRemoteDevice().getName()
                        + " / " + socket.getRemoteDevice().getAddress());
                manageConnectedSocket(socket);
            }
        } catch (IOException | SecurityException e) {
            if (running) {
                Log.e(TAG, "Bluetooth server error", e);
                showSocketStateMessage("SOCKET ERROR: " + e.getMessage());
            }
        } finally {
            closeConnection();
            closeQuietly(bluetoothServerSocket);
            bluetoothServerSocket = null;
            running = false;
        }
    }

    private void manageConnectedSocket(BluetoothSocket socket) {
        try {
            inputStream = socket.getInputStream();
            outputStream = socket.getOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while (running && socket.isConnected()
                    && (bytesRead = inputStream.read(buffer)) != -1) {
                String received = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                showDisplayMessage(received);
            }
        } catch (IOException | SecurityException e) {
            if (running) {
                Log.e(TAG, "Bluetooth connection read error", e);
                showSocketStateMessage("CONNECTION ERROR: " + e.getMessage());
            }
        } finally {
            closeConnection();
            if (running) {
                showSocketStateMessage("CONNECTION CLOSED");
            }
        }
    }

    public synchronized void write(String data) throws IOException {
        if (outputStream == null || connectedSocket == null || !connectedSocket.isConnected()) {
            throw new IOException("No hay una conexión Bluetooth activa");
        }
        outputStream.write(data.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    public synchronized void closeConnection() {
        closeQuietly(connectedSocket);
        connectedSocket = null;
        inputStream = null;
        outputStream = null;
    }

    public void cancel() {
        running = false;
        closeConnection();
        closeQuietly(bluetoothServerSocket);
        bluetoothServerSocket = null;
    }

    private void showDisplayMessage(String displayMessage) {
        sendHandlerMessage(0, displayMessage);
    }

    private void showSocketStateMessage(String displayMessage) {
        sendHandlerMessage(1, displayMessage);
    }

    private void sendHandlerMessage(int messageType, String text) {
        if (handlerNetworkExecutorResult != null) {
            Message message = handlerNetworkExecutorResult.obtainMessage(messageType,
                    text.replace('_', ' '));
            message.sendToTarget();
        }
    }

    private void closeQuietly(AutoCloseable resource) {
        if (resource != null) {
            try {
                resource.close();
            } catch (Exception e) {
                Log.w(TAG, "Error closing Bluetooth resource", e);
            }
        }
    }
}
