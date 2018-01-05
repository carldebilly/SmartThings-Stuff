/**
*  TRÅDFRI Dimmer v0.2
*
*  Copyright 2017 Kristian Andrews (v0.1)
*  Modified 2018-01 by Carl de Billy (v0.2)
*   and published on Github:
*   https://github.com/carldebilly/SmartThings-Stuff/edit/master/devicetypes/andrews-k/tradfri-dimmer-v0-2.src/tradfri-dimmer-v0-2.groovy
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
*  To do:
*  1) make the handler "useful" (able to control another _real_ device)
*  2) support battery reporting
*/
metadata {
    definition (name: "Tradfri Dimmer v0.2", namespace: "andrews.k", author: "Kristian Andrews") {
        capability "Sensor"
        capability "Configuration"
        capability "Switch"
        capability "Switch Level"

        fingerprint endpointId: "01", profileId: "0104", deviceId: "0810", deviceVersion: "02", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000"
    }


    simulator {
        // TODO: define status and reply messages here
    }
    
    // UI tile definitions
    tiles(scale: 2) {
        multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
            tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
                attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
                attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
                attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
            }
            tileAttribute ("device.level", key: "SLIDER_CONTROL") {
                attributeState "level", action:"switch level.setLevel"
            }
        }

        standardTile("refresh", "device.refresh", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
            state "default", label:"", action:"refresh.refresh", icon:"st.secondary.refresh"
        }

        main(["switch"])
        details(["switch", "refresh"])
    }
    
}

def configure() {
    log.debug "Configiring Reporting and Bindings."
    def configCmds = []
    
    state = {}

    configCmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0001 {${device.zigbeeId}} {}" // battery
    configCmds += "zdo bind 0x${device.deviceNetworkId} 0x01 0x01 0x0008 {${device.zigbeeId}} {}" // intensity changes
    configCmds += "delay 2000"

    return configCmds + refresh()
}


def refresh() {
    log.debug "refreshing..."
}

// parse events into attributes
def parse(String description) {
    def now = now()
    
    computeVelocity(now)
    
    if (description?.startsWith("catchall:")) {
        def descMap = zigbee.parseDescriptionAsMap(description)
        
        def cluster = descMap.clusterInt
        def command = descMap.commandInt
        def data = descMap.data
        
        if(cluster == 0x0008) {
            switch(command) {
            case 0x00:
                def level = Integer.parseInt(data[0], 16)
                def speed = Integer.parseInt(data[1]+data[2], 16)
                log.debug "cmd-$command: Go to level. Level=$level, speed=$speed."
                state.velocity = 0
                state.level = level
                break
            case 0x01:
                def direction = data[0] == "00" ? "up" : "down"
                def rate = Integer.parseInt(data[1], 16)
                if(rate == 0xFF) rate = 5
                log.debug "cmd-$command: Moving. Direction=$direction, rate=$rate."
                //state.velocity = direction == "up" ? rate : -1 * rate
                state.velocity = direction == "up" ? 15 : -15
                break
            case 0x04:
                def level = Integer.parseInt(data[0], 16)
                def speed = Integer.parseInt(data[1]+data[2], 16)
                log.debug "cmd-$command: Go to level with on/off. Level=$level, speed=$speed."
                state.velocity = 0
                state.level = level
                if(level == 0)
                    state.switch = 0
                else
                    state.switch = 1
                break
            case 0x05:
                def direction = data[0] == "00" ? "up" : "down"
                def rate = Integer.parseInt(data[1], 16)
                if(rate == 0xFF) rate = 5
                log.debug "cmd-$command: Moving with on/off. Direction=$direction, rate=$rate."
                //state.velocity = direction == "up" ? rate : -1 * rate
                state.velocity = direction == "up" ? 15 : -15
                break
            case 0x07:
                log.debug "cmd-$command: Stopped Moving."
                state.velocity = 0
                break
            }
        }
        else {
            log.debug "CATCH-ALL Map: $descMap"
        }
    }
    sendEvents()
}

def computeVelocity(now) {

    def velocity = state.velocity ?: 0

    if(!velocity) return // nothing to do
    
    def currentLevel = state.level ?: 0
    def currentTs = state.timestamp = state.timestamp ?: now
    def duration = (now - currentTs) / 1000000

    if(!duration) return // nothing to do
    
    def increment = (Integer)(duration * velocity);
    state.level = currentLevel + increment

    if(state.level > 0xFF) {
        state.level = 0xFF
    }

    if(state.level < 0x00) {
        state.level = 0x00
    }

    sendEvents()
}

def sendEvents() {
	Integer level = (state.level ?: 0) * 100 / 0xFF
    def sw = state.switch ? "on" : "off"
    sendEvent(name: "level", value: level)
    sendEvent(name: "switch", value: sw)
}

// handle commands
def on() {
    log.debug "Executing 'on'"
    state.switch = 1
    state.velocity = 0
    if(!state.level) {
        state.level = 0xFF
    }
    sendEvents()
}

def off() {
    log.debug "Executing 'off'"
    state.switch = 0
    state.velocity = 0
    sendEvents()
}

def setLevel(int lev) {
    log.debug "Executing 'setLevel' $lev"
    
    Integer newLevel = lev * 0xFF / 100
    state.level = newLevel
    state.velocity = 0
    sendEvents()
}
