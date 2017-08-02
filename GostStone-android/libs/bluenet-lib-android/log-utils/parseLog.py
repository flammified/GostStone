def parse(fileName):
	scans =            {"timestamps":[], "timestampsNs":[], "address":[], "rssi":[], "calibratedRssi":[],}
	scanStops =        {"timestamps":[], "timestampsNs":[],}
	scanStarts =       {"timestamps":[], "timestampsNs":[],}
	phoneInteractive = {"timestamps":[], "timestampsNs":[], "isActive":[],}
	appForeGround =    {"timestamps":[], "timestampsNs":[], "isForeGround":[],}
	annotations =      {"timestamps":[], "timestampsNs":[], "floor":[], "x":[], "y":[],}
	orientations =     {"timestamps":[], "timestampsNs":[], "azimuth":[], "pitch":[], "roll":[],}
	steps =            {"timestamps":[], "timestampsNs":[], "numSteps":[]}
	gyro =             {"timestamps":[], "timestampsNs":[], "x":[], "y":[], "z":[],}
	accelero =         {"timestamps":[], "timestampsNs":[], "x":[], "y":[], "z":[],}
	gravity =          {"timestamps":[], "timestampsNs":[], "x":[], "y":[], "z":[],}

	scanning = False
	logfile = open(fileName, "r")
	for line in logfile:
		columns = line.rstrip().split(' ')
		if (columns[1] == "onScan"):
			scans["timestamps"].append(int(columns[0]))
			# scans["timestampsNs"].append(int(columns[1]))
			scans["address"].append(columns[2])
			scans["rssi"].append(int(columns[3]))
			scans["calibratedRssi"].append(int(columns[4]))

		elif (columns[1] == "startScan"):
			if (scanning == True):
				print "Error at " + columns[0] + " ?"
			scanning = True
			scanStarts["timestamps"].append(int(columns[0]))
			# scanStarts["timestampsNs"].append(int(columns[1]))

		elif (columns[1] == "stopScan"):
			scanning = False
			scanStops["timestamps"].append(int(columns[0]))
			# scanStops["timestampsNs"].append(int(columns[1]))

		elif (columns[1] == "phoneInteractive"):
			phoneInteractive["timestamps"].append(int(columns[0]))
			# phoneInteractive["timestampsNs"].append(int(columns[1]))
			phoneInteractive["isActive"].append(int(columns[2]))

		elif (columns[1] == "appForeGround"):
			appForeGround["timestamps"].append(int(columns[0]))
			# appForeGround["timestampsNs"].append(int(columns[1]))
			appForeGround["isForeGround"].append(1)

		elif (columns[1] == "appBackGround"):
			appForeGround["timestamps"].append(int(columns[0]))
			# appForeGround["timestampsNs"].append(int(columns[1]))
			appForeGround["isForeGround"].append(0)

		elif (columns[1] == "setLocation"):
			annotations["timestamps"].append(int(columns[0]))
			# annotations["timestampsNs"].append(int(columns[1]))
			annotations["floor"].append(columns[2])
			annotations["x"].append(float(columns[3]))
			annotations["y"].append(float(columns[4]))

		elif (columns[1] == "orientation"):
			orientations["timestamps"].append(int(columns[0]))
			# orientations["timestampsNs"].append(int(columns[1]))
			orientations["azimuth"].append(float(columns[2]))
			orientations["pitch"].append(float(columns[3]))
			orientations["roll"].append(float(columns[4]))

		elif (columns[1] == "stepCount"):
			steps["timestamps"].append(int(columns[0]))
			# steps["timestampsNs"].append(int(columns[1]))
			steps["numSteps"].append(int(float(columns[2])))

		elif (columns[1] == "accelero"):
			accelero["timestamps"].append(int(columns[0]))
			# accelero["timestampsNs"].append(int(columns[1]))
			accelero["x"].append(float(columns[2]))
			accelero["y"].append(float(columns[3]))
			accelero["z"].append(float(columns[4]))

		elif (columns[1] == "gyro"):
			gyro["timestamps"].append(int(columns[0]))
			# gyro["timestampsNs"].append(int(columns[1]))
			gyro["x"].append(float(columns[2]))
			gyro["y"].append(float(columns[3]))
			gyro["z"].append(float(columns[4]))

		elif (columns[1] == "gravity"):
			gravity["timestamps"].append(int(columns[0]))
			# gravity["timestampsNs"].append(int(columns[1]))
			gravity["x"].append(float(columns[2]))
			gravity["y"].append(float(columns[3]))
			gravity["z"].append(float(columns[4]))


	#	print line.rstrip()
	#	for column in line.rstrip().split(' '):
	#		print column
	logfile.close()
	data = {"scans": scans,
			"scanStarts": scanStarts,
			"scanStops": scanStops,
			"phoneInteractive": phoneInteractive,
			"appForeGround": appForeGround,
			"annotations": annotations,
			"orientations": orientations,
			"steps": steps,
			"accelero": accelero,
			"gyro": gyro,
			"gravity": gravity,
			}
	return data