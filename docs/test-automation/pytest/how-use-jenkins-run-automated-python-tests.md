
# How to use Jenkins to run automated Python tests

Jenkins is a popular open-source tool for continuous integration and delivery. It allows you to automate various tasks such as building, testing, and deploying your code. In this article, I will show you how to use Jenkins to run automated Python tests using two popular frameworks: **pytest** and **nose**.

## What are Python tests?

Python tests are pieces of code that check the functionality and quality of your Python code. They help you find and fix bugs, improve performance, and ensure that your code meets the requirements and expectations. There are different types of tests, such as unit tests, integration tests, functional tests, etc. In this article, I will focus on unit tests, which are the most basic and common type of tests.

Unit tests are tests that check the behavior of a single unit of code, such as a function or a class. They are usually written using a testing framework that provides tools and conventions for writing and running tests. Some of the most popular testing frameworks for Python are **pytest** and **nose**.

## What is pytest?

Pytest is a testing framework that makes it easy to write small and expressive tests. It has a simple syntax that uses plain assert statements, automatic test discovery, powerful fixtures and plugins, and rich reporting features. Pytest can also generate JUnit-compatible XML test reports and Cobertura-compatible code coverage reports, which can be integrated into Jenkins for trending and reporting purposes.

## What is nose?

Nose is another testing framework that extends the built-in unittest module. It has similar features as pytest, such as automatic test discovery, fixtures and plugins, and JUnit-compatible XML test reports. Nose also supports running tests in parallel, which can speed up the testing process.

## How to use Jenkins to run Python tests?

To use Jenkins to run Python tests, you need to do the following steps:

1. Download and install Jenkins on your machine or server. Jenkins provides installers for different operating systems¹.
2. Install and configure the required Jenkins plugins to run Python tests. Some of the plugins that you may need are: **ShiningPanda**, which provides a build environment for Python projects; **jUnit**, which allows you to publish test results from various testing frameworks; **Cobertura**, which allows you to publish code coverage reports from various tools².
3. Install Python and the testing frameworks that you want to use on your machine or server. You can use pip or conda to install pytest, nose, mock, etc.
4. Write your Python unit tests using pytest or nose conventions. For example, you can name your test files as test\_\*.py or \*\_test.py, and use assert statements or unittest.TestCase subclasses to write your test cases³ .
5. Create a Jenkinsfile in your project repository that defines your pipeline stages and steps. A Jenkinsfile is a text file that contains the definition of a Jenkins pipeline using a domain-specific language (DSL). You can use either a declarative or a scripted syntax to write your Jenkinsfile. For example, here is a simple Jenkinsfile that uses a declarative syntax and runs Python tests using pytest:

```groovy
pipeline 

  agent {
    docker {
      image 'python:3.5.1'
    }
  }

  stages {
    stage ('Build') {
      steps {
        sh 'python -m py_compile sources/add2vals.py sources/calc.py'
      }
    }
    stage ('Test') {
      steps {
        // sh 'python -m pytest --junit-xml test-reports/results.xml sources/test_calc.py'
        sh 'python -m pytest --capture=tee-sys -o junit_logging=all --junit-xml=pytest_unit.xml sources/test_calc.py'
      }

      post {
        always {
          xunit 'test-reports/results.xml'
        }
      }
    }
  }
}
```

Docs for [\--capture=tee-sys](https://docs.pytest.org/en/7.1.x/how-to/capture-stdout-stderr.html) and [junit\_logging=all](https://docs.pytest.org/en/7.1.x/reference/reference.html#confval-junit_logging)

6. Push your Jenkinsfile to your project repository and create a new pipeline job or multibranch pipeline job in Jenkins. You need to specify the source code repository and the branch that contains your Jenkinsfile.

7. Run the pipeline job and wait for it to finish. You can monitor the progress and status of your pipeline on the Jenkins dashboard.

8. Verify the results by checking the test reports and code coverage reports on the Jenkins dashboard. You can also see the trends and statistics of your tests over time.

## Conclusion

In this article, I have shown you how to use Jenkins to run automated Python tests using pytest and nose frameworks. I have also shown you how to create a Jenkins pipeline using a Jenkinsfile that defines your pipeline stages and steps. This can help you improve the quality and reliability of your Python code, as well as save time and resources by automating the testing process. I hope you found this article useful and informative.


## Reference

- https://www.linkedin.com/pulse/how-use-jenkins-run-automated-python-tests-antonio-quarta/
- [(1) Build a Python app with PyInstaller - Jenkins. ](https://www.jenkins.io/doc/tutorials/build-a-python-app-with-pyinstaller/)
- [(2) Jenkins pipeline: Run python script - Stack Overflow.](https://stackoverflow.com/questions/55350360/jenkins-pipeline-run-python-script)
- [(3) Jenkins and Python.](https://www.jenkins.io/solutions/python/)
- https://realpython.com/python-testing/
- https://stackoverflow.com/questions/37073957/pytest-print-to-console-and-capture-output-in-the-junit-report#42862802
- https://stackoverflow.com/questions/73933602/ansible-gitlab-pipeline-output-to-cli-and-junit
- https://pypi.org/project/ansible-lint-junit/
- https://sleeplessbeastie.eu/2021/01/11/how-to-store-and-visualize-playbook-events/
