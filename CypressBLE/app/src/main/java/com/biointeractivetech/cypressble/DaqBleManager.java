package com.biointeractivetech.cypressble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static android.os.SystemClock.sleep;

/**
 * Created by Sohail on 2017-04-17.
 */

/**
 * Class for initiating, and managing connection to BIT DAQ device
 * over bluetooth low energy APIs provided by Android
 */
public class DaqBleManager {
    private Context appContext;

    private CypressInterface callback;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt;
    private int mConnectionState = STATE_DISCONNECTED;

    public static final int STATE_DISCONNECTED = 0;
    public static final int STATE_CONNECTING = 1;
    public static final int STATE_CONNECTED = 2;
    public static final int STATE_DISCONNECTING = 3;
    public static final int STATE_SERVICES_DISCOVERED = 4;

    private static final UUID UUID_CUSTOM_SERIAL_PORT_PROFILE_RX = UUID.fromString("0003cdd1-0000-1000-8000-00805f9b0131");
    private static final UUID UUID_CUSTOM_SERIAL_PORT_PROFILE_TX = UUID.fromString("0003cdd2-0000-1000-8000-00805f9b0131");

    private BluetoothGattCharacteristic characteristic_custom_serial_profile_tx = null;

    private final String LOG_TAG = "Daq BLE Manager";

    /**
     * Implement this interface to listen to the data received from DAQ
     */
    public interface CypressInterface{
        /**
         * reports the bluetooth connection state
         * see the STATE_<description> variables for examples
         * @param state
         */
        void bleConnectionStateUpdate(int state);

        /**
         * called when a fsr data packet is received
         * @param time time in milliseconds received from DAQ
         * @param data array of data containing fsr pressure value
         */
        void fsrDataRecieved(int time, int data[]);

        /**
         * called when imu data packet is received
         * @param time time in milliseconds received from DAQ (not currently implemented)
         * @param roll roll in degrees
         * @param pitch pitch in degrees
         * @param yaw yaw in degrees
         */
        void imuDataRecieved(int time, float roll, float pitch, float yaw);

        /**
         * Emulates a Bluetooth SPP profile
         * @param data ascii string received from DAQ
         */
        void uartDataRecieved(String data);
    }

    /**
     * Call once at the start of application
     * @param callback_interface interface to listen to incoming data from DAQ
     * @param manager Bluetooth manager instance derived from getSystemService(Context.BLUETOOTH_SERVICE) in Android
     * @param android_context context of the application using this class
     * @return true if setup succeeds
     */
    public boolean create(CypressInterface callback_interface, BluetoothManager manager, Context android_context){
        Log.d(LOG_TAG, "create entry");
        callback = callback_interface;
        mBluetoothManager = manager;
        appContext = android_context;
        Log.d(LOG_TAG, "create exit");
        return initializeBluetooth();
    }

    /**
     * connect to a bluetooth device
     * @param mac_address hexdecimal mac address of device, for example 0D:58:40:2E:00:6C
     * @return true if connection request succeeds
     */
    public boolean connect(final String mac_address){
        if(!initializeBluetooth()){
            Log.d(LOG_TAG, "bluetooth init failed, cannot connect");
            return false;
        }

        if (mBluetoothDeviceAddress != null && mac_address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
            if (mBluetoothGatt.connect()) {
                Log.d(LOG_TAG, "Connection request complete for existing device");
                return true;
            } else {
                Log.d(LOG_TAG, "Connection request failed");
                return false;
            }
        }

        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(mac_address);
        if (device == null) {
            Log.d(LOG_TAG, "Cannot get remote device");
            return false;
        }

        mBluetoothGatt = device.connectGatt(appContext, false, mGattCallback);
        mBluetoothDeviceAddress = mac_address;
        Log.d(LOG_TAG, "Connection request compelete");
        return true;
    }

    /**
     * Disconnect from an existing connection without releasing all assets
     * @return true if successful
     */
    public boolean disconnect(){
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return false;
        }
        mBluetoothGatt.disconnect();

        return true;
    }

    /**
     * Destroy all assets, will need to call create again before use
     */
    public void destroy(){
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }

    /**
     * get the bluetooth connection status
     * @return integer number represented by STATE_<description> finals defined in this class
     */
    public int getStatus(){
        return mConnectionState;
    }

    /**
     * Set the fsr data stream to enabled or disabled
     * @param enabled true to enable stream
     */
    public void setFsrData(boolean enabled){
        if(enabled){
            sendData("$fsr,enable;");
        }else{
            sendData("$fsr,disable;");
        }
    }

    /**
     * Set the imu data stream to enabled or disabled
     * @param enabled true to enabled stream
     */
    public void setImuData(boolean enabled){
        if(enabled){
            sendData("$imu,enable;");
        }else{
            sendData("$imu,disable;");
        }
    }

    /**
     * Set time delay between fsr data packets in stream
     * @param millis delay in milli seconds
     */
    public void setFsrDelay(int millis){
        sendData("$fsr,delay," + millis + ";");
    }

    /**
     * Set time delay between imu data packets in stream
     * @param millis delay in milli seconds
     */
    public void setImuDelay(int millis){
        sendData("$imu,delay," + millis + ";");
    }

    /**
     * Send ascii information modelled as Bluetooth SPP
     * @param data string ascii data to send to device
     */
    public void sendUartData(String data){
        sendData(data);
    }

    private boolean initializeBluetooth() {
        if (mBluetoothManager == null) {
            Log.d(LOG_TAG, "Bluetooth manager is null, failed init");
            return false;
        }

        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.d(LOG_TAG, "Bluetooth adapter is null, failed init");
            return false;
        }

        return true;
    }

    private void updateStatus(int state){
        mConnectionState = state;
        callback.bleConnectionStateUpdate(state);
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                mBluetoothGatt.discoverServices();
                updateStatus(STATE_CONNECTED);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                updateStatus(STATE_DISCONNECTED);
            } else if(newState == BluetoothProfile.STATE_CONNECTING){
                updateStatus(STATE_CONNECTING);
            } else if(newState == BluetoothProfile.STATE_DISCONNECTING){
                updateStatus(STATE_DISCONNECTING);
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(LOG_TAG, "Services discovered");
                updateStatus(STATE_SERVICES_DISCOVERED);
                List<BluetoothGattService> gattServices = mBluetoothGatt.getServices();
                for (BluetoothGattService gattService : gattServices) {
                    List<BluetoothGattCharacteristic> gattCharacteristics = gattService.getCharacteristics();
                    for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                        if(gattCharacteristic.getUuid().equals(UUID_CUSTOM_SERIAL_PORT_PROFILE_RX)){
                            Log.d(LOG_TAG, "Cypress UART Rx profile found");
                            setCharacteristicNotification(gattCharacteristic, true);
                        }else if(gattCharacteristic.getUuid().equals(UUID_CUSTOM_SERIAL_PORT_PROFILE_TX)){
                            Log.d(LOG_TAG, "Cypress UART Tx profile found");
                            characteristic_custom_serial_profile_tx = gattCharacteristic;
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                broadcastUpdate(characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            broadcastUpdate(characteristic);
        }
    };

    private void broadcastUpdate(final BluetoothGattCharacteristic characteristic) {
        if(UUID_CUSTOM_SERIAL_PORT_PROFILE_RX.equals(characteristic.getUuid())){
            final byte[] data_raw = characteristic.getValue();
            final int[] data = new int[data_raw.length];

            for(int i = 0; i < data.length; i++){
                if(data_raw[i] < 0){
                    data[i] = (~data_raw[i])+1;
                }else{
                    data[i] = data_raw[i];
                }
                data[i] = 0;
                data[i] |= data_raw[i] & 0x00ff;
            }

            if(data[0] == ('F' | 0x80) && data.length == 15){
                int time = 0;
                time += (data[1] << 24) & 0xFF000000;
                time += (data[2] << 16) & 0xFF0000;
                time += (data[3] << 8) & 0xFF00;
                time += (data[4]) & 0xFF;
                callback.fsrDataRecieved(time, Arrays.copyOfRange(data, 5, 15));

            }else if(data[0] == ('I' | 0x80) && data.length == 7){
                float yaw =     ((data_raw[2] << 8) | (data[1] & 0x000000FF))/10;
                float roll =    ((data_raw[4] << 8) | (data[3] & 0x000000FF))/10;
                float pitch =   ((data_raw[6] << 8) | (data[5] & 0x000000FF))/10;
                callback.imuDataRecieved(0, roll, pitch, yaw);

            }else{
                String parsed_data = "";
                for(int i = 0; i < data.length; i++){
                    parsed_data += ((char)data[i]);
                }
                callback.uartDataRecieved(parsed_data);

            }
        }
    }

    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            return;
        }

        mBluetoothGatt.setCharacteristicNotification(characteristic, enabled);
    }

    private void sendData(String str){
        Log.d(LOG_TAG, "Sending to remote: " + str);
        byte[] data = new byte[str.length()];
        for(int i = 0; i < data.length; i++){
            data[i] = (byte)str.charAt(i);
        }
        if(characteristic_custom_serial_profile_tx != null){
            characteristic_custom_serial_profile_tx.setValue(data);
            if(!mBluetoothGatt.writeCharacteristic(characteristic_custom_serial_profile_tx)){
                sleep(500);
                mBluetoothGatt.writeCharacteristic(characteristic_custom_serial_profile_tx);
            };
        }
    }
}
