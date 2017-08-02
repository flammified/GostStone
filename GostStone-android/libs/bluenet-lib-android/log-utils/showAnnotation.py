#!/usr/bin/python

#from pylab import *
import matplotlib.pyplot as plt
#import matplotlib.cbook as cbook
import sys
import parseLog

data = parseLog.parse(sys.argv[1])
annotations = data["annotations"]


########################################
## Show annotated positions over time ##
########################################

floors = set()
for annotation in annotations:
	floor = annotation["floor"]
	floors.add(floor)

paths = {}
for floor in floors:
	paths[floor] = [[],[],[]] # each floor has 3 lists: time, x, y

for annotation in annotations:
	time = annotation["timestamps"]
	floor = annotation["floor"]
	x = annotation["x"]
	y = annotation["y"]
	paths[floor][0].append(time)
	paths[floor][1].append(x)
	paths[floor][2].append(y)


# Plot position over time
for floor in floors:
	plt.figure()
	image = plt.imread(floor + ".png")
	plt.imshow(image)
	plt.grid(True)
	plt.plot(paths[floor][1], paths[floor][2])
	plt.title(floor)


plt.show()
