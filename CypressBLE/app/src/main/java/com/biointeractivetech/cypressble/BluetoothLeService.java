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

import android.app.Service;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;

import static android.os.SystemClock.sleep;

/**
 * Service for managing connection and data communication with a GATT server hosted on a
 * given Bluetooth LE device.
 */
public class BluetoothLeService extends Service implements DaqBleManager.CypressInterface {
    private final static String TAG = BluetoothLeService.class.getSimpleName();

    DaqBleManager daqManager = new DaqBleManager();

    public final static String BROADCAST_STATUS_GATT_CONNECTED =              "MENRVA.bluetooth.le.STATUS_GATT_CONNECTED";
    public final static String BROADCAST_STATUS_GATT_CONNECTING =             "MENRVA.bluetooth.le.STATUS_GATT_CONNECTING";
    public final static String BROADCAST_STATUS_GATT_DISCONNECTED =           "MENRVA.bluetooth.le.STATUS_GATT_DISCONNECTED";
    public final static String BROADCAST_STATUS_GATT_DISCONNECTING =           "MENRVA.bluetooth.le.STATUS_GATT_DISCONNECTING";
    public final static String BROADCAST_STATUS_GATT_SERVICES_DISCOVERED =    "MENRVA.bluetooth.le.STATUS_GATT_SERVICES_DISCOVERED";

    public final static String BROADCAST_ACTION_GATT_CONNECT =                "MENRVA.bluetooth.le.ACTION_GATT_CONNECT";
    public final static String BROADCAST_ACTION_GATT_DISCONNECT =             "MENRVA.bluetooth.le.ACTION_GATT_DISCONNECT";
    public final static String BROADCAST_ACTION_DATA_AVAILABLE =              "MENRVA.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String BROADCAST_ACTION_SEND_DATA =                   "MENRVA.bluetooth.le.ACTION_SEND_DATA";
    public final static String BROADCAST_ACTION_GET_STATUS =                  "MENRVA.bluetooth.le.ACTION_GET_STATUS";
    public final static String BROADCAST_ACTION_JSON_DATA_AVAILABLE =         "MENRVA.bluetooth.le.ACTION_JSON_DATA_AVAILABLE";
    public final static String BROADCAST_ACTION_JSON_DATA_SEND =              "MENRVA.bluetooth.le.ACTION_JSON_SEND_DATA";
    public final static String BROADCAST_EXTRA_DATA =                         "MENRVA.bluetooth.le.EXTRA_DATA";



    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // BROADCAST_ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver clientIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            Log.d(TAG, action);

            if(action.equals(BROADCAST_ACTION_SEND_DATA)){
                String str = intent.getStringExtra(BROADCAST_EXTRA_DATA);
                Log.d(TAG, str);
                daqManager.sendUartData(str);
            }else if(action.equals(BROADCAST_ACTION_GATT_CONNECT)){
                if((daqManager.getStatus() != DaqBleManager.STATE_CONNECTED) && (daqManager.getStatus() != DaqBleManager.STATE_CONNECTING)){
                    String adr = intent.getStringExtra(BROADCAST_EXTRA_DATA);
                    daqManager.connect(adr);
                }else{
                    Log.d(TAG, "Already in connected state");
                    broadcastCurrentState();
                }
            }else if(action.equals(BROADCAST_ACTION_GATT_DISCONNECT)){
                daqManager.disconnect();
            }else if(action.equals(BROADCAST_ACTION_GET_STATUS)){
                broadcastCurrentState();
            }else if(action.equals(BROADCAST_ACTION_JSON_DATA_SEND)){
                Log.d(TAG, "JSON message = " + intent.getStringExtra(BROADCAST_EXTRA_DATA));
                try{
                    JSONObject jsonObj = new JSONObject(intent.getStringExtra(BROADCAST_EXTRA_DATA));
                    String message = "";
                    switch(jsonObj.getString("message")){
                        case "settings":{
                            if(jsonObj.has("enable fsr")){  daqManager.setFsrData(jsonObj.getBoolean("enable fsr"));}
                            if(jsonObj.has("fsr delay")){   daqManager.setFsrDelay(jsonObj.getInt("fsr delay"));}
                            if(jsonObj.has("enable imu")){  daqManager.setImuData(jsonObj.getBoolean("enable imu"));}
                            if(jsonObj.has("imu delay")){   daqManager.setImuDelay(jsonObj.getInt("imu delay"));}
                        }break;
                    }

                }catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    };

    private void broadcastCurrentState(){
        String action = "";
        switch(daqManager.getStatus()){
            case DaqBleManager.STATE_CONNECTED:{
                action = BROADCAST_STATUS_GATT_CONNECTED;
            }break;

            case DaqBleManager.STATE_CONNECTING:{
                action = BROADCAST_STATUS_GATT_CONNECTING;
            }break;

            case DaqBleManager.STATE_DISCONNECTED:{
                action = BROADCAST_STATUS_GATT_DISCONNECTED;
            }break;

            case DaqBleManager.STATE_DISCONNECTING:{
                action = BROADCAST_STATUS_GATT_DISCONNECTING;
            }break;

            case DaqBleManager.STATE_SERVICES_DISCOVERED:{
                action = BROADCAST_STATUS_GATT_SERVICES_DISCOVERED;
            }break;
        }

        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    @Override
    public void bleConnectionStateUpdate(int state) {
        broadcastCurrentState();
    }

    @Override
    public void fsrDataRecieved(int time, int[] data) {
        JSONObject jsonObj = new JSONObject();
        try{
            jsonObj.put("message", "fsr data");
            jsonObj.put("time", time);
            JSONArray array = new JSONArray();
            for(int j = 0; j < data.length; j++){
                array.put(j, data[j]);
            }
            jsonObj.put("fsr", array);
            Intent intent = new Intent(BROADCAST_ACTION_JSON_DATA_AVAILABLE);
            intent.putExtra(BROADCAST_EXTRA_DATA, jsonObj.toString());
            sendBroadcast(intent);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void imuDataRecieved(int time, float roll, float pitch, float yaw) {
        JSONObject jsonObj = new JSONObject();
        try{
            jsonObj.put("message", "imu data");
            jsonObj.put("yaw", yaw);
            jsonObj.put("roll", roll);
            jsonObj.put("pitch", pitch);
            Intent intent = new Intent(BROADCAST_ACTION_JSON_DATA_AVAILABLE);
            intent.putExtra(BROADCAST_EXTRA_DATA, jsonObj.toString());
            sendBroadcast(intent);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void uartDataRecieved(String data) {
        Intent intent = new Intent(BROADCAST_ACTION_DATA_AVAILABLE);
        intent.putExtra(BROADCAST_EXTRA_DATA, data);
        sendBroadcast(intent);
    }

    public class LocalBinder extends Binder {
        BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        close();
        return super.onUnbind(intent);
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public void onCreate() {
        if (daqManager.create(this, (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE), getApplicationContext())) {
            Log.d(TAG, "Initialization complete");
        }else{
            Log.e(TAG, "Unable to initializeBluetooth Bluetooth");
        }
        registerReceiver(clientIntentReceiver, clientIntentFilter());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    public void close() {
        daqManager.destroy();
        unregisterReceiver(clientIntentReceiver);
    }

    private static IntentFilter clientIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.BROADCAST_ACTION_SEND_DATA);
        intentFilter.addAction(BluetoothLeService.BROADCAST_ACTION_GATT_CONNECT);
        intentFilter.addAction(BluetoothLeService.BROADCAST_ACTION_GATT_DISCONNECT);
        intentFilter.addAction(BluetoothLeService.BROADCAST_ACTION_GET_STATUS);
        intentFilter.addAction(BluetoothLeService.BROADCAST_ACTION_JSON_DATA_SEND);
        return intentFilter;
    }
}
