package nl.dobots.bluenet.ble.base.callbacks;

import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.utils.BleLog;

/**
 * Copyright (c) 2016 Dominik Egger <dominik@dobots.nl>. All rights reserved.
 * <p>
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3, as
 * published by the Free Software Foundation.
 * <p>
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 3 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * <p>
 * Created on 24-11-16
 *
 * A generic IExecStatusCallback object which can be used in connectAndExecute functions.
 * A callback can be given as parameter which will be triggered onSuccess and onError
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class SimpleExecStatusCallback implements IExecStatusCallback {

	private static final String TAG = SimpleExecStatusCallback.class.getCanonicalName();

	private IStatusCallback    _statusCallback;
	private IByteArrayCallback _byteArrayCallback;
	private IBooleanCallback   _booleanCallback;
	private IIntegerCallback   _integerCallback;
	private ILongCallback      _longCallback;
	private IFloatCallback     _floatCallback;

	public SimpleExecStatusCallback() {}

	public SimpleExecStatusCallback(IStatusCallback statusCallback) {
		_statusCallback = statusCallback;
	}

	public SimpleExecStatusCallback(IByteArrayCallback byteArrayCallback) {
		_byteArrayCallback = byteArrayCallback;
	}

	public SimpleExecStatusCallback(IBooleanCallback booleanCallback) {
		_booleanCallback = booleanCallback;
	}

	public SimpleExecStatusCallback(IIntegerCallback integerCallback) {
		_integerCallback = integerCallback;
	}

	public SimpleExecStatusCallback(ILongCallback longCallback) {
		_longCallback = longCallback;
	}

	public SimpleExecStatusCallback(IFloatCallback floatCallback) {
		_floatCallback = floatCallback;
	}

	@Override
	public void onSuccess() {
		if (_statusCallback != null) {
			_statusCallback.onSuccess();
		} else {
			BleLog.getInstance().LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onSuccess(byte[] result) {
		if (_byteArrayCallback != null) {
			_byteArrayCallback.onSuccess(result);
		} else {
			BleLog.getInstance().LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onSuccess(boolean value) {
		if (_booleanCallback != null) {
			_booleanCallback.onSuccess(value);
		} else {
			BleLog.getInstance().LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onSuccess(int result) {
		if (_integerCallback != null) {
			_integerCallback.onSuccess(result);
		} else {
			BleLog.getInstance().LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onSuccess(long result) {
		if (_longCallback != null) {
			_longCallback.onSuccess(result);
		} else {
			BleLog.getInstance().LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onSuccess(float result) {
		if (_floatCallback != null) {
			_floatCallback.onSuccess(result);
		} else {
			BleLog.getInstance().LOGw(TAG, "Stub, Wrong usage?!");
		}
	}


	@Override
	public void onError(int error) {
		if (_statusCallback != null) {
			_statusCallback.onError(error);
		} else if (_byteArrayCallback != null) {
			_byteArrayCallback.onError(error);
		} else if (_booleanCallback != null) {
			_booleanCallback.onError(error);
		} else if (_integerCallback != null) {
			_integerCallback.onError(error);
		} else if (_longCallback != null) {
			_longCallback.onError(error);
		} else if (_floatCallback != null) {
			_floatCallback.onError(error);
		} else {
			BleLog.getInstance().LOGw(TAG, "Stub, Wrong usage?!");
		}
	}

	@Override
	public void onExecuteSuccess(boolean disconnect) {
		// nothing to do
	}
}
