PY_TESTS=       \
test_http.py    \
test_promise.py \
test_main.py    \
test_clock.py

PY_TEST_PATHS=$(PY_TESTS:%=test/py/interrogate/%)

# Oh my god fuck fuck fuck python is awful i hate it just kill me now
tests:	$(PY_TEST_PATHS)
	PYTHONPATH=src/py/interrogate python test/py/interrogate/test_http.py
	PYTHONPATH=src/py/interrogate python test/py/interrogate/test_promise.py
	PYTHONPATH=src/py/interrogate python test/py/interrogate/test_main.py
	PYTHONPATH=src/py/interrogate python test/py/interrogate/test_clock.py

.PHONY:	tests
