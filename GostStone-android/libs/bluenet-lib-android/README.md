# Bluenet Android Library

This android library is part of the [crownstone-sdk](https://github.com/crownstone/crownstone-sdk) and simplifies the interaction with BLE devices running [bluenet](https://github.com/crownstone/bluenet). For more information about the bluenet firmware, have a look at the [bluenet repository](https://github.com/crownstone/bluenet). For general information about the crownstone, have a look at the [crownstone-sdk repository](https://github.com/crownstone/crownstone-sdk)

# Example

For a simple example of how to use the library, check out the bluenet-example-android [here](https://github.com/crownstone/bluenet-example-android). It shows how to scan for devices (using a service, or manually) and how to connect to a crownstone to set PWM on and off.

A more extensive example can be found in the crownstone-dev app [here](https://github.com/crownstone/crownstone-dev). This app is used for developing / debugging the crownstones.

# Protocol

The library simplifies interaction with the bluenet firmware. It encapsulates the [protocol](https://github.com/crownstone/bluenet/blob/master/PROTOCOL.md) used for communication in simple to use objects.

# Overview

The library is built up in three stages:

## Lowest Level: BleCore

The BleCore class implements Android specific functions around the Bluetooth Adapter. It implements the callbacks used by the Bluetooth Adapter for reading and writing characteristics, subscribing to characteristics and handling Ble scans.

Objects, i.e. scanned devices, or data received from read requests are returned as JSON Objects

This level is independent of the [bluenet protocol](https://github.com/crownstone/bluenet/blob/master/PROTOCOL.md). It only implements functions necessary to communicate over Bluetooth Low Energy based on the classes provided by Android. 

## Mid Level: BleBase

The BleBase class extends the BleCore class and adds all characteristics available in the bluenet firmware. I.e. it implements the [bluenet protocol](https://github.com/crownstone/bluenet/blob/master/PROTOCOL.md) in an easy to use way. So that a user of the library does not need to know the exact layout of the messages defined by the protocol.

- It parses the data received at read characteristic requests and encapsulates them into objects. e.g.
    - [Control packet](https://github.com/crownstone/bluenet/blob/master/PROTOCOL.md#control_packet)
    - [Config packet](https://github.com/crownstone/bluenet/blob/master/PROTOCOL.md#config_packet)
    - [Power Samples](https://github.com/crownstone/bluenet/blob/master/PROTOCOL.md#power_samples_packet)
    - etc.
- It parses the advertisements received during scans to retrieve additional data such as the [Service Data](https://github.com/crownstone/bluenet/blob/master/PROTOCOL.md#scan_response_servicedata_packet) or the [iBeacon advertisement data](https://github.com/crownstone/bluenet/blob/master/PROTOCOL.md#ibeacon_packet) (major, minor, UUID, etc)

All functions are implemented in an asynchronous way. This means that the functions expect a callback, which will be informed about the success / failure of the function call.

### Read / Write

The base class provides functions to read and write to the characteristics available in the bluenet firmware. 

For write functions, the value to be written is given as part of the arguments together with a status callback. The callback has two functions, `onSuccess` and `onError`. `onSuccess` is triggered without parameter and signals that the function completed successfully. The `onError` function has a parameter error to show the reason of failure. For the list of errors, see `BleErrors`.

Read functions only have one parameter, which is a callback depending on the type of value to be read. The basic types are:
- IBooleanCallback
- IIntegerCallback
- IStringCallback

Functions which return an object, like reading power samples, or reading configuration values, return special objects and have designated callbacks:
- IPowerSamplesCallback
- IConfigurationCallback

All callbacks of the read functions have two functions, `onSuccess` and `onError`. The parameter type of the `onSuccess` call depends on the type of the callback, the `onError` function returns an error code.

### Operation

If you want to call a function of the base class, you need to connect first to the device and discover the services of the device, otherwise the functions will trigger an error. Other than that, no checks are done, i.e. the library tries to write/read from a characteristic, even if the device does not have those charcteristics.

The steps to read/write are as follows:

1. connect to the device using `connectDevice()`
2. discover the services/characteristic with `discoverServices()`. 
    Note: This function has to be called before you can read/write any of the characteristics, otherwise the calls will fail.
3. call the read/write function

Once the device does not need to be accessed anymore

1. disconnect from the device with `disconnectDevice()`
2. close the device with `closeDevice()`. 
    If you don't close the device, the android system keeps the device "open", although disconnected. Most phones can only have a certain number of phones "open", around 8, and if you want to connect to another device, eg. the 9th, it will fail.

### Subscribing

The BleBase class also provides a function to subscribe to characteristics and receive notifications if the value changes/updates. The `subscribe` function takes 5 arguments:
- MAC address of the device (note that the device needs to be continuously connectd in order to receive notifications)
- Service UUID and Characteristic UUID
- Status callback
    Once subscribed successfully to the characteristic, the status callback returns a subscriber id in it's `onSuccess` callback. This id is required to unsubscribe again later on.
- Data callback
    If successfully subscribed, the `onData` function of the data callback triggers with a JSONObject containing the update.

You can subscribe several times to the same characteristic. The library takes care of the subscriptions and forwards them to all subscribers.

To unsubscribe again from the characteristic, you need to provide the subscriberId which was received during the subscribe.

## Top level: BleExt

The BleExt class is the extended version of the library and is most likely the one you want to use. It provides additional functionality on top of the base functionality provided in BleBase.

This includes:

- Keeping up the discovered services/characteristics
- Keeping up the list of scanned devices
    - updating the current, average, history rssi values for each device
    - calculating distance for iBeacons
- Filtering scanned devices (crownstone, guidestone, ibeacon, all)
- Automatic retries
- 

It also provides extended functions such as:

- connectAndDiscover. Connects and discovers available services/characteristics
- connectAndExecute. Connects and executes a function if service/characteristic is available

Each read/write operation can be called in two different ways:

- one set of functions can be called if the device is already connected
- the other functions will first connect, then execute, then disconnect again after a delay expired.

E.g. To read write the PWM value of the device

1. `writePwm(int value, IStatusCallback callback)`
    this function checks first if the device is connected. If it is not connected, the callback's `onError()` will be triggered. If it is connected, it checks first if the characteristic is available. If that check succeeds, the PWM value is written. 
    Note: The connection at the end of the call will be kept open indefinetly.
2. `writePwm(string address, int value, IStatusCallback callback)`
    this function will first connect to the device with the given address, once connected it retrieves the services/characteristic, then the same behaviour applies as in the previous function. Once the value is written, a timeout is started, and once the timeout expires, the device is disconnected and closed again.

### State and Configuration

The bluenet firmware provides two special characteristics. One to read/write configuration values, one to read/notify state variables. These characteristics take messages which include a type variable to define which config value/state variable should be read. To simplify interaction with these characteristics, additional classes are provided:

- BleBaseState / BleExtState
    These classes provide functions to read State variables, e.g Temperature, Switch State, Power Usage, etc.
    If the device is connected continuously, it's also possible to subscribe to state notifications. This way, every time the state variable changes, a notification event is triggered with the updated value

- BleBaseConfiguration / BleExtConfiguration
    Each configuration type has a separate function to easily set and read the different values.

## Service

We also provide a service class which does interval scanning for you. The scan duration and scan break can be specified. There are two different types of callbacks which can be registered:

- IntervalScanListener
    This listener triggers an event at the start and at the end of each scan interval. To get the list of scanned devices at the end of the interval, use the function `getDeviceMap()` in the onScanEnd event, which will return a HashMap of the scanned devices. The map will be updated with every device scanned during the scan interval. It will stay intact over all scan intervals. If you are only interested in the scanned devices during one interval, clear the map first at the `onStartScan` event with `clearDeviceMap()`
- ScanDeviceListener
    This listener triggers for every received advertisement of a device. 
    Note: The device returned is kept up to date over all scan intervals. 
    
The service provides functions to start, stop, pause, change interval or pause durations or to set a filter on the devices which should be returned.

Most parameters can also be provided in the Bundle when the Service is created.
    
## Manual Scanning

To manually scan for devices, i.e. not using the service, you can either use the BleBase or the BleExt. Both classes provide a function `startEndlessScan`. The scan will run until you call `stopEndlessScan`. The function parses the received advertisements into a `BleDevice` object, i.e. it parses the iBeacon data and the Service Data used by the Crownstones.

The BleExt, in adition to parsing the data, also provides filtering. E.g. to only return Crownstones, or to return only iBeacon devices. Moreover, it keeps a list of scanned devices, and updates the devices every time it receives a new advertisement. During the update, the device averages the rssi values over a time (defined by an expiration timeout). If the device is an iBeacon, it uses the TxPower of the iBeacon protocol to calculate the current distance from the iBeacon based on the average rssi. The longer the expiration timeout, the longer it takes for the distance value to update.

It's also possible to whitelist/blacklist devices in the BleExt class to only return a set of devices (given the MAC addresses) or to exclude a set of devices from the scans.

## Copyrights

The copyrights (2014-2015) for the code belongs to the team of Distributed Organisms B.V. and are provided under an noncontagious open-source license:

* Authors: Dominik Egger, Anne van Rossum, Bart van Vliet, Marc Hulscher, Peet van Tooren
* Date: 16. Jul. 2015
* License: LGPL v3+, Apache, or MIT, your choice
* Crownstone B.V. https://crownstone.rocks
* Rotterdam, The Netherlands
