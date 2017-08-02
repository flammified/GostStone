#!/usr/bin/python

#from pylab import *
import matplotlib.pyplot as plt
import sys
import parseLog

data = parseLog.parse(sys.argv[1])
scans = data["scans"]
scanStops = data["scanStops"]
scanStarts = data["scanStarts"]
phoneInteractive = data["phoneInteractive"]
appForeGround = data["appForeGround"]

############################################
## Visualize number of scans per interval ##
############################################

# Config
buckets = 20
tStep = 250 # in ms

# Init
endTimes=range(tStep,(buckets+1)*tStep,tStep)
numScans=[]
for i in range(0,buckets+1):
	numScans.append([])

numScansTranspose=[]

# Fill up the data, count number of scans per bucket
i=0
for tStart in scanStarts:
	# Get first stopScan after current startScan
	for tStop in scanStops:
		if (tStop > tStart):
			break
	dt = tStop - tStart
	num = 0
	nums = []
	for j in range(0,buckets):
		while (i<len(scans) and scans[i][0] < tStart+endTimes[j]):
			if (scans[i][0] >= tStart ):
				num += 1
			i += 1

		numScans[j].append(num)
		nums.append(num)

	while (i<len(scans) and scans[i][0] < tStop):
		num += 1
		i += 1
	numScans[buckets].append(num)

	numScansTranspose.append(nums)

# Calculate statistics

numValidScans = 0
relativeNumScans=[]
for i in range(0,buckets):
	relativeNumScans.append([])

averageNumScans=[]
for i in range(0,buckets):
	averageNumScans.append(0)

for j in range(0,len(numScans[0])):
	max = float(numScans[buckets][j])
	if (max > 0):
		numValidScans += 1
		for i in range(0,buckets):
			relativeNumScans[i].append(float(numScans[i][j]) / max)
			averageNumScans[i] += numScans[i][j]

for i in range(0,buckets):
	averageNumScans[i] = float(averageNumScans[i]) / numValidScans
#	print "average number of scans after " + str(endTimes[i]) + "ms: " + str(averageNumScans[i])


# Plot num scans over time
#plt.plot(scanStarts, numScans[buckets], label="all")
#for i in reversed(range(0,buckets)):
#	label = str((i+1)*tStep) + "ms"
#	plt.plot(scanStarts, numScans[i], label=label)
#plt.legend()
#plt.grid(True)

plt.plot(endTimes, numScans[0:buckets])
plt.xlabel('time after startScan (ms)')
plt.ylabel('number of scans')

# Plot average num scans per bucket
plt.figure()
plt.plot(endTimes, averageNumScans)
plt.grid(True)
plt.xlabel('time after startScan (ms)')
plt.ylabel('average number of scans')

plt.show()
