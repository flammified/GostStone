#!/usr/bin/python

#from pylab import *
import matplotlib.pyplot as plt
import sys
import datetime

data = parseLog.parse(sys.argv[1])
scans = data["scans"]
scanStops = data["scanStops"]
scanStarts = data["scanStarts"]
phoneInteractive = data["phoneInteractive"]
appForeGround = data["appForeGround"]

beaconNames = {
	"F8:27:73:28:DA:FE":"hans",
	"F1:C1:B8:AC:03:CD":"chloe",
	"E1:89:95:C1:06:04":"meeting",
	"DB:26:1F:D9:FA:5E":"old office chloe",
	"F0:20:A1:2C:57:D4":"hallway 0",
	"C9:92:1A:78:F4:81":"trash room",
	"CF:5E:84:EF:00:91":"lunch room",
	"ED:F5:F8:E3:6A:F6":"kitchen",
	"EB:4D:30:14:6D:C1":"server room",
	"F4:A2:89:23:53:92":"basement",
	"EB:82:34:DA:EE:0B":"balcony",
	"ED:AF:F3:7E:E1:47":"jan geert",
	"C6:27:A8:D7:D4:C7":"allert",
	"E0:31:D7:C5:CA:FF":"hallway 1",
	"D7:59:D6:BD:2A:5A":"dobots software",
	"DE:41:8E:2F:58:85":"interns",
	"D7:D5:51:82:49:43":"peet",
	"FD:CB:99:58:0B:88":"hallway 2",
	"EF:36:60:78:1F:1D":"dobots hardware",
	"E8:B7:37:29:F4:77":"hallway 3",
	"C5:25:3F:5E:92:6F":"small multi purpose room",
	"EA:A6:ED:8A:13:8E":"billiard table",
	"C5:71:64:3A:15:74":"proto table",
}

#############################################
## Visualize rssi of each stone over time ##
#############################################

rssis = {} # averaged rssi for each interval, per ble address
numScans = {} # number of scans for each interval, per ble address
addresses = [] # all seen addresses
for scan in scans:
	address = scan[1]
	if (address not in rssis):
		rssis[address] = []
		numScans[address] = []
		addresses.append(address)


# Fill up the data, count number of scans per bucket
scanStartsFormatted = []
scanStartsHours = []
i=0
t=0
for tStart in scanStarts:
	scanStartsFormatted.append(datetime.datetime.fromtimestamp(tStart/1000).strftime('%Y-%m-%d %H:%M:%S'))
	scanStartsHours.append(float(tStart - scanStarts[0])/1000/3600)
	

	# Get first stopScan after current startScan
	for tStop in scanStops:
		if (tStop > tStart):
			break
	dt = tStop - tStart

	for address in addresses:
		numScans[address].append(0)
		rssis[address].append(0)

	while (i<len(scans) and scans[i][0] < tStop):
		address = scans[i][1]
		numScans[address][-1] += 1
		rssi = scans[i][2]
		rssis[address][-1] += rssi
		i += 1

	for address in addresses:
		if (numScans[address][-1] == 0):
			rssis[address][-1] = -100
		else:
			rssis[address][-1] = float(rssis[address][-1]) / numScans[address][-1]

# Plot rssi over time
colors = ['b', 'g', 'r', 'c', 'm', 'y', 'k']
lineStyles = ['-', '--', '-.', ':']
plt.figure()
color=0
style=0
for address in addresses:
	label = address
	if (address in beaconNames):
		label = beaconNames[address]
	plt.plot(scanStartsHours, rssis[address], colors[color] + lineStyles[style], label=label)
	color += 1
	if (color >= len(colors)):
		color = 0
		style = (style+1) % len(lineStyles)

plt.grid(True)
plt.legend()
plt.xlabel('time (h)')
plt.ylabel('rssi')

plt.show()
