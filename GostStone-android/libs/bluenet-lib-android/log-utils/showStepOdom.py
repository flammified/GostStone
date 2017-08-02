#!/usr/bin/python

#from pylab import *
import matplotlib.pyplot as plt
import numpy as np
import sys
import parseLog

data = parseLog.parse(sys.argv[1])
steps = data["steps"]
orientations = data["orientations"]

stepSize = 0.7 # m
stepTime = 300 # ms

x=[0]
y=[0]
orientationIndex = 0
firstStep = False
prevTimestamp = 0
for stepTimestamp in steps["timestamps"]:
	if (not firstStep):
		prevTimestamp = stepTimestamp
		firstStep = True
		continue

	bestOrientationInd = 0
	while True:
		dt = stepTimestamp - orientations["timestamps"][orientationIndex]
		if (dt < stepTime/2):
			break
		bestOrientationInd = orientationIndex
		orientationIndex += 1
	print "Time since last step:%i" % (stepTimestamp - prevTimestamp)
	print "stepTimestamp=%i orientationTimestamp=%i dt=%i" % (stepTimestamp, orientations["timestamps"][bestOrientationInd], stepTimestamp- orientations["timestamps"][bestOrientationInd])
	x.append(x[-1] + stepSize * np.cos(orientations["azimuth"][bestOrientationInd]))
	y.append(y[-1] + stepSize * np.sin(orientations["azimuth"][bestOrientationInd]))
	prevTimestamp = stepTimestamp

plt.figure()
plt.plot(x,y)

# t=[]
# a=[]
# for orientation in orientations:
# 	t.append(orientation[0])
# 	a.append(orientation[1])
# plt.figure()
# plt.plot(t,a)


plt.show()