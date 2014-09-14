#!/usr/bin/python


import unittest
import main
import random


class MainTest(unittest.TestCase):

  def test_coverage_tracker(self):
    t = main.CoverageTracker()
    self.assertEquals(0, t.get_next_uncovered(0, 10))
    t = t.add_range(0, 2)
    self.assertEquals(3, t.get_next_uncovered(0, 10))
    t = t.add_range(4, 5)
    self.assertEquals(3, t.get_next_uncovered(0, 10))
    t = t.add_range(3, 3)
    self.assertEquals(6, t.get_next_uncovered(0, 10))
    self.assertEquals(6, t.get_next_uncovered(0, 6))
    self.assertEquals(None, t.get_next_uncovered(0, 5))
    t = t.add_range(-5, -3)
    self.assertEquals(6, t.get_next_uncovered(0, 10))

  def test_random_coverage_tracker(self):
    def random_range():
      a = random.randint(0, 128)
      b = (a + random.randint(-16, 16))
      return (min(a, b), max(a, b))
    random.seed(1345234)
    for il in range(0, 32):
      t = main.CoverageTracker()
      covered = set()
      for ir in range(0, 32):
        (start, end) = random_range()
        t = t.add_range(start, end)
        covered = covered.union(range(start, end + 1))
        for ic in range(0, 32):
          (start, end) = random_range()
          found = t.get_next_uncovered(start, end)
          available = set(range(start, end + 1)).difference(covered)
          if len(available) == 0:
            expected = None
          else:
            expected = min(available)
          self.assertEquals(expected, found)

if __name__ == '__main__':
  runner = unittest.TextTestRunner(verbosity=0)
  unittest.main(testRunner=runner)
