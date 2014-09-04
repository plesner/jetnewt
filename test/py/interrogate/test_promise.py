#!/usr/bin/python


import unittest
import promise


class PromiseTest(unittest.TestCase):

  def test_simple_then(self):
    sch = promise.Scheduler()
    p = sch.value(8).then(lambda v: v + 4).then(lambda v: v * 3).then(lambda v: v - 1)
    self.assertFalse(p.is_resolved())
    sch.run_all_tasks()
    self.assertEquals(35, p.get())

  def test_value(self):
    sch = promise.Scheduler()
    p = sch.value(8)
    self.assertTrue(p.is_resolved())
    self.assertEquals(8, p.get())

  def test_delay(self):
    sch = promise.Scheduler()
    p = sch.delay(lambda: 43)
    self.assertFalse(p.is_resolved())
    self.assertRaises(promise.UnresolvedPromise, p.get)
    sch.run_all_tasks()
    self.assertTrue(p.is_resolved())
    self.assertEquals(43, p.get())

  def test_failed_delay(self):
    sch = promise.Scheduler()
    p = sch.delay(lambda: [].foo)
    self.assertFalse(p.is_resolved())
    sch.run_all_tasks()
    self.assertTrue(p.is_resolved())
    self.assertRaises(AttributeError, p.get)

  def test_join_success(self):
    sch = promise.Scheduler()
    promises = [sch.new_promise() for i in range(0, 100)]
    joint = sch.join(promises)
    for i in range(0, 100):
      promises[i].fulfill(i)
    sch.run_all_tasks()
    self.assertTrue(joint.is_resolved())
    self.assertEquals(list(range(0, 100)), joint.get())

  def test_map(self):
    sch = promise.Scheduler()
    outer = [sch.new_promise() for i in range(0, 100)]
    inner = [sch.new_promise() for i in range(0, 100)]
    joint = sch.join(outer)
    moved = joint.map(lambda v: inner[v])
    for i in range(0, 100):
      outer[i].fulfill(i)
    sch.run_all_tasks()
    self.assertFalse(moved.is_resolved())
    for i in range(0, 100):
      inner[i].fulfill(i)
    sch.run_all_tasks()
    self.assertTrue(moved.is_resolved())
    self.assertEquals(list(range(0, 100)), moved.get())

  def test_join_failure(self):
    sch = promise.Scheduler()
    promises = [sch.new_promise() for i in range(0, 100)]
    joint = sch.join(promises)
    promises[50].fail("error", None)
    for i in range(0, 100):
      promises[i].fulfill(i)
    sch.run_all_tasks()
    self.assertTrue(joint.is_resolved())
    self.assertTrue("error", joint.get_error())
    for i in range(0, 100):
      if i == 50:
        self.assertEquals("error", promises[i].get_error())
      else:
        self.assertEquals(i, promises[i].get())


if __name__ == '__main__':
  runner = unittest.TextTestRunner(verbosity=0)
  unittest.main(testRunner=runner)
