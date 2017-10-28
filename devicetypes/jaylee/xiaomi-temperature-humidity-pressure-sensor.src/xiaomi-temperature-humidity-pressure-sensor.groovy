metadata {
	definition (name: "Xiaomi Temperature Humidity Pressure Sensor", namespace: "jaylee", author: "jaylee") {
		capability "Temperature Measurement"
		capability "Relative Humidity Measurement"
		capability "Sensor"
        capability "Battery"
        capability "Refresh"
        
        attribute "lastCheckin", "String"
        attribute "pressure", "String"
        
		fingerprint profileId: "0104", deviceId: "0302", inClusters: "0000,0001,0003,0009,0402,0405"
        fingerprint profileId: "0104", deviceId: "5F01", inClusters: "0000,0003,FFFF,0402,0403,0405", outClusters: "0000,0004,FFFF", deviceJoinName: "Xiaomi-Temp"
	}

	// simulator metadata
	simulator {
        status "pressure": "read attr - raw: 314F0104031C000029F203140028FF1000297627, dni: 314F, endpoint: 01, cluster: 0403, size: 1C, attrId: 0000, encoding: 29, value: 2776290010ff28001403f2"
		status "other attr": "read attr - raw: 314F010000200500420C6C756D692E77656174686572, dni:314F, endpoint:01, cluster:0000, size:20, attrId:0005, encoding:42, value:6C756D692E77656174686572"
		status "battery": "catchall: 0104 0000 01 01 0100 00 314F 00 00 0000 0A 01 01FF42250121670C0421A81305210A00062401000200006429310A6521A01A662BA98A01000A210000"
        				   
        
        for (int i = 0; i <= 100; i += 10) {
			status "${i}C": "temperature: $i"
		}

		for (int i = 0; i <= 100; i += 10) {
			status "${i}%": "humidity: ${i}%"
		}
	}
    
	preferences {
		section {
			input title: "Temperature Offset", description: "This feature allows you to correct any temperature variations by selecting an offset. Ex: If your sensor consistently reports a temp that's 5 degrees too warm, you'd enter '-5'. If 3 degrees too cold, enter '+3'. Please note, any changes will take effect only on the NEXT temperature change.", displayDuringSetup: false, type: "paragraph", element: "paragraph"
			input "tempOffset", "number", title: "Degrees", description: "Adjust temperature by this many degrees", range: "*..*", displayDuringSetup: false
		}
    }
    
	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name:"temperature", type:"generic", width:6, height:4) {
			tileAttribute("device.temperature", key:"PRIMARY_CONTROL"){
			    attributeState("default", label:'${currentValue}°',
                backgroundColors:[
					[value: 0, color: "#153591"],
					[value: 6, color: "#1e9cbb"],
					[value: 15, color: "#90d2a7"],
					[value: 23, color: "#44b621"],
					[value: 28, color: "#f1d801"],
					[value: 35, color: "#d04e00"],
					[value: 36, color: "#bc2323"],
                    [value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"]                                    
				]
			)
            }
            tileAttribute("device.lastCheckin", key: "SECONDARY_CONTROL") {
    			attributeState("default", label:'Last Update: ${currentValue}', icon: "st.Health & Wellness.health9")
			}
		}
       
        standardTile("humidity", "device.humidity", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'${currentValue}%', icon:"st.Weather.weather12"
		}
                
        valueTile("pressure", "device.pressure", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "default", label:'${currentValue} mbar', unit:""
		}
        
        valueTile("battery", "device.battery", decoration: "flat", inactiveLabel: false, width: 2, height: 2) {
			state "default", label:'${currentValue}% battery', unit:""
		}
        
		valueTile("temperature2", "device.temperature", decoration: "flat", inactiveLabel: false) {
			state "default", label:'${currentValue}°', icon: "st.Weather.weather2",
                backgroundColors:[
					[value: 0, color: "#153591"],
					[value: 6, color: "#1e9cbb"],
					[value: 15, color: "#90d2a7"],
					[value: 23, color: "#44b621"],
					[value: 28, color: "#f1d801"],
					[value: 35, color: "#d04e00"],
					[value: 36, color: "#bc2323"], 
                    [value: 31, color: "#153591"],
					[value: 44, color: "#1e9cbb"],
					[value: 59, color: "#90d2a7"],
					[value: 74, color: "#44b621"],
					[value: 84, color: "#f1d801"],
					[value: 95, color: "#d04e00"],
					[value: 96, color: "#bc2323"],                          
				]
        }

		standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", action:"refresh.refresh", icon:"st.secondary.refresh"
        }
            
		main(["temperature2"])
		details(["temperature", "battery", "humidity", "refresh", "pressure"])
	}
}

def parseHumidity(String humidityString){
	def pct = (humidityString - "%").trim()

    if (pct.isNumber()) {
        return Math.round(new BigDecimal(pct)).toString()
    }
    
    null
}

def parseTemperature(String temperatureString){
	def value = temperatureString.trim() as Float 
        
    if (getTemperatureScale() == "C") {
        if (tempOffset) {
            return (Math.round(value * 10)) / 10 + tempOffset as Float
        } else {
            return (Math.round(value * 10)) / 10 as Float
        }            	
    } else {
        if (tempOffset) {
            return (Math.round(value * 90/5)) / 10 + 32 + offset as Float
        } else {
            return (Math.round(value * 90/5)) / 10 + 32 as Float
        }            	
    }        
}

def parsePressure(String pressureString) { 
	def value = pressureString.substring(pressureString.length()-4)
    def actualValue = Integer.parseInt(value, 16)
    log.debug "pressure: $actualValue mbar"
    return actualValue
}

// Parse incoming device messages to generate events
def parse(String description) {
	def map = parseDescriptionAsMap(description)
 	log.debug "zigbeeMap: $map"
    
	def now = new Date().format("yyyy MMM dd EEE HH:mm:ss", location.timeZone)
	sendEvent(name: "lastCheckin", value: now)
 
    def unit = ""
    if (map.temperature != null) {
		log.debug "map temperature $map.temperature"
        def value = parseTemperature(map.temperature)
        return createEvent(name: "temperature", value: value, unit: getTemperatureScale())
    } 
    else if (map.catchall != null) {
 		log.debug "map catchall $map.catchall"
        def value = parseCatchAllMessage(description)
  		log.debug "Battery: $value"
       return createEvent(name: "battery", value: value, unit: "%")              
	} 
    else if (map.raw != null && map.cluster == "0403") {
 		log.debug "map pressure $map.raw"
        
		def value = parsePressure(map.value)
        log.debug "Pressure: $value"
        return createEvent(name: "pressure", value: value, unit: "mbar")
	} 
    else if (map.humidity != null) {
 		log.debug "map humidity $map.humidity"
        
 		def value = parseHumidity(map.humidity)
        return createEvent(name: "humidity", value: value, unit: "%")
	}
    else {
    	log.debug "Unsupported cluster $map.cluster"
    }
    
 	return null;
   
	// log.debug "RAW: $description"
	// def name = parseName(description)
    // log.debug "Parsename: $name"
	// def value = parseValue(description)
    // log.debug "Parsevalue: $value"
	// def unit = name == "temperature" ? getTemperatureScale() : (name == "humidity" ? "%" : null)
	// def result = createEvent(name: name, value: value, unit: unit)
    // log.debug "Evencreated: $name, $value, $unit"
	// log.debug "Parse returned ${result?.descriptionText}"
    // def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    // sendEvent(name: "lastCheckin", value: now)
    // sendEvent(name: "pressure", value: "")
	// return result
}


private String parseValue(String description) {

	if (description?.startsWith("temperature: ")) {
		
        
	} else if (description?.startsWith("humidity: ")) {
		def pct = (description - "humidity: " - "%").trim()
        
		if (pct.isNumber()) {
			return Math.round(new BigDecimal(pct)).toString()
		}
	} else if (description?.startsWith("catchall: ")) {
		return parseCatchAllMessage(description)
	} else {
    log.debug "unknown: $description"
    sendEvent(name: "unknown", value: description)
    }
	null
}

private String parseCatchAllMessage(String description) {
	def result = '--'
	def cluster = zigbee.parse(description)
	log.debug "$cluster"
	if (cluster) {
		switch(cluster.clusterId) {
			case 0x0000:
			result = getBatteryResult(cluster.data.get(6)) 
 			break
		}
	}

	return result
}


private String getBatteryResult(rawValue) {
	def linkText = getLinkText(device)

	def result =  '--'
    def maxBatt = 100
    def battLevel = Math.round(rawValue * 100 / 255)
	
	if (battLevel > maxBatt) {
		battLevel = maxBatt
    }

	return battLevel
}

def refresh() {
	log.debug "refresh called"
	def refreshCmds = [
		"st rattr 0x${device.deviceNetworkId} 1 1 0x00", "delay 2000",
		"st rattr 0x${device.deviceNetworkId} 1 1 0x20", "delay 2000"
	]

	return refreshCmds + enrollResponse()
}

def configure() {
	// Device-Watch allows 2 check-in misses from device + ping (plus 1 min lag time)
	// enrolls with default periodic reporting until newer 5 min interval is confirmed
	sendEvent(name: "checkInterval", value: 2 * 60 * 60 + 1 * 60, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])

	// temperature minReportTime 30 seconds, maxReportTime 5 min. Reporting interval if no activity
	// battery minReport 30 seconds, maxReportTime 6 hrs by default
	return refresh() + zigbee.batteryConfig() + zigbee.temperatureConfig(30, 900) // send refresh cmds as part of config
}

def enrollResponse() {
	log.debug "Sending enroll response"
	String zigbeeEui = swapEndianHex(device.hub.zigbeeEui)
	[
		//Resending the CIE in case the enroll request is sent before CIE is written
		"zcl global write 0x500 0x10 0xf0 {${zigbeeEui}}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 ${endpointId}", "delay 2000",
		//Enroll Response
		"raw 0x500 {01 23 00 00 00}", "delay 200",
		"send 0x${device.deviceNetworkId} 1 1", "delay 2000"
	]
}

private String swapEndianHex(String hex) {
	reverseArray(hex.decodeHex()).encodeHex()
}

private byte[] reverseArray(byte[] array) {
	int i = 0;
	int j = array.length - 1;
	byte tmp;
	while (j > i) {
		tmp = array[j];
		array[j] = array[i];
		array[i] = tmp;
		j--;
		i++;
	}
	return array
}

def parseDescriptionAsMap(description) {
    (description - "read attr - ").split(",").inject([:]) { map, param ->
        def nameAndValue = param.split(":")
        if(nameAndValue.length == 2){
	        map += [(nameAndValue[0].trim()):nameAndValue[1].trim()]
        }else{
        	map += []
        }
    }
}