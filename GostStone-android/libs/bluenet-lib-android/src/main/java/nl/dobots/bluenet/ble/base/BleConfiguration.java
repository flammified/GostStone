package nl.dobots.bluenet.ble.base;

import java.nio.charset.Charset;

import nl.dobots.bluenet.ble.base.callbacks.IConfigurationCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.ILongCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.structs.ConfigurationMsg;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.base.callbacks.IBooleanCallback;
import nl.dobots.bluenet.ble.extended.callbacks.IStringCallback;
import nl.dobots.bluenet.utils.BleUtils;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 13-6-16
 *
 * Wraps the Ble calls for configuration settings into separate functions (one set and one get
 * function per configuration type)
 *
 * The set function wraps the call into a Configuration Message, see @ConfigurationMsg and then
 * writes it to the ConfigControl characteristic.
 * The read function retrieves the Configuration Message from the ConfigRead characteristic and
 * parses it, i.e. the length value is verified, and if successful the value is returned (based
 * on the length parameter)
 *
 * @author Dominik Egger
 */
public class BleConfiguration {

	public static final String TAG = BleConfiguration.class.getCanonicalName();

	public static final int IBEACON_PROXIMITY_UUID_LENGTH = 16;
	public static final int ENCRYPTION_KEY_LENGTH = 16;

	private BleBase _bleBase;

	public BleConfiguration(BleBase bleBase) {
		_bleBase = bleBase;
	}

	/**
	 * Write the device name to the configuration
	 * @param address the MAC address of the device
	 * @param value new device name
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setDeviceName(String address, String value, final IStatusCallback callback) {
		byte[] bytes = value.getBytes(Charset.forName("UTF-8"));
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_NAME, bytes.length, bytes);
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the device name from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getDeviceName(String address, final IStringCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_NAME, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() == 0) {
					_bleBase.getLogger().LOGe(TAG, "empty name received!");
					onError(BleErrors.ERROR_EMPTY_VALUE);
				} else {
					String deviceName = new String(configuration.getPayload());
					_bleBase.getLogger().LOGd(TAG, "device name: %s", deviceName);
					callback.onSuccess(deviceName);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the crownstone id to the configuration
	 * @param address the MAC address of the device
	 * @param value new crownstone id
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setCrownstoneId(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_CROWNSTONE_ID, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the crownstone id from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getCrownstoneId(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_CROWNSTONE_ID, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int crownstoneId = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "crownstone id: %d", crownstoneId);
					callback.onSuccess(crownstoneId);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the tx power to the configuration. This can be one of the following values:
	 *  -30, -20, -16, -12, -8, -4, 0, or 4
	 * @param address the MAC address of the device
	 * @param value the new tx power
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setTxPower(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_TX_POWER, 1, new byte[]{(byte)value});
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the tx power from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTxPower(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_TX_POWER, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int txPower = configuration.getByteValue();
					_bleBase.getLogger().LOGd(TAG, "tx power: %d", txPower);
					callback.onSuccess(txPower);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the advertisement interval (in ms) to the configuration
	 * @param address the MAC address of the device
	 * @param value advertisement interval (in ms)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdvertisementInterval(String address, int value, final IStatusCallback callback) {
		// convert ms to value used by the crownstone (which is in increments of 0.625 ms)
		int advertisementInterval = (int)Math.floor(value / BluenetConfig.ADVERTISEMENT_INCREMENT);
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_ADV_INTERVAL, 2, BleUtils.shortToByteArray(advertisementInterval));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the advertisement interval from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getAdvertisementInterval(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_ADV_INTERVAL, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int advertisementInterval = (int) (configuration.getShortValue() * BluenetConfig.ADVERTISEMENT_INCREMENT);
					_bleBase.getLogger().LOGd(TAG, "advertisement interval: %d", advertisementInterval);
					callback.onSuccess(advertisementInterval);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the beacon major to the configuration
	 * @param address the MAC address of the device
	 * @param value new beacon major
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMajor(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_IBEACON_MAJOR, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the beacon major from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMajor(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_IBEACON_MAJOR, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int major = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "major: %d", major);
					callback.onSuccess(major);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the beacon minor to the configuration
	 * @param address the MAC address of the device
	 * @param value new beacon minor
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconMinor(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_IBEACON_MINOR, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the beacon minor from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconMinor(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_IBEACON_MINOR, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int minor = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "major: %d", minor);
					callback.onSuccess(minor);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the beacon calibrated rssi (rssi at 1m) to the configuration
	 * @param address the MAC address of the device
	 * @param value new beacon calibrated rssi
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconCalibratedRssi(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_IBEACON_TXPOWER, 1, new byte[]{(byte)value});
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the beacon calibrated rssi (rssi at 1m) from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconCalibratedRssi(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_IBEACON_TXPOWER, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int calibratedRssi = configuration.getByteValue();
					_bleBase.getLogger().LOGd(TAG, "rssi at 1 m: %d", calibratedRssi);
					callback.onSuccess(calibratedRssi);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the beacon proximity UUID to the configuration
	 * @param address the MAC address of the device
	 * @param value new beacon proximity UUID
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBeaconProximityUuid(String address, String value, final IStatusCallback callback) {
		byte[] bytes = BleUtils.uuidToBytes(value);
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_IBEACON_PROXIMITY_UUID, bytes.length, bytes);
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the beacon proximity UUID from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBeaconProximityUuid(String address, final IStringCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_IBEACON_PROXIMITY_UUID, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != IBEACON_PROXIMITY_UUID_LENGTH) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					String proximityUuid = BleUtils.bytesToUuid(configuration.getPayload());
					_bleBase.getLogger().LOGd(TAG, "proximity UUID: %s", proximityUuid);
					callback.onSuccess(proximityUuid);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 *
	 * @param address
	 * @param value
	 * @param callback
	 */
	public void setAdminKey(String address, String value, IStatusCallback callback) {
//		byte[] bytes = value.getBytes(Charset.forName("UTF-8"));
		byte[] bytes = BleUtils.hexStringToBytes(value);
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_KEY_ADMIN, bytes.length, bytes);
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 *
	 * @param address
	 * @param callback
	 */
	public void getAdminKey(String address, final IStringCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_KEY_ADMIN, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != ENCRYPTION_KEY_LENGTH) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					String key = new String(configuration.getPayload());
					_bleBase.getLogger().LOGd(TAG, "admin key: %s", key);
					callback.onSuccess(key);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 *
	 * @param address
	 * @param value
	 * @param callback
	 */
	public void setMemberKey(String address, String value, IStatusCallback callback) {
//		byte[] bytes = value.getBytes(Charset.forName("UTF-8"));
		byte[] bytes = BleUtils.hexStringToBytes(value);
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_KEY_MEMBER, bytes.length, bytes);
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 *
	 * @param address
	 * @param callback
	 */
	public void getMemberKey(String address, final IStringCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_KEY_MEMBER, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != ENCRYPTION_KEY_LENGTH) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					String key = new String(configuration.getPayload());
					_bleBase.getLogger().LOGd(TAG, "member key: %s", key);
					callback.onSuccess(key);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 *
	 * @param address
	 * @param value
	 * @param callback
	 */
	public void setGuestKey(String address, String value, IStatusCallback callback) {
//		byte[] bytes = value.getBytes(Charset.forName("UTF-8"));
		byte[] bytes = BleUtils.hexStringToBytes(value);
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_KEY_GUEST, bytes.length, bytes);
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 *
	 * @param address
	 * @param callback
	 */
	public void getGuestKey(String address, final IStringCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_KEY_GUEST, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != ENCRYPTION_KEY_LENGTH) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					String key = new String(configuration.getPayload());
					_bleBase.getLogger().LOGd(TAG, "guest key: %s", key);
					callback.onSuccess(key);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	public void setMeshAccessAddress(String address, int value, IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_MESH_ACCESS_ADDRESS, 4, BleUtils.intToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	public void getMeshAccessAddress(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_MESH_ACCESS_ADDRESS, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 4) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int meshAccessAddress = configuration.getIntValue();
					_bleBase.getLogger().LOGd(TAG, "mesh access address: %X", meshAccessAddress);
					callback.onSuccess(meshAccessAddress);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the nearby timeout to the configuration
	 * @param address the MAC address of the device
	 * @param value new nearby timeout
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setNearbyTimeout(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_NEARBY_TIMEOUT, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the nearby timeout from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getNearbyTimeout(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_NEARBY_TIMEOUT, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int nearbyTimeout = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "nearby timeout: %d", nearbyTimeout);
					callback.onSuccess(nearbyTimeout);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the given value to the configuration
	 * @param address the address of the device
	 * @param value new current limit
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setCurrentLimit(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_CURRENT_LIMIT, 1, new byte[]{(byte)value});
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the current limit from the configuration
	 * @param address the address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getCurrentLimit(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_CURRENT_LIMIT, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int currentLimit = configuration.getUint8Value();
					_bleBase.getLogger().LOGd(TAG, "current limit: %d", currentLimit);
					callback.onSuccess(currentLimit);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the passkey to the configuration
	 * @param address the MAC address of the device
	 * @param value new passkey
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPasskey(String address, String value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_PASSKEY, 6, value.getBytes(Charset.forName("UTF-8")));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the passkey from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getPasskey(String address, final IStringCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_PASSKEY, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 6) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					String passkey = new String(configuration.getPayload());
					_bleBase.getLogger().LOGd(TAG, "current limit: %s", passkey);
					callback.onSuccess(passkey);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the scan duration to the configuration
	 * @param address the MAC address of the device
	 * @param value new scan duration
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanDuration(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_SCAN_DURATION, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the scan duration from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getScanDuration(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_SCAN_DURATION, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int scanDuration = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "scan duration: %d", scanDuration);
					callback.onSuccess(scanDuration);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the scan send delay to the configuration
	 * @param address the MAC address of the device
	 * @param value new scan send delay
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanSendDelay(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_SCAN_SEND_DELAY, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the scan send delay from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getScanSendDelay(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_SCAN_SEND_DELAY, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int scanSendDelay = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "scan send delay: %d", scanSendDelay);
					callback.onSuccess(scanSendDelay);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the scan break duration to the configuration
	 * @param address the MAC address of the device
	 * @param value new scan break duration
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanBreakDuration(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_SCAN_BREAK_DURATION, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the scan break duration from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getScanBreakDuration(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_SCAN_BREAK_DURATION, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int scanBreakDuration = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "scan break duration: %d", scanBreakDuration);
					callback.onSuccess(scanBreakDuration);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the scan filter to the configuration
	 * @param address the MAC address of the device
	 * @param value new scan filter
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanFilter(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_SCAN_FILTER, 1, new byte[]{(byte)value});
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the scan filter from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getScanFilter(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_SCAN_FILTER, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int scanFilter = configuration.getUint8Value();
					_bleBase.getLogger().LOGd(TAG, "scan filter: %d", scanFilter);
					callback.onSuccess(scanFilter);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the scan filter fraction to the configuration
	 * @param address the MAC address of the device
	 * @param value new scan filter fraction
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setScanFilterFraction(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_SCAN_FILTER_SEND_FRACTION, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the scan filter fraction from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getScanFilterFraction(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_SCAN_FILTER_SEND_FRACTION, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int scanFilterFraction = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "scan filter fraction: %d", scanFilterFraction);
					callback.onSuccess(scanFilterFraction);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}




	/**
	 * Write the floor to the configuration
	 * @param address the MAC address of the device
	 * @param value the new floor value
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setFloor(String address, int value, final IStatusCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "deprecated");
//		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_FLOOR, 1, new byte[] {(byte)value});
//		_bleBase.writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the floor from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getFloor(String address, final IIntegerCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "deprecated");
//		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_FLOOR, new IConfigurationCallback() {
//			@Override
//			public void onSuccess(ConfigurationMsg configuration) {
//				if (configuration.getLength() != 1) {
//					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
//					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
//				} else {
//					int floor = configuration.getUint8Value();
//					_bleBase.getLogger().LOGd(TAG, "floor: %d", floor);
//					callback.onSuccess(floor);
//				}
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write the wifi value to the configuration
	 * @param address the MAC address of the device
	 * @param value the new wifi value
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setWifi(String address, String value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_WIFI_SETTINGS, value.length(), value.getBytes(Charset.forName("UTF-8")));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the ip from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIp(String address, final IStringCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_WIFI_SETTINGS, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() == 0) {
					_bleBase.getLogger().LOGe(TAG, "empty name received!");
					onError(BleErrors.ERROR_EMPTY_VALUE);
				} else {
					String deviceName = new String(configuration.getPayload(), Charset.forName("UTF-8"));
					_bleBase.getLogger().LOGd(TAG, "device name: %s", deviceName);
					callback.onSuccess(deviceName);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the device type to the configuration
	 * @param address the MAC address of the device
	 * @param value new device type
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setDeviceType(String address, String value, final IStatusCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "deprecated");
//		byte[] bytes = value.getBytes(Charset.forName("UTF-8"));
//		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_DEVICE_TYPE, bytes.length, bytes);
//		_bleBase.writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the device type from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getDeviceType(String address, final IStringCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "deprecated");
//		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_DEVICE_TYPE, new IConfigurationCallback() {
//			@Override
//			public void onSuccess(ConfigurationMsg configuration) {
//				if (configuration.getLength() == 0) {
//					_bleBase.getLogger().LOGe(TAG, "empty device type received!");
//					onError(BleErrors.ERROR_EMPTY_VALUE);
//				} else {
//					String deviceType = new String(configuration.getPayload());
//					_bleBase.getLogger().LOGd(TAG, "device type: %s", deviceType);
//					callback.onSuccess(deviceType);
//				}
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write the room to the configuration
	 * @param address the MAC address of the device
	 * @param value new room
	 * @param callback the callback which will be informed about success or failure
	 */
	@Deprecated
	public void setRoom(String address, String value, final IStatusCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "deprecated");
//		byte[] bytes = value.getBytes(Charset.forName("UTF-8"));
//		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_ROOM, bytes.length, bytes);
//		_bleBase.writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the room from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	@Deprecated
	public void getRoom(String address, final IStringCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "deprecated");
//		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_ROOM, new IConfigurationCallback() {
//			@Override
//			public void onSuccess(ConfigurationMsg configuration) {
//				if (configuration.getLength() == 0) {
//					_bleBase.getLogger().LOGe(TAG, "empty room received!");
//					onError(BleErrors.ERROR_EMPTY_VALUE);
//				} else {
//					String room = new String(configuration.getPayload());
//					_bleBase.getLogger().LOGd(TAG, "room: %s", room);
//					callback.onSuccess(room);
//				}
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write the minimum environment temperature to the configuration
	 * @param address the MAC address of the device
	 * @param value new minimum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMinEnvTemp(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_MIN_ENV_TEMP, 1, new byte[]{(byte)value});
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the minimum environment temperature from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMinEnvTemp(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_MIN_ENV_TEMP, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int temperature = configuration.getByteValue();
					_bleBase.getLogger().LOGd(TAG, "min environment temperature: %d", temperature);
					callback.onSuccess(temperature);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the maximum environment temperature to the configuration
	 * @param address the MAC address of the device
	 * @param value new maximum environment temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxEnvTemp(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_MAX_ENV_TEMP, 1, new byte[]{(byte)value});
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the maximum environment temperature from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMaxEnvTemp(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_MAX_ENV_TEMP, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int temperature = configuration.getByteValue();
					_bleBase.getLogger().LOGd(TAG, "max environment temperature: %d", temperature);
					callback.onSuccess(temperature);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the boot delay to the configuration
	 * @param address the MAC address of the device
	 * @param value new boot delay
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setBootDelay(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_BOOT_DELAY, 2, BleUtils.shortToByteArray(value));
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the boot delay from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getBootDelay(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_BOOT_DELAY, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int bootDelay = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "current limit: %d", bootDelay);
					callback.onSuccess(bootDelay);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the max chip temperature to the configuration
	 * @param address the MAC address of the device
	 * @param value new max chip temperature
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setMaxChipTemperature(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_MAX_CHIP_TEMP, 1, new byte[]{(byte)value});
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the max chip temperature from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMaxChipTemperature(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_MAX_CHIP_TEMP, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int maxChipTemp = configuration.getByteValue();
					_bleBase.getLogger().LOGd(TAG, "max chip temp: %d", maxChipTemp);
					callback.onSuccess(maxChipTemp);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Write the adc sample rate to the configuration
	 * @param address the MAC address of the device
	 * @param value new adc sample rate
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setAdcSampleRate(String address, int value, final IStatusCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "tbd");
		// todo: tbd
//		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_ADC_SAMPLE_RATE, Z, new byte[]{(byte)value});
//		_bleBase.writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the adc sample rate from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getAdcSampleRate(String address, final IIntegerCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "tbd");
		// todo: tbd
//		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_ADC_SAMPLE_RATE, new IConfigurationCallback() {
//			@Override
//			public void onSuccess(ConfigurationMsg configuration) {
//				if (configuration.getLength() != ) {
//					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
//					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
//				} else {
//					= configuration.getValue();
//					_bleBase.getLogger().LOGd(TAG, "current limit: %d", currentLimit);
//					callback.onSuccess(currentLimit);
//				}
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write the power sample burst interval to the configuration
	 * @param address the MAC address of the device
	 * @param value new power sample burst interval
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleBurstInterval(String address, int value, final IStatusCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "tbd");
		// todo: tbd
//		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_POWER_SAMPLE_BURST_INTERVAL, Z, new byte[]{(byte)value});
//		_bleBase.writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the power sample burst interval from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getPowerSampleBurstInterval(String address, final IIntegerCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "tbd");
		// todo: tbd
//		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_POWER_SAMPLE_BURST_INTERVAL, new IConfigurationCallback() {
//			@Override
//			public void onSuccess(ConfigurationMsg configuration) {
//				if (configuration.getLength() != ) {
//					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
//					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
//				} else {
//					= configuration.getValue();
//					_bleBase.getLogger().LOGd(TAG, "current limit: %d", currentLimit);
//					callback.onSuccess(currentLimit);
//				}
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write the power sample continuous interval to the configuration
	 * @param address the MAC address of the device
	 * @param value new power sample continuous interval
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleContInterval(String address, int value, final IStatusCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "tbd");
		// todo: tbd
//		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_POWER_SAMPLE_CONT_INTERVAL, Z, new byte[]{(byte)value});
//		_bleBase.writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the power sample continuous interval from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getPowerSampleContInterval(String address, final IIntegerCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "tbd");
		// todo: tbd
//		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_POWER_SAMPLE_CONT_INTERVAL, new IConfigurationCallback() {
//			@Override
//			public void onSuccess(ConfigurationMsg configuration) {
//				if (configuration.getLength() != ) {
//					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
//					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
//				} else {
//					= configuration.getValue();
//					_bleBase.getLogger().LOGd(TAG, "current limit: %d", currentLimit);
//					callback.onSuccess(currentLimit);
//				}
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write the power sample continuous number of samples to the configuration
	 * @param address the MAC address of the device
	 * @param value new power sample continuous number of samples
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPowerSampleContNumSamples(String address, int value, final IStatusCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "tbd");
		// todo: tbd
//		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_POWER_SAMPLE_CONT_NUM_SAMPLES, Z, new byte[]{(byte)value});
//		_bleBase.writeConfiguration(address, configuration, callback);
	}

	/**
	 * Get the power sample continuous number of samples from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getPowerSampleContNumSamples(String address, final IIntegerCallback callback) {
		_bleBase.getLogger().LOGe(TAG, "tbd");
		// todo: tbd
//		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_POWER_SAMPLE_CONT_NUM_SAMPLES, new IConfigurationCallback() {
//			@Override
//			public void onSuccess(ConfigurationMsg configuration) {
//				if (configuration.getLength() != ) {
//					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
//					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
//				} else {
//					= configuration.getValue();
//					_bleBase.getLogger().LOGd(TAG, "current limit: %d", currentLimit);
//					callback.onSuccess(currentLimit);
//				}
//			}
//
//			@Override
//			public void onError(int error) {
//				callback.onError(error);
//			}
//		});
	}

	/**
	 * Write the pwm period to the configuration
	 * @param address the MAC address of the device
	 * @param value new pwm period in μs (uint 32)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setPwmPeriod(String address, long value, final IStatusCallback callback) {
		byte[] valArr = BleUtils.uint32ToByteArray(value);
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_PWM_PERIOD, valArr.length, valArr);
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the pwm frequency from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getPwmPeriod(String address, final ILongCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_PWM_PERIOD, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 4) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					long pwmPeriod = BleUtils.toUint32(configuration.getIntValue());
					_bleBase.getLogger().LOGd(TAG, "pwm period: %d", pwmPeriod);
					callback.onSuccess(pwmPeriod);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}



	/**
	 * Write the relay high duration to the configuration
	 * @param address the MAC address of the device
	 * @param value new relay high duration in ms (uint_16)
	 * @param callback the callback which will be informed about success or failure
	 */
	public void setRelayHighDuration(String address, int value, final IStatusCallback callback) {
		byte[] valArr = BleUtils.shortToByteArray(value);
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.CONFIG_RELAY_HIGH_DURATION, valArr.length, valArr);
		_bleBase.writeConfiguration(address, configuration, true, callback);
	}

	/**
	 * Get the relay high duration from the configuration
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getRelayHighDuration(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_RELAY_HIGH_DURATION, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 2) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					int relayHighDuration = configuration.getShortValue();
					_bleBase.getLogger().LOGd(TAG, "relay high duration: %d", relayHighDuration);
					callback.onSuccess(relayHighDuration);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}


	////////////////////////////////////////////////////////////////////////////////////////////////
	// read only
	////////////////////////////////////////////////////////////////////////////////////////////////

	/**
	 * Check if mesh is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getMeshEnabled(String address, final IBooleanCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_MESH_ENABLED, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					boolean meshEnabled = configuration.getBooleanValue();
					_bleBase.getLogger().LOGd(TAG, "mesh enabled: %d", meshEnabled);
					callback.onSuccess(meshEnabled);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Check if encryption is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getEncryptionEnabled(String address, final IBooleanCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_ENCRYPTION_ENABLED, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					boolean encryptionEnabled = configuration.getBooleanValue();
					_bleBase.getLogger().LOGd(TAG, "encryption enabled: %d", encryptionEnabled);
					callback.onSuccess(encryptionEnabled);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Check if iBeacon is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getIBeaconEnabled(String address, final IBooleanCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_IBEACON_ENABLED, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					boolean iBeaconEnabled = configuration.getBooleanValue();
					_bleBase.getLogger().LOGd(TAG, "iBeacon enabled: %d", iBeaconEnabled);
					callback.onSuccess(iBeaconEnabled);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Check if scanner is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getScannerEnabled(String address, final IBooleanCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_SCANNER_ENABLED, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					boolean scannerEnabled = configuration.getBooleanValue();
					_bleBase.getLogger().LOGd(TAG, "scanner enabled: %d", scannerEnabled);
					callback.onSuccess(scannerEnabled);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Check if continuous power sampler is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getContPowerSamplerEnabled(String address, final IBooleanCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_CONT_POWER_SAMPLER_ENABLED, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					boolean contPowerSamplerEnabled = configuration.getBooleanValue();
					_bleBase.getLogger().LOGd(TAG, "continuous power sampler enabled: %d", contPowerSamplerEnabled);
					callback.onSuccess(contPowerSamplerEnabled);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

	/**
	 * Check if tracker is enabled
	 * @param address the MAC address of the device
	 * @param callback the callback which will get the value on success, or an error otherwise
	 */
	public void getTrackerEnabled(String address, final IBooleanCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.CONFIG_TRACKER_ENABLED, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != 1) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					boolean trackerEnabled = configuration.getBooleanValue();
					_bleBase.getLogger().LOGd(TAG, "tracker enabled: %d", trackerEnabled);
					callback.onSuccess(trackerEnabled);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

/*

	public void setXXX(String address, int value, final IStatusCallback callback) {
		ConfigurationMsg configuration = new ConfigurationMsg(BluenetConfig.YYY, Z, new byte[]{(byte)value});
		_bleBase.writeConfiguration(address, configuration, callback);
	}

	public void getXXX(String address, final IIntegerCallback callback) {
		_bleBase.getConfiguration(address, BluenetConfig.YYY, new IConfigurationCallback() {
			@Override
			public void onSuccess(ConfigurationMsg configuration) {
				if (configuration.getLength() != ) {
					_bleBase.getLogger().LOGe(TAG, "Wrong length parameter: %d", configuration.getLength());
					onError(BleErrors.ERROR_WRONG_LENGTH_PARAMETER);
				} else {
					 = configuration.getValue();
					_bleBase.getLogger().LOGd(TAG, "current limit: %d", currentLimit);
					callback.onSuccess(currentLimit);
				}
			}

			@Override
			public void onError(int error) {
				callback.onError(error);
			}
		});
	}

*/


}
