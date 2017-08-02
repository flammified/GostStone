package nl.geodan.goststone;

import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.strongloop.android.loopback.AccessToken;
import com.strongloop.android.loopback.callbacks.ListCallback;
import com.strongloop.android.loopback.callbacks.ObjectCallback;
import com.strongloop.android.remoting.adapters.Adapter;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.extended.BleDeviceFilter;
import nl.dobots.bluenet.ble.extended.BleExt;
import nl.dobots.bluenet.ble.extended.BleExtState;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceList;

import nl.dobots.loopback.CrownstoneRestAPI;
import nl.dobots.loopback.loopback.models.Sphere;
import nl.dobots.loopback.loopback.models.Stone;
import nl.dobots.loopback.loopback.models.User;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

	private BleExt _ble;
	private BleExtState blestate;
	private BleDeviceList _bleDeviceList;
	private ListView crownstones_view;

	private AccessToken accesstoken;
	private User user;
	private Map<String, String> keymap;

//	private List<Stone> stones_to_watch;

	MqttAndroidClient mqttAndroidClient;


	private boolean scanning;

	Button scan_button;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		/** Load the Spheres and the device which we have access to, login and store it in our variables */
		initAPI();

		scanning = false;

		crownstones_view = (ListView) findViewById(R.id.crownstones);
		_bleDeviceList = new BleDeviceList();
		DeviceListAdapter adapter = new DeviceListAdapter(this, _bleDeviceList);
		crownstones_view.setAdapter(adapter);

	}

	private void initAPI() {

//		user = new User();
//		accesstoken = new AccessToken();
//		keymap = new HashMap<String, String>();
//
//
//		CrownstoneRestAPI.initializeApi(getApplicationContext());
//
//		/** Can be used later; when multiple spheres are supported */
//
//		CrownstoneRestAPI.getUserRepository().loginUser(Config.EMAIL, Config.PASSWORD, new nl.dobots.loopback.loopback.repositories.UserRepository.LoginCallback() {
//
//			@Override
//			public void onSuccess(AccessToken token, User currentUser) {
//				accesstoken = token;
//				user = (User) currentUser;
//
//				CrownstoneRestAPI.getStoneRepository().findAll(new ListCallback<Stone>() {
//
//					@Override
//					public void onSuccess(List<Stone> objects) {
//						MainActivity.this.stones_to_watch = objects;
//						initMQTT();
//					}
//
//					@Override
//					public void onError(Throwable t) {
//
//					}
//				});
//			}
//
//			@Override
//			public void onError(Throwable t) {
//
//			}
//		});
		initMQTT();
	}

	private void initMQTT() {
		mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), Config.MQTT, "GostStone");
		mqttAndroidClient.setCallback(new MqttCallbackExtended() {
			@Override
			public void connectionLost(Throwable cause) {
				Log.e("Error", cause.toString());
			}

			@Override
			public void messageArrived(String topic, MqttMessage message) throws Exception {

			}

			@Override
			public void deliveryComplete(IMqttDeliveryToken token) {

			}

			@Override
			public void connectComplete(boolean reconnect, String serverURI) {
				Log.d("MQTT", "Connected");
				initBluetooth();
			}
		});
		try {
			MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
			mqttConnectOptions.setAutomaticReconnect(true);
			mqttConnectOptions.setCleanSession(false);
			mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
				@Override
				public void onSuccess(IMqttToken asyncActionToken) {
					DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
					disconnectedBufferOptions.setBufferEnabled(true);
					disconnectedBufferOptions.setBufferSize(100);
					disconnectedBufferOptions.setPersistBuffer(false);
					disconnectedBufferOptions.setDeleteOldestMessages(false);
					mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
				}

				@Override
				public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
					Log.e("Error", "Failed to connect " + exception.toString());
				}

			});
		}
		catch(MqttException e) {
			e.printStackTrace();
		}
	}

	private void initBluetooth() {

		/** Create a BleExt object, which handles Bluetooth Low Energy connection with Crownstones */
		/** And a BleExtState, which handles stuff like notifications and state retrieval */
		_ble = new BleExt();
		blestate = new BleExtState(_ble);

		_ble.setScanFilter(BleDeviceFilter.crownstonePlug);
		_ble.init(this, new IStatusCallback() {
			@Override
			public void onSuccess() {
				// on success is called whenever bluetooth is enabled
				onBleEnabled();
			}


			@Override
			public void onError(int error) {
				Log.e("BLUETOOTH", "Error: " + error);
				// on error is (also) called whenever bluetooth is disabled
				onBleDisabled();
			}
		});

		EncryptionKeys keys = new EncryptionKeys(Config.ADMIN_KEY, Config.MEMBER_KEY, Config.GUEST_KEY);
		_ble.getBleBase().setEncryptionKeys(keys);
		_ble.getBleBase().enableEncryption(true);
		startScan();
	}



	private void startScan() {
		scanning = true;
		_ble.startScan(new IBleDeviceCallback() {
			@Override
			public void onSuccess() {
			}

			@Override
			public void onDeviceScanned(final BleDevice device) {
				// called whenever a device was scanned. the library keeps track of the scanned devices
				// and updates average rssi and distance measurements. the device received here as a
				// parameter already has the updated values.

				// but for this example we are only interested in the list of scanned devices, so
				// we ignore the parameter and get the updated device list, sorted by rssi from the
				// library
				_bleDeviceList = _ble.getDeviceMap().getRssiSortedList();
				runOnUiThread(new Runnable() {
					@Override
					public void run() {
						// the closest device is the first device in the list (because we asked for the
						// rssi sorted list)

						// update the list view
						DeviceListAdapter adapter = ((DeviceListAdapter) crownstones_view.getAdapter());
						adapter.updateList(_bleDeviceList);
						adapter.notifyDataSetChanged();
						CrownstoneServiceData data = device.getServiceData();
						if (data != null) {
							Log.d("Value mW", "" + device.getServiceData().getPowerUsage());
							Log.d("Value switch", "" + device.getServiceData().getSwitchState());
							Log.d("Value temperature", "" + device.getServiceData().getTemperature());


							MqttMessage message = new MqttMessage();

							String payload = device.getAddress() + "|" + data.getPowerUsage() + "|" + (data.getSwitchState()) + "|" + data.getTemperature();

							try {
								message.setPayload(payload.getBytes("utf-8"));
								mqttAndroidClient.publish("GostStone", message);
							} catch (UnsupportedEncodingException | MqttException e) {
								e.printStackTrace();
							}

						}
					}
				});
			}

			@Override
			public void onError(int error) {
				Log.e("BLUETOOTH", "Scan error: " + error);
			}
		});
	}
//
//	private void stopScan() {
//		_ble.stopScan(new IStatusCallback() {
//			@Override
//			public void onSuccess() {
//				scanning = false;
//			}
//
//			@Override
//			public void onError(int error) {
//				// nada
//			}
//		});
//	}

	void onBleEnabled() {
		Toast.makeText(this.getApplicationContext(), "Bluetooth enabled", Toast.LENGTH_LONG).show();
	}

	void onBleDisabled() {
		Toast.makeText(this.getApplicationContext(), "Bluetooth disabled", Toast.LENGTH_LONG).show();
	}

	@Override
	public void onClick(View v) {

		if (v == scan_button) {

			if (!scanning) {
//				this.startScan();
				this.scan_button.setText(R.string.stop_scan);
			} else {
//				this.stopScan();
				this.scan_button.setText(R.string.start_scan);
			}
		}
	}

}
