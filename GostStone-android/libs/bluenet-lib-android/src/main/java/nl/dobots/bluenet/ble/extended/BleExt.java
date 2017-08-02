package nl.dobots.bluenet.ble.extended;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import nl.dobots.bluenet.ble.base.BleBase;
import nl.dobots.bluenet.ble.base.BleBaseEncryption;
import nl.dobots.bluenet.ble.base.BleConfiguration;
import nl.dobots.bluenet.ble.core.BleCoreTypes;
import nl.dobots.bluenet.ble.extended.callbacks.EventListener;
import nl.dobots.bluenet.ble.base.callbacks.IBaseCallback;
import nl.dobots.bluenet.ble.base.callbacks.IBooleanCallback;
import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.core.callbacks.IDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IExecStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IPowerSamplesCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.IWriteCallback;
import nl.dobots.bluenet.ble.base.callbacks.SimpleExecStatusCallback;
import nl.dobots.bluenet.ble.base.structs.ControlMsg;
import nl.dobots.bluenet.ble.mesh.structs.MeshControlMsg;
import nl.dobots.bluenet.ble.base.structs.PowerSamples;
import nl.dobots.bluenet.ble.base.structs.TrackedDeviceMsg;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.BleCore;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.ble.extended.structs.BleDeviceMap;
import nl.dobots.bluenet.ibeacon.BleIbeaconRanging;
import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.Logging;
import nl.dobots.bluenet.utils.BleUtils;

/**
 * Copyright (c) 2015 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p/>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p/>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p/>
 * Created on 15-7-15
 *
 * @author Dominik Egger
 */
public class BleExt extends Logging implements IWriteCallback {

	private static final String TAG = BleExt.class.getCanonicalName();
	private static final int LOG_LEVEL = Log.VERBOSE;

	public static final long CONNECTION_ALIVE_INTERVAL = 10000; // 10 seconds (for 15 seconds timeout on device)

	// default timeout for connection attempt
	private int _connectTimeout = 10000; // 10 seconds

	// default time used for delayed disconnects
	public static final int DELAYED_DISCONNECT_TIME = 5000; // 5 seconds

	private int _numConnectRetries = 3;
	private int _numOtherRetries = 1;

	private BleBase _bleBase;

	// list of devices. scanned devices that pass through the filter will be stored in the list
	private BleDeviceMap _devices = new BleDeviceMap();

	// address of the device we are connecting / talking to
	private String _targetAddress;

	// filter, used to filter devices based on "type", eg. only report crownstone devices, or
	// only report guidestone devices
	private BleDeviceFilter _scanFilter = BleDeviceFilter.all;

	//	private ArrayList<BleIbeaconFilter> _iBeaconFilter = new ArrayList<>();
	private BleIbeaconRanging _iBeaconRanger = new BleIbeaconRanging();

	// current connection state
	private BleDeviceConnectionState _connectionState = BleDeviceConnectionState.uninitialized;

	// keep a list of discovered services and characteristics
	private ArrayList<String> _detectedCharacteristics = new ArrayList<>();

	// handler used for delayed execution and timeouts
	private Handler _handler;

	private ArrayList<String> _blackList;
	private ArrayList<String> _whiteList;

	private IBleDeviceCallback _cloudScanCB;

	private BleExtState _bleExtState;

	private HashMap<String, Integer> _subscriberIds = new HashMap<>();

	public BleExt() {
		_bleBase = new BleBase();
		_bleBase.setOnWriteCallback(this);

		_bleExtState = new BleExtState(this);

		// create handler with its own thread
		HandlerThread handlerThread = new HandlerThread("BleExtHandler");
		handlerThread.start();
		_handler = new Handler(handlerThread.getLooper());
	}

	/**
	 * Get access to the base bluenet object. Only use it if you need to change some low level
	 * settings. Usually this is Not necessary.
	 *
	 * @return BleBase object used by this exented object to interact with the Android Bluetooth
	 * functions
	 */
	public BleBase getBleBase() {
		return _bleBase;
	}

	public BleExtState getBleExtState() {
		return _bleExtState;
	}

	public BleIbeaconRanging getIbeaconRanger() {
		return _iBeaconRanger;
	}

	/**
	 * Set the scan device filter. by setting a filter, only the devices specified will
	 * pass through the filter and be reported to the application, any other detected devices
	 * will be ignored.
	 *
	 * @param filter the filter to be used
	 */
	public void setScanFilter(BleDeviceFilter filter) {
		if (_scanFilter != filter) {
			synchronized (BleExt.class) {
				_devices.clear();
			}
		}
		_scanFilter = filter;
	}

	/**
	 * Get the currently set filter
	 *
	 * @return the device filter
	 */
	public BleDeviceFilter getScanFilter() {
		return _scanFilter;
	}

	@Override
	protected int getLogLevel() {
		return LOG_LEVEL;
	}

	@Override
	protected String getTag() {
		return TAG;
	}

	@Override
	public void setLogger(BleLog logger) {
		super.setLogger(logger);
		_bleBase.setLogger(logger);
	}

//	public void addIbeaconFilter(BleIbeaconFilter filter) {
//		_iBeaconFilter.add(filter);
//	}
//
//	public void remIbeaconFilter(BleIbeaconFilter filter) {
//		for (int i=_iBeaconFilter.size(); i>0; i--) {
//			if (_iBeaconFilter.get(i).equals(filter)) {
//				_iBeaconFilter.remove(i);
//			}
//		}
//	}
//
//	public void clearIbeaconFilter() {
//		_iBeaconFilter.clear();
//	}
//
//	public ArrayList<BleIbeaconFilter> getIbeaconFilter() {
//		return _iBeaconFilter;
//	}

	/**
	 * Get the current target address, i.e. the address of the device we are connected to
	 *
	 * @return the MAC address of the device
	 */
	public String getTargetAddress() {
		return _targetAddress;
	}

	public void setNumRetries(int numRetries) {
		_numConnectRetries = numRetries;
	}

	public void setConnectTimeout(int timeoutMs) {
		_connectTimeout = timeoutMs;
	}

	/**
	 * Get the list of scanned devices. The list is updated every time a device is detected, the
	 * rssi is updated, the average rssi is computed and if the device is an iBeacon, the distance
	 * is estimated
	 *
	 * @return the list of scanned devices
	 */
	public synchronized BleDeviceMap getDeviceMap() {
		// make sure it is refreshed
		_devices.refresh();
		return _devices;
	}

	/**
	 * Clear the list of scanned devices.
	 */
	public synchronized void clearDeviceMap() {
		_devices.clear();
	}

	/**
	 * Get the current connection state
	 *
	 * @return connection state
	 */
	public BleDeviceConnectionState getConnectionState() {
		return _connectionState;
	}

	public boolean enableEncryption(boolean enable) {
		return _bleBase.enableEncryption(enable);
	}

	/**	Set an event callback listener. will be informed about events such as bluetooth on / off,
	 *  location services on / off, etc.
	 *
	 *  @param listener listener used to report if bluetooth is enabled / disabled, etc.
	 */
	public void setEventListener(final EventListener listener) {
		_bleBase.setEventCallback(new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				String state = BleCore.getStatus(json);
				if (state != null) {
					switch (state) {
						case BleCoreTypes.EVT_BLUETOOTH_ON: {
							listener.onEvent(EventListener.Event.BLUETOOTH_TURNED_ON);
							break;
						}
						case BleCoreTypes.EVT_BLUETOOTH_OFF: {
							listener.onEvent(EventListener.Event.BLUETOOTH_TURNED_OFF);
							break;
						}
						case BleCoreTypes.EVT_LOCATION_SERVICES_ON: {
							listener.onEvent(EventListener.Event.LOCATION_SERVICES_TURNED_ON);
							break;
						}
						case BleCoreTypes.EVT_LOCATION_SERVICES_OFF: {
							listener.onEvent(EventListener.Event.LOCATION_SERVICES_TURNED_OFF);
							break;
						}
					}
				}
			}

			@Override
			public void onError(int error) {

			}
		});
	}

	/**
	 * Initializes the BLE Modules and tries to enable the Bluetooth adapter. Note, the callback
	 * provided as parameter will persist. The callback will be triggered whenever the state of
	 * the bluetooth adapter changes. That means if the user turns off bluetooth, then the onError
	 * of the callback will be triggered. And again if the user turns bluetooth on, the onSuccess
	 * will be triggered. If the user denies enabling bluetooth, then onError will be called after
	 * a timeout expires
	 *
	 * @param context       the context used to enable bluetooth, this can be a service or an activity
	 * @param callback      callback, used to report back success / error of the initialization
	 */
	public void init(Context context, final IStatusCallback callback) {
		_bleBase.init(context, new IStatusCallback() {
					@Override
					public void onSuccess() {
						_connectionState = BleDeviceConnectionState.initialized;
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						_connectionState = BleDeviceConnectionState.uninitialized;
						callback.onError(error);
					}
				}
		);
	}

	/**
	 * Close the library and release all callbacks
	 */
	public void destroy() {
		_handler.removeCallbacksAndMessages(null);
		_iBeaconRanger.destroy();
		_bleBase.destroy();
	}

	/**
	 * Set the given addresses as black list. any address on the black list will be ignored
	 * during a scan and not returned as a scan result
	 *
	 * @param addresses the MAC addresses of the devices which should be ignored during a scan
	 */
	public void setBlackList(String[] addresses) {
		_blackList = new ArrayList<>(Arrays.asList(addresses.clone()));
	}

	/**
	 * Clear the black list again in order to get all devices during a scan
	 */
	public void clearBlackList() {
		_blackList = null;
	}

	/**
	 * Set the given addresses as white list. only devices on the white list will be returned
	 * during a scan. any other device will be ignored.
	 *
	 * @param addresses the MAC addresses of the devices which should be returned during a scan
	 */
	public void setWhiteList(String[] addresses) {
		_whiteList = new ArrayList<>(Arrays.asList(addresses.clone()));
	}

	/**
	 * Clear the white list again in order to get all devices during a scan
	 */
	public void clearWhiteList() {
		_whiteList = null;
	}

	/**
	 * Start scanning for devices. devices will be provided through the callback. see
	 * startEndlessScan for details
	 * <p>
	 * Note: clears the device list on start
	 *
	 * @param callback callback used to report back scanned devices
	 * @return true if the scan was started, false if an error occurred
	 */
	public void startScan(final IBleDeviceCallback callback) {
		startScan(true, callback);
	}

	/**
	 * Starts a scan used for interval scanning,  devices will be provided through the callback. see
	 * startEndlessScan for details
	 *
	 * @param clearList if true, clears the list before starting the scan
	 * @param callback  callback used to report back scanned devices
	 * @return true if the scan was started, false if an error occurred
	 */
	public void startScan(boolean clearList, final IBleDeviceCallback callback) {
		if (clearList) {
			clearDeviceMap();
		}
//		return startEndlessScan(callback, null);
		startEndlessScan(callback);
	}

//	/**
//	 * Starts a scan used for interval scanning,  devices will be provided through the callback. see
//	 * startEndlessScan for details
//	 *
//	 * @param clearList if true, clears the list before starting the scan
//	 * @param callback callback used to report back scanned devices
//	 * @param beaconCallback callback used to report back scanned devices that match the iBeacon filter
//	 * @return true if the scan was started, false if an error occurred
//	 */
//	public boolean startScan(boolean clearList, final IBleDeviceCallback callback, final IBleBeaconCallback beaconCallback) {
//		if (clearList) {
//			clearDeviceMap();
//		}
//		return startEndlessScan(callback, beaconCallback);
//	}

	/**
	 * Helper function to start an endless scan. endless meaning, it will continue scanning
	 * until stopScan is called. If a device is detected, the data is parsed into a BleDevice
	 * object. Then the filter is checked to see if the device passes through the filter. If
	 * it passes, the device list is updated, which triggers a recalculation of the devices
	 * average RSSI (and the distance if it is an iBeacon). After the update, it is
	 * reported back through the callbacks onDeviceScanned function.
	 *
	 * @param callback callback used to report back scanned devices
	 * @return true if the scan was started, false if an error occurred
	 */
//	private boolean startEndlessScan(final IBleDeviceCallback callback, @Nullable final IBleBeaconCallback beaconCallback) {
	private void startEndlessScan(final IBleDeviceCallback callback) {
//		checkConnectionState(BleDeviceConnectionState.initialized, null);
		if (_connectionState != BleDeviceConnectionState.initialized) {
			getLogger().LOGe(TAG, "State is not initialized: %s", _connectionState.toString());
			callback.onError(BleErrors.ERROR_WRONG_STATE);
			return;
		}

		_connectionState = BleDeviceConnectionState.scanning;

		_bleBase.startEndlessScan(new IBleDeviceCallback() {
			@Override
			public void onSuccess() {
				callback.onSuccess();
			}

			@Override
			public void onDeviceScanned(BleDevice device) {

//				getLogger().LOGd(TAG, "scanned:" + device.toString());

				if (_blackList != null && _blackList.contains(device.getAddress())) {
					return;
				}
				if (_whiteList != null && !_whiteList.contains(device.getAddress())) {
					return;
				}

//				boolean iBeaconMatch = _iBeaconRanger.onScannedDevice(device, beaconCallback);
				boolean iBeaconMatch = _iBeaconRanger.onScannedDevice(device, null);

//				boolean iBeaconMatch = false;
//				if (!_iBeaconFilter.isEmpty() && beaconCallback != null) {
//					if (device.isIBeacon()) {
//						for (BleIbeaconFilter iBeaconFilter : _iBeaconFilter) {
//							if (iBeaconFilter.matches(device.getProximityUuid(), device.getMajor(), device.getMinor())) {
//								iBeaconMatch = true;
//								break;
//							}
//						}
//					}
//					if (iBeaconMatch) {
//						getLogger().LOGd(TAG, "matching ibeacon filter: " + device.getAddress() + " (" + device.getName() + ")");
//						device = updateDevice(device);
//						beaconCallback.onBeaconScanned(device);
//					}
//					else {
//						getLogger().LOGd(TAG, "not matching any ibeacon filter:" + device.getAddress() + " (" + device.getName() + ")");
//					}
//				}

				// If we didn't get any service data, we probably received an advertisement with no scan response
				// So if the device is already in the list, let's assume that it still passes the filter.
				boolean isInDeviceMap;
				synchronized (BleExt.class) {
					isInDeviceMap = _devices.contains(device);
				}
				if (isInDeviceMap && device.getServiceData() == null) {
					// Just update rssi
				}
				else {
					switch (_scanFilter) {
						case iBeacon:
							if (!device.isIBeacon()) return;
							break;
						case anyStone:
							// TODO: how to deal with stones in dfu mode?
							if (!device.isStone() && !device.isDfuMode()) return;
							break;
						case crownstonePlug:
							if (!device.isCrownstonePlug()) return;
							break;
						case crownstoneBuiltin:
							if (!device.isCrownstoneBuiltin()) return;
							break;
						case guidestone:
							if (!device.isGuidestone()) return;
							break;
						case setupStone:
							if (!device.isSetupMode()) return;
							break;
						case all:
							// return any device that was detected
							break;
					}
				}


				// Update the device list, this triggers recalculation of the average RSSI (and distance estimation if it is a beacon).
				device = updateDevice(device);

				// report the updated device
				if (callback != null) {
					callback.onDeviceScanned(device);
				}

				if (_cloudScanCB != null) {
					_cloudScanCB.onDeviceScanned(device);
				}
			}

			@Override
			public void onError(int error) {
				if (error == BleErrors.ERROR_ALREADY_SCANNING) {
					_bleBase.stopEndlessScan(null);
				}
				_connectionState = BleDeviceConnectionState.initialized;
				callback.onError(error);
			}
		});
	}

	private synchronized BleDevice updateDevice(BleDevice device) {
		return _devices.updateDevice(device);
	}

	/**
	 * Stop scanning for devices
	 *
	 * @param callback the callback used to report success or failure of the stop scan
	 * @return true if the scan was stopped, false otherwise
	 */
	public void stopScan(final IStatusCallback callback) {
		_connectionState = BleDeviceConnectionState.initialized;
		_bleBase.stopEndlessScan(callback);
	}

	/**
	 * Check if currently scanning for devices
	 *
	 * @return true if scanning, false otherwise
	 */
	public boolean isScanning() {
		return _bleBase.isScanning();
	}

	/**
	 * Every time a device is scanned, the onDeviceScanned function of the
	 * callback provided as scanCB parameter will trigger. Use this to enable
	 * cloud upload, i.e. forward the scan to the crownstone-loopack-sdk
	 *
	 * @param scanCB
	 */
	public void enableCloudUpload(IBleDeviceCallback scanCB) {
		_cloudScanCB = scanCB;
	}

	/**
	 * Disable cloud upload again
	 */
	public void disableCloudUpload() {
		_cloudScanCB = null;
	}

	/**
	 * Connect to the device with the given MAC address. Scan first for devices to find possible
	 * devices or make sure that the device you want to connect to is there.
	 *
	 * @param address  the MAC address of the device, in the form of "12:34:56:AB:CD:EF"
	 * @param callback the callback used to report success or failure. onSuccess will be called
	 *                 if the device was successfully connected. onError will be called with an
	 *                 ERROR to report failure.
	 */
	private void connect(final String address, final IStatusCallback callback) {

		if (checkConnectionState(BleDeviceConnectionState.initialized, null)) {

			if (address != null) {
				_targetAddress = address;
			}

			if (_targetAddress == null) {
				callback.onError(BleErrors.ERROR_NO_ADDRESS_PROVIDED);
				return;
			}

			_connectionState = BleDeviceConnectionState.connecting;

			IDataCallback connectCallback = new IDataCallback() {
				@Override
				public void onData(JSONObject json) {
					String status = BleCore.getStatus(json);
					if (status == "connected") {
						onConnect();
						callback.onSuccess();
					} else {
						getLogger().LOGe(TAG, "wrong status received: %s", status);
						_connectionState = BleDeviceConnectionState.initialized;
						callback.onError(BleErrors.ERROR_CONNECT_FAILED);
					}
				}

				@Override
				public void onError(int error) {
					_connectionState = BleDeviceConnectionState.initialized;

					if (!retry(error)) {
						callback.onError(error);
					} else {
						connect(address, callback);
					}
				}
			};

//			if (_bleBase.isClosed(_targetAddress)) {
//				_bleBase.connectDevice(_targetAddress, _connectTimeout, dataCallback);
//			} else if (_bleBase.isDisconnected(_targetAddress)) {
//				_bleBase.reconnectDevice(_targetAddress, 30, dataCallback);
			if (_bleBase.isClosed(_targetAddress) || _bleBase.isDisconnected(_targetAddress)) {
				_bleBase.connectDevice(_targetAddress, _connectTimeout, connectCallback);
			}
		} else if (checkConnectionState(BleDeviceConnectionState.connected, null)) {
			if (_targetAddress.equals(address)) {
				callback.onSuccess();
			} else {
				callback.onError(BleErrors.ERROR_STILL_CONNECTED);
			}
		} else {
			callback.onError(BleErrors.ERROR_WRONG_STATE);
		}

	}

	/**
	 * Helper function to handle connect events. E.g. update the connection state
	 */
	private void onConnect() {
		getLogger().LOGd(TAG, "successfully connected");
		// todo: timeout?
		_connectionState = BleDeviceConnectionState.connected;
		_subscriberIds.clear();

		// Do not automatically keep the connection alive!
//		_handler.postDelayed(_connectionKeepAlive, CONNECTION_ALIVE_INTERVAL);
	}

	/**
	 * Keeps the connection alive by sending NOP commands to the device.
	 * ToDo: Has to be improved by taking advantage of other writes and only send a NOP if no other
	 * write was sent.
	 */
	private Runnable _connectionKeepAlive = new Runnable() {

		@Override
		public void run() {
			if (isConnected(null)) {
				_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_NOP),
					new IStatusCallback() {
						@Override
						public void onSuccess() {
							getLogger().LOGd(TAG, "keep connection alive success");
							rescheduleConnectionKeepAlive();
						}

						@Override
						public void onError(int error) {
							getLogger().LOGd(TAG, "keep connection alive error: %d", error);
							rescheduleConnectionKeepAlive();
						}
					});
			}
		}
	};

	private void rescheduleConnectionKeepAlive() {
		_handler.removeCallbacks(_connectionKeepAlive);
		_handler.postDelayed(_connectionKeepAlive, CONNECTION_ALIVE_INTERVAL);
	}

	@Override
	public void onWrite() {
		// Do not automatically keep the connection alive!
//		rescheduleConnectionKeepAlive();
	}

	/**
	 * Disconnect from the currently connected device. use the callback to report back
	 * success or failure
	 *
	 * @param callback the callback used to report back if the disconnect was successful or not
	 * @return true if disconnect procedure is started, false if an error occurred and disconnect
	 * procedure could not be started
	 */
	public boolean disconnect(final IStatusCallback callback) {
		checkConnectionState(BleDeviceConnectionState.connected, null);
//		if (!checkConnectionState(BleDeviceConnectionState.connected, callback)) return false;

		_connectionState = BleDeviceConnectionState.disconnecting;
		return _bleBase.disconnectDevice(_targetAddress, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				String status = BleCore.getStatus(json);
				if (status == "disconnected") {
					onDisconnect();
					callback.onSuccess();
				} else {
					getLogger().LOGe(TAG, "wrong status received: %s", status);
					callback.onError(BleErrors.ERROR_DISCONNECT_FAILED);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Helper function to handle disconnect events, e.g. update the connection state
	 */
	private void onDisconnect() {
		getLogger().LOGd(TAG, "successfully disconnected");
		// todo: timeout?
		_connectionState = BleDeviceConnectionState.initialized;
		clearDelayedDisconnect();
		_subscriberIds.clear();
//		_detectedCharacteristics.clear();

		_handler.removeCallbacks(_connectionKeepAlive);
	}

	/**
	 * Helper function to check the connection state. calls the callbacks onError function
	 * with an ERROR_WRONG_STATE if the current state does not match the one provided as a parameter
	 *
	 * @param state    the required state, is checked against the current state
	 * @param callback the callback used to report an error if the states don't match, can be null
	 *                 if error doesn't need to be reported, in which case the return value is enough
	 * @return true if states match, false otherwise
	 */
	private boolean checkConnectionState(BleDeviceConnectionState state, IBaseCallback callback) {
		if (_connectionState != state) {
//			getLogger().LOGe(TAG, "wrong connection state: %s instead of %s", _connectionState.toString(), state.toString());
			if (callback != null) {
				callback.onError(BleErrors.ERROR_WRONG_STATE);
			}
			return false;
		}
		return true;
	}

	/**
	 * Close the device after a disconnect. A device has to be closed after a disconnect before
	 * you can connect to it again.
	 * By providing true as the clearCache parameter, the device cache will be cleared, making sure
	 * that next time we connect, the most up to date service and characteristic list will be
	 * retrieved. providing false as parameter, the device cache will not be cleared, and on next
	 * connect and discover, the services and characteristics are retrieved from the cache, and do
	 * not necessarily match the ones on the device. This speeds up discovery if you can be sure
	 * that the device did not change.
	 *
	 * @param clearCache provide true if the device cache should be cleared, will make sure that
	 *                   services and characteristics are read from the device and not from the cache
	 *                   provide false to leave the cached services and characteristics, making
	 *                   the next connect and discover much faster
	 * @param callback   the callback used to report success or failure
	 */
	public void close(boolean clearCache, IStatusCallback callback) {
		getLogger().LOGd(TAG, "closing device ...");
		_bleBase.closeDevice(_targetAddress, clearCache, callback);
	}

	/**
	 * Discover the available services and characteristics of the connected device. The callbacks
	 * onDiscovery function will be called with service UUID and characteristic UUID for each
	 * discovered characteristic. Once the discovery completes, the onSuccess is called or the onError
	 * if an error occurs
	 * <p>
	 * Note: if you get wrong services and characteristics returned, try to clear the cache by calling
	 * close with parameter clearCache set to true. this makes sure that next discover will really
	 * read the services and characteristics from the device and not the cache
	 *
	 * @param callback the callback used to report discovered services and characteristics
	 */
	public void discoverServices(final IDiscoveryCallback callback) {
		discoverServices(callback, true);
	}

	/**
	 * Discover the available services and characteristics of the connected device. The callbacks
	 * onDiscovery function will be called with service UUID and characteristic UUID for each
	 * discovered characteristic. Once the discovery completes, the onSuccess is called or the onError
	 * if an error occurs
	 * <p>
	 * Note: if you get wrong services and characteristics returned, try to clear the cache by calling
	 * close with parameter clearCache set to true. this makes sure that next discover will really
	 * read the services and characteristics from the device and not the cache
	 *
	 * @param callback the callback used to report discovered services and characteristics
	 * @param readSessionNonce true to read the session nonce after discovery
	 */
	public void discoverServices(final IDiscoveryCallback callback, final boolean readSessionNonce) {
		discoverServices(callback, readSessionNonce, false);
	}

	/**
	 * Discover the available services and characteristics of the connected device. The callbacks
	 * onDiscovery function will be called with service UUID and characteristic UUID for each
	 * discovered characteristic. Once the discovery completes, the onSuccess is called or the onError
	 * if an error occurs
	 * <p>
	 * Note: if you get wrong services and characteristics returned, try to clear the cache by calling
	 * close with parameter clearCache set to true. this makes sure that next discover will really
	 * read the services and characteristics from the device and not the cache
	 *
	 * @param callback the callback used to report discovered services and characteristics
	 * @param readSessionNonce true to read the session nonce after discovery
	 * @param forceDiscover  set to true to force a new discovery,
	 *						 if false and cached discovery found, return the lib cache
	 */
	public void discoverServices(final IDiscoveryCallback callback, final boolean readSessionNonce, boolean forceDiscover) {
		getLogger().LOGd(TAG, "discovering services ...");
		_detectedCharacteristics.clear();
		_bleBase.discoverServices(_targetAddress, forceDiscover, new IDiscoveryCallback() {
			@Override
			public void onDiscovery(String serviceUuid, String characteristicUuid) {
				onCharacteristicDiscovered(serviceUuid, characteristicUuid);
				callback.onDiscovery(serviceUuid, characteristicUuid);
			}

			@Override
			public void onSuccess() {
				getLogger().LOGd(TAG, "... discovery done");

				BleDevice dev = _devices.getDevice(_targetAddress);
				if (dev != null && dev.isDfuMode()) {
					getLogger().LOGd(TAG, "device is probably in dfu mode");
				}
				if (hasCharacteristic(BluenetConfig.DFU_CONTROL_UUID, null)) {
					getLogger().LOGd(TAG, "device has dfu control char");
				}
				//if (dev != null && dev.isDfuMode() && hasCharacteristic(BluenetConfig.DFU_CONTROL_UUID, null)) {
				if (hasCharacteristic(BluenetConfig.DFU_CONTROL_UUID, null)) {
					callback.onSuccess();
					return;
				}

				if (readSessionNonce && _bleBase.isEncryptionEnabled()) {
					_bleBase.readSessionNonce(_targetAddress, new IDataCallback() {
						@Override
						public void onData(JSONObject json) {
							callback.onSuccess();
						}

						@Override
						public void onError(int error) {
							if (!retry(error)) {
								callback.onError(error);
							}
							else {
								// TODO: only retry 1 time
								// Clear cache in hope that the cache was wrong
								disconnectAndClose(true, new IStatusCallback() {
									@Override
									public void onSuccess() {
										connectAndDiscover(_targetAddress, callback, true);
									}

									@Override
									public void onError(int error) {
										callback.onError(error);
									}
								});
							}
						}
					});
				}
				else {
					callback.onSuccess();
				}
			}

			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "... discovery failed");
				callback.onError(error);
			}
		});
	}

	/**
	 * Helper function to handle discovered characteristics. the discovered characteristics
	 * are stored in a list, to later make sure that characteristics are only read/ written to
	 * if they were actually discovered (are present on the device)
	 *
	 * @param serviceUuid        the UUID of the service in which the characteristic was found
	 * @param characteristicUuid the UUID of the characteristic
	 */
	private void onCharacteristicDiscovered(String serviceUuid, String characteristicUuid) {
//		getLogger().LOGd(TAG, "discovered characteristic: %s", characteristicUuid);
		// todo: might have to store both service and characteristic uuid, because the characteristic
		//       UUID is not unique!
		_detectedCharacteristics.add(characteristicUuid);
	}

	/**
	 * Function to refresh the device cache and force discover services. Must already be connected.
	 *
	 * @param callback called when successful or failed
	 */
	public void refreshServices(final IStatusCallback callback) {
		refreshServices(true, callback);
	}

	/**
	 * Function to refresh the device cache and force discover services. Must already be connected.
	 *
	 * @param readSessionNonce true to read the session nonce after discovery
	 * @param callback called when successful or failed
	 */
	public void refreshServices(final boolean readSessionNonce, final IStatusCallback callback) {
		getLogger().LOGi(TAG, "Refresh services");
		if (!isConnected(callback)) {
			return;
		}
		_bleBase.refreshDeviceCache(_targetAddress, new IStatusCallback() {
			@Override
			public void onSuccess() {
				getLogger().LOGd(TAG, "Refreshed device cache. Discover services..");
				discoverServices(new IDiscoveryCallback() {
					@Override
					public void onDiscovery(String serviceUuid, String characteristicUuid) {

					}

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Discovered services.");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGd(TAG, "Failed to discover services");
						callback.onError(error);
					}
				}, readSessionNonce, true);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Helper function to check if the connected device has the requested characteristic
	 *
	 * @param characteristicUuid the UUID of the characteristic which should be present
	 * @param callback           the callback on which the onError is called if the characteristic was not found.
	 *                           can be null if no error has to be reported, in which case the return value should
	 *                           be enough
	 * @return true if the device has the characteristic, false otherwise
	 */
	public boolean hasCharacteristic(String characteristicUuid, IBaseCallback callback) {

		if (_detectedCharacteristics.indexOf(characteristicUuid) == -1) {
			if (callback != null) {
				getLogger().LOGe(TAG, "characteristic not found");
				callback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			}
			getLogger().LOGd(TAG, "hasCharacteristic? " + characteristicUuid + " false");
			return false;
		}
		getLogger().LOGd(TAG, "hasCharacteristic? " + characteristicUuid + " true");
		return true;
	}

	/**
	 * Helper function to check if the device has the configuration characteristics. to enable
	 * configuration / settings, the device needs to have the following characteristics:
	 * * CHAR_CONFIG_COTNROL_UUID (to select the configuration to be read or to write a new value
	 * to the configuration)
	 * * CHAR_CONFIG_READ_UUID (to read the value of the configuration previously selected)
	 * if one of the characteristics is missing, configuration is not available
	 *
	 * @param callback the callback to be informed about an error
	 * @return true if configuration characteristics are available, false otherwise
	 */
	public boolean hasConfigurationCharacteristics(IBaseCallback callback) {
		return hasCharacteristic(BluenetConfig.CHAR_CONFIG_CONTROL_UUID, callback) &&
				hasCharacteristic(BluenetConfig.CHAR_CONFIG_READ_UUID, callback);
	}

	/**
	 * Helper function to check if the device has the state characteristics. to read state variables,
	 * the device needs to have the following characteristics:
	 * * CHAR_STATE_COTNROL_UUID (to select the state to be read)
	 * * CHAR_STATE_READ_UUID (to read the value of the state previously selected)
	 * if one of the characteristics is missing, state variables are not available
	 *
	 * @param callback the callback to be informed about an error
	 * @return true if state characteristics are available, false otherwise
	 */
	public boolean hasStateCharacteristics(IBaseCallback callback) {
		return hasCharacteristic(BluenetConfig.CHAR_STATE_CONTROL_UUID, callback) &&
				hasCharacteristic(BluenetConfig.CHAR_STATE_READ_UUID, callback);
	}

	/**
	 * Helper function to check if the command control characteristic is avialable
	 *
	 * @param callback the callback to be informed about an error
	 * @return true if control characteristic is available, false otherwise
	 */
	public boolean hasControlCharacteristic(IBaseCallback callback) {
		return hasControlCharacteristic(callback, false);
	}

	/**
	 * Helper function to check if the command control characteristic is avialable
	 *
	 * @param callback the callback to be informed about an error
	 * @param allowSetupCharacteristic whether it's also ok to use the control characteristic in setup mode
	 * @return true if control characteristic is available, false otherwise
	 */
	public boolean hasControlCharacteristic(IBaseCallback callback, boolean allowSetupCharacteristic) {
		if (allowSetupCharacteristic && hasCharacteristic(BluenetConfig.CHAR_SETUP_CONTROL_UUID, null)) {
			return true;
		}
		else {
			return hasCharacteristic(BluenetConfig.CHAR_CONTROL_UUID, callback);
		}
	}

	/**
	 * Connect to the given device, once connection is established, discover the available
	 * services and characteristics. The connection will be kept open. Need to disconnect and
	 * close manually afterwards.
	 *
	 * @param address  the MAC address of the device for which we want to discover the services
	 * @param callback the callback which will be notified about discovered services and
	 *                 characteristics
	 */
	public void connectAndDiscover(final String address, final IDiscoveryCallback callback) {
		connectAndDiscover(address, callback, true);
	}

	/**
	 * Connect to the given device, once connection is established, discover the available
	 * services and characteristics. The connection will be kept open. Need to disconnect and
	 * close manually afterwards.
	 *
	 * @param address          the MAC address of the device for which we want to discover the services
	 * @param callback         the callback which will be notified about discovered services and
	 *                         characteristics
	 * @param readSessionNonce whether to read the session nonce after discovery
	 */
	public void connectAndDiscover(final String address, final IDiscoveryCallback callback, final boolean readSessionNonce) {
		connect(address, new IStatusCallback() {
			@Override
			public void onSuccess() {
				handleConnectRetrySuccess();
				/* [05.01.16] I am sometimes getting the behaviour that the connect first succeeds
				 *   and then a couple ms later I receive a disconnect again. In such a case, delaying
				 *   the discover leads to the library trying to discover services although a disconnect
				 *   was received in between.
				 *   If the delay is really necessary, we need to find a better solution
				 */
				// TODO: is this delay necessary?
				/* [03.01.17] taking out the delay because it adds delay to the consumer app since that
				 *   is connecting / disconnecting for every write
				 */
//				_handler.postDelayed(new Runnable() {
//					@Override
//					public void run() {
				discoverServices(new IDiscoveryCallback() {
					@Override
					public void onDiscovery(String serviceUuid, String characteristicUuid) {
						callback.onDiscovery(serviceUuid, characteristicUuid);
					}

					@Override
					public void onSuccess() {
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						switch (error) {
							case BleErrors.ERROR_NOT_CONNECTED:
										/* [18 nov 2016] When there was a successful connect, but a disconnect shortly after,
										 * the callback.onError() was called twice. This avoids that behaviour.
										 */
								break;
							default:
								callback.onError(error);
						}
					}
				}, readSessionNonce);
//					}
//				}, 500);
//				discoverServices(callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Disconnect and close the device if disconnect was successful. See @disconnect and @close
	 * functions for details.
	 *
	 * @param clearCache set to true if device cache should be cleared on close. see @close for details
	 * @param callback   the callback which will be notified about success or failure
	 * @return
	 */
	public synchronized boolean disconnectAndClose(boolean clearCache, final IStatusCallback callback) {
		checkConnectionState(BleDeviceConnectionState.connected, null);
//		if (!checkConnectionState(BleDeviceConnectionState.connected, callback)) return false;

		_connectionState = BleDeviceConnectionState.disconnecting;
		return _bleBase.disconnectAndCloseDevice(_targetAddress, clearCache, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				String status = BleCore.getStatus(json);
				if (status == "closed") {
					onDisconnect();
					// give the bluetooth adapter some time to settle after a close
					_handler.postDelayed(new Runnable() {
						@Override
						public void run() {
							callback.onSuccess();
						}
					}, 300);
				} else if (status != "disconnected") {
					getLogger().LOGe(TAG, "wrong status received: %s", status);
				}
			}

			@Override
			public void onError(int error) {
				switch (error) {
					case BleErrors.ERROR_NEVER_CONNECTED:
					case BleErrors.ERROR_NOT_CONNECTED:
						_connectionState = BleDeviceConnectionState.initialized;
						break;
				}
				callback.onError(error);
			}
		});
	}

	/**
	 * Request permission to access BLE (locations) on Android > 6.0
	 *
	 * @param activity activity which will be notified about the permission request result. The
	 *                 activity will need to implement the onRequestPermissionsResult function and
	 *                 the library function @handlePermissionResult.
	 */
	public void requestPermissions(Activity activity) {
		_bleBase.requestPermissions(activity);
	}

	/**
	 * Helper function which will handle the result of the permission request. call this function
	 * from the activity's onActivityResult function
	 *
	 * @param requestCode  The request code, received in onRequestPermissionResult
	 * @param permissions  The requested permissions, received in onRequestPermissionResult
	 * @param grantResults The grant results for the corresponding permissions
	 *                     which is either {@link android.content.pm.PackageManager#PERMISSION_GRANTED}
	 *                     or {@link android.content.pm.PackageManager#PERMISSION_DENIED}. Never null.
	 * @param callback     the callback function which will be informed about success or failure of the
	 *                     permission request
	 */
	public boolean handlePermissionResult(int requestCode, String[] permissions, int[] grantResults, IStatusCallback callback) {
		return _bleBase.handlePermissionResult(requestCode, permissions, grantResults, callback);
	}

	public Handler getHandler() {
		return _handler;
	}

//	abstract class CallbackRunnable implements Runnable {
//		IStatusCallback _callback;
//
//		public void setCallback(IStatusCallback callback) {
//			_callback = callback;
//		}
//	}


	/**
	 * a runnable with a callback. if the runnable is called it disconnects and
	 * closes the device, then calls the callbacks onSuccess or onError function
	 * used in connectAndXXX functions, which call disconnect after a timeout, but if several
	 * functions are called in succession, only connects once and disconnects automatically
	 * after the last call
	 */
	private class DelayedDisconnectRunnable implements Runnable {
		IStatusCallback _callback;

		public void setCallback(IStatusCallback callback) {
			_callback = callback;
		}

		@Override
		public synchronized void run() {
			getLogger().LOGd(TAG, "delayed disconnect timeout");
			disconnectAndClose(false, new IStatusCallback() {
				@Override
				public void onSuccess() {
					if (_callback != null) {
						_callback.onSuccess();
					}
				}

				@Override
				public void onError(int error) {
					if (_callback != null) {
						_callback.onError(error);
					}
				}
			});
			_delayedDisconnect = null;
		}
	}

	// the runnable used for the delayed disconnect.
	private DelayedDisconnectRunnable _delayedDisconnect = null;

	/**
	 * Helper function to set a delayed disconnect. this will clear the previous delayed
	 * disconnect, then register a new delayed disconnect with the DELAYED_DISCONNECT_TIME timeout
	 *
	 * @param callback the callback which should be notified once the disconnect and close completed
	 */
	private void delayedDisconnect(IStatusCallback callback) {
		getLogger().LOGd(TAG, "delay disconnect");
		// remove the previous delayed disconnect
		clearDelayedDisconnect();
		// if a callback is provided (or no delayedDisconnect runnable available)
		if (callback != null || _delayedDisconnect == null) {
			// create and register a new delayed disconnect with the new callback
			_delayedDisconnect = new DelayedDisconnectRunnable();
//			_delayedDisconnect.setCallback(callback);
		} // otherwise post the previous runnable again with the new timeout
		_handler.postDelayed(_delayedDisconnect, DELAYED_DISCONNECT_TIME);
	}

	/**
	 * Clear the delayed disconnect, either when the timer expires, or if the device
	 * disconnects for another reason
	 */
	private boolean clearDelayedDisconnect() {
		if (_delayedDisconnect != null) {
			getLogger().LOGd(TAG, "delay disconnect remove callbacks");
			_handler.removeCallbacks(_delayedDisconnect);
			return true;
		} else {
			return false;
		}
	}

	private void handleConnectRetrySuccess() {
		_connectRetries = 0;
	}

	private void handleOtherRetrySuccess() {
		_otherRetries = 0;
	}

	private int _connectRetries = 0;
	private int _otherRetries = 0;

	private boolean retry(int error) {

		// check if error is retriable ...
		switch (error) {
			case BleErrors.ERROR_SERVICE_NOT_FOUND:
			case BleErrors.ERROR_CHARACTERISTIC_READ_FAILED:
			case BleErrors.ERROR_CHARACTERISTIC_WRITE_FAILED: {
				if (_otherRetries < _numOtherRetries) {
					_otherRetries++;
					getLogger().LOGw(TAG, "retry: %d (error=%d)", _otherRetries, error);
					return true;
				} else {
					_otherRetries = 0;
					return false;
				}
			}
//			case BleErrors.ERROR_CONNECT_FAILED:
			case 133: {
				if (_connectRetries < _numConnectRetries) {
					_connectRetries++;
					getLogger().LOGw(TAG, "connect retry: %d (error=%d)", _connectRetries, error);
					return true;
				} else {
					_connectRetries = 0;
					return false;
				}
			}
			case 19:
			case BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND:
			default: {
				return false;
			}
		}

	}

//	private boolean retry(final String address, final IExecuteCallback function, final IStatusCallback callback) {
//
//		if (_connectRetries < _numConnectRetries) {
//			_connectRetries++;
//			getLogger().LOGw(TAG, "retry: %d", _connectRetries);
//			connectAndExecute(address, function, callback);
//			return true;
//		} else {
//			_connectRetries = 0;
//			return false;
//		}
//
//	}

	/**
	 * Connects to the given device, discovers the available services, then executes the provided
	 * function, before disconnecting and closing the device again. Once everything completed, the
	 * callbacks onSuccess function is called.
	 * Note: the disconnect and close will be delayed, so consequent calls (within the timeout) to
	 * connectAndExecute functions will keep the connection alive until the last call expires
	 *
	 * @param address  the MAC address of the device on which the function should be executed
	 * @param function the function to be executed, i.e. the object providing the execute function
	 *                 which should be executed
	 * @param callback the callback which should be notified once the connectAndExecute function
	 *                 completed (after closing the device, or if an error occurs)
	 */
	public void connectAndExecute(final String address, final IExecuteCallback function, final IExecStatusCallback callback) {
		connectAndExecute(address, function, callback, true);
	}


	/**
	 * Connects to the given device, discovers the available services, then executes the provided
	 * function, before disconnecting and closing the device again. Once everything completed, the
	 * callbacks onSuccess function is called.
	 * Note: the disconnect and close will be delayed, so consequent calls (within the timeout) to
	 * connectAndExecute functions will keep the connection alive until the last call expires
	 *
	 * @param address          the MAC address of the device on which the function should be executed
	 * @param function         the function to be executed, i.e. the object providing the execute function
	 *                         which should be executed
	 * @param callback         the callback which should be notified once the connectAndExecute function
	 *                         completed (after closing the device, or if an error occurs)
	 * @param readSessionNonce whether to read the session nonce after discovery
	 */
	public void connectAndExecute(final String address, final IExecuteCallback function, final IExecStatusCallback callback, final boolean readSessionNonce) {

		final boolean resumeDelayedDisconnect[] = new boolean[]{clearDelayedDisconnect()};
		final boolean executeSuccess[] = new boolean[]{false};
		final boolean executeFailed[] = new boolean[]{false};

		// TODO: if function.execute was successful ignore the connect errors. Need a class variable for that.

		final IExecStatusCallback execStatusCallback = new IExecStatusCallback() {

			public void onExecuteSuccess(boolean disconnect) {
				if (disconnect && resumeDelayedDisconnect[0]) {
					delayedDisconnect(null);
				}
//				handleConnectRetrySuccess();
				executeSuccess[0] = true;
			}

			@Override
			public void onSuccess() {
				callback.onSuccess();
				onExecuteSuccess(true);
			}

			@Override
			public void onSuccess(byte[] result) {
				callback.onSuccess(result);
				onExecuteSuccess(true);
			}

			@Override
			public void onSuccess(boolean value) {
				callback.onSuccess(value);
				onExecuteSuccess(true);
			}

			@Override
			public void onSuccess(int value) {
				callback.onSuccess(value);
				onExecuteSuccess(true);
			}

			@Override
			public void onSuccess(long value) {
				callback.onSuccess(value);
				onExecuteSuccess(true);
			}

			@Override
			public void onSuccess(float value) {
				callback.onSuccess(value);
				onExecuteSuccess(true);
			}

			@Override
			public void onError(final int error) {
				if (resumeDelayedDisconnect[0]) {
					delayedDisconnect(null);
				}
				if (error == BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND) {
					executeFailed[0] = true;
					callback.onError(error);
				} else {
					if (!retry(error)) {
						executeFailed[0] = true;
						callback.onError(error);
					} else {
						connectAndExecute(address, function, callback, readSessionNonce);
					}
				}
			}
		};

		if (checkConnection(address)) {
			function.execute(execStatusCallback);
		} else {
			resumeDelayedDisconnect[0] = true;
			connectAndDiscover(address, new IDiscoveryCallback() {
				@Override
				public void onDiscovery(String serviceUuid, String characteristicUuid) { /* don't care */ }

				@Override
				public void onSuccess() {
					handleConnectRetrySuccess();
					// call execute function
					function.execute(execStatusCallback);
				}

				@Override
				public void onError(final int error) {
					// todo: do we need to disconnect and close here?
					// Clear cache, because ??
					disconnectAndClose(true, new IStatusCallback() {

						private void done() {
							if (!executeSuccess[0] && !executeFailed[0]) { // Callback was already called!
								if (!retry(error)) {
									callback.onError(error);
								} else {
									connectAndExecute(address, function, callback, readSessionNonce);
								}
							}
						}

						@Override
						public void onSuccess() {
							done();
						}

						@Override
						public void onError(int e) {
							done();
						}
					});
				}
			}, readSessionNonce);
		}
	}

	/**
	 * Check if we are currently connected to a device
	 *
	 * @param callback callback to be notified with an error if we are not connected. provide
	 *                 null if no notification is necessary, in which case the return value
	 *                 should be enough.
	 * @return true if device is connected, false otherwise
	 */
	public boolean isConnected(IBaseCallback callback) {
//		if (checkConnectionState(BleDeviceConnectionState.connected, callback)) {
		if (checkConnectionState(BleDeviceConnectionState.connected, null) &&
				_bleBase.isDeviceConnected(_targetAddress)) {
			return true;
		} else {
			if (callback != null) {
				callback.onError(BleErrors.ERROR_NOT_CONNECTED);
			}
			return false;
		}
//		}
//		return false;
	}

	/**
	 * Check if ble is disconnected: ready to be used to connect or to start scanning.
	 * This means it will also return false when it currently is connecting, connected, or disconnecting.
	 *
	 * @param callback callback to be notified with an error if we are not disconnected. provide
	 *                 null if no notification is necessary, in which case the return value
	 *                 should be enough.
	 * @return true when disconnect, false when connecting, connected, or disconnecting
	 */
	public boolean isDisconnected(IBaseCallback callback) {
		switch (_connectionState) {
			case connecting:
			case connected:
			case disconnecting: {
				if (callback != null) {
					callback.onError(BleErrors.ERROR_WRONG_STATE);
				}
				return false;
			}
			default:
				return true;
		}
	}

	/**
	 * Check if ble is initialized: ready to be used to connect or to start scanning.
	 * This means it will also return false when it currently is connecting, connected, disconnecting or scanning.
	 *
	 * @param callback callback to be notified with an error if we are not disconnected. provide
	 *                 null if no notification is necessary, in which case the return value
	 *                 should be enough.
	 * @return true when initialized, false when uninitialized, connecting, connected, disconnecting, or scanning
	 */
	public boolean isInitialized(IBaseCallback callback) {
		if (checkConnectionState(BleDeviceConnectionState.initialized, null)) {
			return true;
		} else {
			if (callback != null) {
				callback.onError(BleErrors.ERROR_NOT_INITIALIZED);
			}
			return false;
		}
	}

	/**
	 * Helper function to check if we are already / still connected, and if a delayed disconnect is
	 * active, restart the delay.
	 *
	 * @param address
	 * @return true if we are connected, false otherwise
	 */
	public synchronized boolean checkConnection(String address) {
		if (isConnected(null) && _targetAddress.equals(address)) {
			if (_delayedDisconnect != null) {
//				delayedDisconnect(null);
				clearDelayedDisconnect();
			}
			return true;
		}
		return false;
	}

	public boolean isSetupMode() {
		return hasCharacteristic(BluenetConfig.CHAR_SETUP_SESSION_NONCE_UUID, null);
	}

	////////////////////////
	// Crownstone service //
	////////////////////////

	/**
	 * Function to write the given control message to the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param controlMsg  the control message to be written to the device
	 * @param callback    the callback which will be informed about success or failure
	 */
	public void writeControl(ControlMsg controlMsg, final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Write control: ", controlMsg.toString());
			if (hasControlCharacteristic(callback)) {
				_bleBase.sendCommand(_targetAddress, controlMsg, callback);
			}
		}
	}

	/**
	 * Function to write the given control message to the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 * <p>
	 *
	 * @param address     the MAC address of the device to which the switch value should be written
	 * @param controlMsg  the control message to be written to the device
	 * @param callback    the callback which will be informed about success or failure
	 */
	public void writeControl(final String address, final ControlMsg controlMsg, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Write control msg...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeControl(controlMsg, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}


	///////////////////
	// Power service //
	///////////////////

	/**
	 * Function to write the given switch value to the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param value    the switch value to be written to the device (0-100)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeSwitch(final int value, final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Set switch to %d", value);
			if (hasControlCharacteristic(callback)) {
				_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_SWITCH, 1, new byte[]{(byte) value}), callback);
			}
		}
	}

	/**
	 * Function to write the given switch value to the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 * <p>
	 *
	 * @param address  the MAC address of the device to which the switch value should be written
	 * @param value    the switch value to be written (0-100)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeSwitch(final String address, final int value, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Set switch to %d", value);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeSwitch(value, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}



	/**
	 * Function to read the current PWM value from the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPwm(final IIntegerCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Reading current PWM value ...");
			if (hasStateCharacteristics(null)) {
				_bleExtState.getSwitchState(_targetAddress, new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						callback.onSuccess(BleUtils.clearBit(result, 7));
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			} else if (hasCharacteristic(BluenetConfig.CHAR_PWM_UUID, callback)) {
				_bleBase.readPWM(_targetAddress, callback);
			}
		}
	}

	/**
	 * Function to read the current PWM value from the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device from which the PWM value should be read
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPwm(final String address, final IIntegerCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading current PWM value ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readPwm(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Function to write the given PWM value to the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param value    the PWM value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writePwm(final int value, final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Set PWM to %d", value);
			if (hasControlCharacteristic(null)) {
				getLogger().LOGd(TAG, "use control characteristic");
				_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_PWM, 1, new byte[]{(byte) value}), callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_PWM_UUID, callback)) {
				_bleBase.writePWM(_targetAddress, value, callback);
			}
		}
	}

	/**
	 * Function to write the given PWM value to the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 * <p>
	 *
	 * @param address  the MAC address of the device to which the PWM value should be written
	 * @param value    the PWM value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writePwm(final String address, final int value, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Set PWM to %d", value);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writePwm(value, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Function to read the current relay value from the device.
	 * callback returns true if relay is on, false otherwise
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readRelay(final IBooleanCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Reading current Relay value ...");
			if (hasStateCharacteristics(null)) {
				_bleExtState.getSwitchState(_targetAddress, new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						callback.onSuccess(BleUtils.isBitSet(result, 7));
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			} else if (hasCharacteristic(BluenetConfig.CHAR_RELAY_UUID, callback)) {
				_bleBase.readRelay(_targetAddress, callback);
			}
		}
	}

	/**
	 * Function to read the current relay value from the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 * callback returns true if relay is on, false otherwise
	 *
	 * @param address  the MAC address of the device from which the Relay value should be read
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readRelay(final String address, final IBooleanCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading current Relay value ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readRelay(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Function to write the given Relay value to the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param relayOn  true if the relay should be switched on, false otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeRelay(final boolean relayOn, final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Set Relay to %b", relayOn);
			if (hasControlCharacteristic(null)) {
				getLogger().LOGd(TAG, "use control characteristic");
				int value = relayOn ? BluenetConfig.RELAY_ON : BluenetConfig.RELAY_OFF;
				_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_RELAY, 1, new byte[]{(byte) value}), callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_RELAY_UUID, callback)) {
				_bleBase.writeRelay(_targetAddress, relayOn, callback);
			}
		}
	}

	/**
	 * Function to write the given Relay value to the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 * <p>
	 *
	 * @param address  the MAC address of the device to which the Relay value should be written
	 * @param relayOn  true if the relay should be switched on, false otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeRelay(final String address, final boolean relayOn, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Set Relay to %b", relayOn);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeRelay(relayOn, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Toggle pwm between ON (pwm = BluenetConfig.PWM_ON) and OFF (pwm = BluenetConfig.PWM_OFF).
	 * Reads first the current PWM value from the device, then switches the PWM value accordingly.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback callback which will be informed about success or failure
	 */
	public void togglePwm(final IBooleanCallback callback) {
		readPwm(new IIntegerCallback() {
			@Override
			public void onSuccess(int result) {
				if (result > 0) {
					pwmOff(new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess(false);
						}

						@Override
						public void onError(int error) {
							callback.onError(error);
						}
					});
				} else {
					pwmOn(new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess(true);
						}

						@Override
						public void onError(int error) {
							callback.onError(error);
						}
					});
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Toggle pwm between ON (pwm = BluenetConfig.PWM_ON) and OFF (pwm = BluenetConfig.PWM_OFF).
	 * Reads first the current PWM value from the device, then switches the PWM value accordingly.
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback callback which will be informed about success or failure.
	 *                 In case of success, the value is true when the relay was switched on, false for off.
	 */
	public void togglePwm(final String address, final IBooleanCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Toggle power ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						togglePwm(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Helper function to set pwm ON (sets pwm value to BluenetConfig.PWM_ON)
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void pwmOn(IStatusCallback callback) {
		writePwm(BluenetConfig.PWM_ON, callback);
	}

	/**
	 * Helper function to set pwm ON (sets pwm value to BluenetConfig.PWM_ON)
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void pwmOn(String address, final IStatusCallback callback) {
		writePwm(address, BluenetConfig.PWM_ON, callback);
	}

	/**
	 * Helper function to set power OFF (sets pwm value to BluenetConfig.PWM_OFF)
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void pwmOff(IStatusCallback callback) {
		writePwm(BluenetConfig.PWM_OFF, callback);
	}

	/**
	 * Helper function to set power OFF (sets pwm value to BluenetConfig.PWM_OFF)
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void pwmOff(String address, final IStatusCallback callback) {
		writePwm(address, BluenetConfig.PWM_OFF, callback);
	}

	/**
	 * Toggle relay between ON (BluenetConfig.RELAY_ON) and OFF (BluenetConfig.RELAY_OFF).
	 * Reads first the current relay value from the device, then switches the relay value accordingly.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback callback which will be informed about success or failure.
	 *                 In case of success, the value is true when the relay was switched on, false for off.
	 */
	public void toggleRelay(final IBooleanCallback callback) {
		readRelay(new IBooleanCallback() {
			@Override
			public void onSuccess(boolean result) {
				if (result) {
					relayOff(new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess(false);
						}

						@Override
						public void onError(int error) {
							callback.onError(error);
						}
					});
				} else {
					relayOn(new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess(true);
						}

						@Override
						public void onError(int error) {
							callback.onError(error);
						}
					});
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Toggle relay between ON (BluenetConfig.RELAY_ON) and OFF (BluenetConfig.RELAY_OFF).
	 * Reads first the current relay value from the device, then switches the relay value accordingly.
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback callback which will be informed about success or failure.
	 *                 In case of success, the value is true when the relay was switched on, false for off.
	 */
	public void toggleRelay(final String address, final IBooleanCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Toggle relay ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						toggleRelay(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Helper function to set relay ON (sets relay value to BluenetConfig.RELAY_ON)
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void relayOn(IStatusCallback callback) {
		writeRelay(true, callback);
	}

	/**
	 * Helper function to set relay ON (sets pwm value to BluenetConfig.RELAY_ON)
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void relayOn(String address, final IStatusCallback callback) {
		writeRelay(address, true, callback);
	}

	/**
	 * Helper function to set relay OFF (sets pwm value to BluenetConfig.RELAY_OFF)
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void relayOff(IStatusCallback callback) {
		writeRelay(false, callback);
	}

	/**
	 * Helper function to set relay OFF (sets pwm value to BluenetConfig.RELAY_OFF)
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void relayOff(String address, final IStatusCallback callback) {
		writeRelay(address, false, callback);
	}

	/**
	 * Function to read the current consumption value from the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPowerConsumption(final IIntegerCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Reading power consumption value ...");
			if (hasStateCharacteristics(null)) {
				_bleExtState.getPowerUsage(_targetAddress, callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_POWER_CONSUMPTION_UUID, callback)) {
				_bleBase.readPowerConsumption(_targetAddress, callback);
			}
		}
	}

	/**
	 * Function to read the current consumption value from the device.
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPowerConsumption(final String address, final IIntegerCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading power consumption value ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readPowerConsumption(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Function to read the current curve from the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPowerSamples(final IPowerSamplesCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_POWER_SAMPLES_UUID, callback)) {
			getLogger().LOGd(TAG, "Reading PowerSamples value ...");
			_bleBase.readPowerSamples(_targetAddress, callback);
		}
	}

	/**
	 * Function to read the current curve from the device.
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPowerSamples(final String address, final IPowerSamplesCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading PowerSamples value ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readPowerSamples(new IPowerSamplesCallback() {
							@Override
							public void onData(PowerSamples result) {
								callback.onData(result);
								execCallback.onExecuteSuccess(true);
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback() {
					@Override
					public void onSuccess() { /* don't care */ }

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			}
		});
	}

	public void subscribePowerSamples(final IPowerSamplesCallback callback) {
		if (!_subscriberIds.containsKey(BluenetConfig.CHAR_POWER_SAMPLES_UUID)) {
			if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_POWER_SAMPLES_UUID, callback)) {
				getLogger().LOGd(TAG, "Subscribing to PowerSamples ...");
				_bleBase.subscribePowerSamples(_targetAddress, new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						_subscriberIds.put(BluenetConfig.CHAR_POWER_SAMPLES_UUID, result);
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				}, callback);
			}
		} else {
			getLogger().LOGd(TAG, "already subscribed");
		}
	}

	public void unsubscribePowerSamples(IStatusCallback callback) {
		if (isConnected(null) && _subscriberIds.containsKey(BluenetConfig.CHAR_POWER_SAMPLES_UUID)) {
			getLogger().LOGd(TAG, "Unsubscribing from PowerSamples ...");
			_bleBase.unsubscribePowerSamples(_targetAddress,
					_subscriberIds.get(BluenetConfig.CHAR_POWER_SAMPLES_UUID),
					callback);
		} else {
			getLogger().LOGd(TAG, "not subscribed or connected");
		}
	}

	/////////////////////
	// General service //
	/////////////////////

	/**
	 * Function to write the given reset value to the device. This will reset the device, and
	 * the behaviour after the reset depends on the given value. see @BluenetTypes for possible
	 * values
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param value    the value to be written to the device
	 * @param callback the callback which will be informed about success or failure
	 */
	private void writeReset(int value, IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Set Reset to %d", value);
			if (hasControlCharacteristic(null)) {
				getLogger().LOGd(TAG, "use control characteristic");
				_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_RESET, 1, new byte[]{(byte) value}), callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_RESET_UUID, callback)) {
				_bleBase.writeReset(_targetAddress, value, callback);
			}
		}
	}

	/**
	 * Function to write the given reset value to the device. This will reset the device, and
	 * the behaviour after the reset depends on the given value. see @BluenetTypes for possible
	 * values
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param value    the value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	private void writeReset(final String address, final int value, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Set Reset to %d", value);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeReset(value, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}


	/**
	 * Function to factory reset the device. This will erase all settings on the device, and
	 * boots it in setup mode. Disconnects and clears cache on success.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeFactoryReset(final IStatusCallback callback) {
		if (isConnected(callback)) {
			int value = BluenetConfig.FACTORY_RESET_CODE;
			getLogger().LOGd(TAG, "Write factory reset with %d", value);
//			if (hasControlCharacteristic(callback, true)) {
//				getLogger().LOGd(TAG, "use control characteristic");
			_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_FACTORY_RESET, 4, BleUtils.intToByteArray(value)), new IStatusCallback() {
				@Override
				public void onSuccess() {
					// Clear cache, as we know that the services will change.
					disconnectAndClose(true, new IStatusCallback() {
						@Override
						public void onSuccess() {
							callback.onSuccess();
						}

						@Override
						public void onError(int error) {
							callback.onSuccess();
						}
					});
				}

				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			});
//			}
		}
	}

	/**
	 * Function to factory reset the device. This will erase all settings on the device, and
	 * boots it in setup mode.
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeFactoryReset(final String address, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeFactoryReset(new IStatusCallback() {
							@Override
							public void onSuccess() {
								callback.onSuccess();
								// give false as parameter because the setup already
								// disconnects as part of the setup process, so trying to
								// disconnect again would cause unnecessary errors
								execCallback.onExecuteSuccess(false);
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback() {
					@Override
					public void onSuccess() { /* don't care */ }

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			}
		});
	}


	/**
	 * Function to make the device disconnect you.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeDisconnectCommand(final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Write disconnect command");
			if (hasControlCharacteristic(callback)) {
				getLogger().LOGd(TAG, "use control characteristic");
				_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_DISCONNECT, 0, new byte[0]), new IStatusCallback() {
					@Override
					public void onSuccess() {
						disconnectAndClose(false, new IStatusCallback() {
							@Override
							public void onSuccess() {
								callback.onSuccess();
							}

							@Override
							public void onError(int error) {
								callback.onSuccess();
							}
						});
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			}
		}
	}


	/**
	 * Function to reset / reboot the device
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetDevice(IStatusCallback callback) {
		writeReset(BluenetConfig.RESET_DEFAULT, callback);
	}

	/**
	 * Function to reset / reboot the device
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetDevice(String address, final IStatusCallback callback) {
		writeReset(address, BluenetConfig.RESET_DEFAULT, callback);
	}

	/**
	 * Function to reset / reboot the device to the bootloader, so that a new device firmware
	 * can be uploaded. Disconnects and clears cache on success.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetToBootloader(final IStatusCallback callback) {
		getLogger().LOGi(TAG, "Reset to bootloader");

		final IStatusCallback resetCallback = new IStatusCallback() {
			@Override
			public void onSuccess() {
				// Clear cache, as we know that the services will change.
				disconnectAndClose(true, new IStatusCallback() {
					@Override
					public void onSuccess() {
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						callback.onSuccess();
					}
				});
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		};

		refreshServices(true, new IStatusCallback() {
			@Override
			public void onSuccess() {
				if (hasCharacteristic(BluenetConfig.DFU_CONTROL_UUID, null)) {
					getLogger().LOGd(TAG, "Already in bootloader!");
					callback.onSuccess(); // Don't disconnect
//					resetCallback.onSuccess();
					return;
				}
				// First try to use the general service
				if (hasCharacteristic(BluenetConfig.CHAR_RESET_UUID, null)) {
					_bleBase.writeReset(_targetAddress, BluenetConfig.RESET_DFU, resetCallback);
				}
				// Then try to use the setup reset characteristic
				else if (isSetupMode() && hasCharacteristic(BluenetConfig.CHAR_SETUP_GOTO_DFU_UUID, null)) {
					_bleBase.writeReset(_targetAddress, BluenetConfig.RESET_DFU, resetCallback);
				}
				// Else try to use the control characteristic
				else if (hasControlCharacteristic(null, true)) {
					_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_GOTO_DFU), resetCallback);
				}
				else {
					callback.onError(BleErrors.ERROR_SERVICE_NOT_FOUND);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Function to reset / reboot the device to the bootloader, so that a new device firmware
	 * can be uploaded
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetToBootloader(final String address, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGi(TAG, "Reset to bootloader " + address);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						resetToBootloader(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}


	/**
	 * Reset the device when in dfu mode (bootloader). Must be already connected!
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetBootloader(final IStatusCallback callback) {
		getLogger().LOGi(TAG, "Reset bootloader");

		// Refresh services in case the cache is wrong.
		// Don't read session nonce: bootloader doesn't have that.
		refreshServices(false, new IStatusCallback() {

			// When done: clear cache, as the services will probably change.
			private void done() {
				disconnectAndClose(true, new IStatusCallback() {
					@Override
					public void onSuccess() {
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						callback.onSuccess();
					}
				});
			}

			@Override
			public void onSuccess() {
				if (!hasCharacteristic(BluenetConfig.DFU_CONTROL_UUID, callback)) {
					return;
				}

				_bleBase.subscribe(_targetAddress, BluenetConfig.DFU_SERVICE_UUID, BluenetConfig.DFU_CONTROL_UUID,
						new IIntegerCallback() {
							@Override
							public void onSuccess(int result) {
								BleLog.getInstance().LOGi(TAG, "Subscribed to dfu control: " + result);
								byte[] val = new byte[1];
								val[0] = 0x06;
								_bleBase.write(_targetAddress, BluenetConfig.DFU_SERVICE_UUID, BluenetConfig.DFU_CONTROL_UUID, val, BleBaseEncryption.ACCESS_LEVEL_ENCRYPTION_DISABLED, new IStatusCallback() {
									@Override
									public void onSuccess() {
										done();
									}

									@Override
									public void onError(int error) {
										// Treat as if it was a success..
										done();
									}
								});
							}

							@Override
							public void onError(int error) {
								BleLog.getInstance().LOGi(TAG, "error: " + error);
								callback.onError(error);
							}
						},
						new IDataCallback() {
							@Override
							public void onData(JSONObject json) {
								BleLog.getInstance().LOGd(TAG, "onData: " + json);
							}

							@Override
							public void onError(int error) {
								BleLog.getInstance().LOGd(TAG, "onError datacallback: " + error);
							}
						});
			}

			@Override
			public void onError(int error) {
				BleLog.getInstance().LOGi(TAG, "error: " + error);
				callback.onError(error);
			}
		});
	}

	/**
	 * Reset the device when in dfu mode (bootloader)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void resetBootloader(final String address, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGi(TAG, "Reset bootloader " + address);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						resetBootloader(execCallback);
					}
				}, new SimpleExecStatusCallback(callback), false); // Don't read session nonce: bootloader doesn't have that
			}
		});
	}




	private void recoverReadWrite(final IStatusCallback callback) {
		getLogger().LOGd(TAG, "Check if device has recovery characteristic");
		if (!hasCharacteristic(BluenetConfig.CHAR_RECOVERY_UUID, null)) {
			callback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
			return;
		}
		getLogger().LOGd(TAG, "Write recovery characteristic");
		byte[] code = BleUtils.intToByteArray(BluenetConfig.RECOVERY_CODE);
		_bleBase.write(_targetAddress, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_RECOVERY_UUID, code, BleBaseEncryption.ACCESS_LEVEL_ENCRYPTION_DISABLED, new IStatusCallback() {
			@Override
			public void onSuccess() {
				// We have to delay the read a bit until the result is written to the characteristic
				// TODO: use notifications instead of a read
				getHandler().postDelayed(new Runnable() {
					@Override
					public void run() {
						getLogger().LOGd(TAG, "Read recovery characteristic");
						_bleBase.read(_targetAddress, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_RECOVERY_UUID, false, new IDataCallback() {
							@Override
							public void onData(JSONObject json) {

								// Verify that the crownstone accepted the recover command
								byte[] data = BleCore.getValue(json);
								getLogger().LOGd(TAG, "Read recover data: " + BleUtils.bytesToString(data));
								//						if (data == null || data.length != 1) {
								if (data == null || data.length < 1) {
									callback.onError(BleErrors.ERROR_RETURN_VALUE_PARSING);
									return;
								}
								switch (BleUtils.toUint8(data[0])) {
									case 1: {
										// Success!
										// Clear cache, as we know that the services will change.
										disconnectAndClose(true, new IStatusCallback() {
											@Override
											public void onSuccess() {
												callback.onSuccess();
											}

											@Override
											public void onError(int error) {
												callback.onSuccess();
											}
										});
										break;
									}
									case 2: {
										callback.onError(BleErrors.ERROR_RECOVER_MODE_DISABLED);
										break;
									}
									default: {
										callback.onError(BleErrors.ERROR_NOT_IN_RECOVERY_MODE);
									}
								}
							}

							@Override
							public void onError(int error) {
								callback.onError(error);
							}
						});
					}
				}, 20); // Should be less than FACTORY_PROCESS_TIMEOUT, only need to wait for a very short time.
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}


	private void recoverStep(final String address, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "recover / factory reset device");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						recoverReadWrite(new IStatusCallback() {
							@Override
							public void onSuccess() {
								callback.onSuccess();
								// give false as parameter because the recover already
								// disconnects as part of the recover process, so trying to
								// disconnect again would cause unnecessary errors
								execCallback.onExecuteSuccess(false);
							}

							@Override
							public void onError(int error) {
								switch (error) {
									case BleErrors.ERROR_RETURN_VALUE_PARSING:
									case BleErrors.ERROR_RECOVER_MODE_DISABLED:
									case BleErrors.ERROR_NOT_IN_RECOVERY_MODE: {
										getLogger().LOGw(TAG, "recover mode disabled or not in recovery mode");
										callback.onError(error);
										execCallback.onSuccess();
										break;
									}
									default: {
										// Do not auto retry
										callback.onError(error);
										execCallback.onSuccess();
//										execCallback.onError(error);
									}
								}
							}
						});
					}
				}, new SimpleExecStatusCallback() {
					@Override
					public void onSuccess() { /* don't care */ }

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				}, false);
//				}
			}
		});
	}

	public void recover(final String address, final IStatusCallback callback) {
		// Perform step 1
		recoverStep(address, new IStatusCallback() {
			@Override
			public void onSuccess() {
				// Perform step 2
				recoverStep(address, callback);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Function to read the current temperature value from the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTemperature(IIntegerCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Reading Temperature value ...");
			if (hasStateCharacteristics(null)) {
				_bleExtState.getTemperature(_targetAddress, callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_TEMPERATURE_UUID, callback)) {
				_bleBase.readTemperature(_targetAddress, callback);
			}
		}
	}

	/**
	 * Function to read the current temperature value from the device.
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTemperature(final String address, final IIntegerCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading Temperature value ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readTemperature(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Function to write the given mesh message to the device. the mesh message will be
	 * forwarded by the device into the mesh network
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param value    the message to be sent to the mesh (through the device)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeMeshMessage(MeshControlMsg value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_MESH_CONTROL_UUID, callback)) {
			getLogger().LOGd(TAG, "Set MeshMessage to %s", value.toString());
			_bleBase.writeMeshMessage(_targetAddress, value, callback);
		}
	}

	/**
	 * Function to write the given mesh message to the device. the mesh message will be
	 * forwarded by the device into the mesh network
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param value    the message to be sent to the mesh (through the device)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeMeshMessage(final String address, final MeshControlMsg value, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Set MeshMessage to %s", value.toString());
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeMeshMessage(value, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}


	//////////////////////////
	// Localization service //
	//////////////////////////

	/**
	 * Function to read the list of tracked devices from the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTrackedDevices(IByteArrayCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_TRACKED_DEVICES_UUID, callback)) {
			getLogger().LOGd(TAG, "Reading TrackedDevices value ...");
			_bleBase.readTrackedDevices(getTargetAddress(), callback);
		}
	}

	/**
	 * Function to read the list of tracked devices from the device.
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTrackedDevices(final String address, final IByteArrayCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading TrackedDevices value ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readTrackedDevices(new IByteArrayCallback() {
							@Override
							public void onSuccess(byte[] result) {
								callback.onSuccess(result);
								execCallback.onExecuteSuccess(true);
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback() {
					@Override
					public void onSuccess() { /* don't care */ }

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			}
		});
	}

	/**
	 * Function to add a new device to be tracked
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param value    the new device to be tracked
	 * @param callback the callback which will be informed about success or failure
	 */
	public void addTrackedDevice(TrackedDeviceMsg value, IStatusCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_TRACK_CONTROL_UUID, callback)) {
			getLogger().LOGd(TAG, "Set TrackedDevice to %s", value.toString());
			_bleBase.addTrackedDevice(getTargetAddress(), value, callback);
		}
	}

	/**
	 * Function to add a new device to be tracked
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param value    the new device to be tracked
	 * @param callback the callback which will be informed about success or failure
	 */
	public void addTrackedDevice(final String address, final TrackedDeviceMsg value, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Set TrackedDevice to %s", value.toString());
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						addTrackedDevice(value, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Function to get the list of scanned BLE devices from the device. Need to call writeScanDevices
	 * first to start and to stop the scan. Consider using @scanForDevices instead
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void listScannedDevices(IByteArrayCallback callback) {
		if (isConnected(callback) && hasCharacteristic(BluenetConfig.CHAR_SCANNED_DEVICES_UUID, callback)) {
			getLogger().LOGd(TAG, "List scanned devices ...");
			_bleBase.listScannedDevices(getTargetAddress(), callback);
		}
	}

	/**
	 * Function to get the list of scanned BLE devices from the device. Need to call writeScanDevices
	 * first to start and to stop the scan. Consider using @scanForDevices instead
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param address  the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void listScannedDevices(final String address, final IByteArrayCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "List scanned devices ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						listScannedDevices(new IByteArrayCallback() {
							@Override
							public void onSuccess(byte[] result) {
								callback.onSuccess(result);
								execCallback.onExecuteSuccess(true);
							}

							@Override
							public void onError(int error) {
								execCallback.onError(error);
							}
						});
					}
				}, new SimpleExecStatusCallback() {
					@Override
					public void onSuccess() { /* don't care */ }

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
			}
		});
	}

	/**
	 * Function to start / stop a scan for BLE devices. After starting a scan, it will run indefinite
	 * until this function is called again to stop it.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param value    true to start scanning for devices, false to stop the scan
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeScanDevices(boolean value, IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "Scan Devices: %b", value);
			if (hasControlCharacteristic(null)) {
				getLogger().LOGd(TAG, "use control characteristic");
				int scan = (value ? 1 : 0);
				_bleBase.sendCommand(getTargetAddress(), new ControlMsg(BluenetConfig.CMD_SCAN_DEVICES, 1, new byte[]{(byte) scan}), callback);
			} else if (hasCharacteristic(BluenetConfig.CHAR_SCAN_CONTROL_UUID, callback)) {
				_bleBase.scanDevices(getTargetAddress(), value, callback);
			}
		}
	}

	/**
	 * Function to start / stop a scan for BLE devices. After starting a scan, it will run indefinite
	 * until this function is called again to stop it.
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param value    true to start scanning for devices, false to stop the scan
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeScanDevices(final String address, final boolean value, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Scan Devices: %b", value);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeScanDevices(value, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	/**
	 * Function to start a scan for devices, stop it again after scanDuration expired, then
	 * return the list of devices
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param scanDuration the duration (in ms) for which the device should scan for other BLE
	 *                     devices
	 * @param callback     the callback which will return the list of scanned devices
	 */
	public void scanForDevices(final int scanDuration, final IByteArrayCallback callback) {
		writeScanDevices(true, new IStatusCallback() {
			@Override
			public void onSuccess() {
				_handler.postDelayed(new Runnable() {
					@Override
					public void run() {
						writeScanDevices(false, new IStatusCallback() {
							@Override
							public void onSuccess() {
								// delay 500 ms, just wait, don't postdelay, since we are already
								// inside the handler, and hopefully 500ms delay won't cause havoc
								SystemClock.sleep(500);
								listScannedDevices(callback);
							}

							@Override
							public void onError(int error) {
								callback.onError(error);
							}
						});
					}
				}, scanDuration);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Function to start a scan for devices, stop it again after scanDuration expired, then
	 * return the list of devices
	 * <p>
	 * Connects to the device if not already connected, and/or delays the disconnect if necessary.
	 *
	 * @param scanDuration the duration (in ms) for which the device should scan for other BLE
	 *                     devices
	 * @param callback     the callback which will return the list of scanned devices
	 */
	public void scanForDevices(final String address, final int scanDuration, final IByteArrayCallback callback) {
		getLogger().LOGd(TAG, "Scan for devices ...");
		if (checkConnection(address)) {
			scanForDevices(scanDuration, callback);
		} else {
			// connect and execute ...
			connectAndExecute(address,
					new IExecuteCallback() {
						@Override
						public void execute(final IExecStatusCallback startExecCallback) {
							// ... start scanning for devices
							writeScanDevices(true, new IStatusCallback() {
								@Override
								public void onSuccess() {
									// if successfully started, post the stop scan with scanDuration delay
									_handler.postDelayed(new Runnable() {
										@Override
										public void run() {
											// once scanDuration delay expired, connect and execute ...
											connectAndExecute(address,
													new IExecuteCallback() {
														@Override
														public void execute(final IExecStatusCallback stopExecCallback) {
															// ... stop scanning for devices
															writeScanDevices(false, new IStatusCallback() {
																@Override
																public void onSuccess() {
																	// if successfully stopped, get the list of scanned devices ...

																	// delay 500 ms to give time for list to be written to characteristic
																	// just wait, don't postdelay, since we are already
																	// inside the handler, and hopefully 500ms delay won't cause havoc
																	SystemClock.sleep(500);
																	// get the list ...
																	listScannedDevices(new IByteArrayCallback() {
																		@Override
																		public void onSuccess(byte[] result) {
																			callback.onSuccess(result);
																			// ... and disconnect again once we have it
																			stopExecCallback.onExecuteSuccess(true);
																		}

																		@Override
																		public void onError(int error) {
//																	callback.onError(error);
																			// also disconnect if an error occurs
																			stopExecCallback.onError(error);
																		}
																	});
																}

																@Override
																public void onError(int error) {
//															callback.onError(error);
																	// disconnect if an error occurs
																	stopExecCallback.onError(error);
																}
															});
														}
													}, new SimpleExecStatusCallback() {
														@Override
														public void onSuccess() { /* don't care */ }

														@Override
														public void onError(int error) {
															callback.onError(error);
														}
													}
											);

										}
									}, scanDuration);
									// after posting, disconnect again
									startExecCallback.onExecuteSuccess(true);
								}

								@Override
								public void onError(int error) {
//								callback.onError(error);
									// disconnect if an error occurs
									startExecCallback.onError(error);
								}
							});
						}
					}, new SimpleExecStatusCallback() {
						@Override
						public void onSuccess() { /* don't care */ }

						@Override
						public void onError(int error) {
							callback.onError(error);
						}
					}
			);
		}
	}

//	public void readMeshData(final IMeshDataCallback callback) {
//		if (isConnected(callback) && hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, callback)) {
//			getLogger().LOGd(TAG, "subscribe to mesh data");
//			_bleBase.readMeshData(getTargetAddress(), callback);
//		}
//	}
//
//	public void subscribeMeshData(final IMeshDataCallback callback) {
//		if (isConnected(callback) && hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, callback)) {
//			getLogger().LOGd(TAG, "subscribe to mesh data");
//			_bleBase.subscribeMeshData(getTargetAddress(), callback);
//		}
//	}
//
//	public void unsubscribeMeshData(final IMeshDataCallback callback) {
//		if (isConnected(callback) && hasCharacteristic(BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID, callback)) {
//			getLogger().LOGd(TAG, "unsubscribe from mesh data");
//			_bleBase.unsubscribeMeshData(getTargetAddress(), callback);
//		}
//	}




	////////////////////////
	// Crownstone service //
	////////////////////////

	/**
	 * Function to write the given PWM value to the device.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeLed(final int led, boolean enable, final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "%s led %d", enable ? "Enable" : "Disable", led);
			_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_SET_LED, 2, new byte[]{(byte) led, (byte)(enable ? 1 : 0) }), callback);
		}
	}

	/**
	 * Function to write the given PWM value to the device. Connects to the device if not already
	 * connected, and/or delays the disconnect if necessary.
	 * <p>
	 * Note: needs to be already connected or an error is created! Use overloaded function
	 * with address otherwise
	 *
	 * @param address  the MAC address of the device to which the PWM value should be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeLed(final String address, final int led, final boolean enable, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "%s led %d", enable ? "Enable" : "Disable", led);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeLed(led, enable, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void writeKeepAliveState(final int action, final int switchState, final int timeout, final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "write keep alive with state %d and timeout %d", switchState, timeout);
			ByteBuffer bb = ByteBuffer.allocate(4);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			bb.put((byte)action);
			bb.put((byte)switchState);
			bb.putShort((short)timeout);
			_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_KEEP_ALIVE_STATE, 4, bb.array()), callback);
		}
	}

	public void writeKeepAliveState(final String address, final int action, final int switchState, final int timeout, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "write keep alive with state %d and timeout %d", switchState, timeout);
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeKeepAliveState(action, switchState, timeout, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void writeKeepAlive(final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "write keep alive");
			_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_KEEP_ALIVE), callback);
		}
	}

	public void writeKeepAlive(final String address, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "write keep alive");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeKeepAlive(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void writeNOP(final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "write NOP");
			_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_NOP), callback);
		}
	}

	public void writeNOP(final String address, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "write NOP");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeNOP(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void writeIncreaseTx(final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "writeIncreaseTx");
			_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_INCREASE_TX), callback);
		}
	}

	public void writeIncreaseTx(final String address, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "writeIncreaseTx");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeIncreaseTx(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void writeResetStateErrors(int stateErrorsBitmask, final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGi(TAG, "write reset state errors: " + Integer.toBinaryString(stateErrorsBitmask));
			ByteBuffer bb = ByteBuffer.allocate(4);
			bb.order(ByteOrder.LITTLE_ENDIAN);
			bb.putInt(stateErrorsBitmask);
			_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_RESET_STATE_ERRORS, 4, bb.array()), callback);
		}
	}

	public void writeResetStateErrors(final String address, final IStatusCallback callback) {
		writeResetStateErrors(address, 0xFFFFFFFF, callback);
	}

	public void writeResetStateErrors(final String address, final int stateErrorsBitmask, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "write reset state errors");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeResetStateErrors(stateErrorsBitmask, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void writeSetTime(long timeStamp, final IStatusCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "writeSetTime: " + timeStamp);
			_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_SET_TIME, 4, BleUtils.uint32ToByteArray(timeStamp)), callback);
		}
	}

	public void writeSetTime(final String address, final long timeStamp, final IStatusCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Writing time ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						writeSetTime(timeStamp, execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}


	public void readFirmwareRevision(final IByteArrayCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "readFirmwareRevision");
			_bleBase.readFirmwareRevision(_targetAddress, callback);
		}
	}

	public void readFirmwareRevision(final String address, final IIntegerCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading firmware revision ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readFirmwareRevision(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void readHardwareRevision(final IByteArrayCallback callback) {
		if (isConnected(callback)) {
			getLogger().LOGd(TAG, "readHardwareRevision");
			_bleBase.readHardwareRevision(_targetAddress, callback);
		}
	}

	public void readHardwareRevision(final String address, final IIntegerCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading hardware revision ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readHardwareRevision(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void readBootloaderRevision(final IByteArrayCallback callback) {
		if (isConnected(callback)) {
			if (hasCharacteristic(BluenetConfig.DFU_CONTROL_UUID, callback)) {
				getLogger().LOGd(TAG, "readBootloaderRevision");
				_bleBase.readBootloaderRevision(_targetAddress, callback);
			}
		}
	}

	public void readBootloaderRevision(final String address, final IIntegerCallback callback) {
		getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGd(TAG, "Reading bootloader revision ...");
				connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						readBootloaderRevision(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

//	public void readTemperature(IIntegerCallback callback) {
//		if (isConnected(callback)) {
//			getLogger().LOGd(TAG, "Reading Temperature value ...");
//			if (hasStateCharacteristics(null)) {
//				_bleExtState.getTemperature(_targetAddress, callback);
//			} else if (hasCharacteristic(BluenetConfig.CHAR_TEMPERATURE_UUID, callback)) {
//				_bleBase.readTemperature(_targetAddress, callback);
//			}
//		}
//	}
//	public void readTemperature(final String address, final IIntegerCallback callback) {
//		getHandler().post(new Runnable() {
//			@Override
//			public void run() {
//				getLogger().LOGd(TAG, "Reading Temperature value ...");
//				connectAndExecute(address, new IExecuteCallback() {
//					@Override
//					public void execute(final IExecStatusCallback execCallback) {
//						readTemperature(execCallback);
//					}
//				}, new SimpleExecStatusCallback(callback));
//			}
//		});
//	}


}
