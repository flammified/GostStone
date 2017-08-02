package nl.dobots.bluenet.ble.base;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IConfigurationCallback;
import nl.dobots.bluenet.ble.base.structs.SetupEncryptionKey;
import nl.dobots.bluenet.ble.core.callbacks.IDataCallback;
import nl.dobots.bluenet.ble.base.callbacks.IDiscoveryCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.IPowerSamplesCallback;
import nl.dobots.bluenet.ble.core.callbacks.IScanCallback;
import nl.dobots.bluenet.ble.base.callbacks.IStateCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.core.callbacks.ISubscribeCallback;
import nl.dobots.bluenet.ble.base.callbacks.IWriteCallback;
import nl.dobots.bluenet.ble.base.structs.ControlMsg;
import nl.dobots.bluenet.ble.base.structs.ConfigurationMsg;
import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.base.structs.EncryptionKeys;
import nl.dobots.bluenet.ble.base.structs.EncryptionSessionData;
import nl.dobots.bluenet.ble.mesh.structs.MeshControlMsg;
import nl.dobots.bluenet.ble.base.structs.PowerSamples;
import nl.dobots.bluenet.ble.base.structs.StateMsg;
import nl.dobots.bluenet.ble.base.structs.TrackedDeviceMsg;
import nl.dobots.bluenet.ble.cfg.BleTypes;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.core.BleCore;
import nl.dobots.bluenet.ble.core.BleCoreTypes;
import nl.dobots.bluenet.ble.base.callbacks.IBooleanCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IBleDeviceCallback;
import nl.dobots.bluenet.ble.extended.structs.BleDevice;
import nl.dobots.bluenet.utils.BleUtils;

public class BleBase extends BleCore {

	private static final String TAG = BleBase.class.getCanonicalName();

	// handler used for delayed execution, e.g. a to get the configuration we need to write first
	// to the select configuration characteristic, then wait for a moment for the device to process
	// the request before reading from the get configuration characteristic
//	private Handler _handler = new Handler();
	private Handler _handler;

	private boolean _encryptionEnabled = false;
	private EncryptionKeys _encryptionKeys = null;
	private EncryptionSessionData _encryptionSessionData = null;
	private boolean _setupMode = false;
	private byte[] _setupEncryptionKey = null;

	private IWriteCallback _onWriteCallback = null;

	/** Hashmap of all subscribers, based on characeristic UUID */
	private HashMap<UUID, ArrayList<IDataCallback>> _subscribers = new HashMap<>();

	/**
	 * "global" subscribe callback will be given to all subscribe calls of the BleCore. The
	 * gatt notifications (or errors) will then be delegated based on the characteristic uuid to all
	 * subscribers of that uuid
	 */
	ISubscribeCallback notificationCallback = new ISubscribeCallback() {
		@Override
		public void onData(UUID uuidService, UUID uuidCharacteristic, JSONObject data) {
			for (IDataCallback callback : getSubscribers(uuidCharacteristic)) {
				callback.onData(data);
			}
		}

		@Override
		public void onError(UUID uuidService, UUID uuidCharacteristic, int error) {
			for (IDataCallback callback : getSubscribers(uuidCharacteristic)) {
				callback.onError(error);
			}
		}
	};

	public BleBase() {
		// create handler with its own thread
		HandlerThread handlerThread = new HandlerThread("BleBaseHandler");
		handlerThread.start();
		_handler = new Handler(handlerThread.getLooper());
	}

	public void setOnWriteCallback(IWriteCallback onWriteCallback) {
		_onWriteCallback = onWriteCallback;
	}

	IStatusCallback _silentStatusCallback = new IStatusCallback() {
		@Override
		public void onSuccess() {
			getLogger().LOGd(TAG, "onSuccess");
		}

		@Override
		public void onError(int error) {
			getLogger().LOGe(TAG, "onError %d", error);
		}
	};

	public boolean enableEncryption(boolean enable) {
		getLogger().LOGi(TAG, "enableEncryption: " + enable);
		_encryptionEnabled = enable;
		return true;
	}

	public boolean isEncryptionEnabled() {
		return _encryptionEnabled;
	}

	public void setEncryptionKeys(EncryptionKeys encryptionKeys) {
		getLogger().LOGd(TAG, "setEncryptionKeys: " + encryptionKeys.toString());
		_encryptionKeys = encryptionKeys;
	}

	public void setSetupEncryptionKey(byte[] key) {
		getLogger().LOGd(TAG, "setSetupEncryptionKey to " + BleUtils.bytesToString(key));
		_setupEncryptionKey = key;
	}

	public void setSetupEncryptionKey(String key) {
		getLogger().LOGd(TAG, "setSetupEncryptionKey to " + key);
		try {
			if (key != null) {
				_setupEncryptionKey = BleUtils.hexStringToBytes(key);
			}
		} catch (java.lang.NumberFormatException e) {
			e.printStackTrace();
		}
	}

	public void clearSetupEncryptionKey() {
		getLogger().LOGd(TAG, "clearSetupEncryptionKey");
		_setupEncryptionKey = null;
	}

	public void setEncryptionSessionData(EncryptionSessionData sessionData) {
		_encryptionSessionData = sessionData;
	}

	@Override
	public void connectDevice(String address, int timeout, IDataCallback callback) {
		clearSetupEncryptionKey(); // Make sure we start clean
		_subscribers.clear();
		super.connectDevice(address, timeout, callback);
	}

	@Override
	public boolean disconnectDevice(String address, IDataCallback callback) {
		_subscribers.clear();
		return super.disconnectDevice(address, callback);
	}

	@Override
	public boolean closeDevice(String address, boolean clearCache, IStatusCallback callback) {
		_subscribers.clear();
		return super.closeDevice(address, clearCache, callback);
	}

	@Override
	public boolean write(String address, String serviceUuid, String characteristicUuid, byte[] value, IStatusCallback callback) {
		return write(address, serviceUuid, characteristicUuid, value, BleBaseEncryption.ACCESS_LEVEL_HIGHEST_AVAILABLE, callback);
	}

	public boolean write(String address, String serviceUuid, String characteristicUuid, byte[] value, char accessLevel, IStatusCallback callback) {
		if (_onWriteCallback != null) {
			_onWriteCallback.onWrite();
		}
		if (_encryptionEnabled && accessLevel != BleBaseEncryption.ACCESS_LEVEL_ENCRYPTION_DISABLED) {
			EncryptionKeys encryptionKeys = _encryptionKeys;
			if (_setupMode && _setupEncryptionKey != null) {
				// TODO: this is a hackish solution
				getLogger().LOGi(TAG, "Use setup encryption key");
				encryptionKeys = new SetupEncryptionKey(_setupEncryptionKey);
			}
			if (encryptionKeys == null) {
				return false;
			}

			// Just use highest available key
			EncryptionKeys.KeyAccessLevelPair keyAccessLevelPair = encryptionKeys.getHighestKey();
			if (_encryptionSessionData == null || keyAccessLevelPair == null) {
				return false;
			}
			byte[] encryptedBytes = BleBaseEncryption.encryptCtr(value, _encryptionSessionData.sessionNonce, _encryptionSessionData.validationKey, keyAccessLevelPair.key, keyAccessLevelPair.accessLevel);
			if (encryptedBytes == null) {
				return false;
			}
			return super.write(address, serviceUuid, characteristicUuid, encryptedBytes, callback);
		}
		return super.write(address, serviceUuid, characteristicUuid, value, callback);
	}

	@Override
	public boolean read(String address, String serviceUuid, String characteristicUuid, IDataCallback callback) {
		return read(address, serviceUuid, characteristicUuid, true, callback);
	}

	public boolean read(String address, String serviceUuid, String characteristicUuid, boolean useEncryption, final IDataCallback callback) {
		if (_encryptionEnabled && useEncryption) {
			IDataCallback encryptedCallback = new IDataCallback() {
				@Override
				public void onData(JSONObject json) {
					byte[] encryptedBytes = getValue(json);

					EncryptionKeys encryptionKeys = _encryptionKeys;
					if (_setupMode && _setupEncryptionKey != null) {
						// TODO: this is a hackish solution
						getLogger().LOGi(TAG, "Use setup encryption key");
						encryptionKeys = new SetupEncryptionKey(_setupEncryptionKey);
					}
					if (_encryptionSessionData == null || encryptionKeys == null) {
						callback.onError(BleErrors.ENCRYPTION_ERROR);
						return;
					}
					byte[] decryptedBytes = BleBaseEncryption.decryptCtr(encryptedBytes, _encryptionSessionData.sessionNonce, _encryptionSessionData.validationKey, encryptionKeys);
					if (decryptedBytes == null) {
						callback.onError(BleErrors.ENCRYPTION_ERROR);
						return;
					}
					setValue(json, decryptedBytes);
					callback.onData(json);
				}
				@Override
				public void onError(int error) {
					callback.onError(error);
				}
			};
			return super.read(address, serviceUuid, characteristicUuid, encryptedCallback);
		}
		return super.read(address, serviceUuid, characteristicUuid, callback);
	}

	/**
	 * Start an endless scan, without defining any UUIDs to filter for. the scan will continue
	 * until stopEndlessScan is called. The function will convert a received device from JSON into
	 * a BleDevice object, the advertisement package will be parsed and included into the object.
	 *
	 * @param callback the callback to be notified if devices are detected
	 * @return true if the scan was started, false otherwise
	 */
	public void startEndlessScan(final IBleDeviceCallback callback) {
		startEndlessScan(new String[]{}, callback);
	}

	/**
	 * Start an endless scan, without defining any UUIDs to filter for. the scan will continue
	 * until stopEndlessScan is called. The function will parse the advertisement package
	 * and include it into the json object received from the android os.
	 *
	 * Additionally, a list of UUIDs can be specified to filter. As a result, only devices with
	 * the given UUIDs will be returned.
	 *
	 * @param callback the callback to be notified if devices are detected
	 * @param serviceUuids a list of UUIDs to filter for
	 * @return true if the scan was started, false otherwise
	 */
	public void startEndlessScan(String[] serviceUuids, final IBleDeviceCallback callback) {
		// wrap the status callback to do some pre-processing of the scan result data
		super.startEndlessScan(serviceUuids, new IScanCallback() {
			@Override
			public void onSuccess() {
				callback.onSuccess();
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}

			@Override
			public void onData(final JSONObject json) {
				byte[] advertisement = BleCore.getBytes(json, BleCoreTypes.PROPERTY_ADVERTISEMENT);

//				if (Build.VERSION.SDK_INT >= 21) {
//					ScanRecord scanRecord = ScanRecord.parseFromBytes(advertisement);
//				}

				parseAdvertisement(advertisement, 0xFF, new IByteArrayCallback() {
					// The callbacks can be called multiple times
					@Override
					public void onSuccess(byte[] result) {
						parseIBeaconData(json, result);
					}

					@Override
					public void onError(int error) {
						getLogger().LOGv(TAG, "json: " + json.toString());
					}
				});

				parseAdvertisement(advertisement, 0x16, new IByteArrayCallback() {
					// The callbacks can be called multiple times
					@Override
					public void onSuccess(byte[] result) {
						parseServiceData(json, result);
					}

					@Override
					public void onError(int error) {
						getLogger().LOGv(TAG, "json: " + json.toString());
					}
				});
				parseAdvertisement(advertisement, 0x06, new IByteArrayCallback() {
					// The callbacks can be called multiple times
					@Override
					public void onSuccess(byte[] result) {
						parseServiceClass(json, result);
					}

					@Override
					public void onError(int error) {
						getLogger().LOGv(TAG, "json: " + json.toString());
					}
				});

				BleDevice device;
				try {
					device = new BleDevice(json);
				} catch (JSONException e) {
					getLogger().LOGe(TAG, "Failed to parse json into device! Err: " + e.getMessage());
					getLogger().LOGd(TAG, "json: " + json.toString());
					return;
				}
				callback.onDeviceScanned(device);
			}
		});
	}

	private void parseAdvertisement(byte[] scanRecord, int search, IByteArrayCallback callback) {

		ByteBuffer bb = ByteBuffer.wrap(scanRecord);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		try {
			while (bb.hasRemaining()) {
				int length = BleUtils.toUint8(bb.get());
				if (length == 0) {
					// we have reached the end of the valid scan record data
					// the rest of the buffer should be filled with 0
					return;
				}

				int type = BleUtils.toUint8(bb.get());
				if (type == search) {
					byte[] result = new byte[length - 1];
					bb.get(result, 0, length - 1);
					callback.onSuccess(result);
				} else {
					// skip length elements
					bb.position(bb.position() + length - 1); // length also includes the type field, so only advance by length-1
				}
			}
		} catch (BufferUnderflowException e) {
//			getLogger().LOGe(TAG, "failed to parse advertisement, search: %d", search);
//			e.printStackTrace();
			callback.onError(BleErrors.ERROR_ADVERTISEMENT_PARSING);
		}
	}

	/**
	 * Helper function to parse manufacturing data of an advertisement/scan response packet. Use
	 * @parseAdvertisement first to retrieve the manufacturing data (type 0xFF), and pass it to
	 * this function together with the json object. it will populate the json object with the data
	 * parsed from the manufacData array.
	 *
	 * @param json the json object in which the data should be included
	 * @param manufacData the byte array containing the manufacturing data
	 */
	@Deprecated
	private void parseDoBotsData(JSONObject json, byte[] manufacData) {
		ByteBuffer bb = ByteBuffer.wrap(manufacData);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		int companyId = bb.getShort();

		if (companyId == BluenetConfig.DOBOTS_COMPANY_ID) {
			try {
				int deviceType = bb.get();
				switch (deviceType) {
					case BluenetConfig.DEVICE_CROWNSTONE: {
						BleCore.addProperty(json, BleTypes.PROPERTY_IS_CROWNSTONE_PLUG, true);
						break;
					}
					case BluenetConfig.DEVICE_GUIDESTONE: {
						BleCore.addProperty(json, BleTypes.PROPERTY_IS_GUIDESTONE, true);
						break;
					}
					case BluenetConfig.DEVICE_FRIDGE: {
						BleCore.addProperty(json, BleTypes.PROPERTY_IS_FRIDGE, true);
						break;
					}
				}
			} catch (Exception e) {
				BleCore.addProperty(json, BleTypes.PROPERTY_IS_CROWNSTONE_PLUG, true);
				getLogger().LOGd(TAG, "old advertisement package: %s", json);
			}
		}
	}

	/**
	 * Helper function to parse the crownstone service data of an advertisement/scan response packet.
	 * Use @parseAdvertisement first to retrieve the service data (type 0x16), and pass it to
	 * this function together with the json object. it will populate the json object with the data
	 * parsed from the serviceData array.
	 *
	 * @param json the json object in which the data should be included
	 * @param serviceData the byte array containing the service data
	 */
	private void parseServiceData(JSONObject json, byte[] serviceData) {
		ByteBuffer bb = ByteBuffer.wrap(serviceData);
		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bb.remaining() < 2) {
			return;
		}
		int serviceUUID = bb.getShort();

		if (serviceUUID == BluenetConfig.CROWNSTONE_PLUG_SERVICE_DATA_UUID) {
			BleCore.addProperty(json, BleTypes.PROPERTY_IS_CROWNSTONE_PLUG, true);
//			CrownstoneServiceData crownstoneServiceData = new CrownstoneServiceData(bb.array(), _encryptionEnabled, _encryptionKeys.getGuestKey());
			CrownstoneServiceData crownstoneServiceData = new CrownstoneServiceData();
			if (crownstoneServiceData.parseBytes(bb.array(), _encryptionEnabled, EncryptionKeys.getGuestKey(_encryptionKeys))) {
				BleCore.addProperty(json, BleTypes.PROPERTY_SERVICE_DATA, crownstoneServiceData);
			}
		}
		else if (serviceUUID == BluenetConfig.CROWNSTONE_BUILTIN_SERVICE_DATA_UUID) {
			BleCore.addProperty(json, BleTypes.PROPERTY_IS_CROWNSTONE_BUILTIN, true);
//			CrownstoneServiceData crownstoneServiceData = new CrownstoneServiceData(bb.array(), _encryptionEnabled, _encryptionKeys.getGuestKey());
			CrownstoneServiceData crownstoneServiceData = new CrownstoneServiceData();
			if (crownstoneServiceData.parseBytes(bb.array(), _encryptionEnabled, EncryptionKeys.getGuestKey(_encryptionKeys))) {
				BleCore.addProperty(json, BleTypes.PROPERTY_SERVICE_DATA, crownstoneServiceData);
			}
		}
		else if (serviceUUID == BluenetConfig.GUIDESTONE_SERVICE_DATA_UUID) {
			BleCore.addProperty(json, BleTypes.PROPERTY_IS_GUIDESTONE, true);
			// TODO: should probably be GuidestoneServiceData in the future.
			CrownstoneServiceData crownstoneServiceData = new CrownstoneServiceData();
			if (crownstoneServiceData.parseBytes(bb.array(), _encryptionEnabled, EncryptionKeys.getGuestKey(_encryptionKeys))) {
				BleCore.addProperty(json, BleTypes.PROPERTY_SERVICE_DATA, crownstoneServiceData);
			}
		}
	}

	/**
	 * Helper function to parse iBeacon data from a byte array into a JSON object
	 * @param scanResult the json object in which the data should be included
	 * @param manufacData the byte array containing the ibeacon data
	 */
	private void parseIBeaconData(JSONObject scanResult, byte[] manufacData) {

		ByteBuffer bb = ByteBuffer.wrap(manufacData);

		bb.order(ByteOrder.LITTLE_ENDIAN);

		if (bb.remaining() < 2) {
			return;
		}
		int companyId = BleUtils.toUint16(bb.getShort());
		if (companyId != BluenetConfig.APPLE_COMPANY_ID) {
			return;
		}

		// ibeacon data is in big endian format
		bb.order(ByteOrder.BIG_ENDIAN);
		// advertisement id is actually two separate bits, first bit is the iBeacon type (0x02),
		// the second is the iBeacon length (0x15), but they are fixed to these values, so we can
		// compare them together
		if (bb.remaining() < 2) {
			return;
		}
		int advertisementId = BleUtils.toUint16(bb.getShort());

		if (advertisementId == BluenetConfig.IBEACON_ADVERTISEMENT_ID && bb.remaining() >= 16+2+2+1) {
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_IS_IBEACON, true);
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_PROXIMITY_UUID, new UUID(bb.getLong(), bb.getLong()));
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_MAJOR, BleUtils.toUint16(bb.getShort()));
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_MINOR, BleUtils.toUint16(bb.getShort()));
			BleCore.addProperty(scanResult, BleTypes.PROPERTY_CALIBRATED_RSSI, bb.get());
		}
	}

	private void parseServiceClass(JSONObject json, byte[] serviceUuidBytes) {
		// Parse "Incomplete List of 128-bit Service Class UUIDs"
//		getLogger().LOGw(TAG, "128-bit service class uuid: " + BleUtils.bytesToString(serviceUuidBytes));
		// 128-bit service class uuid: [35, 209, 188, 234, 95, 120, 35, 21, 222, 239, 18, 18, 48, 21, 0, 0]
		ByteBuffer bb = ByteBuffer.wrap(serviceUuidBytes);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		if (bb.remaining() < 16) {
			return;
		}
		long leastSigBits= bb.getLong();
		long mostSigBits = bb.getLong();
		UUID serviceUuid = new UUID(mostSigBits, leastSigBits);
//		getLogger().LOGw(TAG, "UUID: " + serviceUuid.toString());
		UUID dfuServiceUuid = UUID.fromString(BluenetConfig.DFU_SERVICE_UUID);
//		getLogger().LOGw(TAG, "DFU UUID: " + dfuServiceUuid.toString());
//		getLogger().LOGw(TAG, "match: " + dfuServiceUuid.equals(serviceUuid));

		if (dfuServiceUuid.equals(serviceUuid)) {
			BleCore.addProperty(json, BleTypes.PROPERTY_IS_DFU_MODE, true);
		}
	}

	/**
	 * Discover the available services and characteristics of the device. Parse the received
	 * JSON object and call the callbacks onDiscovery function with service UUID and characteristic
	 * UUID for each discovered characteristic. Once the discovery completes, the onSuccess is
	 * called or the onError if an error occurs
	 *
	 * Note: if you get wrong services and characteristics returned, try to clear the cache by calling
	 * closeDevice with parameter clearCache set to true. this makes sure that next discover will really
	 * read the services and characteristics from the device and not the cache
	 * @param address the MAC address of the device
	 * @param forceDiscover, set to true to force a new discovery,
	 *						 if false and cached discovery found, return the lib cache (not same as previously mentioned cache)
	 * @param callback the callback used to report discovered services and characteristics
	 */
	public void discoverServices(String address, boolean forceDiscover, final IDiscoveryCallback callback) {
		_setupMode = false;

		super.discoverServices(address, forceDiscover, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				try {
					JSONArray services = json.getJSONArray(BleCoreTypes.PROPERTY_SERVICES_LIST);
					for (int i = 0; i < services.length(); i++) {
						JSONObject service = services.getJSONObject(i);
						String serviceUuid = service.getString(BleCoreTypes.PROPERTY_SERVICE_UUID);
						JSONArray characteristics = service.getJSONArray(BleCoreTypes.PROPERTY_CHARACTERISTICS_LIST);
						for (int j = 0; j < characteristics.length(); j++) {
							JSONObject charac = characteristics.getJSONObject(j);
							String characteristicUuid = charac.getString(BleCoreTypes.PROPERTY_CHARACTERISTIC_UUID);
							getLogger().LOGd(TAG, "found service %s with characteristic %s", serviceUuid, characteristicUuid);
							callback.onDiscovery(serviceUuid, characteristicUuid);
						}

						if (serviceUuid.equals(BluenetConfig.SETUP_SERVICE_UUID)) {
							getLogger().LOGd(TAG, "setupMode = true");
							_setupMode = true;
						}
					}
					callback.onSuccess();
				} catch (JSONException e) {
					getLogger().LOGe(TAG, "failed to parse discovery json");
					callback.onError(BleErrors.ERROR_JSON_PARSING);
					e.printStackTrace();
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Discover the available services and characteristics of the device. Parse the received
	 * JSON object and call the callbacks onDiscovery function with service UUID and characteristic
	 * UUID for each discovered characteristic. Once the discovery completes, the onSuccess is
	 * called or the onError if an error occurs
	 *
	 * Note: if you get wrong services and characteristics returned, try to clear the cache by calling
	 * closeDevice with parameter clearCache set to true. this makes sure that next discover will really
	 * read the services and characteristics from the device and not the cache
 	 * @param address the MAC address of the device
	 * @param callback the callback used to report discovered services and characteristics
	 */
	public void discoverServices(String address, final IDiscoveryCallback callback) {
		// by default, return cached discovery if present, otherwise start new discovery
		discoverServices(address, false, callback);
	}

	/**
	 * Helper function to return the list of subscribers for a given uuid.
	 * @param uuid uuid for which the list should be returned
	 * @return list of subscribers
	 */
	private ArrayList<IDataCallback> getSubscribers(UUID uuid) {
		ArrayList<IDataCallback> callbacks = _subscribers.get(uuid);
		if (callbacks == null) {
			callbacks = new ArrayList<>();
			_subscribers.put(uuid, callbacks);
		}
		return callbacks;
	}

	/**
	 * Subscribe to a characteristic. This can be called several times for different callbacks. a
	 * list of subscribers is kept with a subscriberId, so that different functions can subscribe
	 * to the same characteristic.
	 * the statusCallback will return in the onSuccess call the subscriber id, this id will be needed
	 * to unsubscribe afterwards.
	 *
	 * @param address the address of the device
	 * @param serviceUuid the uuid of the service containing the characteristic
	 * @param characteristicUuid the uuid of the characteristic which should be subscribed to
	 * @param statusCallback the callback which will be informed about success or failure.
	 *                       in case of success, the onSuccess function will return the subscriber
	 *                       id which is needed for unsubscribing afterwards
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	public void subscribe(String address, String serviceUuid, String characteristicUuid,
						  final IIntegerCallback statusCallback, final IDataCallback callback) {

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		final ArrayList<IDataCallback> subscribers = getSubscribers(uuid);

		if (subscribers.isEmpty()) {
			subscribers.add(callback);
			if (!super.subscribe(address, serviceUuid, characteristicUuid, new IStatusCallback() {
				@Override
				public void onError(int error) {
					statusCallback.onError(error);
				}

				@Override
				public void onSuccess() {
					statusCallback.onSuccess(subscribers.indexOf(callback));
				}
			}, notificationCallback)) {
				subscribers.remove(callback);
			}
		} else {
			subscribers.add(callback);

//			JSONObject json = new JSONObject();
//			setStatus(json, BleCoreTypes.CHARACTERISTIC_SUBSCRIBED);
//			callback.onData(json);
			statusCallback.onSuccess(subscribers.indexOf(callback));
		}

	}

	private class MultiPartNotificationCallback implements IDataCallback {

		IDataCallback callback;

		MultiPartNotificationCallback(IDataCallback callback, boolean decrypt) {
			this.callback = callback;
			_decrypt = decrypt;
		}

		ByteBuffer buffer = ByteBuffer.allocate(BluenetConfig.BLE_MAX_MULTIPART_NOTIFICATION_LENGTH);
		int messageNr = 0;

		long timeStart;
		boolean _decrypt = true;

		@Override
		public void onData(JSONObject json) {
			final byte[] notificationBytes = BleCore.getValue(json);
			int nr = BleUtils.toUint8(notificationBytes[0]);

			if (nr == 0) {
				timeStart = SystemClock.elapsedRealtime();
				if (messageNr != 0) {
					getLogger().LOGw(TAG, "notification reset!");
					buffer.clear();
				} else {
					// this is the first
				}
			} else if (messageNr > nr) {
				getLogger().LOGe(TAG, "fatal error");
				callback.onError(0);
				return;
			}
			messageNr = nr;

			buffer.put(notificationBytes, 1, notificationBytes.length - 1);

			if (messageNr == 0xFF) {
				getLogger().LOGd(TAG, "received last part");
				getLogger().LOGv(TAG, "duration: %d", SystemClock.elapsedRealtime() - timeStart);
				JSONObject combinedJson = new JSONObject();
				byte[] result = new byte[buffer.position()];
				buffer.rewind();
				buffer.get(result);

				byte[] decryptedBytes;
				if (_decrypt) {
					EncryptionKeys encryptionKeys = _encryptionKeys;
					if (_setupMode && _setupEncryptionKey != null) {
						// TODO: this is a hackish solution
						getLogger().LOGi(TAG, "Use setup encryption key");
						encryptionKeys = new SetupEncryptionKey(_setupEncryptionKey);
					}

					decryptedBytes = BleBaseEncryption.decryptCtr(result, _encryptionSessionData.sessionNonce, _encryptionSessionData.validationKey, encryptionKeys);
					if (decryptedBytes == null) {
						getLogger().LOGw(TAG, "Unable to decrypt");
						callback.onError(BleErrors.ENCRYPTION_ERROR);
						messageNr = 0;
						buffer.clear();
						return;
					}
					result = decryptedBytes;
				}

				BleCore.setValue(combinedJson, result);
				callback.onData(combinedJson);
				messageNr = 0;
				buffer.clear();
			} else {
				getLogger().LOGd(TAG, "received part %d", messageNr + 1);
			}
		}

		@Override
		public void onError(int error) {

		}
	}

	/**
	 * Subscribe to a characteristic. This can be called several times for different callbacks. a
	 * list of subscribers is kept with a subscriberId, so that different functions can subscribe
	 * to the same characteristic.
	 * the statusCallback will return in the onSuccess call the subscriber id, this id will be needed
	 * to unsubscribe afterwards.
	 *
	 * @param address the address of the device
	 * @param serviceUuid the uuid of the service containing the characteristic
	 * @param characteristicUuid the uuid of the characteristic which should be subscribed to
	 * @param statusCallback the callback which will be informed about success or failure.
	 *                       in case of success, the onSuccess function will return the subscriber
	 *                       id which is needed for unsubscribing afterwards
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	public void subscribeMultipart(String address, String serviceUuid, String characteristicUuid,
	                               final IIntegerCallback statusCallback, final IDataCallback callback) {
		subscribeMultipart(address, serviceUuid, characteristicUuid, _encryptionEnabled, statusCallback, callback);
	}

	public void subscribeMultipart(String address, String serviceUuid, String characteristicUuid, boolean decrypt,
						  final IIntegerCallback statusCallback, final IDataCallback callback) {

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		final ArrayList<IDataCallback> subscribers = getSubscribers(uuid);

		final MultiPartNotificationCallback notificationCB = new MultiPartNotificationCallback(callback, decrypt);

		if (subscribers.isEmpty()) {
			subscribers.add(notificationCB);
			if (!super.subscribe(address, serviceUuid, characteristicUuid, new IStatusCallback() {
				@Override
				public void onError(int error) {
					statusCallback.onError(error);
				}

				@Override
				public void onSuccess() {
					statusCallback.onSuccess(subscribers.indexOf(notificationCB));
				}
			}, notificationCallback)) {
				subscribers.remove(notificationCB);
			}
		} else {
			subscribers.add(notificationCB);

//			JSONObject json = new JSONObject();
//			setStatus(json, BleCoreTypes.CHARACTERISTIC_SUBSCRIBED);
//			callback.onData(json);
			statusCallback.onSuccess(subscribers.indexOf(callback));
		}

	}

	/**
	 * Unsubscribe from the characteristic. Requires the subscriberId which was returned
	 * in the statusCallback's onSuccess function during the subscribe call.
	 *
	 * @param address the address of the device
	 * @param serviceUuid the uuid of the service containing the characteristic
	 * @param characteristicUuid the uuid of the characteristic which should be subscribed to
	 * @param subscriberId id obtained from the subscribeState call
	 * @param statusCallback the callback which will be informed about success or failure.
	 */
	public void unsubscribe(String address, String serviceUuid, String characteristicUuid,
							int subscriberId, IStatusCallback statusCallback) {

		UUID uuid = BleUtils.stringToUuid(characteristicUuid);
		ArrayList<IDataCallback> subscribers = getSubscribers(uuid);

		if (subscribers.remove(subscriberId) != null) {
			if (subscribers.isEmpty()) {
				super.unsubscribe(address, serviceUuid, characteristicUuid, statusCallback);
			} else {

//				JSONObject json = new JSONObject();
//				setStatus(json, BleCoreTypes.CHARACTERISTIC_UNSUBSCRIBED);
//				callback.onData(json);

				statusCallback.onSuccess();
			}
		} else {
			statusCallback.onError(BleErrors.WRONG_CALLBACK);
//			callback.onError(BleErrors.WRONG_CALLBACK);
		}
	}

	// todo: better name than single shot?
	/**
	 * Subscribe to a characteristic, get the notifications, then unsubscribe again (silently).
	 * internal use only
	 * @param address the address of the device
	 * @param serviceUuid the uuid of the service containing the characteristic
	 * @param characteristicUuid the uuid of the characteristic which should be subscribed to
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	private void subscribeMultipartSingleShot(final String address, final String serviceUuid,
											  final String characteristicUuid,
											  boolean decrypt,
											  final IDataCallback callback) {

		final int[] subscribeId = {0};

		subscribeMultipart(address, serviceUuid, characteristicUuid, decrypt,
				new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						subscribeId[0] = result;
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				},
				new IDataCallback() {
					@Override
					public void onData(final JSONObject json) {
						// need to wait until unsubscribe is completed before returning the data
						// otherwise if a new read/write is started before the unsubscribe
						// completed the command will get lost
						unsubscribe(address, serviceUuid, characteristicUuid, subscribeId[0],
								new IStatusCallback() {
									@Override
									public void onSuccess() {
										getLogger().LOGd(TAG, "unsubscribed successfully");
										callback.onData(json);
									}

									@Override
									public void onError(int error) {
										getLogger().LOGw(TAG, "unsubscribe failed %d", error);
										callback.onData(json);
									}
								});
					}

					@Override
					public void onError(int error) {
						callback.onError(error);

						// do the unsubscribe silently, i.e. not inform the callback about
						// success or error
						unsubscribe(address, serviceUuid, characteristicUuid, subscribeId[0],
								new IStatusCallback() {
									@Override
									public void onSuccess() {
										getLogger().LOGd(TAG, "unsubscribed successfully");
									}

									@Override
									public void onError(int error) {
										getLogger().LOGw(TAG, "unsubscribe failed %d", error);
									}
								});
					}
				});
	}

	// todo: better name than single shot?
	private void subscribeSingleShot(final String address, final String serviceUuid, final String characteristicUuid,
									 final IDataCallback callback) {

		final int[] subscribeId = {0};

		subscribe(address, serviceUuid, characteristicUuid,
				new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						subscribeId[0] = result;
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				},
				new IDataCallback() {
					@Override
					public void onData(final JSONObject json) {
						// need to wait until unsubscribe is completed before returning the data
						// otherwise if a new read/write is started before the unsubscribe
						// completed the command will get lost
						unsubscribe(address, serviceUuid, characteristicUuid, subscribeId[0],
								new IStatusCallback() {
									@Override
									public void onSuccess() {
										getLogger().LOGd(TAG, "unsubscribed successfully");
										callback.onData(json);
									}

									@Override
									public void onError(int error) {
										getLogger().LOGw(TAG, "unsubscribe failed %d", error);
										callback.onData(json);
									}
								});
					}

					@Override
					public void onError(int error) {
						callback.onError(error);

						// do the unsubscribe silently, i.e. not inform the callback about
						// success or error
						unsubscribe(address, serviceUuid, characteristicUuid, subscribeId[0],
								new IStatusCallback() {
									@Override
									public void onSuccess() {
										getLogger().LOGd(TAG, "unsubscribed successfully");
									}

									@Override
									public void onError(int error) {
										getLogger().LOGw(TAG, "unsubscribe failed %d", error);
									}
								});
					}
				});
	}

	// Crownstone specific characteristic operations

	/**
	 * Read the temperature characteristic on the device and return the temperature as an integer
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readTemperature(String address, final IIntegerCallback callback) {
		getLogger().LOGd(TAG, "read Temperature at service %s and characteristic %s", BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_TEMPERATURE_UUID);
		read(address, BluenetConfig.GENERAL_SERVICE_UUID, BluenetConfig.CHAR_TEMPERATURE_UUID, new IDataCallback() {

			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "Failed to read temperature characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				getLogger().LOGd(TAG, "temperature: %d", bytes[0]);
				callback.onSuccess(bytes[0]);
			}
		});
	}

	/**
	 * Write the given value to the PWM characteristic on the device
	 * @param address the MAC address of the device
	 * @param value the pwm value to be written
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writePWM(String address, int value, final IStatusCallback callback) {
		getLogger().LOGd(TAG, "write %d at service %s and characteristic %s", value, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_PWM_UUID);
		write(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_PWM_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to pwm characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to pwm characteristic");
						callback.onError(error);
					}
				}
		);
	}

	/**
	 * Read the pwm characteristic on the device and return the current pwm value as an integer
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readPWM(String address, final IIntegerCallback callback) {

		getLogger().LOGd(TAG, "read pwm at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_PWM_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_PWM_UUID, new IDataCallback() {

			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "Failed to read pwm characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				int result = BleUtils.toUint8(bytes[0]);
				getLogger().LOGd(TAG, "pwm: %d", result);
				callback.onSuccess(result);
			}
		});
	}

	/**
	 * Write the given value to the Relay characteristic on the device
	 * @param address the MAC address of the device
	 * @param relayOn true if the relay should be switched on, false otherwise
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeRelay(String address, boolean relayOn, final IStatusCallback callback) {
		int value = relayOn ? BluenetConfig.RELAY_ON : BluenetConfig.RELAY_OFF;
		getLogger().LOGd(TAG, "write %d at service %s and characteristic %s", value, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_RELAY_UUID);
		write(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_RELAY_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to Relay characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to relay characteristic");
						callback.onError(error);
					}
				}
		);
	}

	/**
	 * Read the Relay characteristic on the device and return the current Relay value as an integer
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the read value on success, or an error otherwise
	 */
	public void readRelay(String address, final IBooleanCallback callback) {

		getLogger().LOGd(TAG, "read Relay at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_RELAY_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_RELAY_UUID, new IDataCallback() {

			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "Failed to read Relay characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				boolean result = BleUtils.toUint8(bytes[0]) > 0;
				getLogger().LOGd(TAG, "Relay: %b", result);
				callback.onSuccess(result);
			}
		});
	}

	/**
	 * Write to the device scan characteristic to start or stop a scan for BLE devices. After
	 * starting a scan, it will run indefinite until this function is called again to stop it.
	 * @param address the MAC address of the device
	 * @param scan true to start the scan, false to stop
	 * @param callback the callback which will be informed about success or failure
	 */
	public void scanDevices(String address, boolean scan, final IStatusCallback callback) {
		int value = scan ? 1 : 0;
		getLogger().LOGd(TAG, "writeScanDevices: write %d at service %s and characteristic %s", value, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_SCAN_CONTROL_UUID);
		write(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_SCAN_CONTROL_UUID, new byte[]{(byte) value},
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to writeScanDevices characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to writeScanDevices characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Read the device list characteristic to get the list of scanned BLE devices from the device.
	 * Need to call scanDevices first to start and to stop the scan.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the list on success, or an error otherwise
	 */
	public void listScannedDevices(String address, final IByteArrayCallback callback) {
		getLogger().LOGd(TAG, "read device list at service %s and characteristic %s", BluenetConfig.CHAR_SCANNED_DEVICES_UUID, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID);
		read(address, BluenetConfig.CHAR_SCANNED_DEVICES_UUID, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "Failed to read device list characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				getLogger().LOGd(TAG, "device list: %s", BleUtils.bytesToString(bytes));
				callback.onSuccess(bytes);
				// todo: add function to extended, with nice classes and list of objects, etc.
			}
		});
	}

	/**
	 * Read the current consumption characteristic to get the current consumption value from the device.
	 * Need to have called at least once sampleCurrent before a valid current consumption can be
	 * read.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void readPowerConsumption(String address, final IIntegerCallback callback) {
		getLogger().LOGd(TAG, "read current consumption at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_CONSUMPTION_UUID);
		read(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_CONSUMPTION_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "Failed to read current consumption characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				// todo: check if current consumption is only 1 byte
				int value = BleUtils.toUint8(bytes[0]);
				getLogger().LOGd(TAG, "current consumption: %d", value);
				callback.onSuccess(value);
			}
		});
	}

	/**
	 * Read the current curve characteristic to get the current curve from the device. Need to have
	 * called at least once sampleCurrent before a valid current curve can be read.
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the curve on success, or an error otherwise
	 */
	public void readPowerSamples(final String address, final IPowerSamplesCallback callback) {
		getLogger().LOGd(TAG, "read power samples at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID);

		subscribeMultipartSingleShot(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID, false,
				new IDataCallback() {
					@Override
					public void onData(JSONObject json) {
						final byte[] notificationBytes = BleCore.getValue(json);
						try {
							PowerSamples powerSamples = new PowerSamples(notificationBytes);
							callback.onData(powerSamples);
						} catch (BufferUnderflowException e) {
							callback.onError(BleErrors.ERROR_CHARACTERISTIC_READ_FAILED);
						}
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	public void subscribePowerSamples(final String address, final IIntegerCallback statusCallback, final IPowerSamplesCallback callback) {
		getLogger().LOGd(TAG, "subscribe to power samples at service %s and characteristic %s", BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID);

		subscribeMultipart(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID, false,
				statusCallback,
				new IDataCallback() {
					@Override
					public void onData(JSONObject json) {
						final byte[] notificationBytes = BleCore.getValue(json);
						try {
							PowerSamples powerSamples = new PowerSamples(notificationBytes);
							callback.onData(powerSamples);
						} catch (BufferUnderflowException e) {
							callback.onError(BleErrors.ERROR_CHARACTERISTIC_READ_FAILED);
						}
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				});
	}

	public void unsubscribePowerSamples(final String address, int subscriberId, final IStatusCallback statusCallback) {
		unsubscribe(address, BluenetConfig.POWER_SERVICE_UUID, BluenetConfig.CHAR_POWER_SAMPLES_UUID,
				subscriberId, statusCallback);
	}

	/**
	 * Wrapper function which first calls select configuration, and on success calls the get configuration
	 *
	 * @param address the address of the device
	 * @param configurationType the configuration type, see enum ConfigurationMsg Types in BluenetConfig.
	 * @param callback callback function to be called with the read configuration object
	 */
	public void getConfiguration(final String address, final int configurationType, final IConfigurationCallback callback) {

		final int[] subscriberId = new int[1];

		subscribeConfiguration(address,
				new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						subscriberId[0] = result;
						selectConfiguration(address, configurationType, new IStatusCallback() {
							@Override
							public void onSuccess() {
								// yippi, wait for configuration to come in on notification
							}

							@Override
							public void onError(int error) {
								// select failed, unsubscribe again
								unsubscribeConfiguration(address, subscriberId[0], _silentStatusCallback);
								callback.onError(error);
							}
						});
					}

					@Override
					public void onError(int error) {
						// subscribe failed
						callback.onError(error);
					}
				},
				new IConfigurationCallback() {
					@Override
					public void onSuccess(final ConfigurationMsg configuration) {
						// need to wait until unsubscribe is completed before returning the data
						// otherwise if a new read/write is started before the unsubscribe
						// completed the command will get lost
						unsubscribeConfiguration(address, subscriberId[0], new IStatusCallback() {
							@Override
							public void onSuccess() {
								callback.onSuccess(configuration);
							}

							@Override
							public void onError(int error) {
								callback.onError(error);
							}
						});
					}

					@Override
					public void onError(int error) {
						unsubscribeConfiguration(address, subscriberId[0], _silentStatusCallback);
						callback.onError(error);
					}
				}
		);
	}

	public void unsubscribeConfiguration(String address, int subscriberId, IStatusCallback callback) {
		if (_setupMode) {
			unsubscribe(address, BluenetConfig.SETUP_SERVICE_UUID, BluenetConfig.CHAR_SETUP_CONFIG_READ_UUID,
					subscriberId, callback);
		} else {
			unsubscribe(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONFIG_READ_UUID,
					subscriberId, callback);
		}
	}

	public void subscribeConfiguration(String address, final IIntegerCallback statusCallback, final IConfigurationCallback callback) {
		if (_setupMode) {
			subscribeConfiguration(address, BluenetConfig.SETUP_SERVICE_UUID,
					BluenetConfig.CHAR_SETUP_CONFIG_READ_UUID, statusCallback, callback);
		} else {
			subscribeConfiguration(address, BluenetConfig.CROWNSTONE_SERVICE_UUID,
					BluenetConfig.CHAR_CONFIG_READ_UUID, statusCallback, callback);
		}
	}

	/**
	 * Subscribe to the StateRead characteristic to receive gatt notifications. the statusCallback's
	 * onSuccess function will be triggered on success with the subscriber id. this id is needed
	 * for unsubscribing afterwards.
	 *
	 * @param address the address of the device
	 * @param statusCallback the callback which will be informed about success or failure.
	 *                       in case of success, the onSuccess function will return the subscriber
	 *                       id which is needed for unsubscribing afterwards
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	private void subscribeConfiguration(String address, String serviceUuid, String characteristicUuid,
										final IIntegerCallback statusCallback, final IConfigurationCallback callback) {

		subscribeMultipart(address, serviceUuid, characteristicUuid,
				statusCallback,
				new IDataCallback() {

					@Override
					public void onError(int error) {
						callback.onError(error);
					}

					@Override
					public void onData(JSONObject json) {
						final byte[] decryptedBytes = BleCore.getValue(json);

						getLogger().LOGv(TAG, BleUtils.bytesToString(decryptedBytes));

//						ConfigurationMsg configuration = new ConfigurationMsg(decryptedBytes);
						ConfigurationMsg configuration = new ConfigurationMsg();
						if (!configuration.fromArray(decryptedBytes)) {
							getLogger().LOGw(TAG, "failed to parse configuration: " + BleUtils.bytesToString(decryptedBytes));
							callback.onError(BleErrors.ERROR_MSG_PARSING);
						}
						else {
							getLogger().LOGd(TAG, "read configuration: %s", configuration.toString());
							callback.onSuccess(configuration);
						}
					}
				}
		);
	}

	/**
	 * Write the configuration value to the set configuration characteristic. the configuration is a
	 * ConfigurationMsg object which starts with a byte for the configuration type, then 1 byte
	 * reserved for byte alignment, 2 bytes for the length of the payload data, and the payload
	 * data
	 * @param address the address of the device
	 * @param configuration configuration to be written to the set configuration characteristic
	 * @param callback callback function to be called on success or error
	 */
	private void writeConfiguration(final String address, final ConfigurationMsg configuration,
									final boolean verify, String serviceUuid, String characteristicUuid,
									final IStatusCallback callback) {
		byte[] bytes = configuration.toArray();
		getLogger().LOGd(TAG, "configuration: write %s at service %s and characteristic %s", BleUtils.bytesToString(bytes), serviceUuid, characteristicUuid);
		write(address, serviceUuid, characteristicUuid, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to configuration characteristic");
						// we need to give the crownstone some time to handle the config write,
						// because it needs to access persistent memory in order to store the new
						// config value
						_handler.postDelayed(new Runnable() {
							@Override
							public void run() {
								// if verify is set, get the configuration value from the crownstone
								// and verify that the value we read is the value we set
								if (verify) {
									getConfiguration(address, configuration.getType(), new IConfigurationCallback() {
										@Override
										public void onSuccess(ConfigurationMsg readConfig) {
											if (Arrays.equals(readConfig.getPayload(), configuration.getPayload())) {
												callback.onSuccess();
											} else {
												getLogger().LOGe(TAG, "write: %s, read: %s", BleUtils.bytesToString(configuration.getPayload()), BleUtils.bytesToString(readConfig.getPayload()));
												callback.onError(BleErrors.ERROR_VALIDATION_FAILED);
											}
										}

										@Override
										public void onError(int error) {
											callback.onError(error);
										}
									});
								} else {
									callback.onSuccess();
								}
							}
						}, 1000);
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to configuration characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Write the configuration value to the set configuration characteristic. the configuration is a
	 * ConfigurationMsg object which starts with a byte for the configuration type, then 1 byte
	 * reserved for byte alignment, 2 bytes for the length of the payload data, and the payload
	 * data
	 * Note: this function selects the appropriate characteristic/service automatically depending
	 * on whether the Crownstone is in Setup or Normal operation mode
	 * @param address the address of the device
	 * @param configuration configuration to be written to the set configuration characteristic
	 * @param verify
	 * @param callback callback function to be called on success or error
	 */
	public void writeConfiguration(String address, ConfigurationMsg configuration,
								   boolean verify, final IStatusCallback callback) {
		if (_setupMode) {
			writeConfiguration(address, configuration, verify, BluenetConfig.SETUP_SERVICE_UUID,
					BluenetConfig.CHAR_SETUP_CONFIG_CONTROL_UUID, callback);
		} else {
			writeConfiguration(address, configuration, verify, BluenetConfig.CROWNSTONE_SERVICE_UUID,
					BluenetConfig.CHAR_CONFIG_CONTROL_UUID, callback);
		}
	}

	/**
	 * Write to the configuration control characteristic to select a configuration that we want to
	 * read afterwards. Need to delay the call to readConfiguration to give the device some time
	 * to process the request.
	 * @param address the address of the device
	 * @param configurationType the configuration type, see enum ConfigurationMsg Types in BluenetConfig.
	 * @param callback the callback which will be informed about success or failure
	 */
	private void selectConfiguration(String address, int configurationType, String serviceUuid,
									 String characteristicUuid, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(configurationType, BluenetConfig.READ_VALUE, 0, new byte[]{});
		byte[] bytes = configuration.toArray();
		getLogger().LOGd(TAG, "select configuration: write %d at service %s and characteristic %s", configurationType, serviceUuid, characteristicUuid);
		write(address, serviceUuid, characteristicUuid, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to select configuration characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to select configuration characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Write to the configuration control characteristic to select a configuration that we want to
	 * read afterwards. Need to delay the call to readConfiguration to give the device some time
	 * to process the request.
	 * Note: this function selects the appropriate characteristic/service automatically depending
	 * on whether the Crownstone is in Setup or Normal operation mode
	 * @param address the address of the device
	 * @param configurationType the configuration type, see enum ConfigurationMsg Types in BluenetConfig.
	 * @param callback the callback which will be informed about success or failure
	 */
	private void selectConfiguration(String address, int configurationType, final IStatusCallback callback) {
		if (_setupMode) {
			selectConfiguration(address, configurationType, BluenetConfig.SETUP_SERVICE_UUID,
					BluenetConfig.CHAR_SETUP_CONFIG_CONTROL_UUID, callback);
		} else {
			selectConfiguration(address, configurationType, BluenetConfig.CROWNSTONE_SERVICE_UUID,
					BluenetConfig.CHAR_CONFIG_CONTROL_UUID, callback);

		}
	}

	/**
	 * Read the get configuration characteristic to get the configuration value which was
	 * previously selected. Need to call selectConfiguration first to select a configuration value
	 * to be read.
	 * Note: Need to delay the call to readConfiguration to give the device some time
	 * to process the request.
	 * Consider using @getConfiguration instead
	 * @param address the address of the device
	 * @param callback callback function to be called with the read configuration object
	 */
	private void readConfiguration(String address, String serviceUuid, String characteristicUuid,
								   final IConfigurationCallback callback) {
		getLogger().LOGd(TAG, "read configuration at service %s and characteristic %s", serviceUuid, characteristicUuid);
		read(address, serviceUuid, characteristicUuid, new IDataCallback() {
			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "Failed to read configuration characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				ConfigurationMsg configuration = new ConfigurationMsg();
				if (!configuration.fromArray(bytes)) {
					getLogger().LOGw(TAG, "failed to parse configuration: " + BleUtils.bytesToString(bytes));
					callback.onError(BleErrors.ERROR_MSG_PARSING);
				}
				else {
					getLogger().LOGd(TAG, "read configuration: %s", configuration.toString());
					callback.onSuccess(configuration);
				}
			}
		});
	}

	/**
	 * Read the get configuration characteristic to get the configuration value which was
	 * previously selected. Need to call selectConfiguration first to select a configuration value
	 * to be read.
	 * Note: Need to delay the call to readConfiguration to give the device some time
	 * to process the request.
	 * Consider using @getConfiguration instead
	 * Note: this function selects the appropriate characteristic/service automatically depending
	 * on whether the Crownstone is in Setup or Normal operation mode
	 * @param address the address of the device
	 * @param callback callback function to be called with the read configuration object
	 */
	private void readConfiguration(String address, final IConfigurationCallback callback) {
		if (_setupMode) {
			readConfiguration(address, BluenetConfig.SETUP_SERVICE_UUID,
					BluenetConfig.CHAR_SETUP_CONFIG_READ_UUID, callback);
		} else {
			readConfiguration(address, BluenetConfig.CROWNSTONE_SERVICE_UUID,
					BluenetConfig.CHAR_CONFIG_READ_UUID, callback);
		}
	}

	/**
	 * Enables state variable notifications on the device. every time the value changes, the device
	 * will write a StateMsg with opCode notification to the ConfigRead characteristic
	 *
	 * @param address the address of the device
	 * @param stateType the state variable for which notifications should be enabled, see @BleStateTypes
	 * @param callback the callback which will be informed about success or failure of the enable call
	 */
	public void enableStateNotification(final String address, final int stateType, final IStatusCallback callback) {
		StateMsg state = new StateMsg(stateType, BluenetConfig.NOTIFY_VALUE, 1, new byte[]{1});
		byte[] bytes = state.toArray();
		getLogger().LOGd(TAG, "notify state: write %d at service %s and characteristic %s", stateType, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_CONTROL_UUID);
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_CONTROL_UUID, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to select state characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to select state characteristic");
						callback.onError(error);
					}
				});
	}

//	private void subscribeState(String address, final IStateCallback callback) {
//		subscribeState(address, callback, null);
//	}

	/**
	 * Subscribe to the StateRead characteristic to receive gatt notifications. the statusCallback's
	 * onSuccess function will be triggered on success with the subscriber id. this id is needed
	 * for unsubscribing afterwards.
	 *
	 * @param address the address of the device
	 * @param statusCallback the callback which will be informed about success or failure.
	 *                       in case of success, the onSuccess function will return the subscriber
	 *                       id which is needed for unsubscribing afterwards
	 * @param callback the callback which will be triggered every time a gatt notification arrives
	 */
	public void subscribeState(String address, final IIntegerCallback statusCallback, final IStateCallback callback) {

		subscribeMultipart(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID,
				statusCallback,
				new IDataCallback() {

					@Override
					public void onError(int error) {
						callback.onError(error);
					}

					@Override
					public void onData(JSONObject json) {
						final byte[] decryptedBytes = BleCore.getValue(json);

						getLogger().LOGd(TAG, BleUtils.bytesToString(decryptedBytes));
						StateMsg state = new StateMsg();
						if (!state.fromArray(decryptedBytes)) {
							getLogger().LOGw(TAG, "failed parsing state notification: ", BleUtils.bytesToString(decryptedBytes));
							callback.onError(BleErrors.ERROR_MSG_PARSING);
						}
						else {
							getLogger().LOGd(TAG, "received state notification: %s", state.toString());
							callback.onSuccess(state);
						}
					}
				}
		);
	}

	/**
	 * Unsubscribe from the StateRead characteristic. Requires the subscriberId which was returned
	 * in the statusCallback's onSuccess function during the subscribeState call.
	 *
	 * @param address the address of the device
	 * @param subscriberId id obtained from the subscribeState call
	 */
	public void unsubscribeState(String address, int subscriberId) {
		unsubscribe(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID,
				subscriberId, new IStatusCallback() {
					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "unsubscribe state success");
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "unsubscribe state error: %d", error);
					}
				});
	}

	/**
	 * Unsubscribe from the StateRead characteristic. Requires the subscriberId which was returned
	 * in the statusCallback's onSuccess function during the subscribeState call.
	 *
	 * @param address the address of the device
	 * @param subscriberId id obtained from the subscribeState call
	 * @param callback the callback which will be informed about success or failure.
	 */
	public void unsubscribeState(String address, int subscriberId, final IStatusCallback callback) {
		unsubscribe(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID,
				subscriberId, callback);
	}

	/**
	 * Helper function to get state notifications. it will first subscribe to the StateRead characteristic
	 * if that is successful, it will enable notifications for the state variable specified. After that,
	 * every time the state variable is updated on the device, the callback's onSuccess function is called.
	 *
	 * @param address the address of the device
	 * @param type the state variable for which notifications should be enabled, see @BleStateTypes
	 * @param statusCallback the callback which will be informed about success or failure of the enable/subscribe
	 *                       calls
	 * @param callback the callback which will be triggered for every notification / state variable update
	 */
	public void getStateNotifications(final String address, final int type, final IIntegerCallback statusCallback,
									  final IStateCallback callback) {

		final int[] subscriberId = new int[1];

		subscribeState(address, new IIntegerCallback() {
					@Override
					public void onSuccess(int result) {
						subscriberId[0] = result;
						enableStateNotification(address, type, new IStatusCallback() {

							@Override
							public void onSuccess() {
								getLogger().LOGd(TAG, "notify state success");
								statusCallback.onSuccess(subscriberId[0]);
							}

							@Override
							public void onError(int error) {
								unsubscribeState(address, subscriberId[0]);
							}
						});
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "notify state error: %d", error);
						callback.onError(error);
					}
				},
				new IStateCallback() {
					@Override
					public void onSuccess(StateMsg state) {
						if (state.getOpCode() == BluenetConfig.NOTIFY_VALUE) {
							callback.onSuccess(state);
						}
					}

					@Override
					public void onError(int error) {
						callback.onError(error);
					}
				}
		);
	}


	/**
	 * Wrapper function which first calls select state, and on success calls the get state
	 *
	 * @param address the address of the device
	 * @param stateType the state type, see enum StateMsg Types in BluenetConfig.
	 * @param callback callback function to be called with the read state object
	 */
	public void getState(final String address, final int stateType, final IStateCallback callback) {

		final int[] subscriberId = new int[1];

		subscribeState(address,
			new IIntegerCallback() {
				@Override
				public void onSuccess(int result) {
					subscriberId[0] = result;
					selectState(address, stateType, new IStatusCallback() {
						@Override
						public void onSuccess() {
							// yippi, wait for state to come in on notification
						}

						@Override
						public void onError(int error) {
							// select failed, unsubscribe again
							unsubscribeState(address, subscriberId[0]);
							callback.onError(error);
//							_handler.postDelayed(new Runnable() {
//								@Override
//								public void run() {
//									unsubscribeState(address, subscriberId[0]);
//								}
//							}, 200);
						}
					});
				}

				@Override
				public void onError(int error) {
					// subscribe failed
					callback.onError(error);
				}
			},
			new IStateCallback() {
				@Override
				public void onSuccess(final StateMsg state) {
					// need to wait until unsubscribe is completed before returning the data
					// otherwise if a new read/write is started before the unsubscribe
					// completed the command will get lost
					unsubscribeState(address, subscriberId[0], new IStatusCallback() {
						@Override
						public void onSuccess() {
							getLogger().LOGd(TAG, "unsubscribe state success");
							callback.onSuccess(state);
						}

						@Override
						public void onError(int error) {
							getLogger().LOGe(TAG, "unsubscribe state error: %d", error);
							callback.onSuccess(state);
						}
					});
//					_handler.postDelayed(new Runnable() {
//						@Override
//						public void run() {
//							unsubscribeState(address, subscriberId[0]);
//						}
//					}, 200);
				}

				@Override
				public void onError(int error) {
					unsubscribeState(address, subscriberId[0]);
					callback.onError(error);
//					_handler.postDelayed(new Runnable() {
//						@Override
//						public void run() {
//							unsubscribeState(address, subscriberId[0]);
//						}
//					}, 200);
				}
			}
		);

//		selectState(address, stateType, new IStatusCallback() {
//			@Override
//			public void onSuccess() {
//				// todo: do we need a timeout here?
////				_handler.postDelayed(new Runnable() {
////					@Override
////					public void run() {
//						readState(address, callback);
////					}
////				}, 50);
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write to the state control characteristic to select a state variable that we want to
	 * read afterwards. Need to delay the call to readState to give the device some time
	 * to process the request.
	 * @param address the address of the device
	 * @param stateType the state type, see enum StateMsg Types in BluenetConfig.
	 * @param callback the callback which will be informed about success or failure
	 */
	public void selectState(String address, int stateType, final IStatusCallback callback) {
		StateMsg state = new StateMsg(stateType, BluenetConfig.READ_VALUE, 0, new byte[]{});
		byte[] bytes = state.toArray();
		getLogger().LOGd(TAG, "select state: write %d at service %s and characteristic %s", stateType, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_CONTROL_UUID);
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_CONTROL_UUID, bytes,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to select state characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to select state characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Read the get state characteristic to get the state value which was
	 * previously requested. Need to call selectState first to select a state variable
	 * to be read.
	 * Note: Need to delay the call to readState to give the device some time
	 * to process the request.
	 * Consider using @getState instead
	 * @param address the address of the device
	 * @param callback callback function to be called with the read state object
	 */
	public void readState(String address, final IStateCallback callback) {
		getLogger().LOGd(TAG, "read state at service %s and characteristic %s", BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID);
		read(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_STATE_READ_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "Failed to read state characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				StateMsg state = new StateMsg();
				if (!state.fromArray(bytes)) {
					getLogger().LOGw(TAG, "failed parsing state notification: ", BleUtils.bytesToString(bytes));
					callback.onError(BleErrors.ERROR_MSG_PARSING);
				}
				else {
					getLogger().LOGd(TAG, "read state: %s", state.toString());
					callback.onSuccess(state);
				}
			}
		});
	}

//	private void sendCommand(String address, ControlMsg command, String serviceUuid, String characteristicUuid,
//							 final IStatusCallback callback) {
//		sendCommand(address, command, serviceUuid, characteristicUuid, BleBaseEncryption.ACCESS_LEVEL_HIGHEST_AVAILABLE, callback);
//	}

	private void sendCommand(String address, ControlMsg command, String serviceUuid, String characteristicUuid, char accessLevel,
							 final IStatusCallback callback) {
		byte[] bytes = command.toArray();
		getLogger().LOGd(TAG, "control command: write %s at service %s and characteristic %s", BleUtils.bytesToString(bytes), BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONTROL_UUID);
		write(address, serviceUuid, characteristicUuid, bytes, accessLevel,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to control characteristic");
						// delay probably not needed anymore since we decoupled characteristic writes
						// from interrupt in firmware
						callback.onSuccess();
//						// we need to give the crownstone some time to handle the control command
//						_handler.postDelayed(new Runnable() {
//							@Override
//							public void run() {
//								callback.onSuccess();
//							}
//						}, 200);
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to control characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Send the give command to the control characteristic. the device then executes the command defined
	 * by the command parameter
	 * Note: this function selects the appropriate characteristic/service automatically depending
	 * on whether the Crownstone is in Setup or Normal operation mode
	 * @param address the address of the device
	 * @param command command to be executed on the device
	 * @param callback callback function to be called on success or error
	 */
	public void sendCommand(String address, ControlMsg command, final IStatusCallback callback) {
		sendCommand(address, command, BleBaseEncryption.ACCESS_LEVEL_HIGHEST_AVAILABLE, callback);
	}

	/**
	 * Send the give command to the control characteristic. the device then executes the command defined
	 * by the command parameter
	 * Note: this function selects the appropriate characteristic/service automatically depending
	 * on whether the Crownstone is in Setup or Normal operation mode
	 * @param address the address of the device
	 * @param command command to be executed on the device
	 * @param accessLevel access level to use (see BleBaseEncryption)
	 * @param callback callback function to be called on success or error
	 */
	public void sendCommand(String address, ControlMsg command, char accessLevel, final IStatusCallback callback) {
		if (_setupMode) {
			sendCommand(address, command, BluenetConfig.SETUP_SERVICE_UUID, BluenetConfig.CHAR_SETUP_CONTROL_UUID, accessLevel, callback);
		} else {
			sendCommand(address, command, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_CONTROL_UUID, accessLevel, callback);
		}
	}

	/**
	 * Write the given mesh message to the mesh characteristic. the mesh message will be
	 * forwarded by the device into the mesh network
	 * @param address the address of the device
	 * @param message the mesh message to be sent into the mesh
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeMeshMessage(String address, MeshControlMsg message, final IStatusCallback callback) {
		getLogger().LOGd(TAG, "mesh message: write %s at service %s and characteristic %s", message.toString(), BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_MESH_CONTROL_UUID);
		byte[] msgBytArr = message.toArray();
		if (msgBytArr.length > BluenetConfig.MESH_MAX_PAYLOAD_SIZE) {
			getLogger().LOGe(TAG, "Message too large: " + BleUtils.bytesToString(msgBytArr));
			callback.onError(BleErrors.ERROR_WRONG_PAYLOAD_SIZE);
			return;
		}
		write(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_MESH_CONTROL_UUID, msgBytArr,
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to mesh message characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to mesh message characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Read the list tracked devices characteristic to get the list of tracked devices
	 * @param address the address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void readTrackedDevices(String address, final IByteArrayCallback callback) {
		getLogger().LOGd(TAG, "read tracked devices at service %s and characteristic %s", BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_TRACKED_DEVICES_UUID);
		read(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_TRACKED_DEVICES_UUID, new IDataCallback() {
			@Override
			public void onError(int error) {
				getLogger().LOGe(TAG, "Failed to read tracked devices characteristic");
				callback.onError(error);
			}

			@Override
			public void onData(JSONObject json) {
				byte[] bytes = BleCore.getValue(json);
				getLogger().LOGd(TAG, "tracked devices: %s", BleUtils.bytesToString(bytes));
				callback.onSuccess(bytes);
				// todo: add function to extended, with nice classes and list of objects, etc.
			}
		});
	}

	/**
	 * Write the given device to the add tracked device characteristic to add it as a device
	 * to the list of tracked devices
	 * @param address the address of the device
	 * @param device device to be tracked
	 * @param callback the callback which will be informed about success or failure
	 */
	public void addTrackedDevice(String address, TrackedDeviceMsg device, final IStatusCallback callback) {
		getLogger().LOGd(TAG, "add tracked device: write %s at service %s and characteristic %s", device.toString(), BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_TRACK_CONTROL_UUID);
		write(address, BluenetConfig.INDOOR_LOCALIZATION_SERVICE_UUID, BluenetConfig.CHAR_TRACK_CONTROL_UUID, device.toArray(),
				new IStatusCallback() {

					@Override
					public void onSuccess() {
						getLogger().LOGd(TAG, "Successfully written to add tracked device characteristic");
						callback.onSuccess();
					}

					@Override
					public void onError(int error) {
						getLogger().LOGe(TAG, "Failed to write to add tracked device characteristic");
						callback.onError(error);
					}
				});
	}

	/**
	 * Write the reset value to the reset characteristic
	 * @param address the address of the device
	 * @param value reset value, can be either RESET_DEFAULT or RESET_BOOTLOADER
	 * @param callback the callback which will be informed about success or failure
	 */
	public void writeReset(String address, int value, final IStatusCallback callback) {
		getLogger().LOGd(TAG, "writeReset: " + value + " setupMode=" + _setupMode);
		if (_setupMode) {
			writeReset(address, value, BluenetConfig.SETUP_SERVICE_UUID,
					BluenetConfig.CHAR_SETUP_GOTO_DFU_UUID, callback);
		} else {
			writeReset(address, value, BluenetConfig.GENERAL_SERVICE_UUID,
					BluenetConfig.CHAR_RESET_UUID, callback);
		}
	}

	/**
	 * Write the reset value to the reset characteristic
	 * @param address the address of the device
	 * @param value reset value, can be either RESET_DEFAULT or RESET_BOOTLOADER
	 * @param serviceUuid service UUID to write to
	 * @param characteristicUuid characteristic UUID to write to
	 * @param callback the callback which will be informed about success or failure
	 */
	private void writeReset(String address, int value, String serviceUuid, String characteristicUuid, final IStatusCallback callback) {
			getLogger().LOGd(TAG, "reset: write %d at service %s and characteristic %s", value, serviceUuid, characteristicUuid);
			write(address, serviceUuid, characteristicUuid, new byte[]{(byte) value}, BleBaseEncryption.ACCESS_LEVEL_ENCRYPTION_DISABLED,
					new IStatusCallback() {

						@Override
						public void onSuccess() {
							getLogger().LOGd(TAG, "Successfully written to reset characteristic");
							callback.onSuccess();
						}

						@Override
						public void onError(int error) {
							getLogger().LOGe(TAG, "Failed to write to reset characteristic");
							callback.onError(error);
						}
					});
	}

//	public void readMeshData(String address, final IMeshDataCallback callback) {
//		getLogger().LOGd(TAG, "Reading mesh data...");
//		read(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
//				new IDataCallback() {
//					@Override
//					public void onData(JSONObject json) {
//						byte[] bytes = BleCore.getValue(json);
//						MeshNotificationPacket meshData = new MeshNotificationPacket(bytes);
//						BleMeshHubData meshHubData = BleMeshDataFactory.fromBytes(meshData.getData());
//						callback.onData(meshHubData);
//					}
//
//					@Override
//					public void onError(int error) {
//						callback.onError(error);
//					}
//				});
//	}
//
//	ByteBuffer _notificationBuffer = ByteBuffer.allocate(100);
//	// set flag to false in case of buffer overflow. no other way to detect invalid messages
//	// if needed, could add message number?
//	boolean _notificationBufferValid = true;
//	// for backwards compatibility
//	boolean _hasStartMessageType = false;
//	int _meshSubscriberId = 0;
//
//	public void subscribeMeshData(final String address, final IMeshDataCallback callback) {
//		getLogger().LOGd(TAG, "subscribing to mesh data...");
//		_hasStartMessageType = false;
//		subscribe(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
//			new IIntegerCallback() {
//				@Override
//				public void onSuccess(int result) {
//					_meshSubscriberId = result;
//				}
//
//				@Override
//				public void onError(int error) {
//					callback.onError(error);
//				}
//			},
//			new IDataCallback() {
//				@Override
//				public void onData(JSONObject json) {
//					if (BleCore.getStatus(json) == BleCoreTypes.CHARACTERISTIC_PROP_NOTIFY) {
//
//						final byte[] notificationBytes = BleCore.getValue(json);
//
//						BleMeshHubData meshData;
//						MeshNotificationPacket meshDataPart = new MeshNotificationPacket(notificationBytes);
//						try {
//							switch (meshDataPart.getOpCode()) {
//								case 0x0: {
////								meshData = BleMeshDataFactory.fromBytes(notificationBytes);
////								meshData = BleMeshDataFactory.fromBytes(meshDataPart.getData());
//									break;
//								}
//								case 0x20: {
//									_hasStartMessageType = true;
//									_notificationBuffer.clear();
//									_notificationBufferValid = true;
//									_notificationBuffer.put(meshDataPart.getData());
//									break;
//								}
//								case 0x21: {
//									if (_notificationBufferValid) {
//										_notificationBuffer.put(meshDataPart.getData());
//									}
//									break;
//								}
//								case 0x22: {
//									if (_notificationBufferValid) {
//										_notificationBuffer.put(meshDataPart.getData());
//										meshData = BleMeshDataFactory.fromBytes(_notificationBuffer.array());
//										callback.onData((BleMeshHubData) meshData);
//									}
//									if (!_hasStartMessageType) {
//										_notificationBuffer.clear();
//										_notificationBufferValid = true;
//									}
//									break;
//								}
//							}
//						} catch (BufferOverflowException e) {
//							getLogger().LOGe(TAG, "notification buffer overflow. missed some messages?!");
//							_notificationBufferValid = false;
//						}
//
//
//						// unfortunately notifications only report up to 23 bytes, so we can't use
//						// the value provided in the notification directly. however, we can now
//						// read the characteristic to get the full content
//
////						readMeshData(address, callback);
////						read(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
////							new IDataCallback() {
////								@Override
////								public void onData(JSONObject json) {
////									byte[] bytes = BleCore.getValue(json);
////
////									MeshNotificationPacket meshData = BleMeshDataFactory.fromBytes(bytes);
////
////									for (int i = 0; i < notificationBytes.length; ++i) {
////										if (notificationBytes[i] != bytes[i]) {
////											getLogger().LOGe(TAG, "did not receive same mesh message as in notifaction");
////											final MeshNotificationPacket notificationMeshData = BleMeshDataFactory.fromBytes(notificationBytes);
////											getLogger().LOGe(TAG, "notification was from: %s", ((MeshScanResultPacket)notificationMeshData).getSourceAddress());
////											getLogger().LOGe(TAG, "read is from: %s", ((MeshScanResultPacket)meshData).getSourceAddress());
////											break;
////										}
////									}
////
////									callback.onData(meshData);
////								}
////
////								@Override
////								public void onError(int error) {
////									callback.onError(error);
////								}
////							});
//
//
//
//
////						byte[] bytes = BleCore.getValue(json);
////						MeshNotificationPacket meshData = BleMeshDataFactory.fromBytes(bytes);
////						callback.onData(meshData);
//					}
//				}
//
//				@Override
//				public void onError(int error) {
//					callback.onError(error);
//				}
//		});
//	}
//
//	public void unsubscribeMeshData(final String address, final IMeshDataCallback callback) {
//		getLogger().LOGd(TAG, "subscribing to mesh data...");
//		unsubscribe(address, BluenetConfig.MESH_SERVICE_UUID, BluenetConfig.MESH_DATA_CHARACTERISTIC_UUID,
//				_meshSubscriberId,
//				new IStatusCallback() {
//					@Override
//					public void onSuccess() {
//					}
//
//					@Override
//					public void onError(int error) {
//						callback.onError(error);
//					}
//				});
//	}

	public void readSessionNonce(final String address, final IDataCallback callback) {
		getLogger().LOGd(TAG, "readSessionNonce");
		IDataCallback sessionCallback = new IDataCallback() {
			@Override
			public void onData(final JSONObject json) {
				byte[] data = getValue(json);
				getLogger().LOGv(TAG, "get session nonce (setup=%b): %s", _setupMode, BleUtils.bytesToString(data));
				if (_setupMode) {
					_encryptionSessionData = BleBaseEncryption.getSessionData(data, false);
				}
				else {
					if (_encryptionKeys == null) {
						getLogger().LOGe(TAG, "no keys set!");
						callback.onError(BleErrors.ENCRYPTION_ERROR);
						return;
					}
					byte[] decryptedData = BleBaseEncryption.decryptEcb(data, _encryptionKeys.getGuestKey());
					_encryptionSessionData = BleBaseEncryption.getSessionData(decryptedData);
				}

				if (_encryptionSessionData == null) {
					getLogger().LOGe(TAG, "no keys set!");
					callback.onError(BleErrors.ENCRYPTION_ERROR);
					return;
				}
				getLogger().LOGd(TAG, "sessionNonce:" + BleUtils.bytesToString(_encryptionSessionData.sessionNonce));
				getLogger().LOGd(TAG, "validationKey:" + BleUtils.bytesToString(_encryptionSessionData.validationKey));
				addBytes(json, "sessionNonce", _encryptionSessionData.sessionNonce);
				addBytes(json, "validationKey", _encryptionSessionData.validationKey);

				// In setup mode, also get the sesssion key
				if (_setupMode) {
					readSessionKey(address, new IByteArrayCallback() {
						@Override
						public void onSuccess(byte[] result) {
							setSetupEncryptionKey(result);
							callback.onData(json);
						}

						@Override
						public void onError(int error) {
							callback.onError(error);
						}
					});
				}
				else {
					callback.onData(json);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		};
		if (_setupMode) {
			read(address, BluenetConfig.SETUP_SERVICE_UUID, BluenetConfig.CHAR_SETUP_SESSION_NONCE_UUID, false, sessionCallback);
		}
		else {
			read(address, BluenetConfig.CROWNSTONE_SERVICE_UUID, BluenetConfig.CHAR_SESSION_NONCE_UUID, false, sessionCallback);
		}

	}
	
	public void readSessionKey(final String address, final IByteArrayCallback callback) {
		if (_setupMode) {
			getLogger().LOGd(TAG, "readSessionKey");
			read(address, BluenetConfig.SETUP_SERVICE_UUID, BluenetConfig.CHAR_SESSION_KEY_UUID, false, new IDataCallback() {

				@Override
				public void onData(JSONObject json) {
					byte[] bytes = BleCore.getValue(json);
					getLogger().LOGd(TAG, "session key: %s", BleUtils.bytesToString(bytes));
					callback.onSuccess(bytes);
				}

				@Override
				public void onError(int error) {
					getLogger().LOGe(TAG, "Failed to read session key");
					callback.onError(error);
				}
			});
		} else {
			callback.onError(BleErrors.ERROR_CHARACTERISTIC_NOT_FOUND);
		}
	}



	public void readFirmwareRevision(final String address, final IByteArrayCallback callback) {
		getLogger().LOGd(TAG, "readFirmwareRevision");
		read(address, BluenetConfig.DEVICE_INFO_SERVICE_UUID, BluenetConfig.CHAR_SOFTWARE_REVISION_UUID, false, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				byte[] bytes = getValue(json);
				getLogger().LOGd(TAG, "firmware version: %s", new String(bytes));
				callback.onSuccess(bytes);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void readHardwareRevision(final String address, final IByteArrayCallback callback) {
		getLogger().LOGd(TAG, "readHardwareRevision");
		read(address, BluenetConfig.DEVICE_INFO_SERVICE_UUID, BluenetConfig.CHAR_HARDWARE_REVISION_UUID, false, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				byte[] bytes = getValue(json);
				getLogger().LOGd(TAG, "hardware version: %s", new String(bytes));
				callback.onSuccess(bytes);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void readBootloaderRevision(final String address, final IByteArrayCallback callback) {
		getLogger().LOGd(TAG, "readBootloaderRevision");
		read(address, BluenetConfig.DEVICE_INFO_SERVICE_UUID, BluenetConfig.CHAR_SOFTWARE_REVISION_UUID, false, new IDataCallback() {
			@Override
			public void onData(JSONObject json) {
				byte[] bytes = getValue(json);
				getLogger().LOGd(TAG, "bootloader version: %s", new String(bytes));
				callback.onSuccess(bytes);
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}


}
