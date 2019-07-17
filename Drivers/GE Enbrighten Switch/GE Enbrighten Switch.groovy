/**
 *  IMPORT URL: https://raw.githubusercontent.com/Botched1/Hubitat/master/Drivers/GE%20Enbrighten%20Switch/GE%20Enbrighten%20Switch.groovy
 *
 *  GE Enbrighten Z-Wave Plus Switch
 *
 *  1.0.0 (07/16/2019) - Initial Version
 */

metadata {
	definition (name: "GE Enbrighten Z-Wave Plus Switch", namespace: "Botched1", author: "Jason Bottjen") {
		capability "Actuator"
		capability "PushableButton"
		capability "DoubleTapableButton"
		capability "Configuration"
		capability "Refresh"
		capability "Sensor"
		capability "Switch"
		capability "Light"
	}

 preferences {
        input name: "paramLED", type: "enum", title: "LED Indicator Behavior", multiple: false, options: ["0" : "LED ON When Switch OFF (default)", "1" : "LED ON When Switch ON", "2" : "LED Always OFF", "3" : "LED Always ON"], required: false, displayDuringSetup: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: false	
	    input name: "logDesc", type: "bool", title: "Enable descriptionText logging", defaultValue: true	
    }
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Parse
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def parse(String description) {
    def result = null
	if (description != "updated") {
		if (logEnable) log.debug "parse() >> zwave.parse($description)"
		def cmd = zwave.parse(description)

		if (logEnable) log.debug "cmd: $cmd"
		
		if (cmd) {
			result = zwaveEvent(cmd)
        }
	}
    if (!result) { if (logEnable) log.debug "Parse returned ${result} for $description" }
    else {if (logEnable) log.debug "Parse returned ${result}"}
	
	return result
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Z-Wave Messages
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def zwaveEvent(hubitat.zwave.commands.crc16encapv1.Crc16Encap cmd) {
	if (logEnable) log.debug "zwaveEvent(): CRC-16 Encapsulation Command received: ${cmd}"
	
	def newVersion = 1
	
	// SwitchMultilevel = 38 decimal
	// Configuration = 112 decimal
	// Manufacturer Specific = 114 decimal
	// Association = 133 decimal
	if (cmd.commandClass == 38) {newVersion = 3}
	if (cmd.commandClass == 112) {newVersion = 2}
	if (cmd.commandClass == 114) {newVersion = 2}								 
	if (cmd.commandClass == 133) {newVersion = 2}		
	
	def encapsulatedCommand = zwave.getCommand(cmd.commandClass, cmd.command, cmd.data, newVersion)
	if (encapsulatedCommand) {
       zwaveEvent(encapsulatedCommand)
   } else {
       log.warn "Unable to extract CRC16 command from ${cmd}"
   }
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug "---BASIC REPORT V1--- ${device.displayName} sent ${cmd}"
	if (logEnable) log.debug "This report does nothing in this driver, and shouldn't have been called..."
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    if (logEnable) log.debug "---BASIC SET V1--- ${device.displayName} sent ${cmd}"
	def result = []
	
	if (cmd.value == 255) {
		if (logEnable) log.debug "Double Tap Up Triggered"
		if (logDesc) log.info "$device.displayName had Doubletap up (button 1) [physical]"
		result << sendEvent([name: "doubleTapped", value: 1, descriptionText: "$device.displayName had Doubletap up (button 1) [physical]", type: "physical", isStateChange: true])
    }
	else if (cmd.value == 0) {
		if (logEnable) log.debug "Double Tap Down Triggered"
		if (logDesc) log.info "$device.displayName had Doubletap down (button 2) [physical]"
		result << sendEvent([name: "doubleTapped", value: 2, descriptionText: "$device.displayName had Doubletap down (button 2) [physical]", type: "physical", isStateChange: true])
    }

    return result
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	if (logEnable) log.debug "---ASSOCIATION REPORT V2--- ${device.displayName} sent groupingIdentifier: ${cmd.groupingIdentifier} maxNodesSupported: ${cmd.maxNodesSupported} nodeId: ${cmd.nodeId} reportsToFollow: ${cmd.reportsToFollow}"
    if (cmd.groupingIdentifier == 3) {
    	if (cmd.nodeId.contains(zwaveHubNodeId)) {
        	sendEvent(name: "numberOfButtons", value: 2, displayed: false)
        }
        else {
        	sendEvent(name: "numberOfButtons", value: 0, displayed: false)
			zwave.associationV2.associationSet(groupingIdentifier: 3, nodeId: zwaveHubNodeId).format()
			zwave.associationV2.associationGet(groupingIdentifier: 3).format()
        }
    }
}


def zwaveEvent(hubitat.zwave.commands.configurationv2.ConfigurationReport cmd) {
	if (logEnable) log.debug "---CONFIGURATION REPORT V2--- ${device.displayName} sent ${cmd}"
    def config = cmd.scaledConfigurationValue.toInteger()
    def result = []
	def name = ""
    def value = ""
    def reportValue = cmd.configurationValue[0]
    switch (cmd.parameterNumber) {
        case 3:
            name = "indicatorStatus"
            value = reportValue == 1 ? "when on" : reportValue == 2 ? "never" : "when off"
            break
        default:
            break
    }
	result << sendEvent([name: name, value: value, displayed: false])
	return result
}

def zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "---BINARY SWITCH REPORT V1--- ${device.displayName} sent ${cmd}"
    
	def desc, newValue, curValue, newType
	
	// check state.bin variable to see if event is digital or physical
	if (state.bin)
	{
		newType = "digital"
	}
	else
	{
		newType = "physical"
	}
	
	// Reset state.bin variable
	state.bin = 0
	
	curValue = device.currentValue("switch")

	if (cmd.value) { // == 255) {
		desc = "$device.displayName was turned on [$newType]"
		//if (logDesc) log.info "$device.displayName is on"
		newValue = "on"
	} else {
		desc = "$device.displayName was turned off [$newType]"
		//if (logDesc) log.info "$device.displayName is off"
		newValue = "off"
	}

	if (curValue != newValue) {
		if (logDesc) log.info "$device.displayName is " + (cmd.value ? "on" : "off")
		createEvent([name: "switch", value: cmd.value ? "on" : "off", descriptionText: "$desc", type: "$newType", isStateChange: true])
	}
}

def zwaveEvent(hubitat.zwave.commands.versionv1.VersionReport cmd) {
	def fw = "${cmd.applicationVersion}.${cmd.applicationSubVersion}"
	updateDataValue("fw", fw)
	if (logEnable) log.debug "---VERSION REPORT V1--- ${device.displayName} is running firmware version: $fw, Z-Wave version: ${cmd.zWaveProtocolVersion}.${cmd.zWaveProtocolSubVersion}"
}

def zwaveEvent(hubitat.zwave.commands.hailv1.Hail cmd) {
	if (logEnable) log.debug "Hail command received..."
	if (logEnable) log.debug "This does nothing in this driver, and shouldn't have been called..."
}

def zwaveEvent(hubitat.zwave.commands.centralscenev1.CentralSceneNotification cmd) {
	if (logEnable) log.debug "CentralSceneNotification V1 Called."
	if (logEnable) log.debug "This does nothing in this driver, and can be ignored..."
}

def zwaveEvent(hubitat.zwave.Command cmd) {
    log.warn "${device.displayName} received unhandled command: ${cmd}"
}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Driver Commands / Functions
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
def on() {
	if (logEnable) log.debug "Turn device ON"
	state.bin = -1
	delayBetween([
		zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	],500)
}

def off() {
	if (logEnable) log.debug "Turn device OFF"
	state.bin = -1
	delayBetween([
		zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00).format(),
		zwave.switchBinaryV1.switchBinaryGet().format()
	],500)
}

def refresh() {
	log.info "refresh() is called"
	
	def cmds = []
	cmds << zwave.switchBinaryV1.switchBinaryGet().format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 6).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 16).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 30).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 31).format()
    cmds << zwave.configurationV2.configurationGet(parameterNumber: 32).format()
	cmds << zwave.associationV2.associationGet(groupingIdentifier: 3).format()
	delayBetween(cmds,500)
}

def installed() {
	configure()
}

def updated() {
    log.info "updated..."
    log.warn "debug logging is: ${logEnable == true}"
    log.warn "description logging is: ${txtEnable == true}"
    if (logEnable) runIn(1800,logsOff)

    if (state.lastUpdated && now() <= state.lastUpdated + 3000) return
    state.lastUpdated = now()

	def cmds = []
    
    cmds << zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV1.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()

	// Set LED param
	if (paramLED==null) {
		paramLED = 0
	}	
	cmds << zwave.configurationV2.configurationSet(scaledConfigurationValue: paramLED.toInteger(), parameterNumber: 3, size: 1).format()
	cmds << zwave.configurationV2.configurationGet(parameterNumber: 3).format()
	
    delayBetween(cmds, 500)
}

def configure() {
    log.info "configure triggered"
	state.bin = -1
	def cmds = []
	sendEvent(name: "numberOfButtons", value: 2, displayed: false)
    cmds << zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV1.associationRemove(groupingIdentifier:2, nodeId:zwaveHubNodeId).format()
	cmds << zwave.associationV1.associationSet(groupingIdentifier:3, nodeId:zwaveHubNodeId).format()
    delayBetween(cmds, 500)
}

def logsOff(){
    log.warn "debug logging disabled..."
    device.updateSetting("logEnable",[value:"false",type:"bool"])
}
