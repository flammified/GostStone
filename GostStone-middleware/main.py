#!/usr/bin/env python3

import paho.mqtt.client as mqtt
import requests
import logging
import os
import sys
import time
import pickle


def post_to_gost_datastream(stream_id, value):
    server_url = st_server + ":" + str(st_port)
    server_url = server_url + "/v1.0/Datastreams(%d)/Observations" % stream_id
    observation = {'result': value}

    print(str(observation) + " to " + server_url)
    response = requests.post(server_url, json=observation)
    print(response.json())


def create_sensor(address):
    server_url = st_server + ":" + str(st_port)
    server_url = server_url + "/v1.0/Sensors"

    sensor = {
        "name": address,
        "description": address,
        "encodingType": "application/pdf",
        "metadata": "-"
    }

    response = requests.post(server_url, json=sensor)
    return response.json()["@iot.id"]


def create_thing(address):
    server_url = st_server + ":" + str(st_port)
    server_url = server_url + "/v1.0/Things"

    thing = {
        "name": address,
        "description": address,
        "properties": {
            "organisation": "Geodan",
            "owner": "-"
        },
        "Locations": [
            {
                "name": "-",
                "description": "-",
                "encodingType": "application/vnd.geo+json",
                "location": {
                    "type": "Point",
                    "coordinates": [
                        4.913056,
                        52.342268
                    ]
                }
            }
        ]

    }

    response = requests.post(server_url, json=thing)
    return response.json()["@iot.id"]


def create_datastream(name, symbol, unit, o_p, thing, sensor):

    server_url = st_server + ":" + str(st_port)
    server_url = server_url + "/v1.0/Datastreams"

    datastream = {
        "name": name,
        "unitOfMeasurement": {
            "symbol": symbol,
            "name": unit,
            "definition": "http://www.qudt.org/qudt/owl/1.0.0/unit/Instances.html"
        },
        "description": name + " datastream from Crownstone",
        "observationType": "http://www.opengis.net/def/observationType/OGC-OM/2.0/OM_Measurement",
        "Thing": {"@iot.id": str(thing)},
        "ObservedProperty": {"@iot.id": str(o_p)},
        "Sensor": {"@iot.id": str(sensor)}
    }

    response = requests.post(server_url, json=datastream)
    return response.json()["@iot.id"]


def create_sensorthings_entities(message, dns):
    sensor_id = create_sensor(message[0])
    thing_id = create_thing(message[0])

    temp = create_datastream("Temp", "C", "Celcius", 50, thing_id, sensor_id)
    power = create_datastream("Power", "mW", "mW", 51, thing_id, sensor_id)
    switch = create_datastream("Switch", "%", "%", 52, thing_id, sensor_id)

    print(message[0])

    dns[message[0]] = {
        "temp": temp,
        "power": power,
        "switch": switch
    }

    pickle.dump(dns, open(device_file_path, 'wb'))


def on_connect(client, userdata, flags, rc):
    logging.info("Connected to mqtt server.")


def on_message(client, userdata, msg):
    message = msg.payload.decode("utf-8").split("|")

    address = message[0]

    if address not in dns:
        create_sensorthings_entities(message, dns)

    power = dns[address]["power"]
    switch = dns[address]["switch"]
    temperature = dns[address]["temp"]

    post_to_gost_datastream(power, int(message[1]))
    post_to_gost_datastream(switch, int(message[2]))
    post_to_gost_datastream(temperature, int(message[3]))


# device config file path
device_file_path = '/dns/dns.pckl'

# Load config from registrar
try:
    with open(device_file_path, 'rb') as cache:
        try:
            dns = pickle.load(cache)
        except EOFError as _:
            dns = {}
except FileNotFoundError as _:
    dns = {}

# The mqtt server
mqtt_server = os.environ.get("mqtt_host")

# The port of the mqtt server
mqtt_port = int(os.environ.get("mqtt_port"))

# The sensorthings server
st_server = "http://" + os.environ.get("sensorthings_host")

# The port of the sensorthings server
st_port = int(os.environ.get("sensorthings_port"))

# Keepalive timout
keep_alive = 60

error_timeout = 60  # in seconds, so one minute

format = '[%(asctime)-15s] %(message)s'
filename = 'parser.log'
# logging.basicConfig(filename=filename, level=logging.DEBUG, format=format)
logging.basicConfig(stream=sys.stdout, level=logging.DEBUG)

client = mqtt.Client(client_id="crownstone-middleware")
client.on_connect = on_connect
client.on_message = on_message

# Run forever!
while True:
    try:
        client.connect(mqtt_server, mqtt_port, keep_alive)
        logging.info("Connected to " +
                     mqtt_server + " on port " +
                     str(mqtt_port))
        client.subscribe("GostStone")

        logging.info("Initialized")

    except Exception as e:
        logging.error(str(e))
        time.sleep(error_timeout)
        continue

    client.loop_forever()
