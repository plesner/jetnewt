#!/usr/bin/python


import unittest
import http


# Implementation that fakes out time and waiting.
class FakeLeakyBucket(http.LeakyBucket):
  
  def __init__(self, permits_per_second, max_accumulation):
    self.current_time = 0
    super(FakeLeakyBucket, self).__init__(permits_per_second, max_accumulation)

  def get_current_time_millis(self):
    return self.current_time

  def sleep_millis(self, millis):
    self.current_time += millis


class LeakyBucketTest(unittest.TestCase):

  def test_simple(self):
    leaky = FakeLeakyBucket(10, 12)
    # Each wait advances time 100ms
    leaky.wait_for_permit()
    self.assertEquals(100, leaky.current_time)
    leaky.wait_for_permit()
    self.assertEquals(200, leaky.current_time)
    # Skipping time ahead to 1000 should unlock 8 permits without waiting.
    leaky.current_time = 1000
    for i in range(0, 8):
      leaky.wait_for_permit()
      self.assertEquals(1000, leaky.current_time)
    # Now we've caught up and the next one should wait.
    leaky.wait_for_permit()
    self.assertEquals(1100, leaky.current_time)
    # Skipping way ahead should only unlock 12 permits, by the max accumulation.
    leaky.current_time = 10000
    for i in range(0, 12):
      leaky.wait_for_permit()
      self.assertEquals(10000, leaky.current_time)
    # Now we should be back to waiting.
    leaky.wait_for_permit()
    self.assertEquals(10100, leaky.current_time)


if __name__ == '__main__':
  runner = unittest.TextTestRunner(verbosity=0)
  unittest.main(testRunner=runner)
