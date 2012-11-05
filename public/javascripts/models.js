var GtfsEditor = GtfsEditor || {};

(function(G, $) {

  var jsonifyValidator = function(attrs, property, model) {
    var obj;
    if (_.isString(attrs[property])) {
      try {
        obj = JSON.parse(attrs[property]);
      } catch(e) {
        // location was a string, but not JSON. This will break on the server.
      }
      if (obj) {
        model.set(property, obj, {silent: true});
      }
    }
  };

  G.Agency = Backbone.Model.extend({
    defaults: {
      id: null,
      gtfsAgencyId: null,
      name: null,
      url: null,
      timezone: null,
      lang: null,
      phone: null
    }
  });

  G.Agencies = Backbone.Collection.extend({
    type: 'Agencies',
    model: G.Agency,
    url: '/api/agency/'
  });

  G.Route = Backbone.Model.extend({
    defaults: {
      id: null,
      gtfsRouteId: null,
      routeShortName: null,
      routeLongName: null,
      routeDesc: null,
      routeType: null,
      routeUrl: null,
      routeColor: null,
      routeTextColor: null,
      weekday: null,
      saturday: null,
      sunday: null,
      agency: null
    }
  });

  G.Routes = Backbone.Collection.extend({
    type: 'Routes',
    model: G.Route,
    url: '/api/route/'
  });

  G.Stop = Backbone.Model.extend({
    defaults: {
      id: null,
      gtfsStopId: null,
      stopCode: null,
      stopName: null,
      stopDesc: null,
      zoneId: null,
      stopUrl: null,
      agency: null,
      locationType: null,
      parentStation: null,
      location: null
    },
    // This function serves to allow stringified JSON as a valid input. A bit
    // hacky, but it's a great place to do it.
    validate: function(attrs) {
      jsonifyValidator(attrs, 'location', this);
    }
  });

  G.Stops = Backbone.Collection.extend({
    type: 'Stops',
    model: G.Stop,
    url: '/api/stop/'
  });

  // Untested
  //    |
  //    V
  G.TripPattern = Backbone.Model.extend({
    defaults: {
      id: null,
      name: null,
      headsign: null,
      patternStops: [],
      shape: null,
      route: null
    },

    initialize: function() {
      this.on('change', this.sortPatternStops, this);

      this.sortPatternStops();
    },

    sortPatternStops: function() {
      var patternStops = _.sortBy(this.get('patternStops'), function(ps){
        return ps.stopSequence;
      });

      this.set('patternStops', patternStops, {silent: true});
    },

    validate: function(attrs) {
      jsonifyValidator(attrs, 'patternStops', this);

      // Override the sequence value to match the array order
      this.sortPatternStops();
      _.each(this.get('patternStops'), function(ps, i) {
        ps.stopSequence = i+1;
      });
    },
    // name, headsign, alignment, stop_times[], shape, route_id (fk)
      // stop_id, travel_time, dwell_time

    addStop: function(stopTime) {
      var patternStops = this.get('patternStops').push(stopTime);
      this.set('patternStops', patternStops);
    },

    addStopAt: function(stopTime, i) {
      var patternStops = this.get('patternStops').splice(i, 0, stopTime);
      this.set('patternStops', patternStops);
    },

    removeStopAt: function(i) {
      var patternStops = this.get('patternStops').splice(i, 1)[0];
      this.set('patternStops', patternStops);
      return patternStops;
    },

    moveStopTo: function(fromIndex, toIndex) {
      var stopTimes = this.get('patternStops'),
          stopTime;

      stopTime = this.removeStopAt(fromIndex);
      this.addStopAt(stopTime, toIndex);
    }
  });

  G.TripPatterns = Backbone.Collection.extend({
    type: 'TripPatterns',
    model: G.TripPattern,
    url: '/api/trippattern/'
  });

  G.Calendars = Backbone.Collection.extend({
    // days, start_date, end_date, exceptions[]
  });

  G.Trips = Backbone.Collection.extend({
    // trip_pattern_id (fk), cal_id (fk),
      // start_time (for static trips)
      // start_time, end_time, headway (for frequencies)
      // is_frequency
  });

})(GtfsEditor, jQuery);