package nl.dobots.bluenet.utils;

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
 * Created on 14-12-16
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public abstract class Logging {

	private BleLog _logger;

	private boolean _logLevelUpdated = false;

	protected abstract int getLogLevel();
	protected abstract String getTag();

	public BleLog getLogger() {
		if (_logger == null) {
			_logger = BleLog.getInstance();
		}
		if (!_logLevelUpdated) {
//			_logger.setLogLevelPerTag(getTag(), getLogLevel());
			_logLevelUpdated = true;
		}
		return _logger;
	}

	public void setLogger(BleLog logger) {
		_logger = logger;
		_logLevelUpdated = false;
	}

}
