package nl.dobots.bluenet.ble.base.callbacks;

import nl.dobots.bluenet.ble.base.structs.PowerSamples;

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
 * Created on 5-7-16
 *
 * @author Dominik Egger
 */
public interface IPowerSamplesCallback extends IBaseCallback {

	void onData(PowerSamples data);
}
