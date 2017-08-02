#!/usr/bin/python

import sys

scans = []
scanStops = []
scanStarts = []
phoneInteractive = []
appForeGround = []

def usage():
	print "Usage: " + sys.argv[0] + " [not] <interactive|foreground> <logfile>"
	sys.exit(1)

if (len(sys.argv) < 2):
	usage()
argn = 1
filterReverse = False
if (sys.argv[argn] == "not"):
	filterReverse = True
	argn += 1

filter = sys.argv[argn]
argn += 1
if (filter != "interactive" and filter != "foreground"):
	usage()

logfile = open(sys.argv[argn], "r")
outfile = sys.argv[argn]
if (filterReverse):
	outfile = open(outfile.rsplit(".", 1)[0] + "-not-" + filter + "." + outfile.rsplit(".", 1)[1], "w")
else:
	outfile = open(outfile.rsplit(".", 1)[0] + "-" + filter + "." + outfile.rsplit(".", 1)[1], "w")

# TODO: make sure we don't miss the stopScan

passFilter = False
if (filter == "interactive"):
	passFilter = True
scanning = False
scanBuf = []
for line in logfile:
	columns = line.rstrip().split(' ')
	if (columns[1] == "phoneInteractive" and filter == "interactive"):
		if (columns[2] == "1"):
			passFilter = not filterReverse
			scanBuf = []
		else:
			passFilter = filterReverse
			scanBuf = []
	elif (columns[1] == "appForeGround" and filter == "foreground"):
		passFilter = not filterReverse
		scanBuf = []
	elif (columns[1] == "appBackGround" and filter == "foreground"):
		passFilter = filterReverse
		scanBuf = []

	if (passFilter):
		if (columns[1] == "onScan" and scanning == True):
#		if (columns[1] == "onScan"):
			scanBuf.append(line)
#			outfile.write(line)
		elif (columns[1] == "startScan"):
			scanning = True
#			outfile.write(line)
#			scanBuf = []
			scanBuf.append(line)
		elif (columns[1] == "stopScan"):
#			if (scanning == True):
			for scanLine in scanBuf:
				outfile.write(scanLine)
			scanBuf = []
			scanning = False
			outfile.write(line)

outfile.close()
logfile.close()
