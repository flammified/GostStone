# GostStone

This repository contains the "glue" between [crownstones](https://crownstone.rocks/) and [GOST](https://github.com/gost/server). This is in the form of an Android application that pushes BLE advertisement data (and when implemented bluenet mesh data) to MQTT, which is send to dynamically created SensorThings entities in a Python script.

## GostStone-android

This is pretty straightforward: configure it in Config.java by filling in the keys and the MQTT server. You can get the keys from the [Crownstone Rest API](https://crownstone.rocks/business/developers/#rest_api). Then push it to an android device using Android-Studio and keep it turned on. We use an [Udoo Neo](https://www.udoo.org/udoo-neo/) for this..

## GostStone-middleware

To run this, install everything under requirements.txt (```pip install -R requirements.txt```), fill in the correct MQTT server (same one as GostStone-android) and other settings under the docker-compose.yml file and run ```docker-compose up```. This takes care of everything regarding the middleware.
