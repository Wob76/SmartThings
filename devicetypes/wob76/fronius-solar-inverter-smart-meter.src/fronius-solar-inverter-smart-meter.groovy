/**
 *  Fronius Solar Inverter & Smart Meter
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
	input("MeterNumber", "number", title: "Smart Meter Number", description: "The Smart Meter Number", required: false, displayDuringSetup: true)
    input("destIp", "text", title: "IP", description: "Inverter Local IP Address", required: true, displayDuringSetup: true)
    input("destPort", "number", title: "Port", description: "TCP Port", required: true, displayDuringSetup: true)
    // input("solarMax", "number", title: "Solar System Size", description: "in Watts (5kw = 5000)", required: true, displayDuringSetup: true)
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
                    [value: 5000, color: "#00a0dc"]
                ]
            }
            tileAttribute("device.power_details", key: "SECONDARY_CONTROL") {
                attributeState("power_details", label:'${currentValue}', icon: "st.Appliances.appliances17", defaultState: true)
            }
        }
        
		standardTile("HouseUsage", "HouseUsage", width: 2, height: 2, inactiveLabel: false) {
			state "default", label: "House Usage"
		}
		valueTile("HousePower", "device.house_power", width: 2, height: 2, inactiveLabel: false) {
			state "house_power", label:'${currentValue}W'
		}
		valueTile("HouseMeter", "device.usage_meter", width: 2, height: 2, inactiveLabel: false) {
			state "usage_meter", label:'${currentValue}'
		}
        
		standardTile("SolarUsage", "SolarUsage", width: 2, height: 2, inactiveLabel: false) {
			state "default", label: "Solar Usage"
		}
		valueTile("SolarUsageNow", "device.s_usage", width: 2, height: 2, inactiveLabel: false) {
			state "solar_power", label:'${currentValue}W'
		}
		valueTile("SolarMeter", "device.s_usage_meter", width: 2, height: 2, inactiveLabel: false) {
			state "Solar_usage_meter", label:'${currentValue}'
		}
        
        standardTile("GridPower", "GridPower", width: 2, height: 2, inactiveLabel: false) {
			state "default", label: "Grid Power"
		}
        
        valueTile("Grid", "device.grid", width: 2, height: 2, inactiveLabel: false) {
			state "Grid", label:'${currentValue}W'
		}
        
        valueTile("Grid_Imported", "device.imported", width: 2, height: 2, inactiveLabel: false) {
			state "Grid_Imported", label:'${currentValue}'
		}

		valueTile("Grid_Exported", "device.exported", width: 2, height: 2, inactiveLabel: false) {
			state "Grid_Exported", label:'${currentValue}'
		}
        
		valueTile("autonomy", "device.autonomy", width: 2, height: 2, inactiveLabel: false) {
			state "autonomy", label:'${currentValue}'
		}
        
		valueTile("self_consumption", "device.self_consumption", width: 2, height: 2, inactiveLabel: false) {
			state "self_consumption", label:'${currentValue}'
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
		details(["solar", "HouseUsage", "HousePower", "HouseMeter", "SolarUsage", "SolarUsageNow", "SolarMeter", "GridPower", "Grid", "Grid_Imported", "Grid_Exported", "autonomy", "self_consumption", "poll"])
	}
}

def initialize() {
	log.info "Fronius Inverter ${textVersion()}"
    sendEvent(name: "solar_power", value: 0	)
    sendEvent(name: "energy", value: 0 )
    sendEvent(name: "house_power", value: 0 )
    sendEvent(name: "grid", value: 0 )
    sendEvent(name: "YearValue", value: 0 )
    sendEvent(name: "TotalValue", value: 0 )
    sendEvent(name: "autonomy", value: 0 )
    sendEvent(name: "self_consumed", value: 0 )
	state.pgrid = 0
	state.etotal = 0
    state.ppv = 0
    
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
        
        //Restore state stored values form Inverter Run
        def P_Grid = state.pgrid
        def P_PV = state.ppv
        def E_Total = state.etotal
        
        def fromgrid = 0
		def grid_consumed = result.Body.Data."$MeterNumber".EnergyReal_WAC_Sum_Consumed
		def togrid = 0
		def grid_produced = result.Body.Data."$MeterNumber".EnergyReal_WAC_Sum_Produced
        if (P_Grid > 0) {
            fromgrid = P_Grid
        } else if (P_Grid < 0) {
            togrid = 0 - P_Grid
        }
        
  		// Import and Export Meters
        def imported = grid_consumed
        def imported_unit = "Wh"
        if (imported < 1000000) {
        	imported = (imported/1000)
            imported_unit = "kWh"
        } else {
        	imported = (imported/1000000)
            imported_unit = "MWh"
        }
        imported = (Math.round(imported * 100))/100
        
		// Import and Export Meters
        def exported = grid_produced
        def exported_unit = "Wh"
        if (exported < 1000000) {
        	exported = (exported/1000)
            exported_unit = "kWh"
        } else {
        	exported = (exported/1000000)
            exported_unit = "MWh"
        }
        exported = (Math.round(exported * 100))/100

        // Consumption = Generation + Import - Export
		def usage_meter = E_Total + grid_consumed - grid_produced
        def usage_meter_unit = "Wh"
        if (usage_meter < 1000000) {
        	usage_meter = (usage_meter/1000)
            usage_meter_unit = "kWh"
        } else {
        	usage_meter = (usage_meter/1000000)
            usage_meter_unit = "MWh"
        }
        usage_meter = (Math.round(usage_meter * 100))/100
        
        // Self Consumption = Generation - Export (now and total meter)
		def SUsage = Math.round(P_PV - togrid)
        def SUsage_unit = "W"
		def SUsage_meter = E_Total - grid_produced
        def SUsage_meter_unit = "Wh"
        if (SUsage_meter < 1000000) {
        	SUsage_meter = (SUsage_meter/1000)
            SUsage_meter_unit = "kWh"
        } else {
        	SUsage_meter = (SUsage_meter/1000000)
            SUsage_meter_unit = "MWh"
        }
        SUsage_meter = (Math.round(SUsage_meter * 100))/100
   
        sendEvent(name: "usage_meter", value: "${usage_meter}${usage_meter_unit}", unit:usage_meter_unit )
        sendEvent(name: "imported", value: "${imported}${imported_unit}", unit:imported_unit )
        sendEvent(name: "exported", value: "${exported}${exported_unit}", unit:exported_unit )
		sendEvent(name: "s_usage", value: "${SUsage}", unit:SUsage_unit )
		sendEvent(name: "s_usage_meter", value: "${SUsage_meter}${SUsage_meter_unit}", unit:SUsage_meter_unit )
        
    } else {
    	// Parse Data From Inverter
        // Grid Power +Value = Importing -Value = Exporting
        def P_Grid = Math.round(result.Body.Data.Site.P_Grid)
        state.pgrid = result.Body.Data.Site.P_Grid
        def P_Grid_unit = "W"
        
        // House Power Usage (Raw is Neg Value)
        def P_Load = Math.round((0 - result.Body.Data.Site.P_Load))
        def P_Load_unit = "W"
        
        // Current Solar Production
        def P_PV = 0
        def P_PV_unit = "W"
        if (result.Body.Data.Site.P_PV != null) {
            P_PV = result.Body.Data.Site.P_PV
		}
        state.ppv = P_PV

		// Daily Production Value (Current FW Issue means this value is often wrong)
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
    
        // Yearly Solar Production Value
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

		// Total Solar Production Value
        def E_Total = result.Body.Data.Site.E_Total
        state.etotal = E_Total
        def E_Total_unit = "Wh"
        if (E_Total < 1000000) {
        	E_Total = (E_Total/1000)
            E_Total_unit = "kWh"
        } else {
        	E_Total = (E_Total/1000000)
            E_Total_unit = "MWh"
        }
        E_Total = (Math.round(E_Total * 100))/100
        
        // Current Autonomy (Percentage) 100% = No Grid Power
		def Autonomy = (Math.round(result.Body.Data.Site.rel_Autonomy * 10))/10
        
        // Current Self Consuption (Percentage)
		def Self_Consumption = (Math.round(result.Body.Data.Site.rel_SelfConsumption * 10))/10
        
/*
		[name: "solar_power", value: Math.round(P_PV), unit: "W"]
        [name: "energy", value: (E_Day/1000), unit: "kWh"]
        [name: "house_power", value: Math.round(P_Load), unit: "W"]
        [name: "grid", value: Math.round(P_Grid), unit: "W"]
*/

        sendEvent(name: "solar_power", value: "${P_PV}", unit:P_PV_unit )
        sendEvent(name: "energy", value: "${E_Day}${E_Day_unit}", unit:E_Year_unit )
        sendEvent(name: "house_power", value: "${P_Load}", unit:P_Load_unit )
        sendEvent(name: "grid", value: "${P_Grid}", unit:P_Grid_unit )
        sendEvent(name: "YearValue", value: "${E_Year}${E_Year_unit}", unit:E_Year_unit )
        sendEvent(name: "TotalValue", value: "${E_Total}${E_Total_unit}", unit:E_Total_unit )
        sendEvent(name: "autonomy", value: "${Autonomy}%", unit:"" )
		sendEvent(name: "self_consumption", value: "${Self_Consumption}%", unit:"" )
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