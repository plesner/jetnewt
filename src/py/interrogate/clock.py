import time

class Timestamp(object):

  @staticmethod
  def to_date(millis):
    localtime = time.localtime(millis / 1000.0)
    return time.strftime("%d.%m.%y", localtime)

  @staticmethod
  def to_time(millis):
    localtime = time.localtime(millis / 1000.0)    
    return time.strftime("%H:%M", localtime)

  @staticmethod
  def from_date_time(date_str, time_str):
    tup = time.strptime("%s-%s" % (date_str, time_str), "%d.%m.%y-%H:%M")
    secs = time.mktime(tup)
    return int(secs * 1000)

# Given a date as a string, returns the previous date string.
def get_previous_date(date):
  midnight = Timestamp.from_date_time(date, "00:00")
  hour_before_midnight = midnight - (1000 * 60 * 60)
  return Timestamp.to_date(hour_before_midnight)
