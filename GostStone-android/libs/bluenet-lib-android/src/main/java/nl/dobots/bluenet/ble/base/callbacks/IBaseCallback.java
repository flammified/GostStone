package nl.dobots.bluenet.ble.base.callbacks;

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
 * Interface defining a base for every callback used by the library. I.e. every callback
 * used by the library needs to have an onError function to report back errors
 * success functions depend on the type of data returned and are thus part of the different
 * derived interfaces.
 *
 * @author Dominik Egger
 */
public interface IBaseCallback {

	void onError(int error);

}
