import urllib
import urllib2
import httplib
import time
import logging
import sqlite3
import xml.etree.ElementTree
import promise
import zlib
import threading
import Queue
import sys


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
    self.lock = threading.Lock()

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
    self.lock.acquire()
    try:
      current_time = self.get_current_time_millis()
      self._limit_accumulation(current_time)
      self._wait_for_next_permit(current_time)
    finally:
      self.lock.release()

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
    self.local = threading.local()
    self.tasks = Queue.Queue()
    self.filename = filename
    self.keep_going = True
    thread = threading.Thread(target=self._run_owner_thread)
    thread.daemon = True
    thread.start()

  # Only the thread that creates the connection is allowed to use it so we
  # spawn an owner thread which does all the work.
  def _run_owner_thread(self):
    self.db = sqlite3.connect(self.filename)
    self.db.execute("CREATE TABLE IF NOT EXISTS requests (timestamp, url, response)")
    while self.keep_going:
      (thunk, chan) = self.tasks.get()
      chan.put(thunk())

  # Submit a task to be executed on the owner thread. The submitting thread
  # will block until the task has been executed. The result will be the task's
  # result.
  def _run_as_owner(self, thunk):
    # Use a thread local queue to communicate the result, creating one if it
    # doesn't already exist.
    chan = getattr(self.local, "chan", None)
    if chan is None:
      chan = Queue.Queue()
      self.local.chan = chan
    # Enqueue the task for the owner to execute.
    self.tasks.put((thunk, chan))
    # Wait until it's done.
    return chan.get()

  # Returns the latest response to a request to the given url, None if we
  # haven't seen that url before.
  def get_response(self, url):
    def do_get_response():
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
    return self._run_as_owner(do_get_response)

  # Returns the timestamp of the latest request to any url.
  def get_latest_timestamp(self):
    def do_get_latest_timestamp():
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
    return self._run_as_owner(do_get_latest_timestamp)

  # Records a response to a backend request.
  def add_response(self, timestamp, url, response):
    def do_add_response():
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
    return self._run_as_owner(do_add_response)

  # Closes the connection to the database, flushing any outstanding writes.
  def close(self):
    def do_close():
      self.keep_going = False
      self.db.close()
    return self._run_as_owner(do_close)


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


# A really simple pool that distributes submitted tasks among N threads.
class SimpleThreadPool(object):

  def __init__(self, size):
    self.tasks = Queue.Queue()
    for i in range(0, size):
      name = "W%s" % i
      thread = threading.Thread(name=name, target=self._run_worker)
      thread.daemon = True
      thread.start()

  def _run_worker(self):
    while True:
      thunk = self.tasks.get()
      try:
        thunk()
      except Exception, e:
        _LOG.error("%s", e)

  # Submit a task to be executed eventually by one of the worker threads.
  def submit(self, thunk):
    self.tasks.put(thunk)


# A http request proxy that keeps track of request caching and rate limiting.
class HttpProxy(object):

  def __init__(self, scheduler, cache, user_agent, reqs_per_sec, max_accum,
      pool_size):
    self.scheduler = scheduler
    self.cache = HttpRequestCache(cache)
    self.limiter = LeakyBucket(reqs_per_sec, max_accum)
    self.limiter.set_last_permit(self.cache.get_latest_timestamp())
    self.user_agent = user_agent
    self.thread_pool = SimpleThreadPool(pool_size)
    self.in_flight_lock = threading.Lock()
    self.in_flight = {}
    self.launch_times = set()

  # Issues the given request, returning a promise for the text result.
  def fetch_text(self, request):
    url = request.get_url()
    # Try fetching the response from the cache.
    cached_response = self.cache.get_response(url)
    if cached_response is None:
      return self._fetch_url_from_backend(url)
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
    self.in_flight_lock.acquire()
    try:
      return self._fetch_url_from_backend_unsafe(url)
    finally:
      self.in_flight_lock.release()

  # Does the work of fetching from the backend assuming that the in-flight lock
  # is held by the current thread. 
  def _fetch_url_from_backend_unsafe(self, url):
    in_flight = self.in_flight.get(url, None)
    if not in_flight is None:
      # There is already a request for the given url in flight so just use that
      # value.
      return in_flight
    def do_fetch_url():
      # Wait for the rate limiter to give permission.
      self.limiter.wait_for_permit()
      thread_name = threading.current_thread().name
      _LOG.info("Backend [%s]: %s" % (thread_name, url))
      # Build and send the request.
      request = urllib2.Request(url)
      request.add_header("User-Agent", self.user_agent)
      timestamp = get_current_time_millis()
      response = urllib2.urlopen(request)
      raw_result = response.read()
      decoded_result = raw_result.decode("utf8")
      # Propagate and cache the result.
      result.fulfill(unicode(decoded_result))
      self.cache.add_response(timestamp, url, decoded_result)
      # Remove this from the set of requests in flight.
      self.in_flight_lock.acquire()
      try:
        self.launch_times.add(timestamp)
        del self.in_flight[url]
      finally:
        self.in_flight_lock.release()
    # Launch a new request.
    result = self.scheduler.new_promise()
    self.in_flight[url] = result
    self.thread_pool.submit(do_fetch_url)
    return result

  def get_stats(self):
    if len(self.launch_times) < 2:
      return None
    times = sorted(self.launch_times)
    min_launch = times[0]
    max_launch = times[-1]
    total_time_millis = max_launch - min_launch
    total_time_secs = total_time_millis / 1000.0
    secs_per_req = total_time_secs / (len(self.launch_times) - 1)
    reqs_per_sec = 1.0 / secs_per_req
    return {"reqs_per_sec": reqs_per_sec}

  def close(self):
    self.cache.close()
