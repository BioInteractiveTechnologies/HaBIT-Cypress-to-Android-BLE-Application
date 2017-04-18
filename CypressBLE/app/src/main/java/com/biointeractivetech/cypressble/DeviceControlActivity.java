/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.biointeractivetech.cypressble;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONObject;

import static android.os.SystemClock.sleep;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";

    private TextView mConnectionState;
    private TextView mDataField;
    private TextView fsrDataField;
    private TextView imuDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private boolean mConnected = false;

    private EditText messageToSend;
    private Button messButton;

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // BROADCAST_ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, action);

            if(BluetoothLeService.BROADCAST_STATUS_GATT_CONNECTING.equals(action)){
                displayStatus("Connecting ...", Color.YELLOW);
            } else if (BluetoothLeService.BROADCAST_STATUS_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                invalidateOptionsMenu();
                messageToSend.setVisibility(View.VISIBLE);
                messButton.setVisibility(View.VISIBLE);
            } else if (BluetoothLeService.BROADCAST_STATUS_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                displayStatus("Disconnected", Color.RED);
                invalidateOptionsMenu();
                clearUI();
                messageToSend.setVisibility(View.INVISIBLE);
                messButton.setVisibility(View.INVISIBLE);

            } else if (BluetoothLeService.BROADCAST_STATUS_GATT_SERVICES_DISCOVERED.equals(action)) {
                displayStatus("Confirming Link ...", Color.YELLOW);
                _sendMessage("$send,info;");
                //_sendMessage("$debug,enable;");
            } else if (BluetoothLeService.BROADCAST_ACTION_DATA_AVAILABLE.equals(action)) {
                displayStatus("Connected", Color.BLUE);
                displayRawData(intent.getStringExtra(BluetoothLeService.BROADCAST_EXTRA_DATA));
            } else if (BluetoothLeService.BROADCAST_ACTION_JSON_DATA_AVAILABLE.equals(action)){
                displayStatus("Connected", Color.BLUE);
                try{
                    JSONObject obj = new JSONObject(intent.getStringExtra(BluetoothLeService.BROADCAST_EXTRA_DATA));
                    switch(obj.getString("message")) {
                        case "fsr data": {
                            displayFSRData(intent.getStringExtra(BluetoothLeService.BROADCAST_EXTRA_DATA));
                        }
                        break;

                        case "imu data": {
                            displayIMUData(intent.getStringExtra(BluetoothLeService.BROADCAST_EXTRA_DATA));
                        }
                        break;
                    }
                }catch(Exception e){

                }
            }
        }
    };


    private void clearUI() {
        displayRawData("");
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);

        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mConnectionState = (TextView) findViewById(R.id.connection_status);
        mDataField = (TextView) findViewById(R.id.data_value);
        messageToSend = (EditText) findViewById(R.id.message_to_send);
        messButton = (Button) findViewById(R.id.send_message);
        messageToSend.setVisibility(View.INVISIBLE);
        messButton.setVisibility(View.INVISIBLE);

        fsrDataField = (TextView) findViewById(R.id.fsr_data);
        imuDataField = (TextView) findViewById(R.id.imu_data);

        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);

        clearUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disconnectFromDevice();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:{
                connectToDevice(mDeviceAddress);
                return true;
            }
            case R.id.menu_disconnect:{
                disconnectFromDevice();
                return true;
            }
            case android.R.id.home:{
                onBackPressed();
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public void enableFSR(View view){
        _sendMessage("$real,enable;");
        sleep(2000);
        _sendMessage("$fsr,enable;");
        _sendMessage("$fsr,delay," + ((EditText) findViewById(R.id.fsr_frequency)).getText() + ";");
    }

    public void disableFSR(View view){
        _sendMessage("$fsr,disable;");
    }

    public void enableIMU(View view){
        _sendMessage("$real,enable;");
        sleep(2000);
        _sendMessage("$imu,enable;");
        _sendMessage("$imu,delay," + ((EditText) findViewById(R.id.fsr_frequency)).getText() + ";");
    }

    public void disableIMU(View view){
        _sendMessage("$imu,disable;");
    }

    public void _sendMessage(String message){
        final Intent intent = new Intent(BluetoothLeService.BROADCAST_ACTION_SEND_DATA);
        intent.putExtra(BluetoothLeService.BROADCAST_EXTRA_DATA, message);
        sendBroadcast(intent);
    }

    public void sendMessage(View view) {
        String message = messageToSend.getText().toString();
        _sendMessage(message);
    }

    public void connectToDevice(String adr){
        final Intent intent = new Intent(BluetoothLeService.BROADCAST_ACTION_GATT_CONNECT);
        intent.putExtra(BluetoothLeService.BROADCAST_EXTRA_DATA, adr);
        sendBroadcast(intent);
    }

    public void disconnectFromDevice(){
        final Intent intent = new Intent(BluetoothLeService.BROADCAST_ACTION_GATT_DISCONNECT);
        sendBroadcast(intent);
    }

    private void displayStatus(final String message, final int color){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setBackgroundColor(color);
                mConnectionState.setText(message);
            }
        });
    }

    private void displayFSRData(final String data){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fsrDataField.setText(data);
            }
        });
    }

    private void displayIMUData(final String data){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                imuDataField.setText(data);
            }
        });
    }

    private void displayRawData(final String data) {
        if (data != null) {
            String d = mDataField.getText().toString();
            String[] snips = data.split("\n");
            if(snips.length > 1){
                d = snips[snips.length-1];
            }else{
                d += data;
            }
            final String d2 = d;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mDataField.setText(d2);
                }
            });
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.BROADCAST_STATUS_GATT_CONNECTING);
        intentFilter.addAction(BluetoothLeService.BROADCAST_STATUS_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.BROADCAST_STATUS_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.BROADCAST_STATUS_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.BROADCAST_ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.BROADCAST_ACTION_JSON_DATA_AVAILABLE);
        return intentFilter;
    }
}
