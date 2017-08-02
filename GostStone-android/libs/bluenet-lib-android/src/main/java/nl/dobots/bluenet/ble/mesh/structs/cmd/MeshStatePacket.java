package nl.dobots.bluenet.ble.mesh.structs.cmd;

import nl.dobots.bluenet.ble.base.structs.StateMsg;
import nl.dobots.bluenet.ble.cfg.BluenetConfig;

/**
 * Copyright (c) 2017 Dominik Egger <dominik@dobots.nl>. All rights reserved.
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
 * Created on 27-1-17
 *
 * @author Dominik Egger <dominik@dobots.nl>
 */
public class MeshStatePacket extends MeshCommandPacket {

	private StateMsg _stateMsg;

	public MeshStatePacket() {
		super();
		_stateMsg = null;
	}

	public MeshStatePacket(StateMsg message, int... ids) {
		super(BluenetConfig.MESH_CMD_STATE, ids);
		_stateMsg = message;
		setPayload(_stateMsg.toArray());
	}

	@Override
	public boolean fromArray(byte[] bytes) {
		if (!super.fromArray(bytes)) {
			return false;
		}
		_stateMsg = new StateMsg();
		if (!_stateMsg.fromArray(getPayload())) {
			_stateMsg = null;
			return false;
		}
		return true;
	}

	@Override
	protected String payloadToString() {
		return _stateMsg.toString();
	}
}
