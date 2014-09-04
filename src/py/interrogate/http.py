import urllib
import urllib2
import httplib
import time
import logging
import sqlite3
import xml.etree.ElementTree
import promise
import zlib


logging.basicConfig(level=logging.INFO)
_LOG = logging.getLogger(__name__)


def get_current_time_millis():
  return int(time.time() * 1000)


# Rate limiter that issues permits at a certain rate but only allows a certain
# number of permits to accumulate regardless of how long you wait.
class LeakyBucket(object):

  def __init__(self, permits_per_sec, max_accumulation):
    self.millis_per_permit = 1000.0 / permits_per_sec
    self.max_accumulation = max_accumulation
    self.last_permit = self.get_current_time_millis()

  # Returns the current time in millis since epoch.
  def get_current_time_millis(self):
    return get_current_time_millis()

  # Sleeps for the given number of milliseconds.
  def sleep_millis(self, millis):
    secs = millis / 1000.0
    _LOG.info("Waiting %ss. for permit", int(secs))
    time.sleep(secs)

  # Explicitly sets the time of the last permit.
  def set_last_permit(self, value):
    self.last_permit = value
    return self

  # Waits until the next permit is issued.
  def wait_for_permit(self):
    current_time = self.get_current_time_millis()
    self._limit_accumulation(current_time)
    self._wait_for_next_permit(current_time)

  # Waits until the next permit it issued, ignoring accumulation.
  def _wait_for_next_permit(self, current_time):
    next_permit = self.last_permit + self.millis_per_permit
    if current_time < next_permit:
      self.sleep_millis(next_permit - current_time)
    self.last_permit = next_permit

  # Ensures that no more than the allowed number of permits has accumulated.
  def _limit_accumulation(self, current_time):
    accumulation = (current_time - self.last_permit) / self.millis_per_permit
    if accumulation > self.max_accumulation:
      new_last_permit = current_time - (self.max_accumulation * self.millis_per_permit)
      assert new_last_permit > self.last_permit
      self.last_permit = new_last_permit


# A persistent cache that stores raw http request information.
class HttpRequestCache(object):

  def __init__(self, filename):
    self.db = sqlite3.connect(filename)
    self.db.execute("CREATE TABLE IF NOT EXISTS requests (timestamp, url, response)")

  # Returns the latest response to a request to the given url, None if we
  # haven't seen that url before.
  def get_response(self, url):
    cursor = self.db.execute("""
      SELECT response
      FROM requests
      WHERE url = ?
      ORDER BY timestamp DESC
    """, (url,))
    result = cursor.fetchone()
    if result is None:
      return None
    else:
      response_zip = result[0]
      response_str = zlib.decompress(response_zip)
      return response_str.decode("utf-8")

  # Returns the timestamp of the latest request to any url.
  def get_latest_timestamp(self):
    cursor = self.db.execute("""
      SELECT timestamp
      FROM requests
      ORDER BY timestamp DESC
    """)
    first = cursor.fetchone()
    if first is None:
      return 0
    else:
      return first[0]

  # Records a response to a backend request.
  def add_response(self, timestamp, url, response):
    # Responses are typically xml which is highly verbose and redundant and so
    # take up obscene amounts of space if not zipped. The unicode/buffer
    # conversion stuff is really fragile so watch out if you change it.
    response_str = response.encode("utf-8")
    response_zip = buffer(zlib.compress(response_str, 9))
    self.db.execute("""
      INSERT INTO requests
      VALUES (?, ?, ?)
    """, (timestamp, url, response_zip))
    self.db.commit()

  # Closes the connection to the database, flushing any outstanding writes.
  def close(self):
    self.db.close()


# Records state about an http request. The main purpose of this class is to
# accumulate parameters and properly encode the url.
class HttpRequest(object):

  def __init__(self, path):
    self.path = path
    self.params = []

  # Add a set of query parameters to this request.
  def add_param(self, **kwargs):
    for (name, raw_value) in kwargs.items():
      str_value = unicode(raw_value).encode("utf8")
      self.params.append((name, str_value))
    return self

  # Returns the full url of this request.
  def get_url(self):
    if len(self.params) == 0:
      return self.path
    else:
      params = urllib.urlencode(self.params)
      return "%s?%s" % (self.path, params)


# A http request proxy that keeps track of request caching and rate limiting.
class HttpProxy(object):

  def __init__(self, scheduler, cache, user_agent, reqs_per_sec, max_accum):
    self.scheduler = scheduler
    self.cache = HttpRequestCache(cache)
    self.limiter = LeakyBucket(reqs_per_sec, max_accum)
    self.limiter.set_last_permit(self.cache.get_latest_timestamp())
    self.user_agent = user_agent

  # Issues the given request, returning a promise for the text result.
  def fetch_text(self, request):
    url = request.get_url()
    # Try fetching the response from the cache.
    cached_response = self.cache.get_response(url)
    if cached_response is None:
      return self.scheduler.delay(lambda: self._fetch_url_from_backend(url))
    else:
      return self.scheduler.value(cached_response)

  # Issues the given request, returning a promise for the xml result.
  def fetch_xml(self, request):
    def parse_xml(text):
      return xml.etree.ElementTree.fromstring(text.encode("utf8"))
    return self.fetch_text(request).then(parse_xml)

  # Returns a promise for the result of fetching the given url from this proxy's
  # backend. This also takes care of caching the result.
  def _fetch_url_from_backend(self, url):
    self.limiter.wait_for_permit()
    _LOG.info("Backend: %s" % url)
    request = urllib2.Request(url)
    request.add_header("User-Agent", self.user_agent)
    timestamp = get_current_time_millis()
    response = urllib2.urlopen(request)
    raw_result = response.read()
    result = unicode(raw_result.decode("utf8"))
    self.cache.add_response(timestamp, url, result)
    return result

  def close(self):
    self.cache.close()
