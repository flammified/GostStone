package nl.dobots.bluenet.ble.extended;

import android.util.Log;
import android.util.Pair;

import java.util.ArrayList;
import java.util.HashMap;

import nl.dobots.bluenet.ble.base.BleBase;
import nl.dobots.bluenet.ble.base.BleBaseState;
import nl.dobots.bluenet.ble.base.callbacks.IByteArrayCallback;
import nl.dobots.bluenet.ble.base.callbacks.IExecStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.IIntegerCallback;
import nl.dobots.bluenet.ble.base.callbacks.SimpleExecStatusCallback;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;
import nl.dobots.bluenet.utils.BleLog;

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
 * @author Dominik Egger
 */
public class BleExtState {

	// use BleLog.getInstance().setLogLevelPerTag(BleBaseState.class.getCanonicalName(), <NEW_LOG_LEVEL>)
	// to change the log level
	private static final int LOG_LEVEL = Log.WARN;

	public static final String TAG = BleExtState.class.getCanonicalName();

	private BleExt _bleExt;
	private BleBase _bleBase;

	private BleBaseState _bleBaseState;

	private static int globalSubscriberId = 0;
	private static HashMap<Character, SubscriberCallback> _subscriberList = new HashMap<>();

	public BleExtState(BleExt bleExt) {
		_bleExt = bleExt;
		_bleBase = _bleExt.getBleBase();
		_bleBaseState = new BleBaseState(_bleBase);
	}

	public void getSwitchState(final IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
			getLogger().LOGd(TAG, "Get switch state ...");
			_bleBaseState.getSwitchState(_bleExt.getTargetAddress(), callback);
		}
	}

	public void getSwitchState(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getSwitchState(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IExecStatusCallback execCallback) {
					getSwitchState(execCallback);
				}
			}, new SimpleExecStatusCallback(callback));
		}
	}

	public void getAccumulatedEnergy(final IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
			getLogger().LOGd(TAG, "Get accumulated energy ...");
			_bleBaseState.getAccumulatedEnergy(_bleExt.getTargetAddress(), callback);
		}
	}

	public void getAccumulatedEnergy(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getAccumulatedEnergy(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IExecStatusCallback execCallback) {
					getAccumulatedEnergy(execCallback);
				}
			}, new SimpleExecStatusCallback(callback));
		}
	}

	public void getPowerUsage(final IIntegerCallback callback) {
		if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
			getLogger().LOGd(TAG, "Get power usage ...");
			_bleBaseState.getPowerUsage(_bleExt.getTargetAddress(), callback);
		}
	}

	public void getPowerUsage(String address, final IIntegerCallback callback) {
		if (_bleExt.checkConnection(address)) {
			getPowerUsage(callback);
		} else {
			_bleExt.connectAndExecute(address, new IExecuteCallback() {
				@Override
				public void execute(final IExecStatusCallback execCallback) {
					getPowerUsage(execCallback);
				}
			}, new SimpleExecStatusCallback(callback));
		}
	}

	public void getTemperature(final IIntegerCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
					getLogger().LOGd(TAG, "Get temperature ...");
					_bleBaseState.getTemperature(_bleExt.getTargetAddress(), callback);
				}
			}
		});
	}

	public void getTemperature(final String address, final IIntegerCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGv(TAG, "Get temperature");
//				if (_bleExt.checkConnection(address)) {
//					getTemperature(callback);
//				} else {
					_bleExt.connectAndExecute(address, new IExecuteCallback() {
						@Override
						public void execute(final IExecStatusCallback execCallback) {
							getTemperature(execCallback);
						}
					}, new SimpleExecStatusCallback(callback));
//				}
			}
		});
	}

	public void getErrorState(final IIntegerCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
					getLogger().LOGd(TAG, "Get error state ...");
					_bleBaseState.getErrorState(_bleExt.getTargetAddress(), callback);
				}
			}
		});
	}

	public void getErrorState(final String address, final IIntegerCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGv(TAG, "Get error state");
				_bleExt.connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						getErrorState(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}


	public static int setStateErrorBit(char bitPos, int errorBitmask) {
        return errorBitmask | (1 << bitPos);
    }
	public static boolean isStateError(char bitPos, int errorBitmask) {
		return ((errorBitmask & (1L << bitPos)) != 0);
	}
	public static boolean isErrorOvercurrent(int errorBitmask) {
		return isStateError(BluenetConfig.STATE_ERROR_POS_OVERCURRENT, errorBitmask);
	}
	public static boolean isErrorOvercurrentDimmer(int errorBitmask) {
		return isStateError(BluenetConfig.STATE_ERROR_POS_OVERCURRENT_DIMMER, errorBitmask);
	}
	public static boolean isErrorChipTemperature(int errorBitmask) {
		return isStateError(BluenetConfig.STATE_ERROR_POS_TEMP_CHIP, errorBitmask);
	}
	public static boolean isErrorDimmberTemperature(int errorBitmask) {
		return isStateError(BluenetConfig.STATE_ERROR_POS_TEMP_DIMMER, errorBitmask);
	}


	public void getTime(final IIntegerCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
					getLogger().LOGd(TAG, "getTime");
					_bleBaseState.getTime(_bleExt.getTargetAddress(), callback);
				}
			}
		});
	}

	public void getTime(final String address, final IIntegerCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGv(TAG, "Get time ...");
				_bleExt.connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						getTime(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	public void getSchedule(final IByteArrayCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				if (_bleExt.isConnected(callback) && _bleExt.hasStateCharacteristics(callback)) {
					getLogger().LOGd(TAG, "getSchedule");
					_bleBaseState.getSchedule(_bleExt.getTargetAddress(), callback);
				}
			}
		});
	}

	public void getSchedule(final String address, final IByteArrayCallback callback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				getLogger().LOGv(TAG, "Get schedule ...");
				_bleExt.connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						getSchedule(execCallback);
					}
				}, new SimpleExecStatusCallback(callback));
			}
		});
	}

	////////////////////////////////////////////////////////////////////////////////////////////////
	// Notifications
	////////////////////////////////////////////////////////////////////////////////////////////////


	class SubscriberCallback implements IIntegerCallback {

		ArrayList<Pair<Integer,IIntegerCallback>> _subscribers = new ArrayList<>();

		int _subscriberId;

		public int addSubscriber(IIntegerCallback callback) {
			int id = globalSubscriberId++;
			_subscribers.add(new Pair<>(id, callback));
			return id;
		}

		public boolean removeSubscriber(int id) {
			for (Pair<Integer, IIntegerCallback> p : _subscribers) {
				if (p.first == id) {
					_subscribers.remove(p);
					return true;
				}
			}
			return false;
		}

		@Override
		public void onSuccess(int result) {
			for (Pair<Integer, IIntegerCallback> p : _subscribers) {
				p.second.onSuccess(result);
			}
		}

		@Override
		public void onError(int error) {
			for (Pair<Integer, IIntegerCallback> p : _subscribers) {
				p.second.onError(error);
			}
		}

		public void setSubscriberId(int result) {
			_subscriberId = result;
		}
	}

	private SubscriberCallback check(char type, IIntegerCallback statusCallback, IIntegerCallback callback) {
		if (_bleExt.isConnected(statusCallback) && _bleExt.hasStateCharacteristics(statusCallback)) {
			if (!_subscriberList.containsKey(type)) {
				final SubscriberCallback cb = new SubscriberCallback();
				_subscriberList.put(type, cb);
				return cb;
			} else {
				final SubscriberCallback cb = _subscriberList.get(type);
				statusCallback.onSuccess(cb.addSubscriber(callback));
				return cb;
			}
		}
		return null;
	}

	public boolean stopNotifications(int subscriberId) {
		for (SubscriberCallback subscriberCallback : _subscriberList.values()) {
			if (subscriberCallback.removeSubscriber(subscriberId)) {
				return true;
			}
		}
		return false;
	}

	public void getSwitchStateNotifications(final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		final SubscriberCallback cb = check(BluenetConfig.STATE_SWITCH_STATE, statusCallback, callback);
		if (cb != null) {
			_bleBaseState.getSwitchStateNotifications(_bleExt.getTargetAddress(),
					new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							cb.setSubscriberId(result);
							statusCallback.onSuccess(cb.addSubscriber(callback));
						}

						@Override
						public void onError(int error) {
							_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
						}
					}, cb);
		}
	}

	public void getAccumulatedEnergyNotifications(final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		final SubscriberCallback cb = check(BluenetConfig.STATE_ACCUMULATED_ENERGY, statusCallback, callback);
		if (cb != null) {
			_bleBaseState.getAccumulatedEnergyNotifications(_bleExt.getTargetAddress(),
					new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							cb.setSubscriberId(result);
							statusCallback.onSuccess(cb.addSubscriber(callback));
						}

						@Override
						public void onError(int error) {
							_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
						}
					}, cb);
		}
	}

	public void getPowerUsageNotifications(final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		final SubscriberCallback cb = check(BluenetConfig.STATE_ACCUMULATED_ENERGY, statusCallback, callback);
		if (cb != null) {
			_bleBaseState.getPowerUsageNotifications(_bleExt.getTargetAddress(),
					new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							cb.setSubscriberId(result);
							statusCallback.onSuccess(cb.addSubscriber(callback));
						}

						@Override
						public void onError(int error) {
							_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
						}
					}, cb);
		}
	}

	public void getTemperatureNotifications(final IIntegerCallback statusCallback,
											final IIntegerCallback callback) {
		final SubscriberCallback cb = check(BluenetConfig.STATE_TEMPERATURE, statusCallback, callback);
		if (cb != null) {
			_bleBaseState.getTemperatureNotifications(_bleExt.getTargetAddress(),
					new IIntegerCallback() {
						@Override
						public void onSuccess(int result) {
							cb.setSubscriberId(result);
							statusCallback.onSuccess(cb.addSubscriber(callback));
						}

						@Override
						public void onError(int error) {
							_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
						}
					}, cb);
		}
	}

//
//	public void getSwitchStateNotifications(final IIntegerCallback statusCallback,
//											final IIntegerCallback callback) {
//		if (_bleExt.isConnected(statusCallback) && _bleExt.hasStateCharacteristics(statusCallback)) {
//			if (!_subscriberList.containsKey(BluenetConfig.STATE_SWITCH_STATE)) {
//				final SubscriberCallback cb = new SubscriberCallback();
//				_subscriberList.put(BluenetConfig.STATE_SWITCH_STATE, cb);
//
//				_bleBaseState.getSwitchStateNotifications(_bleExt.getTargetAddress(),
//				new IIntegerCallback() {
//					@Override
//					public void onSuccess(int result) {
//						cb.setSubscriberId(result);
//						statusCallback.onSuccess(cb.addSubscriber(callback));
//					}
//
//					@Override
//					public void onError(int error) {
//						_subscriberList.remove(BluenetConfig.STATE_SWITCH_STATE);
//					}
//				}, cb);
//			} else {
//				final SubscriberCallback cb = _subscriberList.get(BluenetConfig.STATE_SWITCH_STATE);
//				statusCallback.onSuccess(cb.addSubscriber(callback));
//			}
//		}
//	}

	private BleLog getLogger() {
		BleLog logger = _bleExt.getLogger();
		// update the log level to the default of this class if it hasn't been set already
		if (logger.getLogLevel(TAG) == null) {
			logger.setLogLevelPerTag(TAG, LOG_LEVEL);
		}
		return logger;
	}
}
