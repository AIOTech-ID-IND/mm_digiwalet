package com.waleta.mmwaleta;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.io.IOException;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final String ACTION_USB_PERMISSION = "com.example.USB_PERMISSION";
    private static final int READ_WAIT_MILLIS = 100;

    private UsbSerialPort port;
    private TextView sensorDataTextView;

    // BroadcastReceiver untuk menangani izin USB
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        // Jika izin diberikan, buka koneksi port serial
                        if (device != null) {
                            setupUsbConnection(device);
                        }
                    } else {
                        Log.e(TAG, "USB permission denied");
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sensorDataTextView = findViewById(R.id.sensorDataTextView);
        Button connectButton = findViewById(R.id.connectButton);

        connectButton.setOnClickListener(v -> startUsbConnection());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Mendaftarkan BroadcastReceiver untuk menangani izin USB
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        registerReceiver(usbReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Meng-unregister receiver ketika aktivitas tidak lagi aktif
        unregisterReceiver(usbReceiver);
    }

    private void startUsbConnection() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);

        if (availableDrivers.isEmpty()) {
            Log.e(TAG, "No USB drivers available");
            return;
        }

        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDevice device = driver.getDevice();
        UsbDeviceConnection connection = manager.openDevice(device);

        if (connection == null) {
            // Jika tidak ada koneksi, minta izin untuk mengakses perangkat USB
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            manager.requestPermission(device, permissionIntent);
        } else {
            // Jika sudah diberi izin, buka port serial
            setupUsbConnection(device);
        }
    }

    private void setupUsbConnection(UsbDevice device) {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        UsbSerialDriver driver = UsbSerialProber.getDefaultProber().findAllDrivers(manager).get(0);
        UsbDeviceConnection connection = manager.openDevice(device);

        if (connection != null) {
            port = driver.getPorts().get(0); // Ambil port pertama
            try {
                port.open(connection);
                port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
                Log.i(TAG, "Serial Port Opened Successfully");
                readData();
            } catch (IOException e) {
                Log.e(TAG, "Error opening port: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "Connection to USB device failed");
        }
    }

    private void readData() {
        new Thread(() -> {
            byte[] response = new byte[1024]; // Buffer untuk data yang diterima
            try {
                while (true) {
                    if (port != null && port.isOpen()) {
                        int len = port.read(response, 0);
                        if (len > 0) {
                            String sensorData = new String(response, 0, len).trim();

                            // Cek panjang string setelah trim
                            if (sensorData.length() > 2) {
                                runOnUiThread(() -> sensorDataTextView.setText(sensorData));
                                Log.i(TAG, "Data received: " + sensorData);
                            } else {
                                Log.i(TAG, "Data too short, ignored: " + sensorData);
                            }
                        }
                    } else {
                        Log.e(TAG, "Port is closed or not initialized");
                        break;
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Error reading from port: " + e.getMessage());
            }
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (port != null) {
            try {
                port.close();
                Log.i(TAG, "Port closed successfully");
            } catch (IOException e) {
                Log.e(TAG, "Error closing port: " + e.getMessage());
            }
        }
    }
}
