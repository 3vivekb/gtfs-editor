package jobs;

import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;

import java.io.File;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.HashSet;
import java.util.Map;
import java.util.zip.ZipFile;

import javax.persistence.EntityManager;
import javax.persistence.Query;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.joda.time.LocalDate;
import org.mapdb.Fun.Tuple2;
import org.onebusaway.gtfs.impl.GtfsDaoImpl;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.calendar.ServiceDate;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs.serialization.GtfsWriter;

import com.conveyal.gtfs.GTFSFeed;
import com.conveyal.gtfs.model.Frequency;
import com.conveyal.gtfs.model.Service;
import com.conveyal.gtfs.model.Shape;
import com.mchange.v2.c3p0.impl.DbAuth;
import com.vividsolutions.jts.geom.Coordinate;

import models.gtfs.GtfsSnapshotExport;
import models.gtfs.GtfsSnapshotExportStatus;
import models.gtfs.GtfsSnapshotMerge;
import models.gtfs.GtfsSnapshotMergeTask;
import models.gtfs.GtfsSnapshotMergeTaskStatus;
import models.transit.Agency;
import models.transit.AttributeAvailabilityType;
import models.transit.Route;
import models.transit.ServiceCalendar;
import models.transit.ServiceCalendarDate;
import models.transit.ServiceCalendarDateType;
import models.transit.Stop;
import models.transit.StopTime;
import models.transit.TripPatternStop;
import models.transit.TripShape;
import models.transit.Trip;
import play.Logger;
import play.Play;
import play.jobs.Job;
import play.jobs.OnApplicationStart;
import utils.DirectoryZip;

public class ProcessGtfsSnapshotExport extends Job {

	private Long _gtfsSnapshotExportId;
	
	public ProcessGtfsSnapshotExport(Long gtfsSnapshotExportId)
	{
		this._gtfsSnapshotExportId = gtfsSnapshotExportId;
	}
	
	public void doJob() {
		
		GtfsSnapshotExport snapshotExport = null;
		while(snapshotExport == null)
		{
			snapshotExport = GtfsSnapshotExport.findById(this._gtfsSnapshotExportId);
			Logger.warn("Waiting for snapshotExport to save...");
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		try 
		{
			GTFSFeed feed = new GTFSFeed();
			
			File gtfsZip = new File(Play.configuration.getProperty("application.publicDataDirectory"), snapshotExport.getDirectory() + ".zip");
					
			// these keep track of what we've saved so far
			TLongSet stopList = new TLongHashSet();
			TLongSet routeList = new TLongHashSet();
			TLongSet shapeList = new TLongHashSet();
		
			
			for(Agency agency : snapshotExport.agencies)
			{
				// export agencies
				com.conveyal.gtfs.model.Agency gtfsAgency = agency.toGtfs();
				feed.agency.put(gtfsAgency.agency_id, gtfsAgency);
				
				// export calendars
				List<ServiceCalendar> calendars = ServiceCalendar.find("agency = ?", agency).fetch();
				
				for(ServiceCalendar calendar : calendars) {
					Service service = calendar.toGtfs(toGtfsDate(snapshotExport.calendarFrom), toGtfsDate(snapshotExport.calendarTo));
					feed.services.put(service.service_id, service);
										
					List<Trip> trips = Trip.find("serviceCalendar = ?", calendar).fetch();
					
					for(Trip trip : trips) {	
						List<TripPatternStop> patternStopTimes = TripPatternStop.find("pattern = ? order by stopSequence", trip.pattern).fetch();
						
						if(trip.useFrequency == null || patternStopTimes == null || (trip.useFrequency && patternStopTimes.size() == 0) || !trip.pattern.route.agency.id.equals(agency.id) || (trip.useFrequency && trip.headway.equals(0)) || (trip.useFrequency && trip.startTime.equals(trip.endTime)))
							continue;

						// save the route, if we haven't already
						if (!routeList.contains(trip.pattern.route.id))
						{
							com.conveyal.gtfs.model.Route route = trip.pattern.route.toGtfs(gtfsAgency);
							feed.routes.put(route.route_id, route);
							routeList.add(trip.pattern.route.id);
						}
						
						// save the shape
						if (trip.pattern.shape != null && !shapeList.contains(trip.pattern.shape.id)) {
							// TODO: this leaves feed.shapes in an undefined state. We don't really care for the time being.
							for (Shape shp : trip.pattern.shape.toGtfs()) {
								feed.shapePoints.put(new Tuple2<String, Integer>(shp.shape_id, shp.shape_pt_sequence), shp);
							}
							
							shapeList.add(trip.pattern.shape.id);
						}
							
						com.conveyal.gtfs.model.Trip gtfsTrip = trip.toGtfs(feed.routes.get(trip.pattern.route.getGtfsId()), service);
						feed.trips.put(gtfsTrip.trip_id, gtfsTrip);					

						Frequency f = trip.getFrequency(gtfsTrip); 
						
						if (f != null) {
							feed.frequencies.put(gtfsTrip.trip_id, f);
							
							// build up the schedule
							Integer cumulativeTime = 0;
							for (TripPatternStop stopTime : patternStopTimes) {
								if (!stopList.contains(stopTime.stop.id)) {
									com.conveyal.gtfs.model.Stop stop = stopTime.stop.toGtfs(); 
									feed.stops.put(stop.stop_id, stop);
									stopList.add(stopTime.stop.id);
								}
								
								if(stopTime.defaultTravelTime != null) {
									
									// need to flag negative travel times in the patterns!
									if(stopTime.defaultTravelTime < 0)
										cumulativeTime -= stopTime.defaultTravelTime;
									else
										cumulativeTime += stopTime.defaultTravelTime;	
								}
								
								int arrivalTime = cumulativeTime;
								
								if(stopTime.defaultDwellTime != null) {
									
									// need to flag negative dwell times in the patterns!
									if(stopTime.defaultDwellTime < 0)
										cumulativeTime -= stopTime.defaultDwellTime;
									else
										cumulativeTime += stopTime.defaultDwellTime;
								}
								
							    com.conveyal.gtfs.model.StopTime st = new com.conveyal.gtfs.model.StopTime();
							    st.trip_id = gtfsTrip.trip_id;
							    st.stop_id = stopTime.stop.getGtfsId();
							    st.arrival_time = arrivalTime;
							    st.departure_time = cumulativeTime;
							    st.pickup_type = stopTime.stop.pickupType != null ? stopTime.stop.pickupType.toGtfsValue() : 0;
							    st.drop_off_type = stopTime.stop.dropOffType != null ? stopTime.stop.dropOffType.toGtfsValue() : 0;
							    st.shape_dist_traveled = Double.NaN;
							    st.stop_sequence = stopTime.stopSequence;
							    st.timepoint = stopTime.timepoint != null ? (stopTime.timepoint ? 1 : 0) : st.INT_MISSING;
								
								feed.stop_times.put(new Tuple2(st.trip_id, st.stop_sequence), st);
							}
						}
						else {
							// timetable based feed						
							List<StopTime> stopTimes = StopTime.find("trip = ? order by stopSequence", trip).fetch();
							
							for(StopTime stopTime : stopTimes) {
								if (!stopList.contains(stopTime.stop.id)) {
									com.conveyal.gtfs.model.Stop stop = stopTime.stop.toGtfs(); 
									feed.stops.put(stop.stop_id, stop);
									stopList.add(stopTime.stop.id);
								}
								
								com.conveyal.gtfs.model.StopTime st = stopTime.toGtfs();
								feed.stop_times.put(new Tuple2(st.trip_id, st.stop_sequence), st);
							}
						}
					}
				}
				
			}
			
			feed.toFile(gtfsZip.getAbsolutePath());
			
			snapshotExport.status = GtfsSnapshotExportStatus.SUCCESS;
			
			snapshotExport.save();
		
		}
		catch(Exception e)
		{
			Logger.error("error");
			e.printStackTrace();
		}
	}

	public static int toGtfsDate(Date date) {
		LocalDate d = new LocalDate(date.getTime());
		return
				d.getYear() * 10000 +
				d.getMonthOfYear() * 100 +
				d.getDayOfMonth();				
	}
}

