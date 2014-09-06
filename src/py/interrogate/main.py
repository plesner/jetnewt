#!/usr/bin/python


# Utility for extracting timetables from rejseplanen.dk. This script runs a
# pipeline that extracts timetable information based on queries to the REST api.
# It is quite gentle -- it is rate limited, never asks for the same url twice.
# The strategy is as follows.
#
#   1. The configuration specifies a small number of _hub_ stops/stations. These
#      are places that, between them, all the routes of interest pass through.
#   2. For each hub a list of all arrivals and departures for some particular
#      day are requested. For each arrival we record the name of the route and
#      the start point, for each destination we record the name and the end
#      point. Since all routes of interest pass through some hub this will give
#      us the start and end points of all routes of interest.
#   3. For each station that is a start point we fetch all departures. For each
#      end point we fetch all arrivals. This will give us the full extent of
#      all the routes we're interested in.
#   4. For each arrival and departure of a route we're interested in at a
#      terminus, fetch the journey details which give all the times and stops
#      for that trip.
#
# For clarity, here's the terminology used in the code.
#
#   Stop: a location where you can get on or off. Stops have a unique string
#     name, ie. "Aarhus H", and a unique string ID, ie. "008600053".
#
#   Route: A named transport route, ie. "Bus 2A". The name of a route doesn't
#     tell you where it can transport you -- each typically has at least two
#     directions, for instance, so routes are more used for organization than
#     for semantics.
#
#   Sub-route: Each route has a number of sub-routes which do tell you where
#     you can be transported. For instance, the route "Bus 2A" has two
#     sub-routes, one for each direction. But they can also have more, for
#     instance if the route alternates destinations between trips.
#
#   Journey: an instance of a sub-route at a particular time. A sub-route is
#     timeless, it's a start and endpoint and the stops and times in between,
#     whereas a journey happens at a concrete time and exactly once.
#
#   Transit: the arrival or departure of a vehicle at a stop. A transit happens
#     at a particular place at a particular time. Arrivals and departures have
#     slightly different information associated with them so we sometimes need
#     to distinguish between them, but often they can be treated together as
#     generic transits.
#
#   Terminus: the start or end of a sub-route. A terminus can be both a start
#     and end at the same time (they typically are).
#
#   Board: a list of arrivals or departures at a particular stop. Like transits,
#     sometimes the two types have to be handled separately but when they're
#     handled together they're called boards.

import time
import re
import logging
import Queue
import promise
import sys
import argparse
import yaml
import rejseplanen
import clock
import collections


logging.basicConfig(level=logging.INFO)
_LOG = logging.getLogger(__name__)


_CHROME_USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.76 Safari/537.36"


# Encapsulates the script configuration settings.
class Config(object):

  def __init__(self, options):
    self.options = options
    self.vars = vars(options)
    self.config = self._parse_config(self.options.config)

  def get_reqs_per_sec(self):
    return self._get_setting("reqs_per_sec", 0.1)

  def get_max_accum(self):
    return self._get_setting("max_accum", 4)

  def get_parallelism(self):
    return self._get_setting("parallelism", 1)

  def get_rest_base_url(self):
    return self._get_setting("rest_base_url", None)

  def get_http_cache(self):
    return self._get_setting("http_cache", "httpcache.db")

  def get_http_user_agent(self):
    return self._get_setting("http_user_agent", _CHROME_USER_AGENT)

  def get_date(self):
    return self._get_setting("date", None)

  def get_time_range_start(self):
    return self._get_subsetting("time_range", "start", None)

  def get_time_range_end(self):
    return self._get_subsetting("time_range", "end", None)

  def get_hubs(self):
    return self.config.get("hubs")

  def get_route_whitelist(self):
    return self.config.get("route_whitelist", [])

  # Returns the value of the setting with the given name.
  def _get_setting(self, name, default=None):
    if self.vars[name] is None:
      return self.config.get(name, default)
    else:
      return self.vars[name]

  def _get_subsetting(self, name, subname, default=None):
    var = self.vars["%s_%s" % (name, subname)]
    if not var is None:
      return var
    members = self.config.get(name, {})
    return members.get(subname, default)

  # Info log all the config settings.
  def log_values(self):
    _LOG.info("reqs per sec: %s", self.get_reqs_per_sec())
    _LOG.info("max accum: %s", self.get_max_accum())
    _LOG.info("rest base url: %s", self.get_rest_base_url())
    _LOG.info("parallelism: %s", self.get_parallelism())
    _LOG.info("http cache: %s", self.get_http_cache())
    _LOG.info("http user agent: %s", self.get_http_user_agent())
    _LOG.info("date: %s" % self.get_date())
    _LOG.info("time range: %s - %s" % (self.get_time_range_start(), self.get_time_range_end()))
    for hub in self.get_hubs():
      _LOG.info("- hub: %s", hub)

  # Raise an error if the configuration is somehow invalid or incomplete.
  def validate(self):
    if self.get_rest_base_url() is None:
      raise AssertionError("No rest_base_url specified")
    if self.get_date() is None:
      raise AssertionError("No date specified")
    if self.get_hubs() is None:
      raise AssertionError("No hubs specified")
    if self.get_time_range_start() is None:
      raise AssertionError("No time range start specified")
    if self.get_time_range_end() is None:
      raise AssertionError("No time range end specified")

  # Parses and returns the yaml config.
  def _parse_config(self, filename):
    return yaml.load(open(filename, "rt"))


# A filter built from a list of regular expressions that contains a string if
# any of the regexps match.
class StringFilter(object):

  def __init__(self, patterns):
    self.patterns = [re.compile("^%s$" % p) for p in patterns]

  def contains(self, input):
    for pattern in self.patterns:
      if not pattern.match(input) is None:
        return True
    return False


# A collection of information about a route.
class RouteInfo(object):

  def __init__(self, name, departure_journeys, arrival_journeys):
    self.name = name
    self.departure_journeys = departure_journeys
    self.arrival_journeys = arrival_journeys

  # Returns a list of the journeys that can be verified as covering the complete
  # route.
  def get_verified_journeys(self):
    def get_stop_key(stop, timestamp):
      stop_name = stop.get_name()
      return (stop_name, timestamp)
    journey_map = {}
    joint_journeys = [
      (True, self.departure_journeys),
      (False, self.arrival_journeys)
    ]
    # Scan through all the journeys, for arrivals and departures, and collect
    # all the identical ones. This keys on just the start and end stops, it
    # should be safe to assume that you won't see two busses with the same
    # name leaving the same place at the same time, and arriving at the same
    # different place at the same time, but taking different paths.
    for (is_depart, journeys) in joint_journeys:
      for journey in journeys:
        transit = journey.get_transit()
        first = journey.get_stops()[0]
        first_key = get_stop_key(first, first.get_departure())
        last = journey.get_stops()[-1]
        last_key = get_stop_key(last, last.get_arrival())
        key = (first_key, last_key)
        if not key in journey_map:
          journey_map[key] = []
        journey_map[key].append((is_depart, journey))
    # Scan through all the pairs to find the ones that have been hit by both an
    # arrival and a departure. Arrivals and departures each fix one terminus but
    # not the other and if we have two together that take the same path then
    # we've got both terminuses fixed and the route is valid.
    verified = {}
    unverified = {}
    for (key, hits) in journey_map.items():
      # Zip out the is_depart and journey element of each pair and make a set of
      # the is_departs. If the set has two elements then it must contain True
      # and False which means that we've seen the same journey from both ends.
      (is_departs, journeys) = zip(*hits)
      if len(set(is_departs)) == 2:
        verified[key] = journeys
      else:
        unverified[key] = journeys
    return (verified, unverified)

# The main class that sets up the interrogation pipeline.
class Interrogate(object):

  def __init__(self, args):
    parser = self._build_option_parser()
    self.options = parser.parse_args(args)
    self.config = Config(self.options)
    self.config.validate()
    self.scheduler = promise.Scheduler()
    self.service = self._new_service()
    self.route_whitelist = StringFilter(self.config.get_route_whitelist())
    self.routes_processed = set()
    self.routes_ignored = set()
    self.transit_cache = {}

  def main(self):
    try:
      self._run()
    finally:
      self._print_stats()
      self._close()

  def _print_stats(self):
    stats = self.service.get_backend_stats()
    if stats is None:
      return
    reqs_per_sec = stats["reqs_per_sec"]
    _LOG.info("average backend qps: %s" % reqs_per_sec)

  def _build_option_parser(self):
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", required=True,
      help="The interrogation config to use")
    parser.add_argument("--reqs-per-sec", type=float,
      help="Max number of requests that can be issued per second (default: 0.1)")
    parser.add_argument("--max_accum", type=float,
      help="Max number of request permits that may accumulate (default: 4)")
    parser.add_argument("--parallelism", type=int,
      help="Max number of simultaneour requests to the backend (default: 1)")
    parser.add_argument("--rest-base-url", type=str,
      help="The base url of the rest api")
    parser.add_argument("--http-cache", type=str,
      help="The file to use for the persistent http cache (default: httpcache.db)")
    parser.add_argument("--http-user-agent", type=str,
      help="The user agent string to use in backend requests (default: chrome's)")
    parser.add_argument("--date", type=str,
      help="The date to fetch plans for")
    parser.add_argument("--time-range-start", type=str,
      help="The beginning of the time range to cover")
    parser.add_argument("--time-range-end", type=str,
      help="The end of the time range to cover")
    return parser

  # Set up the pipeline, then run it.
  def _run(self):
    self.config.log_values()
    done_p = self._build_pipeline()
    while True:
      self.scheduler.run_all_tasks()
      if done_p.is_resolved():
        break
      else:
        time.sleep(0.1)
    print done_p.get_error_trace()
    print done_p.get()
    print "Processed: %s" % ", ".join(sorted(self.routes_processed))
    print "Ignored: %s" % ", ".join(sorted(self.routes_ignored))

  # The toplevel function that creates the entire pipeline without running it.
  def _build_pipeline(self):
    hubs = self.config.get_hubs()
    # Look up all arrivals to and departures from the hubs.
    hub_arrivals_p = self.scheduler.join(map(self._fetch_arrivals_by_name, hubs))
    hub_departures_p = self.scheduler.join(map(self._fetch_departures_by_name, hubs))
    # Extract mappings from route names to their terminuses (start and end
    # stations).
    starts_p = hub_arrivals_p.then(self._extract_terminuses)
    ends_p = hub_departures_p.then(self._extract_terminuses)
    all_terminuses_p = self.scheduler.join([starts_p, ends_p])
    # Join the starts and ends together for each route.
    route_terminuses_p = all_terminuses_p.then(self._join_route_terminuses)
    # Fetch the departures and arrivals for all the terminuses.
    terminus_boards_p = route_terminuses_p.map_dict(self._fetch_terminus_boards)
    # For each transit at a terminus of a route we're interested in, fetch the
    # journey details.
    journeys_p = terminus_boards_p.map_dict(self._fetch_terminus_board_journeys)
    # For each route bundle all the information we've fetched up in an object.
    routes_p = journeys_p.map_dict(self._bundle_route)

    return routes_p

  # Given the name of a stop, fetches the full departure board for that stop.
  def _fetch_departures_by_name(self, name):
    return self._fetch_transits_by_name(rejseplanen.DEPARTURES, name)

  # Given the name of a stop, fetches the full arrival board for that stop.
  def _fetch_arrivals_by_name(self, name):
    return self._fetch_transits_by_name(rejseplanen.ARRIVALS, name)

  # Given the name of a stop, fetches either the departures or the arrivals
  # depending on the type argument.
  def _fetch_transits_by_name(self, type, name):
    info_p = self.service.get_location_info_by_name(name)
    id_p = info_p.then(lambda info: info.get_id())
    return id_p.then(lambda id: self._fetch_transits_by_id(type, id))

  # Fetches all the transits boards of the given type for the given id. The time
  # to fetch within is given by the configuration.
  def _fetch_transits_by_id(self, type, id):
    cache_key = (type, id)
    if cache_key in self.transit_cache:
      return self.transit_cache[cache_key]
    date = self.config.get_date()
    range_start = self.config.get_time_range_start()
    range_end = self.config.get_time_range_end()
    start = clock.Timestamp.from_date_time(date, range_start)
    end = clock.Timestamp.from_date_time(date, range_end)
    result = self._fetch_transits_within(type, id, start, end).then(self._merge_transits)
    self.transit_cache[cache_key] = result
    return result

  # Fetches all the arrivals boards for the given id within the given
  # timestamps. This potentially causes a number of requests to be sent to
  # the service to cover the whole time period. The result is a list of transit
  # responses.
  def _fetch_transits_within(self, type, id, start, end):
    responses = []
    # Adds a response to the result and possibly issues the remaining requests
    # if there are more to send.
    def process_response(response):
      responses.append(response)
      highest_time = 0
      for transit in response.get_transits():
        highest_time = max(highest_time, transit.get_timestamp())
      if highest_time <= end:
        # The latest transit is before the requested endtime so we need to go
        # again, starting from the highest time seen. This will cause transits
        # at the highest time to be returned again but we'll dedup those later.
        return send_next_request(highest_time)
      else:
        return responses
    # Sends the remaining requests, starting from the given timestamp.
    def send_next_request(timestamp):
      response_p = self.service.get_transits(type, id, timestamp)
      return response_p.then(process_response)
    # Send off a request just for the start time. If we need more then the
    # post-processing of the result will take care of issuing more requests.
    return send_next_request(start)

  # Given a list of transit board responses (which is a list of lists of
  # transits) merges all the sublists together to a flat list of transits where
  # the transits in the input have been sorted, checked against the whitelist,
  # and dedup'ed.
  def _merge_transits(self, all_responses):
    entries = {}
    for stop_response in all_responses:
      for transit in stop_response.get_transits():
        timestamp = transit.get_timestamp()
        route_name = transit.get_route_name()
        should_process = self.route_whitelist.contains(route_name)
        if should_process:
          self.routes_processed.add(route_name)
          stop_name = transit.get_stop()
          key = transit.get_unique_key()
          entries[key] = transit
        else:
          self.routes_ignored.add(route_name)
    result = []
    for key in sorted(entries.keys()):
      result.append(entries[key])
    return result

  # Given a list of transit boards, returns the terminuses (starts for arrivals,
  # ends for departures) as a map from route names to the set of terminuses for
  # that route name. Note that this gives you only one side, either starts or
  # stops.
  def _extract_terminuses(self, all_transits):
    result = collections.OrderedDict()
    for hub_transits in all_transits:
      for transit in hub_transits:
        route_name = transit.get_route_name()
        if not route_name in result:
          result[route_name] = set()
        result[route_name].add(transit.get_terminus())
    return result

  # Given two maps of terminuses, one from route names to starts and one from
  # route names to ends, matches the starts and ends together for each route and
  # returns a mapping from route_name to (starts, ends) tuples.
  def _join_route_terminuses(self, input):
    (starts, ends) = input
    start_routes = set(starts.keys())
    end_routes = set(ends.keys())
    for route in start_routes.difference(end_routes):
      _LOG.warn("Route '%s' has start but no end." % route)
    for route in end_routes.difference(start_routes):
      _LOG.warn("Route '%s' has end but no start." % route)
    all_routes = sorted(start_routes.union(end_routes))
    result = collections.OrderedDict()
    for route_name in sorted(start_routes.intersection(end_routes)):
      route_starts = starts.get(route_name, [])
      route_ends = ends.get(route_name, [])
      result[route_name] = (route_starts, route_ends)
    return result

  # Given a route name and a (starts, ends) tuple returns a promise for the
  # transit board for all the terminuses: departures for the starts and arrivals
  # for the ends. The result is a promise for a tuple containing (departures,
  # arrivals).
  def _fetch_terminus_boards(self, route_name, input):
    (starts, ends) = input
    # Fetch departures for all the start points.
    departures_p = self.scheduler.join(map(self._fetch_departures_by_name, starts))
    arrivals_p = self.scheduler.join(map(self._fetch_arrivals_by_name, ends))
    return self.scheduler.join([departures_p, arrivals_p])

  # Given a route name and a (departure boards, arrival boards) tuple, fetches
  # the journey details for all the transit board transits of the route. The
  # result is a pair or (departure journeys, arrival journeys) matching the
  # input lists.
  def _fetch_terminus_board_journeys(self, route_name, input):
    (departures, arrivals) = input
    # Filter out the transits that don't involve the route we're interested
    # in.
    def fetch_filtered_journeys(all_transits):
      all_journeys_ps = []
      for terminus_transits in all_transits:
        for transit in terminus_transits:
          if transit.get_route_name() != route_name:
            continue
          journey_p = self.service.get_journey(transit)
          all_journeys_ps.append(journey_p)
      return self.scheduler.join(all_journeys_ps)
    departure_journeys_p = fetch_filtered_journeys(departures)
    arrival_journeys_p = fetch_filtered_journeys(arrivals)
    return self.scheduler.join([departure_journeys_p, arrival_journeys_p])

  def _bundle_route(self, route_name, input):
    (departure_journeys, arrival_journeys) = input
    result = RouteInfo(route_name, departure_journeys, arrival_journeys)
    (verified, unverified) = result.get_verified_journeys()
    vs = len(verified)
    us = len(unverified)
    return "%s ~ %s (%s)" % (vs, us, int((100.0 * vs) / (vs + us)))

  # Creates and returns the underlying rest service wrapper.
  def _new_service(self):
    return rejseplanen.Rejseplanen(
      self.config.get_rest_base_url(),
      self.scheduler,
      http_cache=self.config.get_http_cache(),
      http_user_agent=self.config.get_http_user_agent(),
      reqs_per_sec=self.config.get_reqs_per_sec(),
      max_accum=self.config.get_max_accum(),
      parallelism=self.config.get_parallelism())

  def _close(self):
    self.service.close()


if __name__ == "__main__":
  Interrogate(sys.argv[1:]).main()
