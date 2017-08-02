package nl.dobots.bluenet.ble.extended;

import nl.dobots.bluenet.ble.base.BleBase;
import nl.dobots.bluenet.ble.base.BleConfiguration;
import nl.dobots.bluenet.ble.base.callbacks.IExecStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.IProgressCallback;
import nl.dobots.bluenet.ble.core.callbacks.IStatusCallback;
import nl.dobots.bluenet.ble.base.callbacks.SimpleExecStatusCallback;
import nl.dobots.bluenet.ble.base.structs.ControlMsg;
import nl.dobots.bluenet.ble.cfg.BleErrors;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;
import nl.dobots.bluenet.ble.extended.callbacks.IExecuteCallback;

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
 * Created on 15-11-16
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class CrownstoneSetup {

	private static final String TAG = CrownstoneSetup.class.getCanonicalName();

	private BleBase _bleBase;
	private BleExt _bleExt;

	private String _targetAddress;
	private BleConfiguration _bleConfiguration;

	private int _currentStep;

	private int _crownstoneId;
	private String _adminKey;
	private String _memberKey;
	private String _guestKey;
	private int _meshAccessAddress;
	private String _iBeaconUuid;
	private int _iBeaconMajor;
	private int _iBeaconMinor;
	private IProgressCallback _progressCallback;
	private IStatusCallback _statusCallback;

	private boolean _cancel;

	public CrownstoneSetup(BleExt bleExt) {
		_bleExt = bleExt;
		_bleBase = bleExt.getBleBase();
		_bleConfiguration = new BleConfiguration(_bleBase);
	}

	IStatusCallback _defaultCallback =  new IStatusCallback() {
		@Override
		public void onSuccess() {
			_progressCallback.onProgress(_currentStep, null);
			setupStep(_currentStep+1);
		}

		@Override
		public void onError(int error) {
			setupError(error);
		}
	};

	private void setupError(int error) {
		_bleExt.getLogger().LOGe(TAG, "setup failed at step %d with error %d", _currentStep, error);
		_progressCallback.onProgress(0, null);
		_progressCallback.onError(BleErrors.ERROR_SETUP_FAILED);
		_statusCallback.onError(BleErrors.ERROR_SETUP_FAILED);
	}

	private void setupStep(final int step) {
		if (_cancel) {
			_statusCallback.onError(BleErrors.ERROR_SETUP_CANCELED);
			return;
		}

		_currentStep = step;
		switch(step) {
			case 0:
			case 1: {
//				// hack, make sure step is set to 1 in case function was called with step 0
//				_currentStep = 1;
//				_bleBase.readSessionKey(_targetAddress, new IByteArrayCallback() {
//					@Override
//					public void onSuccess(byte[] result) {
//						_progressCallback.onProgress(1, null);
//						_bleBase.setSetupEncryptionKey(result);
////						SetupEncryptionKey encryptionKey = new SetupEncryptionKey(result);
////						_bleBase.setEncryptionKeys(encryptionKey);
//
//						// go directly to step 3
//						setupStep(3);
//					}
//
//					@Override
//					public void onError(int error) {
//						setupError(error);
//					}
//				});
//				break;
			}
			case 2: {
				// nothing to do here, session nonce was already read out on connect
				// nothing to do here, set session nonce and enable encryption are handled in callbacks
			}
			case 3: {
				// Increase the TX power of the crownstone. This is possible, since the data is encrypted from here on.
				// hack, make sure step is set to 3 in case function was called with step 2
				_currentStep = 3;
				_bleExt.writeIncreaseTx(_defaultCallback);
				break;
			}
			case 4: {
				_currentStep = 4;
				_bleConfiguration.setCrownstoneId(_targetAddress, _crownstoneId, _defaultCallback);
				break;
			}
			case 5: {
				_bleConfiguration.setAdminKey(_targetAddress, _adminKey, _defaultCallback);
				break;
			}
			case 6: {
				_bleConfiguration.setMemberKey(_targetAddress, _memberKey, _defaultCallback);
				break;
			}
			case 7: {
				_bleConfiguration.setGuestKey(_targetAddress, _guestKey, _defaultCallback);
				break;
			}
			case 8: {
				_bleConfiguration.setMeshAccessAddress(_targetAddress, _meshAccessAddress, _defaultCallback);
				break;
			}
			case 9: {
				_bleConfiguration.setBeaconProximityUuid(_targetAddress, _iBeaconUuid, _defaultCallback);
				break;
			}
			case 10: {
				_bleConfiguration.setBeaconMajor(_targetAddress, _iBeaconMajor, _defaultCallback);
				break;
			}
			case 11: {
				_bleConfiguration.setBeaconMinor(_targetAddress, _iBeaconMinor, _defaultCallback);
				break;
			}
			case 12: {
				finalizeSetup(_defaultCallback);
				break;
			}
			case 13: {
				// Clear cache, as we know that the services will change.
				_bleExt.disconnectAndClose(true, _defaultCallback);
				break;
			}
			case 14: {
//				_bleBase.setEncryptionKeys(new EncryptionKeys(_adminKey, _memberKey, _guestKey));
				_statusCallback.onSuccess();
				break;
			}
			default: {
				_bleExt.getLogger().LOGe(TAG, "wrong setup step!");
				_statusCallback.onError(BleErrors.ERROR_WRONG_STATE);
			}
		}
	}

	private void finalizeSetup(IStatusCallback callback) {
		_bleBase.sendCommand(_targetAddress, new ControlMsg(BluenetConfig.CMD_VALIDATE_SETUP), callback);
	}

	public void executeSetup(int crownstoneId, String adminKey, String memberKey, String guestKey,
							 int meshAccessAddress, String iBeaconUuid, int iBeaconMajor,
							 int iBeaconMinor, IProgressCallback progressCallback, IStatusCallback statusCallback) {

		if (_bleExt.isConnected(statusCallback)) {
			_bleExt.getLogger().LOGd(TAG, "executeSetup");

			_targetAddress = _bleExt.getTargetAddress();
			_crownstoneId = crownstoneId;
			_adminKey = adminKey;
			_memberKey = memberKey;
			_guestKey = guestKey;
			_meshAccessAddress = meshAccessAddress;
			_iBeaconUuid = iBeaconUuid;
			_iBeaconMajor = iBeaconMajor;
			_iBeaconMinor = iBeaconMinor;
			_progressCallback = progressCallback;
			_statusCallback = statusCallback;

			_cancel = false;

			setupStep(1);
		}

	}

	public void executeSetup(final String address, final int crownstoneId, final String adminKey, final String memberKey,
							 final String guestKey, final int meshAccessAddress, final String iBeaconUuid, final int iBeaconMajor,
							 final int iBeaconMinor, final IProgressCallback progressCallback, final IStatusCallback statusCallback) {
		_bleExt.getHandler().post(new Runnable() {
			@Override
			public void run() {
				_bleExt.getLogger().LOGd(TAG, "connect and executeSetup");
				_bleExt.connectAndExecute(address, new IExecuteCallback() {
					@Override
					public void execute(final IExecStatusCallback execCallback) {
						executeSetup(crownstoneId, adminKey, memberKey, guestKey, meshAccessAddress, iBeaconUuid,
								iBeaconMajor, iBeaconMinor, progressCallback, new IStatusCallback() {
									@Override
									public void onSuccess() {
										statusCallback.onSuccess();
										// use parameter false because the setup already
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
					public void onSuccess() {
						/* don't care */
					}

					@Override
					public void onError(int error) {
						statusCallback.onError(error);
					}
				});
			}
		});
	}

	public void cancelSetup() {
		_cancel = true;
	}

}
