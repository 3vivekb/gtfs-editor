package models;

import java.io.File;
import java.io.Serializable;

import org.mapdb.Fun.Tuple2;

import utils.JacksonSerializers;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Represents a snapshot of an agency database.
 * @author mattwigway
 *
 */
public class Snapshot implements Serializable {
	public static final long serialVersionUID = -2450165077572197392L;
	
	/** Is this snapshot the current snapshot - the most recently created or restored (i.e. the most current view of what's in master */
	public boolean current;
	
	/** The version of this snapshot */
	public int version;
	
	/** The name of this snapshot */
	public String name;
	
	/** ID: agency ID, version */
    @JsonSerialize(using=JacksonSerializers.Tuple2IntSerializer.class)
    @JsonDeserialize(using=JacksonSerializers.Tuple2IntDeserializer.class)
	public Tuple2<String, Integer> id;
	
	/** The agency associated with this */
	public String agencyId;
	
	/** the date/time this snapshot was taken (millis since epoch) */
	public long snapshotTime;
	
	/** Used for Jackson deserialization */
	public Snapshot () {}
	
	public Snapshot (String agencyId, int version) {
		this.agencyId = agencyId;
		this.version = version;
		this.computeId();
	}
	
	/** create an ID for this snapshot based on agency ID and version */
	public void computeId () {
		this.id = new Tuple2(agencyId, version);
	}
	
	public Snapshot clone () {
		Snapshot s = new Snapshot();
		s.current = this.current;
		s.version = version;
		s.name = name;
		s.id = id;
		s.agencyId = agencyId;
		s.snapshotTime = snapshotTime;
		return s;
	}
}
