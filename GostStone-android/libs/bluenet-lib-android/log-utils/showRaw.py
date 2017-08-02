#!/usr/bin/python

#from pylab import *
import matplotlib.pyplot as plt
import numpy as np
import scipy.signal as signal
import sys
import parseLog

data = parseLog.parse(sys.argv[1])
accelero = data["accelero"]
gyro = data["gyro"]
gravity = data["gravity"]
steps = data["steps"]

# plt.figure()
# plt.plot(accelero["timestamps"], accelero["x"])
# plt.plot(accelero["timestamps"], accelero["y"])
# plt.plot(accelero["timestamps"], accelero["z"])
# plt.plot(steps["timestamps"], [3]*len(steps["timestamps"]), "^")
# plt.title("accelero")


# plt.figure()
# plt.plot(gravity["timestamps"], gravity["x"])
# plt.plot(gravity["timestamps"], gravity["y"])
# plt.plot(gravity["timestamps"], gravity["z"])
# plt.title("gravity")


# plt.figure()
# plt.plot(gyro["timestamps"], gyro["x"])
# plt.plot(gyro["timestamps"], gyro["y"])
# plt.plot(gyro["timestamps"], gyro["z"])
# plt.plot(steps["timestamps"], [0]*len(steps["timestamps"]), "^")
# plt.title("gyro")


# plt.figure()
# plt.plot(steps["timestamps"], steps["numSteps"])
# plt.title("steps")


# dtAcc = [0] * (len(accelero["timestamps"]) - 1)
# for i in range(0, len(dtAcc)):
# 	dtAcc[i] = accelero["timestamps"][i + 1] - accelero["timestamps"][i]
# dtGrav = [0] * (len(gravity["timestamps"]) - 1)
# for i in range(0, len(dtGrav)):
# 	dtGrav[i] = gravity["timestamps"][i + 1] - gravity["timestamps"][i]
# plt.figure()
# plt.plot(dtAcc)
# plt.plot(dtGrav)
# plt.title("dt (ms)")


# Default timestamps to use
timestamps = accelero["timestamps"]

# Subtract first timestamp from timestamps
startTimestamp = timestamps[0]
for i in range(0, len(timestamps)):
	timestamps[i] -= startTimestamp
for i in range(0, len(gravity["timestamps"])):
	gravity["timestamps"][i] -= startTimestamp
for i in range(0, len(steps["timestamps"])):
	steps["timestamps"][i] -= startTimestamp
for i in range(0, len(gyro["timestamps"])):
	gyro["timestamps"][i] -= startTimestamp


# Interpolate gravity, so that we get similar timestamps to accelero data
gravX = np.interp(timestamps, gravity["timestamps"], gravity["x"])
gravY = np.interp(timestamps, gravity["timestamps"], gravity["y"])
gravZ = np.interp(timestamps, gravity["timestamps"], gravity["z"])
gravity["x"] = gravX
gravity["y"] = gravY
gravity["z"] = gravZ
gravity["timestamps"] = timestamps


# Sum acceleration
accSum = [0]*len(timestamps)
for i in range(0, len(timestamps)):
	for ax in ["x", "y", "z"]:
		accSum[i] += accelero[ax][i]**2
	accSum[i] = np.sqrt(accSum[i]) - 9.81


# Subtract gravity from accelero
for ax in ["x", "y", "z"]:
	for i in range(0, len(accelero[ax])):
		accelero[ax][i] -= gravity[ax][i]


# plt.figure()
# plt.plot(timestamps, accelero["x"])
# plt.plot(timestamps, accelero["y"])
# plt.plot(timestamps, accelero["z"])
# plt.plot(steps["timestamps"], [3]*len(steps["timestamps"]), "^")
# plt.title("accelero - gravity")


# Project accelero on gravity vector
accProj = [0]*len(timestamps)
for i in range(0, len(timestamps)):
	accVec = [accelero["x"][i], accelero["y"][i], accelero["z"][i]]
	gravVec = [gravity["x"][i], gravity["y"][i], gravity["z"][i]]
	accProj[i] = 1.0 * np.dot(accVec, gravVec) / np.linalg.norm(gravVec)
# plt.figure()
# plt.plot(timestamps, accProj)
# plt.title("accelero-gravity projected on minus gravity vector")


# Low pass filter
duration = timestamps[-1] - timestamps[0]
sampleRate = 1000.0 * len(timestamps) / duration # In Hz
nyq = 0.5 * sampleRate
print "sampleRate: %fHz" % (sampleRate)
cutoff = 7.0 / sampleRate
print "cutoff:", cutoff

numtaps = int(sampleRate)+1 # Number of samples to use for "averaging"
print "numtaps=", numtaps
#h = signal.firwin(numtaps=numtaps, cutoff=cutoff) # hamming window
#accProjLowPass = signal.lfilter(h, 1.0, accProj)

order = 2
numerator, denominator = signal.butter(2, cutoff, btype="lowpass")
accProjLowPass = signal.lfilter(numerator, denominator, accProj)

lowPassTimestamps = [0]*len(timestamps)
for i in range(0, len(timestamps)):
	#timeShift = (numtaps/2 / sampleRate) * 1000
#	timeShift = (order / sampleRate) * 1000
	timeShift = 0
	lowPassTimestamps[i] = timestamps[i] - timeShift
print "timeShift: %fs" % (timeShift)

# modMovingAvg = [0.0]*len(timestamps)
# modMovingAvg[0] = accProj[0]
# for i in range(1, len(timestamps)):
# 	modMovingAvg[i] = (i*modMovingAvg[i-1] + accProj[i])/i


plt.figure()
plt.plot(lowPassTimestamps, accProjLowPass, "-*")
plt.plot(timestamps, accProj, "-*")
plt.plot(timestamps, accSum, ":*")
# plt.plot(timestamps, modMovingAvg, "-*")
plt.plot([timestamps[0], timestamps[-1]], [0, 0], "r")
plt.plot([timestamps[0], timestamps[-1]], [-1.0, -1.0], "r")
plt.plot([timestamps[0], timestamps[-1]], [1.0, 1.0], "r")
plt.title("accelero-gravity projected on minus gravity vector, low pass")

thresholdLow = -1.0
thresholdHigh = 1.0
belowLow = False
aboveZero = False
stepsDetected = []

for i in range(0, len(lowPassTimestamps)):
	acc = accProjLowPass[i]
	if (not belowLow):
		if (acc < thresholdLow):
			belowLow = True
			aboveZero = False
			continue
	else:
		if (acc > 0 and not aboveZero):
			aboveZero = True
			lastStepTimestamp = lowPassTimestamps[i]
		if (acc > thresholdHigh):
			stepsDetected.append(lastStepTimestamp)
			belowLow = False
plt.plot(stepsDetected, [0]*len(stepsDetected), "r^")


plt.show()