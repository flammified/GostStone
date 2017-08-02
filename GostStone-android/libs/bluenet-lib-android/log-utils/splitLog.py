#!/usr/bin/python

import sys
import datetime

scans = []
scanStops = []
scanStarts = []
phoneInteractive = []
appForeGround = []

def usage():
	print "Usage: " + sys.argv[0] + " <logfile>"
	sys.exit(1)

if (len(sys.argv) < 2):
	usage()
argn = 1

logfile = open(sys.argv[argn], "r")
fileName = sys.argv[argn]
fileBaseName = fileName.rsplit(".", 1)[0]
fileExtension = fileName.rsplit(".", 1)[1]

outfiles = {}

dates = set()
for line in logfile:
	columns = line.rstrip().split(' ')
	timestamp = int(columns[0]) / 1000
	date = datetime.datetime.fromtimestamp(timestamp).strftime('%Y-%m-%d')
	if (date not in outfiles):
		fileNameDate = fileBaseName + "-" + date + "." + fileExtension
		outfiles[date] = open(fileNameDate, "w")
		print "New file: " + fileNameDate
	outfiles[date].write(line)

for date in outfiles:
	outfiles[date].close()

logfile.close()
