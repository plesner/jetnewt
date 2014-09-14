#!/usr/bin/python


import unittest
import clock


class MainTest(unittest.TestCase):

  def test_get_previous(self):
    self.assertEquals("02.10.14", clock.get_previous_date("03.10.14"))
    self.assertEquals("01.10.14", clock.get_previous_date("02.10.14"))
    self.assertEquals("30.09.14", clock.get_previous_date("01.10.14"))
    self.assertEquals("28.02.14", clock.get_previous_date("01.03.14"))
    self.assertEquals("29.02.12", clock.get_previous_date("01.03.12"))
    self.assertEquals("31.12.13", clock.get_previous_date("01.01.14"))


if __name__ == '__main__':
  runner = unittest.TextTestRunner(verbosity=0)
  unittest.main(testRunner=runner)
