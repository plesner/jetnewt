import Queue
import traceback
import logging
import collections


logging.basicConfig(level=logging.INFO)
_LOG = logging.getLogger(__name__)


# A task to be executed by the scheduler.
class Task(object):

  def __init__(self, on_run, on_fail, promise):
    self.on_run = on_run
    self.on_fail = on_fail
    self.promise = promise

  # Execute this task, storing the result in the promise.
  def run(self, input):
    if self.on_run is None:
      return
    try:
      result = (self.on_run)(input)
      self.promise.fulfill(result)
    except Exception, error:
      trace = traceback.format_exc()
      self.promise.fail(error, trace)

  # Fails this task's promise without executing the thunk.
  def fail(self, error, trace):
    if not self.on_fail is None:
      (self.on_fail)(error, trace)
    if not self.promise is None:
      self.promise.fail(error, trace)


# Task scheduler used to linearize the execution of promise actions.
class Scheduler(object):

  def __init__(self):
    self.thunks = Queue.Queue()

  # Creates and returns a new unresolved promise.
  def new_promise(self):
    return Promise(self)

  # Adds the execution of the given thunk to the workqueue and returns a promise
  # for the eventual execution of it.
  def delay(self, thunk):
    result_p = self.new_promise()
    task = Task(lambda _: thunk(), None, result_p)
    self.add_thunk(lambda: task.run(None))
    return result_p

  # Returns a promise that has already been fulfilled with the given value.
  def value(self, value):
    result_p = self.new_promise()
    result_p.fulfill(value)
    return result_p

  # Returns a promise that has already failed with the given error.
  def failure(self, error, trace=None):
    result_p = self.new_promise()
    result_p.fail(error, trace)
    return result_p

  # Given a list of promises, returns a promise that resolves to a list of the
  # results or fails if any of the promises fails.
  def join(self, promises):
    # This is pretty straightforward but it's made unnecessarily complicated by
    # python's inane scoping rules. My apologies.
    result_p = self.new_promise()
    length = len(promises)
    remaining = [length]
    values = [None] * length
    def maybe_fulfill_on_success():
      remaining[0] -= 1
      if remaining[0] == 0:
        result_p.fulfill(values)
    for outer_index in range(0, length):
      def add_handler(index):
        def on_success(value):
          values[index] = value
          maybe_fulfill_on_success()
        def on_failure(error, trace):
          result_p.fail(error, trace)
        promises[index].then(on_success, on_failure)
      add_handler(outer_index)
    return result_p

  # Adds a task, a no-argument function, to the queue of tasks this scheduler
  # should execute.
  def add_thunk(self, thunk):
    self.thunks.put(thunk)

  # Runs the next scheduled task.
  def run_next_task(self):
    thunk = self.thunks.get()
    thunk()

  # Returns true iff there are more tasks to execute.
  def has_more_tasks(self):
    return not self.thunks.empty()

  # Runs until all scheduled tasks have been performed.
  def run_all_tasks(self):
    tasks = 0
    while self.has_more_tasks():
      self.run_next_task()
      tasks += 1
      if (tasks % 1000) == 0:
        pending = self.thunks.qsize()
        _LOG.info("Has run %i tasks. Currently pending %i.", tasks, pending)


# Exception thrown if attempting to get the value of a promise that hasn't been
# resolved yet.
class UnresolvedPromise(Exception):

  def __init__(self, promise):
    self.promise = promise


# The result of a computation that may or may not be available yet.
class Promise(object):

  _EMPTY = "empty"
  _FAILED = "failed"
  _FULFILLED = "succeeded"

  # Initialize an empty promise that uses the given scheduler for execution.
  def __init__(self, scheduler):
    self.scheduler = scheduler
    self.state = Promise._EMPTY
    self.value = None
    self.waiters = []

  # Has the computation completed, successfully or otherwise?
  def is_resolved(self):
    return self.state != Promise._EMPTY

  # If this promise has not yet been resolved sets the result and schedules any
  # waiting tasks to be scheduled for execution.
  def fulfill(self, value):
    if self.is_resolved():
      return
    if type(value) == Promise:
      # If the argument is a promise then by default we'll let it propagate
      # rather than set the value of this promise to another promise.
      value.forward(self)
    else:
      self.state = Promise._FULFILLED
      self.value = value
      waiters = self.waiters
      self.waiters = None
      for task in waiters:
        self._fire_task(task)

  # If this promise has not yet been resolved fails it an schedules any waiting
  # tasks to be scheduled for failure.
  def fail(self, error, trace=None):
    if self.is_resolved():
      return
    if trace is None:
      trace = traceback.format_exc()
    self.state = Promise._FAILED
    self.value = (error, trace)
    waiters = self.waiters
    self.waiters = None
    for task in waiters:
      self._fire_task(task)

  # Returns a new promise whose eventual value will be that of the given thunk
  # called with the value of this promise. If this task fails then the result
  # promise will also fail with the same error.
  def then(self, on_fulfilled=None, on_failed=None):
    result = self.scheduler.new_promise()
    task = Task(on_fulfilled, on_failed, result)
    if self.is_resolved():
      self._fire_task(task)
    else:
      self.waiters.append(task)
    return result

  # Returns a new promise whose eventual value will be that of the given thunk
  # applied to the value of this promise. If this task fails then the result
  # promise will also fail with the same error.
  def then_apply(self, on_fulfilled=None, on_failed=None):
    def apply_on_fulfilled(value):
      return on_fulfilled(*value)
    return self.then(apply_on_fulfilled, on_failed)

  # Resolve the given promise the same way as this one, either immediately
  # if this promise has already been resolved, or eventually.
  def forward(self, that):
    if self.is_resolved():
      if self.state == Promise._FULFILLED:
        that.fulfill(self.value)
      else:
        that.fail(*self.value)
    else:
      self.waiters.append(Task(lambda _: self.get(), None, that))

  # Returns a promise that yields the result of applying the given function to
  # all the entries of the result of this promise, which must be a list.
  def map(self, fun):
    def do_map(values):
      result_ps = []
      for value in values:
        if type(value) is Promise:
          value_p = value
        else:
          value_p = self.scheduler.value(value)
        result_p = value_p.then(fun)
        result_ps.append(result_p)
      return self.scheduler.join(result_ps)
    return self.then(do_map)

  # This must be a promise for a dictionary from keys to promises. Returns a new
  # promise for a dictionary in the same order as the input that maps keys to
  # the resolved values of the promises in the input dict.
  def map_dict(self, fun):
    def do_map_dict(dict):
      # Extract the items from the dict to fix the order.
      items = list(dict.items())
      # Grab the keys.
      (keys, _) = zip(*items)
      result_ps = []
      # For each value make a promise for the result of mapping using the map
      # function.
      for (key, value) in items:
        if type(value) is Promise:
          value_p = value
        else:
          value_p = self.scheduler.value(value)
        # Rage!
        def make_mapper(k):
          return lambda v: fun(k, v)
        result_p = value_p.then(make_mapper(key))
        result_ps.append(result_p)
      # Wait for all the mappings to be done and then zip the array of results
      # back up.
      def zip_map_dict_result(values):
        return collections.OrderedDict(zip(keys, values))
      return self.scheduler.join(result_ps).then(zip_map_dict_result)
    return self.then(do_map_dict)

  # Returns the value of this promise if it has been fulfilled, throws its error
  # if it has failed, and throws and UnresolvedPromise error if it hasn't been
  # resolved yet.
  def get(self):
    if self.state == Promise._FULFILLED:
      return self.value
    elif self.state == Promise._FAILED:
      raise self.get_error()
    else:
      raise UnresolvedPromise(self)

  # If this promise has failed, returns the error. If it has succeeded returns
  # None and otherwise fails with an unresolved promise error.
  def get_error(self):
    if self.state == Promise._FULFILLED:
      return None
    elif self.state == Promise._FAILED:
      (error, trace) = self.value
      return error
    else:
      raise UnresolvedPromise(self)

  # If this promise has failed, returns the backtrace. If it has succeeded
  # returns None and otherwise fails with an unresolved promise error.
  def get_error_trace(self):
    if self.state == Promise._FULFILLED:
      return None
    elif self.state == Promise._FAILED:
      (error, trace) = self.value
      return trace
    else:
      raise UnresolvedPromise(self)

  # Fires the given task appropriately, depending on the state of this promise.
  # The task won't be executed immediately but added to the scheduler for later
  # execution.
  def _fire_task(self, task):
    assert self.is_resolved()
    if self.state == Promise._FULFILLED:
      self.scheduler.add_thunk(lambda: task.run(self.value))
    else:
      self.scheduler.add_thunk(lambda: task.fail(*self.value))
