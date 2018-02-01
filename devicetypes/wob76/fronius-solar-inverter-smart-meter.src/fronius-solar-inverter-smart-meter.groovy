/**
 *  Fronius Solar Inverter
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
 
// import groovy.json.JsonSlurper

preferences {
	input("inverterNumber", "number", title: "Inverter Number", description: "The Inverter Number", required: true, displayDuringSetup: true)
    input("destIp", "text", title: "IP", description: "Inverter Local IP Address", required: true, displayDuringSetup: true)
    input("destPort", "number", title: "Port", description: "TCP Port", required: true, displayDuringSetup: true)
    input("pollingInterval", "number", title:"Polling Interval (min)", defaultValue:"5", range: "2..59", required: true, displayDuringSetup: true)
}

metadata {
	definition (name: "Fronius Solar Inverter & Smart Meter", namespace: "Wob76", author: "Beau Dwyer") {
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
            tileAttribute("device.solar_power", key: "PRIMARY_CONTROL") {
                attributeState "power", label:'${currentValue}W', icon: "st.Weather.weather14", defaultState: true, backgroundColors:[
                    [value: 0, color: "	#cccccc"],
                    [value: 5500, color: "#00a0dc"]
                ]
            }
            tileAttribute("device.power_details", key: "SECONDARY_CONTROL") {
                attributeState("power_details", label:'${currentValue}', icon: "st.Appliances.appliances17", defaultState: true)
            }
        }        
        
        valueTile("YearValue", "device.YearValue", width: 2, height: 2, inactiveLabel: false) {
			state "YearValue", label:'${currentValue}'
		}
        valueTile("TotalValue", "device.TotalValue", width: 2, height: 2, inactiveLabel: false) {
			state "TotalValue", label:'${currentValue}'
		}

		standardTile("HouseUsage", "HouseUsage", width: 4, height: 1) {
			state "default", label: "House Usage"
		}
		valueTile("HousePower", "device.house_power", width: 2, height: 1, inactiveLabel: false) {
			state "HousePower", label:'${currentValue}'
		}
        
        standardTile("GridPower", "GridPower", width: 4, height: 1) {
			state "default", label: "Grid Power"
		}
        
        valueTile("Grid", "device.grid", width: 2, height: 1, inactiveLabel: false) {
			state "Grid", label:'${currentValue}'
		}
        
        valueTile("solar2", "device.solar_power", decoration: "flat", inactiveLabel: false) {
			state "solar", label:'${currentValue}', icon: "st.Weather.weather14",
            	backgroundColors:[
                    [value: 0, color: "#cccccc"],
                    [value: 5500, color: "#00a0dc"]
                ]
		}
        
		standardTile("poll", "device.poll", width: 2, height: 2, canChangeIcon: false, inactiveLabel: true, canChangeBackground: false, decoration: "flat") {
			state "poll", label: "", action: "polling.poll", icon: "st.secondary.refresh", backgroundColor: "#FFFFFF"
		}
        
        main(["solar2"])
		details(["solar", "HouseUsage", "HousePower", "GridPower", "Grid", "poll"])
	}
}

def initialize() {
	log.info "Fronius Inverter ${textVersion()}"
    sendEvent(name: "solar_power", value: 0	)
    sendEvent(name: "YearValue", value: 0 )
    sendEvent(name: "energy", value: 0 )
    sendEvent(name: "TotalValue", value: 0 )
    sendEvent(name: "house_power", value: 0 )
    sendEvent(name: "grid", value: 0 )
	poll()
}

// parse events into attributes
def parse(String description) {	
    def msg = parseLanMessage(description)

    // log.info "Message: $msg"
    def result = msg.json
    
    log.info "JSON: $result"
    if (result.Head.RequestArguments.DeviceClass == "Meter") {
		// Parse Data From Smart Meter
    } else {
    	// Parse Data From Inverter
        def P_Grid = Math.round(result.Body.Data.Site.P_Grid);
        def P_Grid_unit = "W"
        
        def P_Load = Math.round((0 - result.Body.Data.Site.P_Load));
        def P_Load_unit = "W"
        
        def P_PV = 0
        def P_PV_unit = "W"
        if (result.Body.Data.Site.P_PV != null) {
            P_PV = result.Body.Data.Site.P_PV
		}

		def E_Day = result.Body.Data.Site.E_Day
        def E_Day_unit = "Wh"
        if (E_Day < 1000000) {
        	E_Day = (E_Day/1000)
            E_Day_unit = "kWh"
        } else {
        	E_Day = (E_Day/1000000)
            E_Day_unit = "MWh"
        }
        E_Day = (Math.round(E_Day * 100))/100
        
        def E_Year = result.Body.Data.Site.E_Year
        def E_Year_unit = "Wh"
        if (E_Year < 1000000) {
        	E_Year = (E_Year/1000)
            E_Year_unit = "kWh"
        } else {
        	E_Year = (E_Year/1000000)
            E_Year_unit = "MWh"
        }
        E_Year = (Math.round(E_Year * 100))/100

        def E_Total = result.Body.Data.Site.E_Total
        def E_Total_unit = "Wh"
        if (E_Total < 1000000) {
        	E_Total = (E_Total/1000)
            E_Total_unit = "kWh"
        } else {
        	E_Total = (E_Total/1000000)
            E_Total_unit = "MWh"
        }
        E_Total = (Math.round(E_Total * 100))/100
        
        log.debug "Now: $P_PV $P_PV_unit"
		log.debug "Day: $E_Day $E_Day_unit"
        log.debug "Year: $E_Year $E_Year_unit"
        log.debug "Total: $E_Total $E_Total_unit"
    
/*
		[name: "solar_power", value: Math.round(P_PV), unit: "W"]
        [name: "energy", value: (E_Day/1000), unit: "kWh"]
        [name: "house_power", value: Math.round(P_Load), unit: "W"]
        [name: "grid", value: Math.round(P_Grid), unit: "W"]
*/

        sendEvent(name: "solar_power", value: "${P_PV}", unit:P_PV_unit )
        sendEvent(name: "energy", value: "${E_Day}${E_Day_unit}", unit:E_Year_unit )
        sendEvent(name: "house_power", value: "${P_Load}${P_Load_unit}", unit:P_Load_unit )
        sendEvent(name: "grid", value: "${P_Grid}${P_Grid_unit}", unit:P_Grid_unit )
        sendEvent(name: "YearValue", value: "${E_Year}${E_Year_unit}", unit:E_Year_unit )
        sendEvent(name: "TotalValue", value: "${E_Total}${E_Total_unit}", unit:E_Total_unit )
        sendEvent(name: 'power_details', value: "Today: ${E_Day}${E_Day_unit}\nYear: ${E_Year}${E_Year_unit} Total: ${E_Total}${E_Total_unit}", unit:E_Year_unit, displayed: false )
    }
}

// handle commands
def poll() {
	def powerFlow = "/solar_api/v1/GetPowerFlowRealtimeData.fcgi"
    def meterRealtime = "/solar_api/v1/GetMeterRealtimeData.cgi?Scope=System"
    def inverter = callInvertor(powerFlow)
    def meter = callInvertor(meterRealtime)

	return [inverter, meter]
}

def callInvertor(path) {
	try
    {
	def hosthex = convertIPtoHex(destIp)
    def porthex = convertPortToHex(destPort)
    device.deviceNetworkId = "$hosthex:$porthex" 

    def hubAction = new physicalgraph.device.HubAction(
   	 		'method': 'GET',
    		'path': path,
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

private String convertIPtoHex(ipAddress) { 
    String hex = ipAddress.tokenize( '.' ).collect {  String.format( '%02X', it.toInteger() ) }.join()
    return hex
}

private String convertPortToHex(port) {
	String hexport = port.toString().format( '%04X', port.toInteger() )
    return hexport
}