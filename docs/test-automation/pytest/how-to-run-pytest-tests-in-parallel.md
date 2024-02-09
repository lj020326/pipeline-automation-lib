
# How to run pytest tests in parallel?

I want to run all my `pytest` tests in parallel instead of sequentially.

my current setup looks like:

```python
class Test1(OtherClass):
    @pytest.mark.parametrize("activity_name", ["activity1", "activity2"])
    @pytest.mark.flaky(reruns=1)
    def test_1(self, activity_name, generate_test_id):
    """
    """

        test_id = generate_random_test_id()
        test_name = sys._getframe().f_code.co_name

        result_triggers = self.proxy(test_name, generate_test_id, test_id, activity_name)

        expected_items = ["response"]
        validate_response("triggers", result_triggers, expected_items)


    @pytest.mark.parametrize("activity_name", ["activity1", "activity2"])
    @pytest.mark.flaky(reruns=1)
    def test_2(self, activity_name, generate_test_id):
    """
    """

        #same idea...
```

I run my tests using `pytest -v -s`.

The result is that my tests are running sequentially, which takes a lot of time since some of them wait for responses from remote servers (integration tests).

Is there any way of running pytest in parallel?


## Answer

[`pytest-xdist`](https://github.com/pytest-dev/pytest-xdist) is a great solution for most cases, but integration tests are special. 

After sending a request to a remote server, another test can start on a new thread instead of waiting for a response. 

This is concurrent testing instead of parallel. Concurrency allows many more tests at once with much less memory and processing overhead.

I wrote the [`pytest-parallel`](https://github.com/browsertron/pytest-parallel) plugin [py3.6+] to enable parallel and concurrent testing. 

Here's how to run your integration tests concurrently:

```shell
$ pytest --tests-per-worker auto
```

## Reference

- [How to run pytest tests in parallel?](https://stackoverflow.com/questions/45733763/how-to-run-pytest-tests-in-parallel)
