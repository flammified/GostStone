package nl.dobots.bluenet.ble.mesh.structs.reply;

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
public class MeshStateReplyPacket extends MeshCommandReplyPacket {

	// TODO: this only holds 1 state reply packet at the moment

	private StateMsg _stateMsg;

	public MeshStateReplyPacket() {
		super();
		_stateMsg = null;
	}

	public MeshStateReplyPacket(StateMsg message, long messageNumber) {
		super(BluenetConfig.MESH_REPLY_CONFIG, messageNumber);
		_stateMsg = message;
		setPayload(_stateMsg.toArray());
	}

//	public MeshStateReplyPacket(byte[] bytes) {
//	}

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

	public StateMsg getReplyItem() {
		return _stateMsg;
	}

}
