package nl.dobots.bluenet.ble.core.callbacks;

import org.json.JSONObject;

import java.util.UUID;

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
 * Created on 10-6-16
 *
 * @author Dominik Egger
 */
public interface ISubscribeCallback {

	void onData(UUID uuidService, UUID uuidCharacteristic, JSONObject data);
	void onError(UUID uuidService, UUID uuidCharacteristic, int error);

}
