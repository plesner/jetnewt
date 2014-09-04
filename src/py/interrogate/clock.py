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
