/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
	definition (name: "Smoke Sensor FGSS-001", namespace: "Fibaro", author: "Ross Smith") {
		capability "Sensor"
		capability "Battery"
		capability "Configuration"
        capability "Refresh"
        capability "Temperature Measurement" //attributes: temperature
        capability "Smoke Detector" //attributes: smoke ("detected","clear","tested")
        attribute "heatAlarm", "enum", ["overheat detected", "clear", "rapid temperature rise", "underheat detected"]

		fingerprint deviceId: "0xA1"
		fingerprint deviceId: "0x21"
		fingerprint deviceId: "0x20"
		fingerprint deviceId: "0x07"
	}

	simulator {
		status "active": "command: 3003, payload: FF"
		status "inactive": "command: 3003, payload: 00"
	}

	tiles (scale: 2){
        multiAttributeTile(name:"smoke", type: "lighting", width: 6, height: 4){
            tileAttribute ("device.smoke", key: "PRIMARY_CONTROL") {
                attributeState("clear", label:"CLEAR", icon:"st.alarm.smoke.clear", backgroundColor:"#ffffff")
                attributeState("detected", label:"SMOKE", icon:"st.alarm.smoke.smoke", backgroundColor:"#e86d13")
                attributeState("tested", label:"TEST", icon:"st.alarm.smoke.test", backgroundColor:"#e86d13")
                attributeState("replacement required", label:"REPLACE", icon:"st.alarm.smoke.test", backgroundColor:"#FFFF66")
                attributeState("unknown", label:"UNKNOWN", icon:"st.alarm.smoke.test", backgroundColor:"#ffffff")
            }
            tileAttribute ("device.battery", key: "SECONDARY_CONTROL") {
                attributeState "battery", label:'Battery: ${currentValue}%', unit:""
            }
        }
        valueTile("temperature", "device.temperature", height: 2, width: 2, inactiveLabel: false, decoration: "flat") {
            state "temperature", label:'${currentValue}°', unit:"C"
        }
        standardTile("refresh", "device.switch", height: 2, width: 2, inactiveLabel: false, decoration: "flat") {
			state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
		} 

		main "smoke"
		details(["smoke", "temperature", "refresh"])
	}
}

def parse(String description) {
	log.debug "Parse: ${description}"
	def result = []
	if (description.startsWith("Err")) {
	    result = createEvent(descriptionText:description, displayed:true)
	} else {
		def cmd = zwave.parse(description, [0x20: 1, 0x30: 1, 0x31: 5, 0x32: 3, 0x80: 1, 0x84: 1, 0x71: 1, 0x9C: 1])
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	return result
}

def refresh() {
    log.trace "Refreshing"
    configure()
}


def updated() {
	log.trace "Updating"
    configure()
	response(zwave.wakeUpV1.wakeUpNoMoreInformation())
}

def smokeAlarmEvent(value) {
    log.debug "smokeAlarmEvent(value): $value"
    def map = [name: "smoke"]
    if (value == 1 || value == 2) {
        map.value = "detected"
        map.descriptionText = "$device.displayName detected smoke"
    } else if (value == 0) {
        map.value = "clear"
        map.descriptionText = "$device.displayName is clear (no smoke)"
    } else if (value == 3) {
        map.value = "tested"
        map.descriptionText = "$device.displayName smoke alarm test"
    } else if (value == 4) {
        map.value = "replacement required"
        map.descriptionText = "$device.displayName replacement required"
    } else {
        map.value = "unknown"
        map.descriptionText = "$device.displayName unknown event"
    }
    createEvent(map)
}

def heatAlarmEvent(value) {
    log.debug "heatAlarmEvent(value): $value"
    def map = [name: "heatAlarm"]
    if (value == 1 || value == 2) {
        map.value = "overheat detected"
        map.descriptionText = "$device.displayName overheat detected"
    } else if (value == 0) {
        map.value = "clear"
        map.descriptionText = "$device.displayName heat alarm cleared (no overheat)"
    } else if (value == 3 || value == 4) {
        map.value = "rapid temperature rise"
        map.descriptionText = "$device.displayName rapid temperature rise"
    } else if (value == 5 || value == 6) {
        map.value = "underheat detected"
        map.descriptionText = "$device.displayName underheat detected"
    } else {
        map.value = "unknown"
        map.descriptionText = "$device.displayName unknown event"
    }
    createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.versionv1.VersionReport cmd) {
    log.info "Executing zwaveEvent 86 (VersionV1): 12 (VersionReport) with cmd: $cmd"
    def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
    updateDataValue("fw", fw)
    def text = "$device.displayName: firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
    createEvent(descriptionText: text, isStateChange: false)
}

def zwaveEvent(physicalgraph.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    log.info "Executing zwaveEvent 72 (ManufacturerSpecificV2) : 05 (ManufacturerSpecificReport) with cmd: $cmd"
    log.debug "manufacturerId:   ${cmd.manufacturerId}"
    log.debug "manufacturerName: ${cmd.manufacturerName}"
    log.debug "productId:        ${cmd.productId}"
    log.debug "productTypeId:    ${cmd.productTypeId}"
    def result = []

    def msr = String.format("%04X-%04X-%04X", cmd.manufacturerId, cmd.productTypeId, cmd.productId)
    updateDataValue("MSR", msr)
    log.debug "After device is securely joined, send commands to update tiles"
    result << zwave.batteryV1.batteryGet()
    result << zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 0x01)
    result << zwave.wakeUpV1.wakeUpNoMoreInformation()

    [[descriptionText:"${device.displayName} MSR report"], response(commands(result, 5000))]
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicReport cmd)
{
	log.trace "BasicReport: ${cmd}"
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.basicv1.BasicSet cmd)
{
	log.trace "BasicSet: ${cmd}"
	sensorValueEvent(cmd.value)
}

def zwaveEvent(physicalgraph.zwave.commands.sensorbinaryv1.SensorBinaryReport cmd)
{
	log.trace "SensorBinaryReport: ${cmd}"
	sensorValueEvent(cmd.sensorValue)
}

def zwaveEvent(physicalgraph.zwave.commands.alarmv1.AlarmReport cmd)
{
	log.trace "AlarmReport: ${cmd}"
	sensorValueEvent(cmd.alarmLevel)
}

def zwaveEvent(physicalgraph.zwave.commands.sensoralarmv1.SensorAlarmReport cmd)
{
	log.trace "SensorAlarmReport: ${cmd}"
	sensorValueEvent(cmd.sensorState)
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
    log.trace "Executing zwaveEvent 31 (SensorMultilevelV5): 05 (SensorMultilevelReport) with cmd: $cmd"
	def map = [ displayed: true, value: cmd.scaledSensorValue.toString() ]
	switch (cmd.sensorType) {
        case 1:
            map.name = "temperature"
            def cmdScale = cmd.scale == 1 ? "F" : "C"
            map.value = convertTemperatureIfNeeded(cmd.scaledSensorValue, cmdScale, cmd.precision)
            map.unit = getTemperatureScale()
            break
        default:
            map.descriptionText = cmd.toString()
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd)
{
	log.trace "WakeUpNotification: ${cmd}"
	def result = []
	result << new physicalgraph.device.HubAction(zwave.wakeUpV1.wakeUpNoMoreInformation().format())
	result << createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)
	result
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.trace "${cmd}"
	def map = [ name: "battery", unit: "%" ]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
        map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
	createEvent(map)
}

def zwaveEvent(physicalgraph.zwave.commands.crc16encapv1.Crc16Encap cmd)
{
	log.trace "Crc16Encap: ${cmd}"
	def versions = [0x20: 1, 0x30: 1, 0x31: 5, 0x32: 3, 0x80: 1, 0x84: 1, 0x71: 1, 0x9C: 1]
	// def encapsulatedCommand = cmd.encapsulatedCommand(versions)
	def version = versions[cmd.commandClass as Integer]
	def ccObj = version ? zwave.commandClass(cmd.commandClass, version) : zwave.commandClass(cmd.commandClass)
	def encapsulatedCommand = ccObj?.command(cmd.command)?.parse(cmd.data)
	if (encapsulatedCommand) {
		return zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.trace "Command: ${cmd}"
	def event = [ displayed: false ]
	event.linkText = device.label ?: device.name
	event.descriptionText = "$event.linkText: $cmd"
	createEvent(event)
}


def configure() {
	log.trace "Configure"
    def request = []

        //1. configure wakeup interval : available: 0, 4200s-65535s, device default 21600s(6hr). Changed it to an ten minutes.
        request += zwave.wakeUpV1.wakeUpIntervalSet(seconds:600, nodeid:zwaveHubNodeId)
        
        // Turn off the ZWave Range Test
        request += zwave.configurationV1.configurationSet(parameterNumber: 80, size: 1, scaledConfigurationValue: 0)
        
        //11. get battery level when device is paired
        request += zwave.batteryV1.batteryGet()

        //12. get temperature reading from device
        request += zwave.sensorMultilevelV5.sensorMultilevelGet(sensorType: 0x01)

        commands(request) + ["delay 10000", zwave.wakeUpV1.wakeUpNoMoreInformation().format()]
}

private commands(commands, delay=200) {
    delayBetween(commands.collect{ command(it) }, delay)
}

private command(physicalgraph.zwave.Command cmd) {
    cmd.format()
}