package nl.dobots.bluenet.ble.extended.structs;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.UUID;

import nl.dobots.bluenet.ble.base.structs.CrownstoneServiceData;
import nl.dobots.bluenet.ble.cfg.BleTypes;
//import nl.dobots.bluenet.utils.BleLog;
import nl.dobots.bluenet.utils.BleLog;
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
public class BleDevice {

	// use BleLog.getInstance().setLogLevelPerTag(BleDevice.class.getCanonicalName(), <NEW_LOG_LEVEL>)
	// to change the log level
	private static final int LOG_LEVEL = Log.WARN;

	public static final String TAG = BleDevice.class.getCanonicalName();

	// to be checked, obtained from http://data.altbeacon.org/android-distance.json
	// device specific constants, these ones are for a Nexus 4/5
	private static final double coeff1 = 0.42093;
	private static final double coeff2 = 6.9476;
	private static final double coeff3 = 0.54992;

	public static final int NUM_ADVERTISEMENT_VALIDATIONS = 3;

	// todo: should we support a device being more than one type?
	enum DeviceType {
		unknown,
		ibeacon,
		crownstonePlug,
		crownstoneBuiltin,
		guidestone,
		@Deprecated
		fridge
	}

	enum CrownstoneMode {
		unknown,
		setup,
		normal,
		dfu,
	}

	private static long expirationTime = 1000;

	private String _address;
	private String _name;
	private int _rssi;
	private DeviceType _type;

	private Integer _averageRssi;
	private ArrayList<RssiMeasurement> _rssiHistory = new ArrayList<>();

	private Double _distance;

	private boolean _isIBeacon;
	private int _major;
	private int _minor;
	private UUID _proximityUuid;
	private int _calibratedRssi;

	private CrownstoneServiceData _serviceData;

	private CrownstoneMode _crownstoneMode = CrownstoneMode.unknown;
	private boolean _isValidatedCrownstone = false;
	private int _lastCrownstoneId = -1;
	private String _lastRandom;
	private int _numSimilarCrownstoneIds = 0;



	public BleDevice(String address, String name, int rssi) {
		_address = address;
		_name = name;
		_rssi = rssi;
		_type = DeviceType.unknown;
		_isIBeacon = false;
		_crownstoneMode = CrownstoneMode.unknown;
		_isValidatedCrownstone = false;

		updateRssiValue((new Date()).getTime(), rssi);
//		_rssiHistory.add(new RssiMeasurement(rssi, (new Date()).getTime()));
	}

	private BleDevice(String address, String name, int rssi, DeviceType type, boolean isIBeacon, int major, int minor, UUID proximityUuid, int calibratedRssi, boolean validated, CrownstoneMode mode) {
		_address = address;
		_name = name;
		_rssi = rssi;
		_type = type;
		_isIBeacon = isIBeacon;
		_major = major;
		_minor = minor;
		_proximityUuid = proximityUuid;
		_calibratedRssi = calibratedRssi;
		_crownstoneMode = mode; // TODO: should this be copied?
		_isValidatedCrownstone = validated; // TODO: should this be copied?

		updateRssiValue((new Date()).getTime(), rssi);
//		_rssiHistory.add(new RssiMeasurement(rssi, (new Date()).getTime()));
	}

	public BleDevice(JSONObject json) throws JSONException {
		_address = json.getString(BleTypes.PROPERTY_ADDRESS);
		// name is not a required property of an advertisement, so if no name is present
		// just use the default name
		_name = json.optString(BleTypes.PROPERTY_NAME, "No Name");
		_rssi = json.getInt(BleTypes.PROPERTY_RSSI);
		_type = determineDeviceType(json);
		_crownstoneMode = CrownstoneMode.unknown;

		if (json.has(BleTypes.PROPERTY_IS_IBEACON) && json.getBoolean(BleTypes.PROPERTY_IS_IBEACON)) {
			_isIBeacon = true;
			_major = json.getInt(BleTypes.PROPERTY_MAJOR);
			_minor = json.getInt(BleTypes.PROPERTY_MINOR);
			_proximityUuid = (UUID) json.get(BleTypes.PROPERTY_PROXIMITY_UUID);
			_calibratedRssi = json.getInt(BleTypes.PROPERTY_CALIBRATED_RSSI);
		}
		if (isStone()) {
			if (json.has(BleTypes.PROPERTY_SERVICE_DATA)) {
				_serviceData = new CrownstoneServiceData(json.getString(BleTypes.PROPERTY_SERVICE_DATA));
			} else {
				_serviceData = new CrownstoneServiceData();
			}

			if (_serviceData.isSetupMode()) {
				_crownstoneMode = CrownstoneMode.setup;
			} else {
				_crownstoneMode = CrownstoneMode.normal;
			}
		}
		if (json.has(BleTypes.PROPERTY_IS_DFU_MODE) && json.getBoolean(BleTypes.PROPERTY_IS_DFU_MODE)) {
			_crownstoneMode = CrownstoneMode.dfu;
//			// Force removal of service data?
//			_serviceData = null;
		}

		validateCrownstone();
		updateRssiValue((new Date()).getTime(), _rssi);
//		_rssiHistory.add(new RssiMeasurement(this._rssi, (new Date()).getTime()));
	}

	public BleDevice clone() {
		return new BleDevice(_address, _name, _rssi, _type,
				_isIBeacon, _major, _minor, _proximityUuid, _calibratedRssi, _isValidatedCrownstone, _crownstoneMode);
	}

	public String toString() {
		String str = "";
		str += _address;
		str += " (" + _name + ")";
		str += ", rssi: [" + _rssi + " avg=" + _averageRssi + "]";
		str += ", type: " + _type.name();
		str += ", mode: " + _crownstoneMode.name();
		str += ", validated: " + _isValidatedCrownstone;
		if (_isIBeacon) {
			str += ", iBeacon: [uuid=" + _proximityUuid + " major=" + _major + " minor=" + _minor + " rssi@1m=" + _calibratedRssi + "]";
		}
		else {
			str += ", iBeacon: no";
		}
		if (isStone() && _serviceData != null) {
			str += ", serviceData: " + _serviceData.toString();
		}
		return str;
	}

	public DeviceType getDeviceType() {
		return _type;
	}

	public boolean isIBeacon() {
//		return _type == DeviceType.ibeacon || _type == DeviceType.guidestone;
		return _isIBeacon;
	}

	public boolean isStone() {
		return _type == DeviceType.crownstonePlug || _type == DeviceType.crownstoneBuiltin || _type == DeviceType.guidestone;
	}

	public boolean isCrownstonePlug() { return _type == DeviceType.crownstonePlug; }

	public boolean isCrownstoneBuiltin() { return _type == DeviceType.crownstoneBuiltin; }

	public boolean isGuidestone() { return _type == DeviceType.guidestone; }

	@Deprecated
	public boolean isFridge() {
		return _type == DeviceType.fridge;
	}

	public boolean isValidatedCrownstone() {
		return _isValidatedCrownstone;
	}

	public boolean isSetupMode() { return _crownstoneMode == CrownstoneMode.setup; }

	public boolean isDfuMode() { return _crownstoneMode == CrownstoneMode.dfu; }

	public boolean isEncrypted() {
		CrownstoneServiceData serviceData = getServiceData();
		if (serviceData != null) {
			return !serviceData.getRandomBytes().equals("");
		}
		return false;
	}

	private DeviceType determineDeviceType(JSONObject json) throws JSONException {
		if (json.has(BleTypes.PROPERTY_IS_CROWNSTONE_PLUG)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_CROWNSTONE_PLUG)) {
				return DeviceType.crownstonePlug;
			}
		}
		if (json.has(BleTypes.PROPERTY_IS_CROWNSTONE_BUILTIN)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_CROWNSTONE_BUILTIN)) {
				return DeviceType.crownstoneBuiltin;
			}
		}
		if (json.has(BleTypes.PROPERTY_IS_GUIDESTONE)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_GUIDESTONE)) {
				return DeviceType.guidestone;
			}
		}
		if (json.has(BleTypes.PROPERTY_IS_FRIDGE)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_FRIDGE)) {
				return DeviceType.fridge;
			}
		}
		if (json.has(BleTypes.PROPERTY_IS_IBEACON)) {
			if (json.getBoolean(BleTypes.PROPERTY_IS_IBEACON)) {
				return DeviceType.ibeacon;
			}
		}
		return DeviceType.unknown;
	}

	public String getAddress() {
		return _address;
	}

	public void setAddress(String address) {
		this._address = address;
	}

	public String getName() {
		return _name;
	}

	public void setName(String name) {
		this._name = name;
	}

	public int getRssi() {
		return _rssi;
	}

	public void setRssi(int rssi) {
//		this._rssi = rssi;
		updateRssiValue(new Date().getTime(), rssi);
	}

	public int getMajor() {
		return BleUtils.toUint16(_major);
	}

	public void setMajor(int major) {
		_major = BleUtils.toUint16(major);
	}

	public int getMinor() {
		return BleUtils.toUint16(_minor);
	}

	public void setMinor(int minor) {
		_minor = BleUtils.toUint16(minor);
	}

	public UUID getProximityUuid() {
		return _proximityUuid;
	}

	public void setProximityUuid(UUID proximityUuid) {
		_proximityUuid = proximityUuid;
	}

	public int getCalibratedRssi() {
		return _calibratedRssi;
	}

	public void setCalibratedRssi(int calibratedRssi) {
		_calibratedRssi = calibratedRssi;
	}

	public CrownstoneServiceData getServiceData() {
		return _serviceData;
	}

	public void setServiceData(CrownstoneServiceData serviceData) {
		_serviceData = serviceData;
	}

	public synchronized void updateRssiValue(long timestamp, int rssi) {
		if (rssi != 127) {
			_rssi = rssi;
			// todo: maybe also add a capacity limit, in case we only scan and never check
			//   for rssi or distance values, we would only add and never remove any values
			//   and the history will only grow
			_rssiHistory.add(new RssiMeasurement(rssi, timestamp));
		}

		_averageRssi = null;
	}

	private synchronized boolean refreshHistory() {
		Date now = new Date();
		ArrayList<RssiMeasurement> newHistory = new ArrayList<>();

		boolean hasChange = false;
		for (RssiMeasurement measurement : _rssiHistory) {
			if (measurement.timestamp + expirationTime > now.getTime()) {
				newHistory.add(measurement);
			} else {
//				if (!hasChange) {
//					Log.d(TAG, "dropping old rssi measurements");
//				}
				hasChange = true;
			}
		}

		this._rssiHistory = newHistory;
		return hasChange;
	}

	public synchronized int getOccurrences() {
		refreshHistory();
		return _rssiHistory.size();
	}

	Comparator<RssiMeasurement> timeSorter = new Comparator<RssiMeasurement>() {
		@Override
		public int compare(RssiMeasurement lhs, RssiMeasurement rhs) {
			return lhs.timestamp.compareTo(rhs.timestamp);
		}
	};

	Comparator<RssiMeasurement> rssiSorter = new Comparator<RssiMeasurement>() {
		@Override
		public int compare(RssiMeasurement lhs, RssiMeasurement rhs) {
			return lhs.rssi.compareTo(rhs.rssi);
		}
	};

	private synchronized ArrayList<RssiMeasurement> getHistoryClone() {
		return (ArrayList<RssiMeasurement>) _rssiHistory.clone();
	}

	public ArrayList<RssiMeasurement> getTimeSortedHistory() {
		refreshHistory();
		ArrayList<RssiMeasurement> clone = getHistoryClone();
		Collections.sort(clone, timeSorter);
		return clone;
	}

	public ArrayList<RssiMeasurement> getRssiSortedHistory() {
		refreshHistory();
		ArrayList<RssiMeasurement> clone = getHistoryClone();
		Collections.sort(clone, rssiSorter);
		return clone;
	}

	/**
	 * set expiration time of rssi measurments. globaly used for every beacon
	 * @param expirationTime
	 */
	public static void setExpirationTime(long expirationTime) {
		BleDevice.expirationTime = expirationTime;
	}

	private synchronized void calculateAverageRssi() {
		Collections.sort(this._rssiHistory, rssiSorter);

		int size = _rssiHistory.size();
		int startIndex = 0;
		int endIndex = size - 1;
		if (size > 2) {
			startIndex = size / 10 + 1;
			endIndex = size - size / 10 - 2;
		}

		double sum = 0;

		for (int i = startIndex; i <= endIndex; i++) {
			sum += _rssiHistory.get(i).rssi;
		}

		_averageRssi = (int)(sum / (endIndex - startIndex + 1));
		_distance = null;
	}

	public synchronized int getAverageRssi() {
		// avoid recalculating if no new measurement was received in between
		// is set to null in updateRssiValue
		if (_averageRssi == null) {
			refresh();
		}
		return _averageRssi;
	}

	public void validateCrownstone() {
		// TODO: if in dfu mode: validate differently
		getLogger().LOGv(TAG, "validateCrownstone " + getAddress());

		if (isDfuMode()) {
			getLogger().LOGv(TAG, "validate crownstone in dfu mode!");
			_lastCrownstoneId = -1;
			_lastRandom = null;
			_isValidatedCrownstone = true;
			return;
		}

		if (!isStone() || _serviceData == null) {
			getLogger().LOGv(TAG, "no service data or no crownstone");
			return;
		}
		if (isSetupMode()) {
			getLogger().LOGv(TAG, "validate crownstone in setup mode!");
			_lastCrownstoneId = -1;
			_lastRandom = null;
			_isValidatedCrownstone = true;
			return;
		}

		getLogger().LOGv(TAG, "_lastCrownstoneId=" + _lastCrownstoneId + " _lastRandom=" + _lastRandom);
		if (_lastCrownstoneId != -1 && _lastRandom != null) {
			// Skip check if crownstone id is external crownstone, or when advertisement didn't change.
			if (_serviceData.isExternalData() || _lastRandom.equals(_serviceData.getRandomBytes())) {
				getLogger().LOGv(TAG, "isExternalData or similar rand");
				return;
			}
			getLogger().LOGv(TAG, "_lastCrownstoneId=" + _lastCrownstoneId + " current=" + _serviceData.getCrownstoneId());
			if (_lastCrownstoneId == _serviceData.getCrownstoneId()) {
				if (!_isValidatedCrownstone) {
//					_numSimilarCrownstoneIds += 1;
					if (++_numSimilarCrownstoneIds >= NUM_ADVERTISEMENT_VALIDATIONS) {
						getLogger().LOGv(TAG, "validated crownstone!");
						_isValidatedCrownstone = true;
					}
					getLogger().LOGv(TAG, "_numSimilarCrownstoneIds=" + _numSimilarCrownstoneIds);
				}
			}
			else {
				_numSimilarCrownstoneIds = 0;
				_isValidatedCrownstone = false;
			}
		}
		_lastCrownstoneId = _serviceData.getCrownstoneId();
		_lastRandom = _serviceData.getRandomBytes();
		getLogger().LOGv(TAG, "updated: _lastCrownstoneId=" + _lastCrownstoneId + " _lastRandom=" + _lastRandom);
	}

	/**
	 * Holds rssi value with timestamp for history
	 */
	private class RssiMeasurement {
		Integer rssi;
		Long timestamp;

		private RssiMeasurement() {}

		public RssiMeasurement(int rssi, long timestamp) {
			this.rssi = rssi;
			this.timestamp = timestamp;
		}
	}

	/**
	 * returns -1 in case of error
	 * @return
	 */
	private void calculateDistance() {
		if (!isIBeacon() || _averageRssi == 0 || _calibratedRssi == 0) {
			_distance = -1.0;
		} else {
			double ratio = _averageRssi * 1.0 / _calibratedRssi;
			if (ratio < 1) {
				_distance = Math.pow(ratio, 10);
			} else {
				_distance = coeff1 * Math.pow(ratio, coeff2) + coeff3;
			}
		}
	}

	/**
	 * only use if isIBeacon returns true. Otherwise, this function will always return -1
	 * returns -1 in case of error
	 * @return
	 */
	public synchronized double getDistance() {
		// avoid recalculating if no new measurement was received in between
		// is set to null in calculateAverageRssi
		if (_distance == null || _averageRssi == null) {
			refresh();
		}
		return _distance;
	}

	public synchronized void refresh() {
//		if (refreshHistory()) {
		refreshHistory();

		calculateAverageRssi();
		calculateDistance();
//		}

	}

	public void copyFromOld(BleDevice old) {
		// Copy from old to new
		_isValidatedCrownstone   = old._isValidatedCrownstone;
		_lastCrownstoneId        = old._lastCrownstoneId;
		_lastRandom              = old._lastRandom;
		_numSimilarCrownstoneIds = old._numSimilarCrownstoneIds;
		_rssiHistory             = old._rssiHistory;


		// If we didn't get any service data, we probably received an advertisement with no scan response
		// In this case, use the old service data
		if (_serviceData == null) {
			// In dfu mode, only copy the old type.
			if (_crownstoneMode == CrownstoneMode.dfu) {
				_type = old._type;
			}
			else {
				_serviceData = old._serviceData;
				_type = old._type;
				_crownstoneMode = old._crownstoneMode;
				_name = old._name;
			}
		}


		updateRssiValue(System.currentTimeMillis(), getRssi());
	}

	public void update(BleDevice newDev) {
		// Copy from new to old
		_serviceData             = newDev._serviceData;
		_type                    = newDev._type;
		_crownstoneMode          = newDev._crownstoneMode;

		//TODO: are these necessary?
		_name                    = newDev._name;
		_isIBeacon               = newDev._isIBeacon;
		_major                   = newDev._major;
		_minor                   = newDev._minor;
		_proximityUuid           = newDev._proximityUuid;
		_calibratedRssi          = newDev._calibratedRssi;

		updateRssiValue(System.currentTimeMillis(), newDev.getRssi());
	}

	private BleLog getLogger() {
		BleLog logger = BleLog.getInstance();
		// update the log level to the default of this class if it hasn't been set already
		if (logger.getLogLevel(TAG) == null) {
			logger.setLogLevelPerTag(TAG, LOG_LEVEL);
		}
		return logger;
	}
}
