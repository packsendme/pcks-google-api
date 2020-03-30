package com.packsendme.api.google.component;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.packsend.api.google.dto.TollsCosts_Dto;
import com.packsendme.api.google.dao.Tolls_DAO;
import com.packsendme.lib.common.constants.GoogleAPI_Constants;
import com.packsendme.lib.simulation.distance.response.dto.DistanceResponse_Dto;
import com.packsendme.lib.simulation.request.dto.SimulationRequest_Dto;
import com.packsendme.lib.simulation.roadway.response.dto.RoadwayCostsResponse_Dto;
import com.packsendme.lib.simulation.roadway.response.dto.TollsCountryResponse_Dto;

@Component
@ComponentScan("com.packsendme.api.google.dao")
public class AnalyzeCostsData_Component {

	private final Double AVERAGE_PRICE_DEFAULT = 0.0;

	private final String ANALYSE_PATTERN_TOLLS = "Toll";
	private final String ANALYSE_PATTERN_COUNTRY = "Entering";
	private final String ANALYSE_PATTERN_START = "Start";
	private final String ANALYSE_PATTERN_END = "End";
	
	private final String ANALYSE_ARRAY_ROUTES = "routes";
	private final String ANALYSE_ARRAY_LEGS = "legs";
	private final String ANALYSE_ARRAY_STEPS = "steps";
	
	// ELEMENT
	private final String ANALYSE_ELEMENT_ADDRESS = "start_address";
	private final String ANALYSE_ELEMENT_HTML = "html_instructions";
	private final String ANALYSE_ELEMENT_STARTLOCATION = "start_location";
	private final String ANALYSE_ELEMENT_ENDLOCATION = "end_location";
	private final String ANALYSE_ELEMENT_DISTANCE = "distance";
	private final String ANALYSE_ELEMENT_TEXT = "text";

	
	@Autowired
	private Tolls_DAO toll_dao;
	
	@Autowired
	private ConnectionGoogleAPI_Component connectionGoogle;
	
	private DistanceResponse_Dto distance_dto = new DistanceResponse_Dto();

	
	@Autowired
	private AnalyzeDistanceData_Component analyzeDistance_Component;
	
	private Map<Integer, String> latlongHistory_map = new HashMap<Integer, String>();
	private int count = 0;
	
	public RoadwayCostsResponse_Dto analyzeJsonTolls(JSONObject jsonObject, SimulationRequest_Dto simulation){
		int tolls = 0;
        String countryName = null, countryNameChange = null;
        JSONObject jsonHtmlInstLast = null;
        
        TollsCosts_Dto tollsCosts_Dto = null;
  		Map<String, TollsCountryResponse_Dto> countryTolls_map = new HashMap<String, TollsCountryResponse_Dto>();
  		TollsCountryResponse_Dto tollsCountry_Dto = null;
  		RoadwayCostsResponse_Dto tollsResponse_Dto = new RoadwayCostsResponse_Dto();

        try {
	        JSONArray jsonRoutes = (JSONArray) jsonObject.get(ANALYSE_ARRAY_ROUTES);
			Iterator<JSONObject> itRoutes = jsonRoutes.iterator();
			JSONArray jsonSteps = null;
			while (itRoutes.hasNext()) {
				JSONObject jsonLegs = itRoutes.next();
				JSONArray jsonArrayLegs = (JSONArray) jsonLegs.get(ANALYSE_ARRAY_LEGS);  
				
			    for (Iterator itLegs = jsonArrayLegs.iterator(); itLegs.hasNext();) {
			    	JSONObject jsonStepsX = (JSONObject) itLegs.next();
			    	
			    	// GET TOTAL DISTANCE
			    	Map totalDistance_map = ((Map)jsonStepsX.get(ANALYSE_ELEMENT_DISTANCE));
			    	String distanceS = totalDistance_map.get(ANALYSE_ELEMENT_TEXT).toString();
			    	tollsResponse_Dto.distance_total = getDistanceParse(distanceS);
			    	
			    	// Find Distance (Origin Location)
		        	distance_dto = getLatLongForDistance(jsonStepsX, ANALYSE_PATTERN_START, simulation);
			    	
        	    	String countryOrigin = jsonStepsX.get(ANALYSE_ELEMENT_ADDRESS).toString();
        	    	countryName = subStringCountryOrigin(countryOrigin);
			    	jsonSteps = (JSONArray) jsonStepsX.get(ANALYSE_ARRAY_STEPS);  //steps   
				}
			    
				for (Iterator itSteps = jsonSteps.iterator(); itSteps.hasNext();) {
				    JSONObject jsonHtmlInst = (JSONObject) itSteps.next();
				    String scheme = ((String) jsonHtmlInst.get(ANALYSE_ELEMENT_HTML)).trim();
				    
					// Find Distance
				    if(countryNameChange != null) {
					    if(countryNameChange.equals(countryName)) {
					    	distance_dto = getLatLongForDistance(jsonHtmlInst, ANALYSE_PATTERN_START, simulation);
					    	countryNameChange = null;
			    		}
				    }
 
				    // Change Country in Direction JSON-GOOGLE
				    if (analyzeContain(scheme,ANALYSE_PATTERN_COUNTRY) == true){
				    	if(tolls > 0) {
				    		// Find Distance
				    		distance_dto = getLatLongForDistance(jsonHtmlInst, ANALYSE_PATTERN_END, simulation);
				    		//Find Tolls Price by Country
				    		tollsCosts_Dto = toll_dao.find(countryName);
				    		tollsCountry_Dto = new TollsCountryResponse_Dto(countryName,tolls,distance_dto.distance,distance_dto.measureUnit,
				    				tollsCosts_Dto.average_price_toll,tollsCosts_Dto.currency_price);
				    		tolls = 0;
				    	}
				    	else {
				    		distance_dto = getLatLongForDistance(jsonHtmlInst, ANALYSE_PATTERN_END, simulation);
				    		tollsCountry_Dto = new TollsCountryResponse_Dto(countryName,0,distance_dto.distance,distance_dto.measureUnit,
				    				AVERAGE_PRICE_DEFAULT,null);
				    	}
			    		countryTolls_map.put(countryName, tollsCountry_Dto);
			    		countryName = subStringCountry(scheme);
			    		countryNameChange = countryName;
				    }
				    // Analyze Tolls in Direction JSON-GOOGLE
				    if (analyzeContain(scheme,ANALYSE_PATTERN_TOLLS) == true){
				    	tolls++;
				    }
				    jsonHtmlInstLast = jsonHtmlInst;
				}
				
				if(tolls > 0) {
		    		// Find Distance
		    		distance_dto = getLatLongForDistance(jsonHtmlInstLast, ANALYSE_PATTERN_END, simulation);
		    		//Find Tolls Price by Country
		    		tollsCosts_Dto = toll_dao.find(countryName);
		    		tollsCountry_Dto = new TollsCountryResponse_Dto(countryName,tolls,distance_dto.distance,distance_dto.measureUnit,
		    				tollsCosts_Dto.average_price_toll,tollsCosts_Dto.currency_price);
		    		countryTolls_map.put(countryName, tollsCountry_Dto);
				}
		    	else {
		    		// Find Distance
		    		distance_dto = getLatLongForDistance(jsonHtmlInstLast, ANALYSE_PATTERN_END, simulation);
		    		tollsCountry_Dto = new TollsCountryResponse_Dto(countryName,0,distance_dto.distance,distance_dto.measureUnit,
		    				AVERAGE_PRICE_DEFAULT,null);
		    		countryTolls_map.put(countryName, tollsCountry_Dto);
		    	}
			}
			if (countryTolls_map.size() > 0) {
				tollsResponse_Dto.status_tolls = true;
				tollsResponse_Dto.countryTolls = countryTolls_map;
			}
			else{
				tollsResponse_Dto.status_tolls = false;
				tollsResponse_Dto.countryTolls = null;
			}
			return tollsResponse_Dto;
        }
        catch (Exception e) {
        	e.printStackTrace();
        	return null;
		}
    }
	
 	public DistanceResponse_Dto getLatLongForDistance(JSONObject object, String patterns, SimulationRequest_Dto simulationDto) {
    	Map latlong_map = null;
		DistanceResponse_Dto distanceResponse_dto = null;
    	SimulationRequest_Dto simulation = new SimulationRequest_Dto();
    	
    	if(patterns.equals(ANALYSE_PATTERN_START)) {
    		latlong_map = ((Map)object.get(ANALYSE_ELEMENT_STARTLOCATION));
    	}
    	else if(patterns.equals(ANALYSE_PATTERN_END)) {
    		latlong_map = ((Map)object.get(ANALYSE_ELEMENT_ENDLOCATION));
    	}
    	
    	String latilongFrom = latlong_map.get("lat").toString();
    	latilongFrom = latilongFrom+","+latlong_map.get("lng").toString();
    	count++;
    	latlongHistory_map.put(count, latilongFrom);
    	
    	if(latlongHistory_map.size() == 2) {
    		simulation.address_origin = latlongHistory_map.get(1);
    		simulation.address_destination = latlongHistory_map.get(2);
    		simulation.unity_measurement_distance_txt = simulationDto.unity_measurement_distance_txt;
    		try {
    			distanceResponse_dto = getDistanceGoogleParser(simulation);
    			System.out.println(" ================================================== ");
	    			System.out.println(" address_origin "+ simulation.address_origin);
	    			System.out.println(" address_destination "+ simulation.address_destination);
	    	    	System.out.println(" distance "+ distanceResponse_dto.distance);
    			System.out.println(" ================================================== ");

    			latlongHistory_map = new HashMap<Integer, String>();
    			count = 0;
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	return distanceResponse_dto;
	}
	
	public DistanceResponse_Dto getDistanceGoogleParser(SimulationRequest_Dto simulation) throws ParseException {
		DistanceResponse_Dto distanceResponse_dto = null;
		String typeAPI = GoogleAPI_Constants.API_DISTANCE;
		ResponseEntity<String> distanceResponse = connectionGoogle.connectionGoogleAPI(simulation, typeAPI);
		
		if (distanceResponse.getStatusCode() == HttpStatus.OK) {
			String jsonData = distanceResponse.getBody();
	    	JSONParser parser = new JSONParser();
	    	JSONObject jsonObject = (JSONObject) parser.parse(jsonData);
	    	
	    	if(jsonObject.get("status").equals("OK")) {
	    		distanceResponse_dto = analyzeDistance_Component.analyzeJsonDistance(jsonObject,simulation);
	    	}
		}
    	return distanceResponse_dto;
	}

	
	public boolean analyzeContain(String scheme, String parse) {
	  boolean bool = scheme.contains(parse);
	  return bool;
	}
	
	// Country change in JSON
	public String subStringCountry(String contain) {
		int startMatch = 0;
		int endMatch = 0;
		String new1 = StringUtils.substring(contain, 0, contain.length() - 6);
		
		Pattern pattern = Pattern.compile(ANALYSE_PATTERN_COUNTRY);
		Matcher matcher = pattern.matcher(new1);
		while(matcher.find()){
			startMatch = matcher.start();
			endMatch = matcher.end();
		}
		String new2 = StringUtils.substring(new1, startMatch, new1.length() + endMatch);
		return StringUtils.substringAfter(new2, ANALYSE_PATTERN_COUNTRY).trim();
	}

	// Country Origin destination
	public String subStringCountryOrigin(String contain) {
		
		int startMatch = 0;
		int endMatch = 0;
		
		Pattern pattern = Pattern.compile(",");
		Matcher matcher = pattern.matcher(contain);
		while(matcher.find()){
			startMatch = matcher.start();
			endMatch = matcher.end();
		}
		String new2 = StringUtils.substring(contain, startMatch, contain.length() + endMatch);
		String new3 = StringUtils.substringAfter(new2, ",").trim();
		return new3;
	}
	
	public double getDistanceParse(String contain) {
        String distanceS = StringUtils.substring(contain, 0, contain.length() - 2);
    	String formatDistance = distanceS.replace(",", ".");
        return Double.parseDouble(formatDistance);
	}
}