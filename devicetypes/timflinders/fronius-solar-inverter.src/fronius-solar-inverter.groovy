/**
 *  Fronius Solar Inverter
 *
 *  Copyright 2015 Tim Flinders
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
 
import groovy.json.JsonSlurper
 
preferences {
	input("inverterNumber", "number", title: "Inverter Number", description: "The Inverter Number", required: true, displayDuringSetup: true)
    input("destIp", "text", title: "IP", description: "The device IP", required: true, displayDuringSetup: true)
    input("destPort", "number", title: "Port", description: "The port you wish to connect", required: true, displayDuringSetup: true)
}

metadata {
	definition (name: "Fronius Solar Inverter", namespace: "TimFlinders", author: "Tim Flinders") {
		capability "Polling"
        capability "Power Meter"
        capability "Energy Meter"
        
        attribute "power_details", "string"
	}

	simulator {
		// TODO: define status and reply messages here
	}

	tiles(scale: 2) {
        
        multiAttributeTile(name:"solar", type:"generic", width:6, height:4) {
            tileAttribute("device.power", key: "PRIMARY_CONTROL") {
                attributeState "power", label:'${currentValue}W', unit: "W", defaultState: true, backgroundColors:[
                    [value: 0, color: "#ffffff"],
                    [value: 5500, color: "#f1d801"]
                ]
            }
            tileAttribute("device.power_details", key: "SECONDARY_CONTROL") {
                attributeState("power_details", label:'${currentValue}', defaultState: true)
            }
        }        
        
        valueTile("YearValue", "device.YearValue", width: 2, height: 2, inactiveLabel: false) {
			state "YearValue", label:'${currentValue} kWh', unit:""
		}
        valueTile("TotalValue", "device.TotalValue", width: 2, height: 2, inactiveLabel: false) {
			state "TotalValue", label:'${currentValue} kWh', unit:""
		}
        
		standardTile("poll", "device.poll", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false, decoration: "flat") {
			state "poll", label: "", action: "polling.poll", icon: "st.secondary.refresh", backgroundColor: "#FFFFFF"
		}
        
        main(["solar"])
		details(["solar", "YearValue", "TotalValue", "poll" ])
	}
}

def initialize() {
	log.info "Fronius Inverter ${textVersion()} ${textCopyright()}"
    sendEvent(name: "power", value: 0	)
    sendEvent(name: "YearValue", value: 0 )
    sendEvent(name: "energy", value: 0 )
    sendEvent(name: "TotalValue", value: 0 )
	poll()
}


// parse events into attributes
def parse(String description) {	
    def msg = parseLanMessage(description)

	def slurper = new JsonSlurper()
    def result = slurper.parseText(msg.body)
    
    def power = result.Body.Data.PAC.Values."$inverterNumber"
    if (result.Body.Data.PAC.Values."$inverterNumber" == null) {
    	power = 0
        }
        
    def yearValue = result.Body.Data.YEAR_ENERGY.Values."$inverterNumber"
    def dayValue = result.Body.Data.DAY_ENERGY.Values."$inverterNumber"
    def totalValue = result.Body.Data.TOTAL_ENERGY.Values."$inverterNumber"
        
    [name: "power", value: Math.round(power), unit: "W"]
    [name: "energy", value: (dayValue/1000), unit: "kWh"]
        
    sendEvent(name: "power", value: power	)
    sendEvent(name: "energy", value: dayValue )
    sendEvent(name: "YearValue", value: (yearValue/1000) )
    sendEvent(name: "TotalValue", value: (totalValue/1000) )
    sendEvent(name: 'power_details', value: "Energy " +(dayValue/1000) + " kWh", displayed: false)
}

// handle commands
def poll() {
    callInvertor()
}


def callInvertor() {
	try
    {
	def hosthex = convertIPtoHex(destIp)
    def porthex = convertPortToHex(destPort)
    device.deviceNetworkId = "$hosthex:$porthex" 

    def hubAction = new physicalgraph.device.HubAction(
   	 		'method': 'GET',
    		'path': "/solar_api/v1/GetInverterRealtimeData.cgi?Scope=System",
        	'headers': [ HOST: "$destIp:$destPort" ]
		) 
        
    hubAction
    }
    catch (Exception e) {
        log.debug "Hit Exception $e on $hubAction"
    }

}

private def textVersion() {
    def text = "Version 1.0"
}

private def textCopyright() {
    def text = "Copyright © 2015 Tim Flinders"
}

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}
