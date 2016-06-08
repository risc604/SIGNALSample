package com.esignal.signaldemo;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.esignal.signaldemo.BluetoothLeService.hexStringToByteArray;
import static java.lang.String.format;

public class MainActivity extends AppCompatActivity
{
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeService mBluetoothLeService;
    private LeDeviceListAdapter mLeDeviceListAdapter;

    private Context mContext;
    private PopupWindow mPopupWindow;
    private ProgressDialog progressDialog;
    private Button Btn_A0ack;
    private Button Btn_A1ack;
    private TextView mDataText;
    private TextView mConnect;
    private ScrollView mScroller;

    private Handler handler = new Handler();
    private boolean mScanning;
    private boolean mInitialFinished = false;
    private boolean OpenDialog = false;
    private boolean SearchBLE = false;
    private boolean BLUETOOTH_ENABLE = false;
    private boolean BLUETOOTH_RECONNECT = false;

    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;
    private static final int REQUEST_ENABLE_BT = 1;

    List<byte[]>    A0ReciveList = new LinkedList<>();

    private final ServiceConnection mServiceConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service)
        {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize())
            {
                BLUETOOTH_ENABLE = false;
                finish();
            }
            else
            {
                BLUETOOTH_ENABLE = true;
            }
            // Automatically connects to the device upon successful start-up initialization.
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName)
        {
            mBluetoothLeService = null;
            mInitialFinished = false;
        }
    };

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent intent)
        {
            //BLE Status Changed
            final String action = intent.getAction();

            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action))                //裝置連線成功
            {
                invalidateOptionsMenu();
                mConnect.setText("Connected");
                InsertMessage(mBluetoothLeService.mBluetoothGattAddress+" Connected");

                if(OpenDialog)
                {
                    OpenDialog=false;
                    progressDialog.dismiss();
                }
            }
            else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action))        //裝置斷線
            {
                invalidateOptionsMenu();
                mConnect.setText("Disconnected");
                InsertMessage(mBluetoothLeService.mBluetoothGattAddress+" Disonnected");

                if(OpenDialog)
                {
                    OpenDialog=false;
                    progressDialog.dismiss();
                }
            }
            else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action))
            {
                // Show all the supported services and characteristics on the user interface.
                invalidateOptionsMenu();
            }
            else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action))
            {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
                //displayData(intent.getByteArrayExtra(BluetoothLeService.EXTRA_DATA));
            }
            else if (BluetoothLeService.ACTION_GATT_DEVICE_DISCOVERED.equals(action))
            {
                DiscoverGattDevice( intent.getStringExtra(BluetoothLeService.ACTION_mBluetoothDeviceName),
                                    intent.getStringExtra(BluetoothLeService.ACTION_mBluetoothDeviceAddress));
            }
            else if (BluetoothLeService.ACTION_Enable.equals(action))
            {
                progressDialog.setMessage("Connected");
                //Notify Enabled & Send Command to BLE Device
                //CommandTest();
            }
            else if (BluetoothLeService.ACTION_Connect_Fail.equals(action))
            {}
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this.getApplicationContext();

        Btn_A0ack = (Button) findViewById(R.id.btn_A0ack);
        Btn_A1ack = (Button) findViewById(R.id.btn_A1ack);
        mDataText = (TextView) findViewById(R.id.DataText);
        mScroller = (ScrollView) findViewById(R.id.Scroller);
        mConnect = (TextView) findViewById(R.id.textView);

        mLeDeviceListAdapter = new LeDeviceListAdapter();
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                scanLeDevice(true);
                showPopupWindowEvent();
            }
        });

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "Please turn Bluetooth power", Toast.LENGTH_SHORT).show();
            finish();
        }

        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);
        startService(gattServiceIntent);

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        mBluetoothAdapter = bluetoothManager.getAdapter();
        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null)
        {
            finish();
            return;
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_COARSE_LOCATION))
            {
            }
            else
            {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSION_REQUEST_COARSE_LOCATION);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings)
        {
            return false;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[],
                                           int[] grantResults)
    {
        switch (requestCode)
        {
            case PERMISSION_REQUEST_COARSE_LOCATION:
            {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    //  Log.d(TAG, "coarse location permission granted");
                }
                else
                {
                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("Functionality limited");
                    builder.setMessage("Since location access has not been granted, this app will not be able to discover beacons when in the background.");
                    builder.setPositiveButton(android.R.string.ok, null);
                    builder.setOnDismissListener(new DialogInterface.OnDismissListener()
                    {
                        @Override
                        public void onDismiss(DialogInterface dialog)
                        {
                        }

                    });
                    builder.show();
                }
                return;
            }
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        // unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unbindService(mServiceConnection);

        mBluetoothLeService.close();
        mBluetoothLeService = null;
        if(handler!=null)   handler.removeCallbacks(updateTimer);
    }

    @Override
    protected void onResume()
    {
        super.onResume();

        A0ReciveList.clear();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled())
        {
            if (!mBluetoothAdapter.isEnabled())
            {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED)
        {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private static IntentFilter makeGattUpdateIntentFilter()
    {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DEVICE_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_Enable);
        intentFilter.addAction(BluetoothLeService.ACTION_Connect_Fail);

        return intentFilter;
    }

    private void scanLeDevice(final boolean enable)
    {
        if (enable)
        {
            mScanning = true;
            mBluetoothLeService.ScanDevice(true);
        }
        else
        {
            mScanning = false;
            mBluetoothLeService.ScanDevice(false);
        }
    }

    private void DiscoverGattDevice(String DeviceName, String DeviceAddress)
    {
        String Address = DeviceAddress.replaceAll(":", "");

        if (DeviceName == null)
        {
            DeviceName = "Unknow Device";
        }

        if (!SearchBLE && (!BLUETOOTH_RECONNECT || !OpenDialog))
        {
            mLeDeviceListAdapter.addDevice(DeviceName, DeviceAddress);
            mLeDeviceListAdapter.notifyDataSetChanged();
        }
    }

    public void cmdA1ack(View v)    // for Button A1ack onClick()
    {
        CommandTest((byte) 0xA1);
    }

    public void cmdA0ack(View v)    // for Button A0ack onClick()
    {
        CommandTest((byte) 0xA0);
    }

    private void displayGattServices(List<BluetoothGattService> gattServices)
    {
        if (gattServices == null) return;
    }

    private void CommandTest(byte command)
    {
        byte[] testCommand = new byte[0];

        if(!mBluetoothLeService.mBluetoothGattConnected)
            return;

        switch ((byte)command)
        {
            case (byte) 0xA0:
                testCommand = new byte[]{0x4D, (byte) 0xFE, 0x00, 0x02, (byte) 0x81, (byte) 0xCE};
                break;

            case (byte) 0xA1:
                testCommand = Utils.mlcTestCommand((byte)0x01);
                break;

            default:
                break;
        }
        //byte[] testCommand = Utils.mlcTestCommand(command);
        mBluetoothLeService.writeCharacteristicCMD(testCommand);
        //mBluetoothLeService.
        //mBluetoothLeService.writeCharacteristicCMD(Utils.mlcTestCommand((byte) 0xA0));
        Log.d("Cmd ", "Write Command to device.");

        //LogDebugShow("Command", testCommand);   //debug.
        //String tmp = new String(testCommand);   //debug
        //InsertMessage(tmp);
        StringBuilder sb= new StringBuilder(testCommand.length);
        for (byte indx: testCommand)
        {
            sb.append(format("%02X", indx));
        }
        Log.d("Cmd ", "Write Command to NC150: " + sb.toString());
        InsertMessage("Cmd:" + sb.toString());
    }

    private void LogDebugShow(String info, byte[] data)
    {
        for (int i=0;i<data.length; i++)
        {
            Log.d(info, " data [" + i + "]= " + format("0x%02X",data[i]));
        }
    }

    /*
    private void displayData(byte[] data)
    {
        //byte[] byteArray = hexStringToByteArray(data);

        if ((data.length>0) && (data != null))
        {
            LogDebugShow("dD()", data);
            InsertMessage(String.valueOf(data));

            switch (data[0])
            {
                case 0x4d:
                    byte[] tmp= {(byte) 0x81};
                    mBluetoothLeService.writeCharacteristicCMD(tmp);
                    mBluetoothLeService.broadcastUpdate(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
                    break;

                default:
                    break;

            }
        }
    }
    */

    /*
    private void CommandTest()
    {
        if(!mBluetoothLeService.mBluetoothGattConnected)return;

        byte[]SendByte=new byte[5];

        SendByte[0] = (byte) 0x01;
        SendByte[1] = (byte) 0x02;
        SendByte[2] = (byte) 0x03;
        SendByte[3] = (byte) 0x04;
        SendByte[4] = (byte) 0x05;

        mBluetoothLeService.writeCharacteristicCMD(SendByte);
        InsertMessage("Send 0x01,0x02,0x03,0x04,0x05");
    }
    */

    private void displayData(String data)
    {
        if (data != null)
        {
            byte[] byteArray = hexStringToByteArray(data);
            Log.d("display Data", "device: " + data);

            if (byteArray[0] == 'M')
            {
                InsertMessage("Rev:" + data);
                Log.d("Dd()", " bA[4]: " + format("%02X", byteArray[4]) + ": " + mBluetoothLeService.mBluetoothGattConnected);

                if ((byteArray[4] == 0xA0) && (mBluetoothLeService.mBluetoothGattConnected) )
                {
                    A0ReciveList.add(byteArray);
                    LogDebugShow("Dd()", byteArray);
                    Log.d("Dd()", "A0 List Size: " + A0ReciveList.size());
                }

                //byte[] tmp = {(byte) 0x81};
                //mBluetoothLeService.writeCharacteristicCMD(tmp);
                //CommandTest();
                //mBluetoothLeService.broadcastUpdate(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
                //Log.d("dD()", "set command.");
            }

            if(OpenDialog)
            {
                OpenDialog = false;
                progressDialog.cancel();
            }

            //String tmp = new String(byteArray);
            //InsertMessage("Byte: " + tmp);
            //LogDebugShow("display 2 Data ", byteArray);

            //if ((byteArray[0] == 'M') && (byteArray[3]))
        }
    }


    @Override
    public void onStop()
    {
        super.onStop();
    }

    private void showPopupWindowEvent()
    {
        dismissPopupWindow();
        View    rootView = getLayoutInflater().inflate(R.layout.popup_window, null);
        Display display = getWindowManager().getDefaultDisplay();
        Point   size = new Point();
        display.getSize(size);
        int width = size.x;
        int height = size.y;

        ListView popupList = (ListView) rootView.findViewById(R.id.popup_list);
        Button popupButton = (Button) rootView.findViewById(R.id.button11);
        popupButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                scanLeDevice(false);
                //mDataText.setText("");
                dismissPopupWindow();
            }
        });

        mLeDeviceListAdapter.clear();
        popupList.setAdapter(mLeDeviceListAdapter);
        popupList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dismissPopupWindow();
                mDataText.setText("");
                OpenDialog("BLE Status", "Try to connect with device...");
                scanLeDevice(false);
                mBluetoothLeService.connect(mLeDeviceListAdapter.getDevice(position));
            }
        });

        mPopupWindow = new PopupWindow(rootView, 2 * width / 3, ActionBar.LayoutParams.WRAP_CONTENT, false);
        mPopupWindow.setAnimationStyle(R.style.popup_window);
        mPopupWindow.setOutsideTouchable(false);
        mPopupWindow.setFocusable(false);
        mPopupWindow.update();
        mPopupWindow.showAtLocation(getLayoutInflater().inflate(R.layout.activity_main, null),
                                    Gravity.CENTER, 0, 0);
    }

    private void dismissPopupWindow()
    {
        if (mPopupWindow != null)
        {
            mPopupWindow.dismiss();
        }
    }

    /*
    public static byte[] hexStringToByteArray(String s)
    {
        int getLength = s.length() % 2;

        if(getLength!=0)
        {
            s="0"+s;
        }

        int len = s.length();

        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2)
        {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4) +
                                    Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
    */

    private void OpenDialog(String Title, String Meaasge)
    {
        if (!OpenDialog)
        {
            OpenDialog = true;
            progressDialog = new ProgressDialog(this);
            progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Cancel",
                    new DialogInterface.OnClickListener()
                    {
                        @Override
                        public void onClick(DialogInterface dialog, int which)
                        {
                        //    updateRemoveNodeState(RemoveNodeAppEvent.USER_CANCEL);
                        }
                    });
            progressDialog.setCancelable(false);
            progressDialog.setTitle(Title);
            progressDialog.setMessage(Meaasge);
            progressDialog.show();
        }
        else
        {
            progressDialog.setTitle(Title);
            progressDialog.setMessage(Meaasge);
        }
    }

    private void InsertMessage(String Message)
    {
        mDataText.setText(mDataText.getText()+Message+"\r\n");
        //mDataText.append(Message);
        mScroller.fullScroll(ScrollView.FOCUS_DOWN);
    }

    private Runnable updateTimer = new Runnable()
    {
        public void run()
        {
            handler.postDelayed(this, 1000);
        }
    };

    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter
    {
        private ArrayList<String> mLeDevices;
        private ArrayList<String> mMacAddress;
        private LayoutInflater mInflator;

        public LeDeviceListAdapter()
        {
            super();
            mLeDevices = new ArrayList<String>();
            mMacAddress = new ArrayList<String>();
            mInflator = MainActivity.this.getLayoutInflater();
        }

        public void addDevice(String Devicename, String MacAddress)
        {
            if (!mMacAddress.contains(MacAddress))
            {
                mLeDevices.add(Devicename);
                mMacAddress.add(MacAddress);
            }
        }

        public String getDevice(int position)
        {
            return mMacAddress.get(position);
        }

        public void clear()
        {
            mLeDevices.clear();
            mMacAddress.clear();
        }

        @Override
        public int getCount()
        {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i)
        {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i)
        {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup)
        {
            ViewHolder holder;

            String device = mMacAddress.get(i);
            String deviceNmae = mLeDevices.get(i);

            if (view == null)
            {
                holder = new ViewHolder();
                view = getLayoutInflater().inflate(R.layout.lst_items, null);
                holder.title = (TextView) view.findViewById(R.id.title);
                holder.subTitle = (TextView) view.findViewById(R.id.subTitle);
                view.setTag(holder);
            }
            else
            {
                holder = (ViewHolder) view.getTag();
            }

            holder.title.setText(deviceNmae);
            holder.subTitle.setText(device);
            return view;
        }
    }

    static class ViewHolder
    {
        TextView title;
        TextView subTitle;
    }
}

