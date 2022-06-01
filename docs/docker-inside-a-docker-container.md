## Can you run Docker inside a Docker container?

![](https://miro.medium.com/max/1400/1*TLfGMbPi7lXyhBJTfCKo2Q.jpeg)

The question that this article looks at is the following: if you run a Docker container that has itself Docker installed, can you then run Docker inside that Docker container (for example, to pull and build images, or to run other containers) with the two Docker instances being completely independent from each other?

The answer is yes, but it is not recommended because it causes many low-level technical problems, which have to do with the way Docker is implemented on the operating system, and which are explained in detail in [this post](./docker-in-docker-the-good-the-bad-and-the-fix.md).

The good news is that there is another, recommended, way to use Docker inside a Docker container, with which the two Docker instances are not independent from each other, but which bypasses these problems.

With this approach, a container, with Docker installed, does not run its own [**Docker daemon**](https://nickjanetakis.com/blog/understanding-how-the-docker-daemon-and-docker-cli-work-together#visualizing-docker-s-architecture), but connects to the Docker daemon of the host system. That means, you will have a Docker CLI in the container, as well as on the host system, but they both connect to one and the same Docker daemon. At any time, there is only one Docker daemon running in your machine, the one running on the host system.

To achieve this, you can start a Docker container, that has Docker installed, with the following [**bind mount**](https://docs.docker.com/storage/bind-mounts/) option:

```
-v /var/run/docker.sock:/var/run/docker.sock
```

For example, you can use the `[**docker**](https://hub.docker.com/r/_/docker/)` image, which is a Docker image that has Docker installed, and start it like this:

```
docker run -ti -v /var/run/docker.sock:/var/run/docker.sock docker
```

And then inside the Docker container that you just started, run some Docker commands, for example:

```
docker imagesdocker ps
```

Observe the output. The output is exactly the same as when you run these commands on the host system.

It looks like the Docker installation of the container that you just started, and that you maybe would expect to be fresh and untouched, already has some images cached and some containers running. This is because we wired up the Docker CLI in the container to talk to the Docker daemon that is already running on the host system.

This means, if you pull an image inside the container, this image will also be visible on the host system (and vice versa). And if you run a container inside the container, this container will actually be a “sibling” to all the containers running on the host machine (including the container in which you are running Docker).

This might be irritating at first. You might think that it would be nice if the Docker installation inside the container was completely encapsulated from the host system. However, complete encapsulation is actually not needed for most use cases, and this workaround is a legitimate solution whenever you need to use Docker inside a Docker container.

But when do you need to use Docker within a Docker container anyway?

## When do you need it?

The question of running Docker in a Docker container occurs frequently when using CI/CD tools like Jenkins.

![](./assets/dind/1_s8mwAOWdb2choX4pj9ozvw.png)

In Jenkins, all the commands in the stages of your pipeline are executed on the agent that you specify. This agent can be a Docker container. So, if one of your commands, for example, in the _Build_ stage, is a Docker command (for example, for building an image), then you have the case that you need to run a Docker command within a Docker container.

Furthermore, Jenkins itself can be run as a Docker container. If you use a Docker agent, you would start this Docker container from within the Jenkins Docker container. If you also have Docker commands in your Jenkins pipeline, then you would have three levels of nested “Dockers”.

However, with the above approach, all these Dockers use one and the same Docker daemon, and all the difficulties of multiple daemons (in this case three) on the same system, that would otherwise occur, are bypassed.

The only thing that you have to do to achieve this is to start each Docker container with the `-v /var/run/docker.sock:/var/run/docker.sock` option, as described above.

## Real Docker in Docker

If you really want to, you can use “real” Docker in Docker, that is nested Docker instances that are completely encapsulated from each other. You can do this with the `dind` (_Docker in Docker_) tag of the `[docker](https://hub.docker.com/r/_/docker/)` image, as follows:

```
docker run --privileged -d docker:dind
```

This approach is described in detail [here](https://github.com/jpetazzo/dind) by Jérôme Petazzoni. But as also mentioned there, there is usually no need to do this.