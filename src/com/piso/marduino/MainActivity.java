package com.piso.marduino;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

@SuppressLint("HandlerLeak") public class MainActivity extends Activity implements OnCheckedChangeListener, SensorEventListener {
	
	Switch switch1;
	TextView txtmovment, txtdirection;
	boolean isConnected = false, isEnabled = false;
	
	private boolean mInizialized;
	private SensorManager mSensor;
	private Sensor mAccelerometer;
	private final float NOISE = (float) 2.0;
	private final float NOISE2 = (float) -2.0;
	
	// Intent request codes
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    
    // Message types sent from the BluetoothChatService Handler
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    // Key names received from the BluetoothCommandService Handler
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
	
	// Name of the connected device
    private String mConnectedDeviceName = null;
    // Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    // Member object for Bluetooth Command Service
    private BluetoothCommandService mCommandService = null;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        
        switch1 = (Switch) findViewById(R.id.switch1);
        txtmovment = (TextView) findViewById(R.id.txtMovment);
        txtdirection = (TextView) findViewById(R.id.txtDirection);
        
        switch1.setEnabled(false);
        switch1.setChecked(false);
        
        if (switch1 != null) {
            switch1.setOnCheckedChangeListener(this);
        }
        
        mInizialized = false;
        mSensor = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        mSensor.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        
     // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, getResources().getString(R.string.not_supported), Toast.LENGTH_LONG).show();
            //finish();
            //return;
        }
        
        setTitle(getResources().getString(R.string.app_name) + " - " + getResources().getString(R.string.not_connect));
    } 
    
    @Override
	protected void onStart() {
		super.onStart();
		
		// If BT is not on, request that it be enabled.
        // setupCommand() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			isEnabled = true;
		}
		// otherwise set up the command service
		else {
			if (mCommandService==null)
				setupCommand();
			isEnabled = true;
		}
	}

    protected void onResume() {
    	super.onResume();
    	mSensor.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    	if (mCommandService != null) {
			if (mCommandService.getState() == BluetoothCommandService.STATE_NONE) {
				mCommandService.start();
			}
		}
    	}
    
    private void setupCommand() {
		// Initialize the BluetoothChatService to perform bluetooth connections
        mCommandService = new BluetoothCommandService(this, mHandler);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		
		if (mCommandService != null)
			mCommandService.stop();
	}
    
    protected void onPause() {
    	super.onPause();
    	mSensor.unregisterListener(this);
    }
    
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_STATE_CHANGE:
                switch (msg.arg1) {
                case BluetoothCommandService.STATE_CONNECTED:
                	setTitle("MArduino - " + mConnectedDeviceName);
                	switch1.setEnabled(true);
                    isConnected = true;
                    break;
                case BluetoothCommandService.STATE_CONNECTING:
                	setTitle("MArduino - " + getResources().getString(R.string.title_connecting));
                    break;
                case BluetoothCommandService.STATE_LISTEN:
                case BluetoothCommandService.STATE_NONE:
                    break;
                }
                break;
            case MESSAGE_DEVICE_NAME:
                // save the connected device's name
                mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
                Toast.makeText(getApplicationContext(), getResources().getString(R.string.title_connected_to)
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                break;
            case MESSAGE_TOAST:
                //Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               //Toast.LENGTH_SHORT).show();
                if (msg.getData().getString(TOAST) == "Impossibile collegarsi") {
                	setTitle("MArduino - " + getResources().getString(R.string.connect_failed));
                } else if (msg.getData().getString(TOAST) == "Connessione scaduta") {
                	setTitle("MArduino - " + getResources().getString(R.string.connect_lost));
                }
                break;
            }
        }
    };
	
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_CONNECT_DEVICE:
            // When DeviceListActivity returns with a device to connect
            if (resultCode == Activity.RESULT_OK) {
                // Get the device MAC address
                String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
                // Get the BLuetoothDevice object
                BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
                // Attempt to connect to the device
                mCommandService.connect(device);
            }
            break;
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled, so set up a chat session
                setupCommand();
            } else {
                // User did not enable Bluetooth or an error occured
                Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
    	if (isChecked) {
    		mInizialized = true;
       } else {
    	    txtmovment.setText(getResources().getString(R.string.stop));
            txtdirection.setText(getResources().getString(R.string.stop));
    	    mInizialized = false;
       }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
    	if (item.getItemId() == R.id.menu_connect) {
    		if (isEnabled) {
    			// Launch the DeviceListActivity to see devices and do scan
            	Intent serverIntent = new Intent(this, DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE);  
    		} else {
    			Toast.makeText(this, getResources().getString(R.string.enable_bluetooth), Toast.LENGTH_SHORT).show();
    		}
    	} else if (item.getItemId() == R.id.menu_info) {
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
        	builder
        	.setTitle("Info")
        	.setMessage(getResources().getString(R.string.author) + "Piso94\n" + getResources().getString(R.string.translator) + "\n" + getResources().getString(R.string.info))
        	.setPositiveButton("Ok", null)
        	.show();
    	}
		return false;
    }

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		float x = event.values[0];
		float y = event.values[1];

		if (mInizialized) {
			if ((x < NOISE) && (x > NOISE2)) x = (float)0.0;
			if ((y < NOISE) && (y > NOISE2)) y = (float)0.0;

			if (isConnected) {
				if (x > 0.0) {
					mCommandService.write(1);
				}
				
				if (x < 0.0) {
					mCommandService.write(2);
				}
				
				if (y > 0.0) {
					mCommandService.write(3);
				}
				
				if (y < 0.0) {
					mCommandService.write(4);
				}
					} else {
						x = (float)0.0;
						y = (float)0.0;
			}
			
			if (x < 0.0) {
				txtmovment.setText(getResources().getString(R.string.forward));
			}
			
			if (x > 0.0) {
				txtmovment.setText(getResources().getString(R.string.back));
			}
			
			if (y < 0.0) {
				txtdirection.setText(getResources().getString(R.string.left));
			}
			
			if (y > 0.0) {
				txtdirection.setText(getResources().getString(R.string.right));
			}
			
			if (x == 0.0) {
				txtmovment.setText(getResources().getString(R.string.stop));
				mCommandService.write(5);
			}
			
			if (y == 0.0) {
				txtdirection.setText(getResources().getString(R.string.stop));
				mCommandService.write(6);
			}
		}
	}
    
}
