
# How to get pytest fixture data dynamically

I'm trying to define init data for several tests scenarios that test a single api endpoint. I want to do this so that I don't have to produce boiler plate code for multiple iterations of a test where just the data differs. I can't seem to wrap my head around how to do this using the built-in pytest fixtures. Here's essentially what I'm trying to do:

In tests/conftext.py:

```python
import pytest

@pytest.fixture(scope="module")
def data_for_a():
    return "a_data"

@pytest.fixture(scope="module")
def data_for_b():
    return "b_data"

```

In tests/tests.py

```python
import pytest

# this works
def test_a(data_for_a):
    assert "a_data" == data_for_a

# but I want to do this and it fails:
scenarios = [
    { "name": "a", "data": data_for_a },
    { "name": "b", "data": data_for_b },
]

for scenario in scenarios:
    print(scenario.name, scenario.data)

# desired output:
# "a a_data"
# "b b_data"

```

I get a `NameError: name 'data_for_a' is not defined` exception. I've tried various approaches to get this to work, but there seems to be no way around having to pass the fixture as a parameter to the test method - so either define a bunch of boilerplate tests or have a bunch of if/else statements in a single test and pass each fixture explicitly. I don't like either of these options. At the moment it seems like I have to just build my own helper module to pull in this test data, but I'd rather use the built-in mechanism for this. Is there any way to do this?

## 2 Answers

### Answer 1

I believe what you're looking for is `pytest_generate_tests`. 

You can define this in a `conftest.py` module placed in the directory containing the tests to be run, which is automatically parsed prior to running any tests via pytest. 

This function can be used to 'parametrize' [sic] your test functions or your fixtures _dynamically_, allowing you to define on the fly the set of inputs over which you would like the test/fixture to iterate.

I've included an example. Consider the following directory structure:

```output
tests
 |
 +-- examples.py
 +-- test_examples.py
 +-- conftest.py
```

Now let's look at each file...

```python
# examples.py
# -----------
example_1 = {
    "friendship": 0.0,
    "totes": 0.0,
}

example_2 = {
    "friendship": 0.0,
    "totes": 0.0,
}

dont_use_me = {
    "friendship": 1.0,
    "totes": 1.0,
}
```

...

```python
# test_examples.py
# ----------------
def test_answer(pydict_fixture):
    for k,v in pydict_fixture.items():
        assert v==0.0
```

...

```python
# conftest.py
# -----------
from os.path import join, dirname, abspath
import imp
import re

def pytest_generate_tests(metafunc):
    this_dir    = dirname(abspath(metafunc.module.__file__))
    #
    if 'pydict_fixture' in metafunc.fixturenames:
        examples_file= join(this_dir, "examples.py")
        examples_module = imp.load_source('examples', examples_file)
        examples_regex = re.compile("example")
        examples = []
        for name, val in examples_module.__dict__.iteritems():
            if examples_regex.search(name):
                examples.append(val)
        metafunc.parametrize('pydict_fixture', examples)

```

In this particular example, I wanted to manage the test cases in a single, separate file. So, I wrote a `pytest_generate_tests` function that, _before any tests are run_, parses `examples.py`, creates a list of dictionaries whose names include the word 'example', and forces `test_answer` to be run on each dictionary in the list. So, `test_answer` will be called twice, once on `example_1` and once on `example_2`. Both tests will pass.

That's the quick-short of it. The most important thing is that the list of inputs is determined dynamically inside of `pytest_generate_tests`, and the test is run once per item in the list.

However, to be complete in my description of what I wrote here, my `pytest_generate_tests` function actually creates a list of inputs for _every_ test function (represented by `pytest`'s predefined `metafunc` variable in `pytest_generate_tests`) that uses the imaginary `pydict_fixture`, and looks for the `examples.py` file _in the directory where `metafunc` resides_! So, potentially this could be extended to run a bunch of different tests on a bunch of different `examples.py` files.

## Reference

- [How to get pytest fixture data dynamically](https://stackoverflow.com/questions/39475849/how-to-get-pytest-fixture-data-dynamically)
