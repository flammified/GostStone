package nl.dobots.bluenet.ble.core;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.core.callbacks.IDataCallback;
import nl.dobots.bluenet.ble.core.callbacks.IScanCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.core.callbacks.ISubscribeCallback;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleUtils;
import nl.dobots.bluenet.utils.Logging;

/*
 * TODO: - implement for multiple connected devices
 */

/**
 * The BleCore class is a wrapper for the Android Bluetooth classes. All API is done through
 * callbacks and JSON.
 * This class does not and should not! have a direct dependency to bluenet, so as
 * to easily switch. In particular, this class should not import any classes higher than core,
 * so no base or extended classes.
 */
public class BleCore extends Logging {

	private static final String TAG = BleCore.class.getCanonicalName();

	// default log level
	private static final int LOG_LEVEL = Log.VERBOSE;

	// Timeout for a bluetooth enable request. If timeout expires, an error is created
	private static final int BLUETOOTH_ENABLE_TIMEOUT = 30000;
	// Timeout for a location service enable request. If timeout expires, en error is created
	private static final int LOCATION_SERVICE_ENABLE_TIMEOUT = 30000;

	// the permission request code for requesting location (needed to use ble)
	private static final int PERMISSIONS_REQUEST_LOCATION = 101;

	// bluetooth adapter used for ble calls
	private BluetoothAdapter _bluetoothAdapter;

	// context which tries to initialize the bluetooth adapter
	private Context _context;

	// Bluetooth Scanner objects for API > 21
	private BluetoothLeScanner _leScanner;
	private ArrayList<ScanFilter> _scanFilters;
	private ScanSettings _scanSettings;
	// default scan mode is low latency
	private int _scanMode = ScanSettings.SCAN_MODE_LOW_LATENCY;

	// the callback used by the API > 21 to report scanned devices
	//	@SuppressLint("NewApi")
	private ScanCallback _coreScanCallback;

	// flags to keep track of state
	private boolean _bluetoothReady = false;
	private boolean _locationServicesReady;

	// check if the broadcast receiver is registered
	private boolean _receiverRegistered = false;

	// flag to keep track of active bluetooth reset
	private boolean _resettingBle = false;

	// keep track of the ble enable dialog (to only show it once globally, even if several
	// libraries try to initialize at the same time)
	static private boolean _bleDialogShowing = false;

	// enum to keep track of device connection state
	private enum ConnectionState {
		DISCONNECTED,
		CONNECTING,
		CONNECTED,
		DISCONNECTING
	}

	// enum to keep track of device discovery state
	private enum DiscoveryState {
		UNDISCOVERED,
		DISCOVERING,
		DISCOVERED
	}

	/**
	 * Private class to keep track of open connections (in preparation for connecting to multiple
	 * devices at the same time)
	 */
	private class Connection {
		// BluetoothGatt object, used to communicate with the BLE device
		private BluetoothGatt _gatt;
		// keep track of connection state
		private ConnectionState _connectionState = ConnectionState.DISCONNECTED;
		// keep track of discovery state
		private DiscoveryState _discoveryState = DiscoveryState.UNDISCOVERED;

		/**
		 * Return the BluetoothGatt object used by this connection to talk to the device
		 * @return BluetoothGatt object
		 */
		public BluetoothGatt getGatt() {
			return _gatt;
		}

		/**
		 * Assign a new BluetoothGatt object to the connection
		 * @param gatt new BluetoothGatt object
		 */
		public void setGatt(BluetoothGatt gatt) {
			_gatt = gatt;
		}

		/**
		 * Return the current connection state
		 * @return connection state
		 */
		public ConnectionState getConnectionState() {
			return _connectionState;
		}

		/**
		 * Set a new connection state
		 * @param connectionState new connection state
		 */
		public void setConnectionState(ConnectionState connectionState) {
			_connectionState = connectionState;
		}

		/**
		 * Get the current discovery state
		 * @return discovery state
		 */
		public DiscoveryState getDiscoveryState() {
			return _discoveryState;
		}

		/**
		 * Set a new discovery state
		 * @param discoveryState new discovery state
		 */
		public void setDiscoveryState(DiscoveryState discoveryState) {
			_discoveryState = discoveryState;
		}
	}

	// a list of connections for different devices
	private HashMap<String, Connection> _connections = new HashMap<>();

	// flag to indicate if currently scanning for devices
	private boolean _scanning;

	// callbacks used to notify events
	// scan callback is informed about scan errors and scanned devices
	private IScanCallback _scanCallback = null;
	// init callback is informed about success or error of init call (cleared after a trigger)
	private IStatusCallback _initializeCallback = null;
	// event callback is informed about changes in bluetooth state and location services
	private IDataCallback _eventCallback = null;
	// connection callback is informed about success / failure of a connect and disconnect calls
	// cleared after successful disconnect or after a connect timeout
	private IDataCallback _connectionCallback = null;
	// discovery callback is used when discovering services and triggers for every discovered
	// characteristic
	private IDataCallback _discoveryCallback = null;
	// callback when a characteristic is read (success or failure)
	private IDataCallback _characteristicsReadCallback = null;
	// callback when a characteristic is written (success or failure)
	private IStatusCallback _characteristicsWriteCallback = null;
	// callback returns if subscribing to a characteristic is successful or failed
	private IStatusCallback _subscribeCallback = null;
	// callback returns if unsubscribing from a characteristic is successful or failed
	private IStatusCallback _unsubscribeCallback = null;
	// keeps track of the list of callbacks which are listening to notifications, 1 callback per
	// characteristic
	private HashMap<UUID, ISubscribeCallback> _notificationCallbacks = new HashMap<>();

	// timeout handler to check for function timeouts, e.g. bluetooth enable, connect, reconnect, etc.
	private Handler _timeoutHandler;
	// the runnable to check if a connect/reconnect times out
	private Runnable _connectTimeout;

	/**
	 * Default constructor
	 */
	public BleCore() {
		// create a timeout handler with it's own thread to take care of timeouts
		HandlerThread timeoutThread = new HandlerThread("TimeoutHandler");
		timeoutThread.start();
		_timeoutHandler = new Handler(timeoutThread.getLooper());
	}

	/**
	 * Return current log level, see {@link Log}
	 * @return log level
	 */
	@Override
	protected int getLogLevel() {
		return LOG_LEVEL;
	}

	/**
	 * Get tag used for logs
	 * @return
	 */
	@Override
	protected String getTag() {
		return TAG;
	}

	/**
	 * Make sure to close and unregister receiver if the object is not used anymore
	 * @throws Throwable
	 */
	@Override
	protected void finalize() throws Throwable {
		super.finalize();
		destroy();
	}

	/**
	 * Broadcast receiver used to handle Bluetooth Events, i.e. turning on and off of the
	 * Bluetooth Adapter
	 */
	private BroadcastReceiver _receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {

			if (intent.getAction().equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
				_bleDialogShowing = false;
				switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
					case BluetoothAdapter.STATE_OFF: {
						_bluetoothReady = false;

						// if bluetooth is turned off inform state callback about the change
						sendEvent(BleCoreTypes.EVT_BLUETOOTH_OFF);

						_connections = new HashMap<>();
						_scanning = false;

						// if bluetooth state turns off because of a reset, enable it again
						if (_resettingBle) {
							_bluetoothAdapter.enable();
						}

						break;
					}
					case BluetoothAdapter.STATE_ON: {
						_bluetoothReady = true;

						if (Build.VERSION.SDK_INT >= 21) {
							_leScanner = _bluetoothAdapter.getBluetoothLeScanner();
							_scanSettings = new ScanSettings.Builder()
									.setScanMode(_scanMode)
									.build();
							_scanFilters = new ArrayList<>();
						}

						// if bluetooth is turned on inform state callback about the change
						sendEvent(BleCoreTypes.EVT_BLUETOOTH_ON);

						// if bluetooth state turns on because of a reset, then reset was completed
						if (_resettingBle) {
							_resettingBle = false;
						} else {
							// if location services are ready, notify success
							if (_locationServicesReady) {
//								_initialized = true;
								// inform the callback about the enabled bluetooth
								if (_initializeCallback != null) {
									_initializeCallback.onSuccess();
									_initializeCallback = null;
								}
							} else {
								// otherwise, request to enable location services
								checkLocationServices();
							}
						}
						// bluetooth was successfully enabled, cancel the timeout
						_timeoutHandler.removeCallbacksAndMessages(null);

						break;
					}
				}
			} else if (intent.getAction().equals(LocationManager.PROVIDERS_CHANGED_ACTION)) {

				// PROVIDERS_CHANGED_ACTION  are also triggered if mode is changed, so only
				// create events if the _locationsServicesReady flag changes

				if (isLocationServicesEnabled()) {
					if (!_locationServicesReady) {
						// if bluetooth is ready, notify success
						if (_bluetoothReady) {
//							_initialized = true;
							if (_initializeCallback != null) {
								_initializeCallback.onSuccess();
								_initializeCallback = null;
							}
						} else {
							// otherwise, request to enable bluetooth
							checkBluetooth();
						}
					}
					_locationServicesReady = true;

					// if location services are turned on inform state callback about the change
					sendEvent(BleCoreTypes.EVT_LOCATION_SERVICES_ON);
				} else {
					_locationServicesReady = false;

					// if location services are turned off inform state callback about the change
					sendEvent(BleCoreTypes.EVT_LOCATION_SERVICES_OFF);
				}
			}
		}
	};

	/**
	 * Change the scan mode used to scan for devices. See ScanSettings for an choice and
	 * explanation of the different scan modes.
	 * You need to stop and start scanning again for this to take effect.
	 * Note: Only used for api 21 and newer
	 * @param mode scan mode to be set, see {@link ScanSettings}
	 */
	@SuppressLint("NewApi")
	public void setScanMode(int mode) {
		if (Build.VERSION.SDK_INT >= 21) {
			_scanSettings = new ScanSettings.Builder()
					.setScanMode(mode)
					.build();
			_scanMode = mode;
		}
	}

	/**
	 * Request location permissions. Needed for api 23 and newer
	 * @param activity an activity is needed for the request. if used from a service, have a look
	 *                 at {@link nl.dobots.bluenet.service.BluetoothPermissionRequest}
	 */
	public void requestPermissions(Activity activity) {
		ActivityCompat.requestPermissions(activity,
				new String[] {Manifest.permission.ACCESS_COARSE_LOCATION},
				PERMISSIONS_REQUEST_LOCATION);

	}

	/**
	 * @return return true if permission result was handled, false otherwise. if true, then
	 *   the result will be passed using the callback.onSuccess or callback.onError functions
	 *   also if true, init is automatically recalled
	 */
	public boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, IStatusCallback callback) {
		switch (requestCode) {
			case PERMISSIONS_REQUEST_LOCATION: {
				if (grantResults.length > 0 &&	grantResults[0] == PackageManager.PERMISSION_GRANTED) {
					init(_context, _initializeCallback);
					callback.onSuccess();
				} else {
					callback.onError(BleErrors.ERROR_BLE_PERMISSION_MISSING);
				}
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the hardware address of the local Bluetooth adapter.
	 * <p>For example, "00:11:22:AA:BB:CC".
	 * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
	 *
	 * @return Bluetooth hardware address as string
	 */
	public String getLocalAddress() {
		if (_bluetoothAdapter != null) {
			return _bluetoothAdapter.getAddress();
		}
		getLogger().LOGe(TAG, "Bluetooth not initialized!");
		return null;
	}

	/**
	 * Get the friendly Bluetooth name of the local Bluetooth adapter.
	 * <p>This name is visible to remote Bluetooth devices.
	 * <p>Requires {@link android.Manifest.permission#BLUETOOTH}
	 *
	 * @return the Bluetooth name, or null on error
	 */
	public String getLocalName() {
		if (_bluetoothAdapter != null) {
			return _bluetoothAdapter.getName();
		}
		getLogger().LOGe(TAG, "Bluetooth not initialized!");
		return null;
	}

	/**	Set an event callback listener. will be informed about events such as bluetooth on / off,
	 *  location services on / off, etc.
	 *
	 *  @param callback callback used to report if bluetooth is enabled / disabled, etc.
	 */
	public void setEventCallback(IDataCallback callback) {
		_eventCallback = callback;
	}

	/**
	 * Send an event to the event listener. see BleCoreTypes for possible events
	 * @param event the event to be sent
	 */
	private void sendEvent(String event) {
		if (_eventCallback != null) {
			JSONObject status = new JSONObject();
			setStatus(status, event);
			_eventCallback.onData(status);
		}
	}

	/**
	 * Initializes the BLE Modules and tries to enable the Bluetooth adapter. Note, the callback
	 * provided as parameter will persist. The callback will be triggered whenever the state of
	 * the bluetooth adapter changes. That means if the user turns off bluetooth, then the onError
	 * of the callback will be triggered. And again if the user turns bluetooth on, the onSuccess
	 * will be triggered. If the user denies enabling bluetooth, then onError will be called after
	 * a timeout expires
	 * @param context the context used to enable bluetooth, this can be a service or an activity
	 * @param callback callback, used to report back init was successful or not
	 */
	@SuppressLint("NewApi")
	public void init(Context context, IStatusCallback callback) {
		_context = context;

		_initializeCallback = callback;

		getLogger().LOGd(TAG, "Initialize BLE hardware");
		// check first if mobile has bluetooth le
		if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) ||
			Build.VERSION.SDK_INT < 18)
		{
			getLogger().LOGe(TAG, "Can't use library without BLE hardware!! Abort.");
			callback.onError(BleErrors.ERROR_BLE_HARDWARE_MISSING);
			return;
		}

		// if api newer than 23, need to check for location permission
		if (Build.VERSION.SDK_INT >= 23) {
			int permissionCheck = ContextCompat.checkSelfPermission(context,
					Manifest.permission.ACCESS_COARSE_LOCATION);
			if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
				getLogger().LOGe(TAG, "BLE permissions not granted!! Abort.");
				callback.onError(BleErrors.ERROR_BLE_PERMISSION_MISSING);
				return;
			}
		}

		BluetoothManager bluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		_bluetoothAdapter = bluetoothManager.getAdapter();

		// if api newer than 21, use the BluetoothLeScanner object to scan
		if (Build.VERSION.SDK_INT >= 21) {
			_leScanner = _bluetoothAdapter.getBluetoothLeScanner();
			_scanSettings = new ScanSettings.Builder()
					.setScanMode(_scanMode)
					.build();
			_scanFilters = new ArrayList<>();
		}

		// register the broadcast receiver for bluetooth action state changes and location manager
		// action changes
		if (!_receiverRegistered) {
			_context.registerReceiver(_receiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
			_context.registerReceiver(_receiver, new IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION));
			_receiverRegistered = true;
		}

		// check if bluetooth is enabled, otherwise ask to enable
		checkBluetooth();

		// if bluetooth is ready, check if location services are enabled, otherwise ask to enable
		if (_bluetoothReady) {
			checkLocationServices();
		}

		// initialize is done if both bluetooth and location services are ready
		if (_bluetoothReady && _locationServicesReady) {
			// TODO: got a NullPointerException (on _initializeCallback) here after disabling location service and waiting for a while, how is that possible?
			if (_initializeCallback != null) {
				_initializeCallback.onSuccess();
				_initializeCallback = null;
			}
		}

	}

	/**
	 * Check if location services are enabled. if api < 23 always return true,
	 * if not enabled, show the LocationRequest activity
	 */
	private void checkLocationServices() {

		if (Build.VERSION.SDK_INT < 23) {
			_locationServicesReady = true;
			return;
		}
		_locationServicesReady = false;

		if (!isLocationServicesEnabled()) {
			Intent intent = new Intent(_context, LocationRequest.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			_context.startActivity(intent);

			_timeoutHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (!isLocationServicesEnabled()) {
						if (_initializeCallback != null) {
							_initializeCallback.onError(BleErrors.ERROR_LOCATION_SERVICES_TURNED_OFF);
						}
					}
				}
			}, LOCATION_SERVICE_ENABLE_TIMEOUT);
		} else {
			_locationServicesReady = true;
		}
	}

	/**
	 * Check if bluetooth is enabled. only show one dialog at a time even if function is called
	 * multiple times.
	 * If not enabled, show the Bluetooth enable request
	 */
	private void checkBluetooth() {

		_bluetoothReady = false;
		if (_bleDialogShowing) return;

		if (!_bluetoothAdapter.isEnabled()) {
			_bleDialogShowing = true;
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			enableBtIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			_context.startActivity(enableBtIntent);
			// use a timeout to check if bluetooth was enabled. if bluetooth is enabled
			// the timeout will be cancelled in the broadcast receiver. if the user denies bluetooth
			// enabling, this will trigger an error.
			_timeoutHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (!_bluetoothAdapter.isEnabled()) {
						_bleDialogShowing = false;
						if (_initializeCallback != null) {
							_initializeCallback.onError(BleErrors.ERROR_BLUETOOTH_NOT_ENABLED);
						}
					}
				}
			}, BLUETOOTH_ENABLE_TIMEOUT);
		} else {
			getLogger().LOGd(TAG, "Bluetooth already enabled");
			_bluetoothReady = true;
		}
	}

	/**
	 * check the state of location services
	 *
	 * @return true if location services enabled, false otherwise
	 */
	public boolean isLocationServicesEnabled() {

		if (Build.VERSION.SDK_INT < 23) {
			return true;
		}

		LocationManager locationManager = (LocationManager) _context.getSystemService(Context.LOCATION_SERVICE);
		boolean isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
		boolean isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

		//Start your Activity if location was enabled:
		return isGpsEnabled || isNetworkEnabled;
	}

	/**
	 * Reset all callbacks and unregister the broadcast receiver
	 */
	public synchronized void destroy() {
		if (_receiverRegistered) {
			_context.unregisterReceiver(_receiver);
			_receiverRegistered = false;
		}

		_bluetoothReady = false;
		_locationServicesReady = false;
		_connectionCallback = null;
		_discoveryCallback = null;
		_initializeCallback = null;
		_eventCallback = null;
		_scanCallback = null;
		_characteristicsReadCallback = null;
		_characteristicsWriteCallback = null;
		_subscribeCallback = null;
		_unsubscribeCallback = null;
		_notificationCallbacks.clear();
	}

	/**
	 * Reset Bluetooth. If Bluetooth is not enabled within BLUETOOTH_ENABLE_TIMEOUT, trigger
	 * a bluetooth off event
	 */
	public void resetBle() {
		if (_bluetoothAdapter.isEnabled()) {
			_resettingBle = true;
			_bluetoothAdapter.disable();
			_timeoutHandler.postDelayed(new Runnable() {
				@Override
				public void run() {
					if (!_bluetoothAdapter.isEnabled()) {

						JSONObject status = new JSONObject();
						setStatus(status, "EVT_BLUETOOTH_OFF");
						_eventCallback.onData(status);
					}
				}
			}, BLUETOOTH_ENABLE_TIMEOUT);
		}
	}

	/**
	 * Check if the device is connected
	 * @param address MAC ddress of the device
	 * @return true if connected, false if not initialized or if not connected
	 */
	public boolean isDeviceConnected(String address) {

		if (!isInitialized()) return false;

		Connection connection = _connections.get(address);
		return !(connection == null ||
				connection.getConnectionState() != ConnectionState.CONNECTED);

	}

	/**
	 * Set the reconnect timeout
	 *
	 * @param address MAC address of the device which is reconnecting
	 * @param timeout timeout in seconds
	 */
	private void setReconnectTimeout(final String address, int timeout) {
		_connectTimeout = new Runnable() {

			@Override
			public void run() {
				getLogger().LOGe(TAG, "timeout reconnecting to %s, ABORT!", address);
				Connection connection = _connections.get(address);
				connection.setConnectionState(ConnectionState.DISCONNECTED);
				connection.getGatt().disconnect();
				if (_connectionCallback != null) {
					_connectionCallback.onError(BleErrors.ERROR_RECONNECT_FAILED);
				}
//				_connectionCallback = null;
			}
		};

		_timeoutHandler.postDelayed(_connectTimeout, timeout * 1000);
	}

	/**
	 * Set the connect timeout
	 *
	 * @param address MAC address of the device which is connecting
	 * @param timeout timeout in seconds
	 */
	private void setConnectTimeout(final String address, int timeout) {
		_timeoutHandler.removeCallbacks(_connectTimeout);
		_connectTimeout = new Runnable() {

			@Override
			public void run() {
				getLogger().LOGe(TAG, "timeout connecting to %s, ABORT!", address);
				Connection connection = _connections.get(address);
				if (connection != null) {
					connection.setConnectionState(ConnectionState.DISCONNECTED);
					BluetoothGatt gatt = connection.getGatt();
					if (gatt != null) {
						// [17.01.17] call gatt.disconnect(), just in case the connection stays open
						//   even after calling gatt.close()
						gatt.disconnect();
						gatt.close();
					} else {
						getLogger().LOGe(TAG, "gatt == null");
					}
					_connections.remove(address);
					if (_connectionCallback != null) {
						_connectionCallback.onError(BleErrors.ERROR_CONNECT_FAILED);
//						_connectionCallback = null;
					} else {
						getLogger().LOGe(TAG, "_connectionCallback == null");
					}
				} else {
					getLogger().LOGe(TAG, "connection == null");
				}
			}
		};

		_timeoutHandler.postDelayed(_connectTimeout, timeout);
	}

	/**
	 * Clear the connect timeout (if connection was successful)
	 */
	private void clearConnectTimeout() {
		getLogger().LOGd(TAG, "clear connect timeout");
		_timeoutHandler.removeCallbacks(_connectTimeout);
	}

	/**
	 * Connect to the given device and set the timeout. Callback is informed about success
	 * or failure of the connect
	 *
	 * @param address the MAC address of the device to connect to
	 * @param timeout timeout of the connect. if connection is not successful within timeout,
	 *                abort and trigger the callback's onError
	 * @param callback the callback to be notified about success or failure
	 *                     onData: status is "connected" when successful.
	 *                     onError: only errors that concern the connection state.
	 */
	public void connectDevice(String address, int timeout, IDataCallback callback) {
		getLogger().LOGd(TAG, "Connecting to %s with %d ms timeout ...", address, timeout);

		if (!isInitialized()) {
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return;
		}

		_connectionCallback = callback;
		BluetoothDevice device = _bluetoothAdapter.getRemoteDevice(address);

		Connection connection = new Connection();
		_connections.put(address, connection);

		setConnectTimeout(address, timeout);

		BluetoothGatt gatt = device.connectGatt(_context, false, new BluetoothGattCallbackExt());
		connection.setGatt(gatt);

	}

	/**
	 * Reconnect to a previously connected device
	 *
	 * @param address the MAC address of the device to connect to
	 * @param timeout timeout of the connect. if connection is not successful within timeout,
	 *                abort and trigger the callback's onError
	 * @param callback the callback to be notified about success or failure
	 */
	public boolean reconnectDevice(String address, int timeout, IDataCallback callback) {

		getLogger().LOGd(TAG, "reconnecting device ...");

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.DISCONNECTED) {
			getLogger().LOGe(TAG, ".. not disconnected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		_connectionCallback = callback;
		connection.setConnectionState(ConnectionState.CONNECTING);
		connection.setDiscoveryState(DiscoveryState.UNDISCOVERED);

		setReconnectTimeout(address, timeout);

		BluetoothGatt gatt = connection.getGatt();
		boolean result = gatt.connect();

		if (!result) {
			getLogger().LOGe(TAG, ".. reconnect failed");
			closeDevice(address, false, new IStatusCallback() {
				@Override
				public void onSuccess() {

				}

				@Override
				public void onError(int error) {

				}
			});
			callback.onError(BleErrors.ERROR_RECONNECT_FAILED);
			return false;
		}

		return true;
	}

	/**
	 * Check if library initialized by checking if bluetooth is ready and location services
	 * are ready
	 *
	 * @return true if initialized, false otherwise
	 */
	private boolean isInitialized() {
		return _bluetoothReady && _locationServicesReady;
	}

	/**
	 * Check if library is ready. Checks if libray is initialized, if bluetooth adapter is
	 * enabled and if location services are enabled
	 *
	 * @return
	 */
	public boolean isReady() {
		if (isInitialized()) {
			return _bluetoothAdapter.isEnabled() && isLocationServicesEnabled();
		} else {
			return false;
		}
	}

	/**
	 * Start discovering services of the connected device. services have to be discovered before
	 * the device can be used to read/write, etc. Uses cache if found.
	 *
	 * @param address the MAC address of the device for which the services should be discovered
	 * @param callback callback to be invoked about discovered services and characteristics, or error
	 */
	public void discoverServices(String address, IDataCallback callback) {
		// by default, return cached discovery if present, otherwise start new discovery
		discoverServices(address, false, callback);
	}

	/**
	 * Start discovering services of the connected device. services have to be discovered before
	 * the device can be used to read/write, etc. To force a new discovery, i.e. not use the cache,
	 * use forceDiscover=true
	 *
	 * @param address the MAC address of the device for which the services should be discovered
	 * @param forceDiscover, set to true to force a new discovery,
	 *						 if false and cached discovery found, return the lib cache (not same as previously mentioned cache)
	 * @param callback callback to be invoked about discovered services and characteristics, or error
	 */
	public void discoverServices(String address, boolean forceDiscover, IDataCallback callback) {
		getLogger().LOGd(TAG, "Discovering services ...");
		_discoveryCallback = callback;

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			getLogger().LOGe(TAG, ".. not connected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return;
		}

		JSONObject json;

		BluetoothGatt gatt = connection.getGatt();

		if (gatt == null) {
			// todo: report error?
			getLogger().LOGe(TAG, "gatt == null");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return;
		}

		switch (connection.getDiscoveryState()) {
			case DISCOVERING:
				getLogger().LOGd(TAG, ".. already running");
				callback.onError(BleErrors.ERROR_ALREADY_DISCOVERING);
				return;
			case DISCOVERED:
				if (!forceDiscover) {
					getLogger().LOGd(TAG, ".. already done, return existing discovery");
					json = getDiscovery(gatt);
					callback.onData(json);
					return;
				}
				// else go to discovery, no break needed!
			default:
				getLogger().LOGd(TAG, ".. start discovery");
				connection.setDiscoveryState(DiscoveryState.DISCOVERING);
				gatt.discoverServices();
				break;
		}
	}

	/**
	 * Get the discovery from the device after a discover, i.e. list of all services and characteristics
	 * parses the discovery and creates a json object with services array and characteristics arrays
	 * in the form of
	 * {
	 *     status: discovered,
	 *     address: ...,
	 *     name: ...,
	 *     services : [
	 *     		{
	 *     		 	serviceUUID: ...,
	 *     		 	characteristics: [
	 *     		 		{
	 *     		 		 	   characteristicUUID: ...,
	 *     		 		 	   properties: { ... } ,
	 *     		 		 	   descriptors: [
	 *     		 		 	   		{
	 *     		 		 	   		    descriptorUUID: ...
	 *     		 		 	   		}
	 *     		 		 	   ]
	 *     		 		}
	 *     		 	]
	 *     		}
	 *     ]
	 * }
	 * @param gatt the bluetooth gatt server obtained from the connection
	 * @return the json object
	 */
	private JSONObject getDiscovery(BluetoothGatt gatt) {

		JSONObject deviceJson = new JSONObject();
		BluetoothDevice device = gatt.getDevice();

		// add status and device info
		setStatus(deviceJson, BleCoreTypes.STATUS_DISCOVERED);
		addDeviceInfo(deviceJson, device);

		// add all services ...
		JSONArray servicesArray = new JSONArray();
		for (BluetoothGattService service : gatt.getServices()) {
			JSONObject serviceJson = new JSONObject();

			addProperty(serviceJson, BleCoreTypes.PROPERTY_SERVICE_UUID, BleUtils.uuidToString(service.getUuid()));

			// .. for each service, add all characteristics ...
			JSONArray characteristicsArray = new JSONArray();
			for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
				JSONObject characteristicJson = new JSONObject();

				addProperty(characteristicJson, BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID, BleUtils.uuidToString(characteristic.getUuid()));
				addProperty(characteristicJson, BleCoreTypes.PROPERTY_PROPERTIES, getProperties(characteristic));

				// .. for each characteristics, add all descriptors ...
				JSONArray descriptorsArray = new JSONArray();
				for (BluetoothGattDescriptor descriptor : characteristic.getDescriptors()) {
					JSONObject descriptorJson = new JSONObject();

					addProperty(descriptorJson, BleCoreTypes.PROPERTY_DESCRIPTOR_UUID, BleUtils.uuidToString(descriptor.getUuid()));
					descriptorsArray.put(descriptorJson);
				}

				addProperty(characteristicJson, BleCoreTypes.PROPERTY_DESCRIPTORS, descriptorsArray);
				characteristicsArray.put(characteristicJson);
			}

			addProperty(serviceJson, BleCoreTypes.PROPERTY_CHARACTERISTICS_LIST, characteristicsArray);
			servicesArray.put(serviceJson);
		}

		addProperty(deviceJson, BleCoreTypes.PROPERTY_SERVICES_LIST, servicesArray);

		return deviceJson;
	}

	/**
	 * Return the properties of a characteristic as a json, the json has the following boolean fields:
	 * 		extendedProperties
	 * 		signedWrite
	 * 		indicate
	 * 		notify
	 * 		write
	 * 		writeNoResponse
	 * 		read
	 * 		broadcast
	 *
	 * @param characteristic the characteristic in question
	 * @return json with the properties of the characteristic
	 */
	private JSONObject getProperties(BluetoothGattCharacteristic characteristic) {

		int properties = characteristic.getProperties();
		JSONObject propertiesJSON = new JSONObject();

		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_BROADCAST, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_BROADCAST));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_READ, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_READ));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_WRITE_NO_RESPONSE, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_WRITE, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_WRITE));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_NOTIFY));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_INDICATE, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_INDICATE));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_SIGNED_WRITE, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE));
		addProperty(propertiesJSON, BleCoreTypes.CHARACTERISTIC_PROP_EXTENDED_PROPERTIES, hasCharacteristicProperty(properties, BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS));

//		addProperty(propertiesJSON, "notifyEncryptionRequired", hasCharacteristicProperty(properties, 0x100));
//		addProperty(propertiesJSON, "indicateEncryptionRequired", hasCharacteristicProperty(properties, 0x200));

		return propertiesJSON;
	}

	/**
	 * discover if the address has a given characteristic. Uses cached data if available.
	 *
	 * @param address the MAC address of the device
	 * @param serviceUuid the UUID of the service
	 * @param characteristicUuid the UUID of the characteristic
	 * @param callback callback to informed, onSuccess is called if the characteristic is found,
	 *                 otherwise the onError is called
	 */
	public void discoverCharacteristic(String address, String serviceUuid, String characteristicUuid,
									   IStatusCallback callback) {
		discoverCharacteristic(address, serviceUuid, characteristicUuid, false, callback);
	}

	/**
	 * Discover if the address has a given characteristic
	 *
	 * @param address the MAC address of the device
	 * @param serviceUuid the UUID of the service
	 * @param characteristicUuid the UUID of the characteristic
	 * @param forceDiscover if true, force a new discovery, i.e. do not use the cache, if false,
	 *                      use cached data if available
	 * @param callback callback to informed, onSuccess is called if the characteristic is found,
	 *                 otherwise the onError is called
	 */
	public void discoverCharacteristic(String address, final String serviceUuid, final String characteristicUuid,
									   boolean forceDiscover, final IStatusCallback callback) {

		discoverServices(address, forceDiscover, new IDataCallback() {

			@Override
			public void onError(int error) {
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				try {
					JSONArray servicesArray = json.getJSONArray(BleCoreTypes.PROPERTY_SERVICES_LIST);
					JSONObject service;
					for (int i = 0; i < servicesArray.length(); ++i) {
						service = servicesArray.getJSONObject(i);
						if (service.getString(BleCoreTypes.PROPERTY_SERVICE_UUID).matches(serviceUuid)) {
							// found service
							JSONArray characteristicsArray = service.getJSONArray(BleCoreTypes.PROPERTY_CHARACTERISTICS_LIST);
							JSONObject characteristic;
							for (int j = 0; j < characteristicsArray.length(); ++j) {
								characteristic = characteristicsArray.getJSONObject(j);
								if (characteristic.getString(BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID).matches(characteristicUuid)) {
									// found characteristic
									callback.onSuccess();
								}
							}
						}
					}
				} catch (JSONException e) {
					getLogger().LOGe(TAG, "failed to decode discovered data");
					callback.onError(BleErrors.ERROR_DISCOVERY_FAILED);
				}
			}
		});
	}

	/**
	 * Start an endless scan for bluetooth le devices. endless means it continues to scan until
	 * stopScan is called.
	 *
	 * @param callback callback to informed about scanned devices or errors
	 */
	public void startEndlessScan(IScanCallback callback) {
		startEndlessScan(new String[] {}, callback);
	}

	/**
	 * Start an endless scan for bluetooth le devices. endless means it continues to scan until
	 * stopScan is called. Use the list of uuids as filter. E.g. to return only devices with
	 * the given service data uuid
	 *
	 * @param uuids list of service data uuids for which to filter
	 * @param callback callback to informed about scanned devices or errors
	 */
	public synchronized void startEndlessScan(String[] uuids, IScanCallback callback) {

		getLogger().LOGd(TAG, "startEndlessScan ...");

		if (!isInitialized()) {
			getLogger().LOGe(TAG, "startEndlessScan ... error: not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return;
		}

		if (!_bluetoothAdapter.isEnabled()) {
			getLogger().LOGe(TAG, "startEndlessScan ... error: ble disabled");
			callback.onError(BleErrors.ERROR_BLUETOOTH_NOT_ENABLED);
			return;
		}

		if (_bluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
			getLogger().LOGe(TAG, "startEndlessScan ... error: ble not on");
			callback.onError(BleErrors.ERROR_BLUETOOTH_NOT_ENABLED);
			return;
		}

		if (Build.VERSION.SDK_INT >= 23) {
			if (!isLocationServicesEnabled()) {
				getLogger().LOGe(TAG, "startEndlessScan ... error: location services disabled");
				callback.onError(BleErrors.ERROR_LOCATION_SERVICES_TURNED_OFF);
				return;
			}
		}

		if (isScanning()) {
			getLogger().LOGe(TAG, "startEndlessScan ... error: already scanning");
			callback.onError(BleErrors.ERROR_ALREADY_SCANNING);
			return;
		}

		_scanCallback = callback;

		// use new functionality if possible
		if (Build.VERSION.SDK_INT >= 21) {
			if (_coreScanCallback == null) {
				createCoreScanCallback();
			}

			if (uuids.length == 0) {
				_scanFilters.clear();
			} else {
				UUID[] serviceUuids = BleUtils.stringToUuid(uuids);
				for (UUID uuid : serviceUuids) {
					ScanFilter filter = new ScanFilter.Builder()
							.setServiceUuid(new ParcelUuid(uuid))
							.build();
					_scanFilters.add(filter);
				}
			}
			getLogger().LOGd(TAG, "BluetoothLeScanner.startScan");
			if (_leScanner == null) {
				getLogger().LOGw(TAG, "startEndlessScan ... error: scanner is not initialized");
				callback.onError(BleErrors.ERROR_SCAN_FAILED);
				return;
			}
			_leScanner.startScan(_scanFilters, _scanSettings, _coreScanCallback);
		} else {
			getLogger().LOGd(TAG, "BluetoothAdapter.startLeScan");
			if (uuids.length == 0) {
				_scanning = _bluetoothAdapter.startLeScan(_coreLeScanCallback);
			} else {
				UUID[] serviceUuids = BleUtils.stringToUuid(uuids);
				_scanning = _bluetoothAdapter.startLeScan(serviceUuids, _coreLeScanCallback);
			}

			if (!_scanning) {
				getLogger().LOGd(TAG, "startEndlessScan ... error: failed to start LeScan");
				callback.onError(BleErrors.ERROR_SCAN_FAILED);
				return;
			}
		}

		callback.onSuccess();
		getLogger().LOGd(TAG, "startEndlessScan ... done");
	}

	/**
	 * For api > 21 use a different scan api
	 */
	@TargetApi(21)
	private void createCoreScanCallback() {
		_coreScanCallback = new ScanCallback() {
			@Override
			public void onScanResult(int callbackType, ScanResult result) {
				if (result.getScanRecord() != null) {
					onDeviceScanned(result.getDevice(), result.getRssi(), result.getScanRecord().getBytes());
				} else {
					onDeviceScanned(result.getDevice(), result.getRssi(), new byte[]{});
				}
			}

			@Override
			public void onBatchScanResults(List<ScanResult> results) {
				for (ScanResult result : results) {
					onScanResult(0, result);
				}
			}

			@Override
			public void onScanFailed(int errorCode) {
				_scanning = false;
				if (_scanCallback != null) {
					_scanCallback.onError(errorCode);
				}
			}
		};
	}

	/**
	 * Stop an endless scan. i.e. stop scanning for ble devices.
	 *
	 * @param callback callback to be informed if the scan was successfully stopped or not
	 */
	public synchronized void stopEndlessScan(@Nullable IStatusCallback callback) {

		getLogger().LOGd(TAG, "stopEndlessScan ...");

		if (!isInitialized()) {
			if (callback != null) callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return;
		}

		if (!_bluetoothAdapter.isEnabled()) {
			getLogger().LOGe(TAG, "stopEndlessScan ... error: ble disabled");
			if (callback != null) callback.onError(BleErrors.ERROR_BLUETOOTH_NOT_ENABLED);
			return;
		}

		if (_bluetoothAdapter.getState() != BluetoothAdapter.STATE_ON) {
			getLogger().LOGe(TAG, "stopEndlessScan ... error: ble not on");
			if (callback != null) callback.onError(BleErrors.ERROR_BLUETOOTH_NOT_ENABLED);
			return;
		}

		// [] just to be sure, stop even if already stopped. has happened that the library
		// thinks the scan is stopped but the bluetooth adapter was still scanning ...
//		if (!isRunning()) {
//			callback.onError(BleCoreTypes.ERROR_NOT_SCANNING);
//			return false;
//		}

		if (Build.VERSION.SDK_INT >= 21) {
			_leScanner.stopScan(_coreScanCallback);
		} else {
			_bluetoothAdapter.stopLeScan(_coreLeScanCallback);
		}
		_scanCallback = null;
		_scanning = false;

		if (callback != null) callback.onSuccess();
		getLogger().LOGd(TAG, "stopEndlessScan ... done");
	}

	/**
	 * Check if the library is scanning for devices
	 *
	 * @return true if scanning, false otherwise
	 */
	public boolean isScanning() {
		return _scanning;
	}

	/**
	 * The callback used by the bluetooth adapter to report scanned devices
	 * FOR API <= 21
	 */
	private LeScanCallback _coreLeScanCallback = new LeScanCallback() {

		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			onDeviceScanned(device, rssi, scanRecord);
		}

	};

	/**
	 * Create a json object from the scanned device and trigger the scanCallback's onData
	 *
	 * @param device the bluetooth device that was scanned
	 * @param rssi the rssi value with which the device was scanned
	 * @param scanRecord the scan record (advertisement data) which was scanned.
	 */
	private synchronized void onDeviceScanned(BluetoothDevice device, int rssi, byte[] scanRecord) {

		// [3.11.15] moved scanning = false into if statement, so that if scan is stopped but a scan
		// result is still received, _scanning is not set to true again
		if (_scanCallback != null) {
			_scanning = true;

			JSONObject scanResult = new JSONObject();
			addDeviceInfo(scanResult, device);
			addProperty(scanResult, BleCoreTypes.PROPERTY_RSSI, rssi);
			addBytes(scanResult, BleCoreTypes.PROPERTY_ADVERTISEMENT, scanRecord);
			setStatus(scanResult, BleCoreTypes.PROPERTY_SCAN_RESULT);

			_scanCallback.onData(scanResult);
		}
	}

	/**
	 * Disconnect a currently connected device
	 *
	 * @param address the MAC address of the device which should be disconnected
	 * @param callback the callback to be informed about success or failure
	 */
	public boolean disconnectDevice(String address, IDataCallback callback) {

		getLogger().LOGd(TAG, "disconnecting device ...");

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			getLogger().LOGe(TAG, ".. not connected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		switch (connection.getConnectionState()) {
			case CONNECTING:
				_connectionCallback = null;
				connection.setConnectionState(ConnectionState.DISCONNECTED);
				break;
			default:
				_connectionCallback = callback;
				connection.setConnectionState(ConnectionState.DISCONNECTING);
		}

		BluetoothGatt gatt = connection.getGatt();

		if (gatt != null) {
			BluetoothDevice device = gatt.getDevice();

			JSONObject deviceJson = new JSONObject();
			addDeviceInfo(deviceJson, device);

//			switch (connection.getConnectionState()) {
//				case CONNECTING:
//					_connectionCallback = null;
//					connection.setConnectionState(ConnectionState.DISCONNECTED);
//					break;
//				default:
//					_connectionCallback = callback;
//					connection.setConnectionState(ConnectionState.DISCONNECTING);
//			}

			gatt.disconnect();

			getLogger().LOGd(TAG, "disconnecting device ... done");

		} else {
			getLogger().LOGe(TAG, "gatt == null");
		}

		return true;
	}

	/**
	 * Check if the device is connected
	 *
	 * @param address MAC address of the device
	 * @return true if connected, false otherwise
	 */
	public boolean isConnected(String address) {
		Connection connection = _connections.get(address);
		return connection.getConnectionState() == ConnectionState.CONNECTED;
	}

	/**
	 * Check if the device is disconnected
	 *
	 * @param address MAC address of the device
	 * @return true if disconnected, false otherwise
	 */
	public boolean isDisconnected(String address) {
		Connection connection = _connections.get(address);
		return connection.getConnectionState() == ConnectionState.DISCONNECTED;
	}

	/**
	 * Check if the device is closed
	 *
	 * @param address MAC address of the device
	 * @return true if closed, false otherwise
	 */
	public boolean isClosed(String address) {
		return !_connections.containsKey(address);
	}

	/**
	 * Close a disconnected device. only a certain number of device can be kept open at a time
	 * even if they are disconnected. to avoid reaching the limit, always close the device after
	 * a disconnect
	 * if clearCache is set to true, the cache of discovered services is cleared. this means next
	 * discover will return a fresh list, but it makes discovery (and connects) slower. so only
	 * refresh if really needed.
	 *
	 * @param address MAC address of the device
	 * @param clearCache if true, clear the cache of discovered services, false otherwise.
	 * @param callback callback to be informed about success or failure
	 */
	public boolean closeDevice(String address, boolean clearCache, IStatusCallback callback) {

		getLogger().LOGd(TAG, "closing device ...");

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.DISCONNECTED) {
			getLogger().LOGe(TAG, ".. still connected?");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();

		if (gatt != null) {
			if (clearCache) {
				refreshDeviceCache(gatt);
			}

			gatt.close();
			_connections.remove(address);
		} else {
			getLogger().LOGe(TAG, "gatt == null");
		}

		getLogger().LOGd(TAG, "closing device ... done");

		callback.onSuccess();
		return true;

	}

	/**
	 * Refresh the cache of discovered services and characteristics
	 *
	 * @param gatt the bluetooth gatt server of the device
	 */
	private boolean refreshDeviceCache(final BluetoothGatt gatt) {
		getLogger().LOGd(TAG, "refreshDeviceCache");
		/*
		 * If the device is bonded this is up to the Service Changed characteristic to notify Android that the services has changed.
		 * There is no need for this trick in that case.
		 * If not bonded, the Android should not keep the services cached when the Service Changed characteristic is present in the target device database.
		 * However, due to the Android bug (still exists in Android 5.0.1), it is keeping them anyway and the only way to clear services is by using this hidden refresh method.
		 */
//		if (force || gatt.getDevice().getBondState() == BluetoothDevice.BOND_NONE) {
		// Log.i(TAG, "refresh");
			/*
			 * There is a refresh() method in BluetoothGatt class but for now it's hidden. We will call it using reflections.
			 */
		boolean success = false;
		try {
			final Method refresh = gatt.getClass().getMethod("refresh");
			if (refresh != null) {
				success = (Boolean) refresh.invoke(gatt);
				getLogger().LOGd(TAG, "Refreshing result: " + success);
			}
		} catch (Exception e) {
			getLogger().LOGe(TAG, "An exception occurred while refreshing device", e);
			getLogger().LOGe(TAG, "Refreshing failed");
			return false;
		}
//		}
		return success;
	}

	/**
	 * Refresh the cache of discovered services and characteristics of a connected device
	 *
	 * @param address the MAC address of the device
	 * @param callback the callback to be informed about success or failure
	 */
	public boolean refreshDeviceCache(String address, IStatusCallback callback) {

		getLogger().LOGd(TAG, "refreshing device cache ...");

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			getLogger().LOGe(TAG, ".. not connected?");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();

		if (gatt == null) {
			getLogger().LOGe(TAG, "gatt == null");
			return false;
		}
		boolean success = refreshDeviceCache(gatt);
		if (!success) {
			callback.onError(BleErrors.ERROR_REFRESH_FAILED);
			return false;
		}
		callback.onSuccess();
		return true;
	}

	/**
	 * Disconnect and close the device
	 *
	 * @param address MAC address of the device
	 * @param clearCache true if the discovery cache should be cleared, false otherwise
	 * @param callback callback to be informed about success or failure
	 */
	public boolean disconnectAndCloseDevice(final String address, final boolean clearCache, final IDataCallback callback) {

		return disconnectDevice(address, new IDataCallback() {

			// only report the first error, i.e. if disconnect and close fail,
			// only report error of disconnect and skip error of close
			private void close(final boolean reportError) {
				closeDevice(address, clearCache, new IStatusCallback() {
					@Override
					public void onSuccess() {
						JSONObject returnJson = new JSONObject();
						setStatus(returnJson, "closed");
						callback.onData(returnJson);
					}

					@Override
					public void onError(int error) {
						if (reportError) {
							callback.onError(error);
						}
					}
				});
			}

			@Override
			public void onData(JSONObject json) {
				String status = getStatus(json);
				if (status == "disconnected") {
					callback.onData(json);
					close(true);
				} else {
					getLogger().LOGe(TAG, "wrong status received: %s", status);
				}
			}

			@Override
			public void onError(int error) {
				// [05.01.16] only report error if close fails, otherwise, we get an error
				//   from the disconnect, and then a success from the close, which is
				//   more confusing than not getting any report back from the disconnect
//				callback.onError(error);
				// also try to close even if disconnect fails, but don't report
				// the error if it fails
				if (error != BleErrors.ERROR_NEVER_CONNECTED) {
					close(true);
				} else {
					// [03.01.17] if never connected, we also don't need to close otherwise it
					//   the close will just throw another never connected error
					callback.onError(error);
				}
			}
		});
	}

	/**
	 * Read a characteristic
	 *
	 * @param address MAC address of the device
	 * @param serviceUuid UUID of the service containing the characteristic
	 * @param characteristicUuid UUID of the characteristic
	 * @param callback callback to be informed about read value or error
	 * @return true if read started, false if error
	 */
	public boolean read(String address, String serviceUuid, String characteristicUuid, IDataCallback callback) {

		getLogger().LOGd(TAG, "read ...");

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			getLogger().LOGe(TAG, ".. not connected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothGattService service = gatt.getService(BleUtils.stringToUuid(serviceUuid));

		if (service == null) {
			getLogger().LOGe(TAG, ".. service not found!");
			callback.onError(BleErrors.ERROR_SERVICE_NOT_FOUND);
			return false;
		}

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);

		if (characteristic == null) {
			getLogger().LOGe(TAG, ".. characteristic not found!");
			callback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			return false;
		}

		// todo: one callback per characteristic / read / write / operation
		_characteristicsReadCallback = callback;

		boolean result = gatt.readCharacteristic(characteristic);
		if (!result) {
			getLogger().LOGe(TAG, ".. failed to read from characteristic!");
			callback.onError(BleErrors.ERROR_CHARACTERISTIC_READ_FAILED);
			return false;
		}

		getLogger().LOGd(TAG, "read ... done");

		return true;
	}

	/**
	 * Write to a characteristic with write type default
	 *
	 * @param address MAC address of the device
	 * @param serviceUuid UUID of the service containing the characteristic
	 * @param characteristicUuid UUID of the characteristic
	 * @param value the value to be written as an array of bytes
	 * @param callback callback to be informed about success or error
	 * @return true if the write was started, false if error
	 */
	public boolean write(String address, String serviceUuid, String characteristicUuid, byte[] value, IStatusCallback callback) {
		return write(address, serviceUuid, characteristicUuid, value, callback, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
	}

	/**
	 * Write to a characteristic with write type no response
	 *
	 * @param address MAC address of the device
	 * @param serviceUuid UUID of the service containing the characteristic
	 * @param characteristicUuid UUID of the characteristic
	 * @param value the value to be written as an array of bytes
	 * @param callback callback to be informed about success or error
	 * @return true if the write was started, false if error
	 */
	public boolean writeNoResponse(String address, String serviceUuid, String characteristicUuid, byte[] value, IStatusCallback callback) {
		return write(address, serviceUuid, characteristicUuid, value, callback, BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
	}

	/**
	 * Write to a characteristic
	 *
	 * @param address MAC address of the device
	 * @param serviceUuid UUID of the service containing the characteristic
	 * @param characteristicUuid UUID of the characteristic
	 * @param value the value to be written as an array of bytes
	 * @param callback callback to be informed about success or error
	 * @param writeType write type to be used, see {@link BluetoothGattCharacteristic}
	 * @return true if the write was started, false if error
	 */
	private synchronized boolean write(String address, String serviceUuid, String characteristicUuid, byte[] value, IStatusCallback callback, int writeType) {

		getLogger().LOGd(TAG, "write ...");

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			callback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			getLogger().LOGe(TAG, ".. not connected");
			callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothGattService service = gatt.getService(BleUtils.stringToUuid(serviceUuid));

		if (service == null) {
			getLogger().LOGe(TAG, ".. service not found!");
			callback.onError(BleErrors.ERROR_SERVICE_NOT_FOUND);
			return false;
		}

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid);

		if (characteristic == null) {
			getLogger().LOGe(TAG, ".. characteristic not found!");
			callback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			return false;
		}

		characteristic.setWriteType(writeType);

		boolean result = characteristic.setValue(value);
		if (!result) {
			getLogger().LOGe(TAG, ".. failed to set value!");
			callback.onError(BleErrors.ERROR_WRITE_VALUE_NOT_SET);
			return false;
		}

		_characteristicsWriteCallback = callback;

		int retry = 0;

		do {
			result = gatt.writeCharacteristic(characteristic);
			if (!result) {
				retry++;
				getLogger().LOGw(TAG, "retry: %d", retry);
				SystemClock.sleep(retry * 50);
			}
		} while (!result && retry <= 3);

		if (!result) {
			getLogger().LOGe(TAG, ".. failed to write characteristic!");
			callback.onError(BleErrors.ERROR_WRITE_FAILED);
			return false;
		}

		getLogger().LOGd(TAG, "write ... done");
		return true;
	}

	/**
	 * Subscribe to a characteristic in order to receive notifications.
	 *
	 * @param address MAC address of the device
	 * @param serviceUuid UUID of the service containing the characteristic
	 * @param characteristicUuid UUID of the characteristic
	 * @param statusCallback callback to be informed about success or error
	 * @param callback callback to be informed about received notifications
	 * @return true if subscribe started, false if error
	 */
	protected boolean subscribe(String address, String serviceUuid, String characteristicUuid,
								IStatusCallback statusCallback, ISubscribeCallback callback) {

		getLogger().LOGd(TAG, "subscribe ...");

		UUID uuidService = BleUtils.stringToUuid(serviceUuid);
		UUID uuidCharacteristic = BleUtils.stringToUuid(characteristicUuid);

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			statusCallback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			statusCallback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			getLogger().LOGe(TAG, ".. not connected");
			statusCallback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothGattService service = gatt.getService(uuidService);

		if (service == null) {
			getLogger().LOGe(TAG, ".. service not found!");
			statusCallback.onError(BleErrors.ERROR_SERVICE_NOT_FOUND);
			return false;
		}

		BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuidCharacteristic);

		if (characteristic == null) {
			getLogger().LOGe(TAG, ".. characteristic not found!");
			statusCallback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			return false;
		}

		BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BleCoreTypes.CLIENT_CONFIGURATION_DESCRIPTOR_UUID);

		if (descriptor == null) {
			getLogger().LOGe(TAG, ".. descriptor not found!");
			statusCallback.onError(BleErrors.ERROR_NOTIFICATION_DESCRIPTOR_NOT_FOUND);
			return false;
		}


		boolean result = descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
		if (!result) {
			getLogger().LOGe(TAG, ".. failed to set descriptor for notifications!");
			statusCallback.onError(BleErrors.ERROR_DESCRIPTOR_SET_FAILED);
			return false;
		}

		result = gatt.writeDescriptor(descriptor);
		if (!result) {
			getLogger().LOGe(TAG, ".. failed to subscribe for notifications!");
			statusCallback.onError(BleErrors.ERROR_SUBSCRIBE_NOTIFICATION_FAILED);
			return false;
		}

		_subscribeCallback = statusCallback;
		_notificationCallbacks.put(characteristic.getUuid(), callback);

		getLogger().LOGd(TAG, "subscribe ... done");
		return true;
	}

	/**
	 * Unsubscribe from a characteristic, i.e. stop receiving notifications
	 *
	 * @param address MAC address of the device
	 * @param serviceUuid UUID of the service containing the characteristic
	 * @param characteristicUuid UUID of the characteristic
	 * @param statusCallback callback to be informed about success or error
	 * @return true if unsubscribe started, false if error
	 */
	protected boolean unsubscribe(String address, String serviceUuid, String characteristicUuid,
								  IStatusCallback statusCallback) {

		getLogger().LOGd(TAG, "unsubscribe ...");

		UUID uuidService = BleUtils.stringToUuid(serviceUuid);
		UUID uuidCharacteristic = BleUtils.stringToUuid(characteristicUuid);

		if (!isInitialized()) {
			getLogger().LOGe(TAG, ".. not initialized");
			statusCallback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			return false;
		}

		Connection connection = _connections.get(address);
		if (connection == null) {
			getLogger().LOGe(TAG, ".. never connected");
			statusCallback.onError(BleErrors.ERROR_NEVER_CONNECTED);
			return false;
		}

		if (connection.getConnectionState() != ConnectionState.CONNECTED) {
			getLogger().LOGe(TAG, ".. not connected");
			statusCallback.onError(BleErrors.ERROR_NOT_CONNECTED);
			return false;
		}

		BluetoothGatt gatt = connection.getGatt();
		BluetoothGattService service = gatt.getService(uuidService);

		if (service == null) {
			getLogger().LOGe(TAG, ".. service not found!");
			statusCallback.onError(BleErrors.ERROR_SERVICE_NOT_FOUND);
			return false;
		}

		BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuidCharacteristic);

		if (characteristic == null) {
			getLogger().LOGe(TAG, ".. characteristic not found!");
			statusCallback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			return false;
		}

		BluetoothGattDescriptor descriptor = characteristic.getDescriptor(BleCoreTypes.CLIENT_CONFIGURATION_DESCRIPTOR_UUID);

		if (descriptor == null) {
			getLogger().LOGe(TAG, ".. descriptor not found!");
			statusCallback.onError(BleErrors.ERROR_NOTIFICATION_DESCRIPTOR_NOT_FOUND);
			return false;
		}

		boolean result = descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
		if (!result) {
			getLogger().LOGe(TAG, ".. failed to set descriptor for notifications!");
			statusCallback.onError(BleErrors.ERROR_DESCRIPTOR_SET_FAILED);
			return false;
		}

		result = gatt.writeDescriptor(descriptor);
		if (!result) {
			getLogger().LOGe(TAG, ".. failed to unsubscribe from notifications!");
			statusCallback.onError(BleErrors.ERROR_UNSUBSCRIBE_NOTIFICATION_FAILED);
			return false;
		}

		_unsubscribeCallback = statusCallback;

		getLogger().LOGd(TAG, "unsubscribe ... done");

		return true;
	}

	/**
	 * The BluetoothGattCallback is used by the Bluetooth Adapter to inform about connection
	 * state changes, read write status, notifcations, subscribe and unsubscribe status, etc.
	 */
	private class BluetoothGattCallbackExt extends BluetoothGattCallback {

		/**
		 * Called whenever the connection state changes, e.g. device disconnects or is connected
		 * We check if the state change is as expected and trigger success or failure on the
		 * connectionCallback
		 */
		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
//			String intentAction;

			BluetoothDevice device = gatt.getDevice();
			Connection connection = _connections.get(device.getAddress());
			if (status == BluetoothGatt.GATT_SUCCESS) {

				if (newState == BluetoothProfile.STATE_CONNECTED) {

					clearConnectTimeout();

					if (connection != null) {
						connection.setConnectionState(ConnectionState.CONNECTED);
					}
					getLogger().LOGd(TAG, "Connected to GATT server.");

					JSONObject json = new JSONObject();
					setStatus(json, "connected");

					if (_connectionCallback != null) {
						_connectionCallback.onData(json);
					}

					//				intentAction = ACTION_GATT_CONNECTED;
					//				broadcastUpdate(intentAction);

				} else if (newState == BluetoothProfile.STATE_DISCONNECTED) {

					if (connection != null) {
						connection.setConnectionState(ConnectionState.DISCONNECTED);
					}
					getLogger().LOGd(TAG, "Disconnected from GATT server.");

					JSONObject json = new JSONObject();
					setStatus(json, "disconnected");

					if (_connectionCallback != null) {
						_connectionCallback.onData(json);
					}

					//				intentAction = ACTION_GATT_DISCONNECTED;
					//				broadcastUpdate(intentAction);

				} else {
					getLogger().LOGd(TAG, "..ing state: %d", status);
					return;
				}
			} else {
				getLogger().LOGe(TAG, "BluetoothGatt Error, status: %d", status);
				clearConnectTimeout();

				// [03.01.17] do not call gatt.close() here, it seems to lead to more gatt error 133
				//   and BluetoothGatt calls close by itself
				// [09.01.17] This seems to lead to staying connected.
				//   We have to figure out which errors automatically disconnect and which don't.
				// [17.01.17] call gatt.disconnect(), just in case the connection stays open
				//   even after calling gatt.close()
				gatt.disconnect();
				gatt.close();
				connection.setConnectionState(ConnectionState.DISCONNECTED);

				if (_connectionCallback != null) {
					_connectionCallback.onError(status);
				}
			}

		}

		/**
		 * Is called when the service discovery completed. we obtain the discovered
		 * services and characteristics and trigger the discoveryCallback
		 */
		@Override
		// New services discovered
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {

			BluetoothDevice device = gatt.getDevice();
			Connection connection = _connections.get(device.getAddress());

			if (status == BluetoothGatt.GATT_SUCCESS) {
				connection.setDiscoveryState(DiscoveryState.DISCOVERED);

				JSONObject json = getDiscovery(gatt);

				if (_discoveryCallback != null) {
					_discoveryCallback.onData(json);
				}

//				broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);

			} else {
				connection.setDiscoveryState(DiscoveryState.UNDISCOVERED);

				getLogger().LOGe(TAG, "Discovery failed, status: %d", status);

				if (_discoveryCallback != null) {
					_discoveryCallback.onError(BleErrors.ERROR_DISCOVERY_FAILED);
				}
			}
		}

		/**
		 * Is called whenever a characteristic is read. this can be successful or failure
		 * trigger the characteristicsReadCallback with the read value or the error
		 */
		@Override
		// Result of a characteristic read operation
		public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

			if (status == BluetoothGatt.GATT_SUCCESS) {

				JSONObject json = new JSONObject();

				setStatus(json, BleCoreTypes.CHARACTERISTIC_PROP_READ);
				setCharacteristic(json, characteristic);
				setValue(json, characteristic.getValue());

				if (_characteristicsReadCallback != null) {
					_characteristicsReadCallback.onData(json);
				}

//				broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);

			} else {
				getLogger().LOGe(TAG, "Characteristic read failed, status: %d", status);

				if (_characteristicsReadCallback != null) {
					_characteristicsReadCallback.onError(BleErrors.ERROR_CHARACTERISTIC_READ_FAILED);
				}
			}
		}

		/**
		 * Is called whenever a notification is received from a subscribed characteristic
		 * trigger the notificationCallback for the given characteristic
		 */
		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

			JSONObject json = new JSONObject();

			setStatus(json, BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY);
			setCharacteristic(json, characteristic);
			setValue(json, characteristic.getValue());

			UUID uuidService = characteristic.getService().getUuid();
			UUID uuidCharacteristic = characteristic.getUuid();

			ISubscribeCallback callback = _notificationCallbacks.get(uuidCharacteristic);
			if (callback != null) {
				getLogger().LOGd(TAG, "notification: %s", BleUtils.bytesToString(characteristic.getValue()));
				callback.onData(uuidService, uuidCharacteristic, json);
			} else {
				getLogger().LOGe(TAG, "callback is null");
			}

		}

		/**
		 * Is called whenever a write on a characteristic completes. either successful or with error
		 * trigger the characteristicsWriteCallback with success or error
		 */
		@Override
		public synchronized void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {

			if (status == BluetoothGatt.GATT_SUCCESS) {

				// do we need to send back the status and the value we wrote??
//				JSONObject json = new JSONObject();
//				setStatus(json, BleCoreTypes.STATUS_WRITTEN);
//				setCharacteristic(json, characteristic);
//				setValue(json, characteristic.getValue());

				if (_characteristicsWriteCallback != null) {
//					_characteristicsWriteCallback.onData(json);
					_characteristicsWriteCallback.onSuccess();
				}

			} else {
				getLogger().LOGe(TAG, "Characteristic write failed, status: %d", status);

				if (_characteristicsWriteCallback != null) {
					_characteristicsWriteCallback.onError(BleErrors.ERROR_CHARACTERISTIC_WRITE_FAILED);
				}
			}
		}

		/**
		 * Is called if the descriptor of a characteristic is read
		 * NOT USED CURRENTLY
		 */
		public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
			if (status == BluetoothGatt.GATT_SUCCESS) {

				JSONObject json = new JSONObject();

				setStatus(json, BleCoreTypes.CHARACTERISTIC_PROP_READ);

				BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();
				setCharacteristic(json, characteristic);

				setValue(json, descriptor.getValue());

				if (_characteristicsReadCallback != null) {
					_characteristicsReadCallback.onData(json);
				}

			} else {
				getLogger().LOGe(TAG, "failed to read descriptor, status: %d", status);

				if (_characteristicsReadCallback != null) {
					_characteristicsReadCallback.onError(BleErrors.ERROR_DESCRIPTOR_READ_FAILED);
				}

			}
		}

		/**
		 * Is called if a descriptor of a characteristic is written. this is the case when
		 * a characteristic is subscribed / unsubscribed.
		 * trigger the subscribeCallback / unsubscribeCallback respectively
		 */
		public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {

			BluetoothGattCharacteristic characteristic = descriptor.getCharacteristic();

			UUID uuidService = characteristic.getService().getUuid();
			UUID uuidCharacteristic = characteristic.getUuid();

			if (descriptor.getUuid().equals(BleCoreTypes.CLIENT_CONFIGURATION_DESCRIPTOR_UUID)) {

				JSONObject json = new JSONObject();

				if (descriptor.getValue() == BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE) {
					// unsubscribe
					boolean result = gatt.setCharacteristicNotification(characteristic, false);

					if (_unsubscribeCallback != null) {
						if (status != BluetoothGatt.GATT_SUCCESS || !result) {
							getLogger().LOGe(TAG, "Failed to unsubscribe");
							_unsubscribeCallback.onError(BleErrors.ERROR_UNSUBSCRIBE_FAILED);
							return;
						}

						getLogger().LOGd(TAG, "unsubscribe success");
						_unsubscribeCallback.onSuccess();
						_notificationCallbacks.remove(characteristic.getUuid());
					} else {
						getLogger().LOGw(TAG, "unsubscribed but callback is null");
					}
				} else {
					// subscribe
					boolean result = gatt.setCharacteristicNotification(characteristic, true);

					if (_subscribeCallback != null) {
						if (status != BluetoothGatt.GATT_SUCCESS || !result) {
							getLogger().LOGe(TAG, "Failed to subscribe");
							_subscribeCallback.onError(BleErrors.ERROR_SUBSCRIBE_FAILED);
							return;
						}

						getLogger().LOGd(TAG, "subscribe success");
						_subscribeCallback.onSuccess();
					} else {
						getLogger().LOGw(TAG, "subscribed but callback is null");
					}
				}
			}

		}
	}

	////////////////////////////////////////////////////////////////////////////////////////////////
	// HELPER FUNCTIONS
	// to populate the JSON objects with data and to retrieve the data again
	////////////////////////////////////////////////////////////////////////////////////////////////


	public static boolean hasCharacteristicProperty(int properties, int property) {
		return (properties & property) == property;
	}

	public static void addProperty(JSONObject json, String key, Object value) {
		try {
			json.put(key, value);
		} catch (JSONException e) {
			e.printStackTrace();
			BleLog.getInstance().LOGe(TAG, "Failed to encode json");
		}
	}

	public static void addDeviceInfo(JSONObject json, BluetoothDevice device) {
		addProperty(json, BleCoreTypes.PROPERTY_ADDRESS, device.getAddress());
		addProperty(json, BleCoreTypes.PROPERTY_NAME, device.getName());
	}

	public static void setCharacteristic(JSONObject json, BluetoothGattCharacteristic characteristic) {
		addProperty(json, BleCoreTypes.PROPERTY_SERVICE_UUID, BleUtils.uuidToString(characteristic.getService().getUuid()));
		addProperty(json, BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID, BleUtils.uuidToString(characteristic.getUuid()));
	}

	public static void setStatus(JSONObject json, String status) {
		addProperty(json, BleCoreTypes.PROPERTY_STATUS, status);
	}

	public static String getStatus(JSONObject json) {
		try {
			return json.getString(BleCoreTypes.PROPERTY_STATUS);
		} catch (JSONException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static void addBytes(JSONObject json, String field, byte[] bytes) {
		String value = BleUtils.bytesToEncodedString(bytes);
		addProperty(json, field, value);
	}

	public static byte[] getBytes(JSONObject json, String field) {
		try {
			String value = json.getString(field);
			return BleUtils.encodedStringToBytes(value);
		} catch (JSONException e) {
			BleLog.getInstance().LOGe(TAG, "failed to read bytes");
			e.printStackTrace();
		}

		return null;
	}

	public static byte[] getValue(JSONObject json) {
		return getBytes(json, BleCoreTypes.PROPERTY_VALUE);
	}

	public static void setValue(JSONObject json, byte[] value) {
		addBytes(json, BleCoreTypes.PROPERTY_VALUE, value);
	}

}
