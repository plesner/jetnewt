import http
import promise
import clock
import abc
import logging


logging.basicConfig(level=logging.INFO)
_LOG = logging.getLogger(__name__)


ARRIVALS = "arrivals"
DEPARTURES = "departures"
ARRIVAL = "arrival"
DEPARTURE = "departure"


# Common superclass for arrivals and departures requests.
class AbstractTransitRequest(object):

  def __init__(self):
    self.id = None
    self.timestamp = None

  # Sets the id of the location to fetch information about.
  def set_id(self, value):
    self.id = value
    return self

  # Sets the time to fetch information from.
  def set_timestamp(self, value):
    self.timestamp = value
    return self

  # Returns the date string corresponding to the timestamp.
  def get_date(self):
    return clock.Timestamp.to_date(self.timestamp)

  # Returns the time string corresponding to the timestamp.
  def get_time(self):
    return clock.Timestamp.to_time(self.timestamp)


# A request for an arrivals board.
class ArrivalsRequest(AbstractTransitRequest):
  
  def get_http_request(self, service):
    return (http.HttpRequest(service.get_request_path("arrivalBoard"))
      .add_param(id=self.id)
      .add_param(date=self.get_date())
      .add_param(time=self.get_time()))

  def process_response(self, url, xml):
    return ArrivalsResponse(url, xml)


class ArrivalsResponse(object):

  def __init__(self, url, xml):
    self.arrivals = [Arrival(url, elm) for elm in xml.findall("Arrival")]

  def get_arrivals(self):
    return self.arrivals

  def get_transits(self):
    return self.get_arrivals()


# A request for an departures board.
class DeparturesRequest(AbstractTransitRequest):
  
  def get_http_request(self, service):
    return (http.HttpRequest(service.get_request_path("departureBoard"))
      .add_param(id=self.id)
      .add_param(date=self.get_date())
      .add_param(time=self.get_time()))

  def process_response(self, url, xml):
    return DepartureResponse(url, xml)


class DepartureResponse(object):

  def __init__(self, url, xml):
    self.departures = [Departure(url, elm) for elm in xml.findall("Departure")]

  def get_departures(self):
    return self.departures

  def get_transits(self):
    return self.get_departures()


# Abstract superclass of an individual departure or arrival.
class AbstractTransit(object):
  __metaclass__ = abc.ABCMeta

  def __init__(self, source_url, xml):
    self.source_url = source_url
    self.date = xml.get("date")
    self.time = xml.get("time")
    self.timestamp = clock.Timestamp.from_date_time(self.date, self.time)
    self.route_name = xml.get("name")
    self.stop = xml.get("stop")
    self.journey_url = xml.find("JourneyDetailRef").get("ref")

  def get_timestamp(self):
    return self.timestamp

  def get_route_name(self):
    return self.route_name

  def get_stop(self):
    return self.stop

  # Returns the url of the request that returned this transit.
  def get_source_url(self):
    return self.source_url

  # Returns the url to fetch to get journey details about this transit.
  def get_journey_url(self):
    return self.journey_url

  # Return a tuple of values that uniquely identifies this transit. The lexical
  # ordering of the tuples defined the canonical ordering of transits so you
  # typically want to do (timestamp, name, ...).
  @abc.abstractmethod
  def get_unique_key(self, input):
    pass

  # Returns the terminus of this transit. For arrivals that would be the start,
  # for departures the end.
  @abc.abstractmethod
  def get_terminus(self):
    pass

  def __unicode__(self):
    return "%s@%s(%s %s)" % (self.route_name, self.stop, self.date, self.time)

  def __str__(self):
    return unicode(self).encode("utf-8")


# An individual arrival.
class Arrival(AbstractTransit):

  def __init__(self, url, xml):
    super(Arrival, self).__init__(url, xml)
    self.start = xml.get("origin")

  def get_start(self):
    return self.start

  def get_unique_key(self):
    return (self.timestamp, self.route_name, self.start, self.stop)

  def get_terminus(self):
    return self.get_start()

  def get_type(self):
    return ARRIVAL

  def __unicode__(self):
    return "%s->%s" % (self.start, super(Arrival, self).__unicode__())


# An individual departure.
class Departure(AbstractTransit):

  def __init__(self, url, xml):
    super(Departure, self).__init__(url, xml)
    self.end = xml.get("finalStop")

  def get_end(self):
    return self.end

  def get_unique_key(self):
    return (self.timestamp, self.route_name, self.end, self.stop)

  def get_terminus(self):
    return self.get_end()

  def get_type(self):
    return DEPARTURE

  def __unicode__(self):
    return "%s->%s" % (super(Departure, self).__unicode__(), self.end)


# A request for details about a particular arrival/departure. These can't be
# built really, the url to use is given in the response of another request.
class JourneyRequest(object):

  def __init__(self, url, transit):
    self.url = url
    self.transit = transit

  def get_http_request(self, service):
    return http.HttpRequest(self.url)

  def process_response(self, url, xml):
    return JourneyResponse(url, self.transit, xml)


class JourneyResponse(object):

  def __init__(self, source_url, transit, xml):
    self.source_url = source_url
    self.transit = transit
    journey_name = xml.find("JourneyName")
    if journey_name is None:
      error = InvalidResponse("Invalid XML response to %s" % source_url)
      error.add_invalid_url(source_url)
      error.add_invalid_url(transit.get_source_url())
      raise error
    self.route_name = journey_name.get("name")
    self.stops = map(self._wrap_stop, xml.findall("Stop"))

  def get_source_url(self):
    return self.source_url

  def get_transit(self):
    return self.transit

  def get_route_name(self):
    return self.route_name

  def get_stops(self):
    return self.stops

  def _wrap_stop(self, xml):
    return JourneyStop(self, xml)


class JourneyStop(object):

  def __init__(self, journey, xml):
    self.journey = journey
    self.name = xml.get("name")
    self.arr_date = xml.get("arrDate", None)
    self.arr_time = xml.get("arrTime", None)
    self.arrival = None
    if not (self.arr_date is None or self.arr_time is None):
      self.arrival = clock.Timestamp.from_date_time(self.arr_date, self.arr_time)
    self.dep_date = xml.get("depDate", None)
    self.dep_time = xml.get("depTime", None)
    self.departure = None
    if not (self.dep_date is None or self.dep_time is None):
      self.departure = clock.Timestamp.from_date_time(self.dep_date, self.dep_time)

  def get_route_name(self):
    return self.journey.get_route_name()

  def get_name(self):
    return self.name

  def get_arrival(self):
    return self.arrival

  def get_departure(self):
    return self.departure

  def __unicode__(self):
    return "stop { name: %s, arr: %s, dep: %s }" % (self.name, self.arrival, self.departure)

  def __str__(self):
    return unicode(self).encode("utf-8")


# A request for location information given the prefix of a location name.
class LocationRequest(object):

  def __init__(self):
    self.input = None

  # Sets the prefix to search for.
  def set_input(self, value):
    self.input = value
    return self

  def get_http_request(self, service):
    return (http.HttpRequest(service.get_request_path("location"))
      .add_param(input=self.input))

  def process_response(self, url, xml):
    return LocationResponse(xml)


# The result of a location request.
class LocationResponse(object):

  def __init__(self, xml):
    self.stop_locations = map(StopLocation, xml.findall("StopLocation"))

  # Returns a list of the stop location object contained in the response.
  def get_stop_locations(self):
    return self.stop_locations


# Wrapper around an xml stop location object.
class StopLocation(object):

  def __init__(self, xml):
    self.name = xml.get("name")
    self.id = xml.get("id")

  def get_name(self):
    return self.name

  def get_id(self):
    return self.id

  def __str__(self):
    return "stop location {name: %s, id: %s}" % (self.name, self.id)


# Error thrown when resolving a location fails.
class UnknownLocation(Exception):

  def __init__(self, name):
    self.name = name

  def __str__(self):
    return repr(self.name)


# Wrapper that keeps track of information about geographic locations.
class LocationRepository(object):

  def __init__(self, scheduler, service):
    self.scheduler = scheduler
    self.service = service
    self.name_to_info = {}
    self.queried = set()
    self.current_request = None

  # Returns a promise for the information about the location with the given
  # name.
  def get_info_by_name(self, name):
    if name in self.name_to_info:
      return self.name_to_info[name]
    if name in self.queried:
      # If we've queried that specific name and still have no info that means
      # there is no info to get so we stop looking here.
      return self.scheduler.failure(UnknownLocation(name))
    # There can only be one backend request active at any one time so we only
    # issue one if one isn't already active. This is because the response from
    # one request contains information about many stops so we always want to
    # check that the response to the current request didn't contain the info
    # we're looking for before issuing a new request for it.
    if (self.current_request is None) or self.current_request.is_resolved():
      # There is no active request so we get to start one.
      def process_response(response):
        return self._process_response(name, response)
      self.current_request = self._get_info_from_backend(name).then(process_response)
    # Wait for any currently active requests to finish and then try getting the
    # info we're interested in again. Either we'll know the answer after the
    # request is done or we'll have another chance to issue a request.
    return self.current_request.then(lambda v: self.get_info_by_name(name))

  # Add the information from a location response to the mapping.
  def _process_response(self, name, response):
    self.queried.add(name)
    for location in response.get_stop_locations():
      self.name_to_info[location.get_name()] = self.scheduler.value(location)

  # Request a promise for the result of a location request for the given name
  # from the backend.
  def _get_info_from_backend(self, name):
    return self.service.fetch(LocationRequest().set_input(name))


class InvalidResponse(Exception):

  def __init__(self, message):
    super(InvalidResponse, self).__init__(message)
    self.invalid_urls = []

  def add_invalid_url(self, value):
    self.invalid_urls.append(value)


# High-level interface to rejseplanen.
class Rejseplanen(object):

  def __init__(self, root, scheduler, http_cache, http_user_agent, reqs_per_sec,
      max_accum, parallelism):
    self.scheduler = scheduler
    self.root = root
    self.http = http.HttpProxy(scheduler, cache=http_cache,
      user_agent=http_user_agent, reqs_per_sec=reqs_per_sec, max_accum=max_accum,
      pool_size=parallelism)
    self.location_repo = LocationRepository(scheduler, self)
    self.journey_cache = {}

  # Returns the full rest api path given an endpoint.
  def get_request_path(self, endpoint):
    return "%s/%s" % (self.root, endpoint)

  def get_backend_stats(self):
    return self.http.get_stats()

  # Issues the given request.
  def fetch(self, request):
    http_request = request.get_http_request(self)
    url = http_request.get_url()
    xml_p = self.http.fetch_xml(http_request)
    def process_xml(xml):
      try:
        return request.process_response(url, xml)
      except InvalidResponse, e:
        for invalid_url in e.invalid_urls:
          _LOG.warning("Dropping %s from http cache", invalid_url)
          self.http.drop_from_cache(invalid_url)
        raise e
    return xml_p.then(process_xml)

  # Returns a promise that will be resolved with location information about
  # the given name.
  def get_location_info_by_name(self, name):
    return self.location_repo.get_info_by_name(name)

  # Returns a promise for the result of an arrivals request for the given id at
  # the given time from the backend.
  def get_arrivals(self, id, timestamp):
    return self.fetch(ArrivalsRequest().set_id(id).set_timestamp(timestamp))

  # Returns a promise for the result of an departures request for the given id
  # at the given time from the backend.
  def get_departures(self, id, timestamp):
    return self.fetch(DeparturesRequest().set_id(id).set_timestamp(timestamp))

  # Returns the arrivals/departures for the given id starting at the given
  # timestamp.
  def get_transits(self, type, id, timestamp):
    if type == ARRIVALS:
      return self.get_arrivals(id, timestamp)
    else:
      assert type == DEPARTURES
      return self.get_departures(id, timestamp)

  def get_journey(self, transit):
    url = transit.get_journey_url()
    if url in self.journey_cache:
      return self.journey_cache[url]
    result = self.fetch(JourneyRequest(url, transit))
    self.journey_cache[url] = result
    return result

  # Closes the http connection down cleanly.
  def close(self):
    self.http.close()
