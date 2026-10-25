# OmniServices 

[![Build Status](https://github.com/omnifaces/omniservices/actions/workflows/maven.yml/badge.svg)](https://github.com/omnifaces/omniservices/actions/workflows/maven.yml)
[![Maven](https://img.shields.io/maven-central/v/org.omnifaces/omniservices)](https://central.sonatype.com/artifact/org.omnifaces/omniservices)
[![Javadoc](http://javadoc.io/badge/org.omnifaces/omniservices.svg)](http://javadoc.io/doc/org.omnifaces/omniservices) 
[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

Utility library that provides EJB3-like features for CDI beans

This project will attempt to implement CDI and Interceptor based versions of various EJB features and services. Currently the following features are implemented:

* EJB @Asynchronous - CDI based [@Asynchronous](https://www.javadoc.io/static/org.omnifaces/omniservices/0.4/org.omnifaces.services/org/omnifaces/services/asynchronous/Asynchronous.html) (note: this is currently available in native Jakarta EE via Jakarta Concurrency 3.0)
* EJB @Stateless - CDI based [@Pooled](https://github.com/omnifaces/omniservices/blob/develop/impl/src/main/java/org/omnifaces/services/pooled/Pooled.java)+@Transactional or [@Service](https://github.com/omnifaces/omniservices/blob/develop/impl/src/main/java/org/omnifaces/services/Service.java)
* EJB @Lock - CDI based [@Lock](https://github.com/omnifaces/omniservices/blob/develop/impl/src/main/java/org/omnifaces/services/lock/Lock.java)

## Requirements

Java 17, CDI 4.1 and Jakarta Interceptors 2.2 (Jakarta EE 11). OmniServices registers itself with a CDI Lite build
compatible extension, so it works on CDI Full implementations (such as Weld) as well as on CDI Lite ones (such as
Quarkus' ArC). `@Service` and `@Transactional` need a Jakarta Transactions implementation (in Quarkus:
`quarkus-narayana-jta`). Without one, the rest works, and `@Service` beans are pooled but not transactional (tested on
Quarkus without `quarkus-narayana-jta`; the Jakarta Transactions API comes with ArC there). On the module path,
OmniServices requires the Jakarta Transactions API.

## Asynchronous methods

An `@Asynchronous` method returns `void` or `Future`, and runs like an EJB asynchronous method:

* It's dispatched to another thread before the other OmniServices interceptors and `@Transactional` run. The caller
  doesn't wait for a pooled instance or a lock, and doesn't share its transaction or request context with the method.
* A `Future` the method returns is resolved before the method's transaction ends and its lock and pooled instance are
  released. The caller's future completes after that. A checked exception the returned future fails with must be
  declared by the method: on Quarkus, an undeclared one reaches the caller wrapped in ArC's
  `ArcUndeclaredThrowableException`.
* The caller's future can only be cancelled before the method started. After that, `cancel(true)` makes
  `AsynchronousInvocation.wasCancelCalled()` return true in the method, which isn't interrupted.
* An exception of a `void` method is logged.
* When waiting for the result of a returned `Future` is interrupted, the interrupt is kept, the transaction rolls back,
  and the caller's future fails with an `UncheckedInterruptedException`.

The methods run on the first available of: the executor service in JNDI at `java:comp/env/concurrent/ThreadPool`; a
MicroProfile `ManagedExecutor` (in Quarkus: with `quarkus-smallrye-context-propagation`), which can be bounded with
`mp.context.ManagedExecutor.maxAsync` and `mp.context.ManagedExecutor.maxQueued`; the Jakarta Concurrency
`java:comp/DefaultManagedExecutorService`; a thread pool of OmniServices' own.

## Pooled beans

Each call of a method of a `@Pooled` bean takes an instance from the bean's pool, and returns it afterwards. The pool
settings are taken from `@Pooled` on the bean class or on its stereotype. Interceptors with a priority before
`PLATFORM_BEFORE + 50` run twice per call; methods that aren't intercepted (such as those of `Object`) don't use the
pool.

A bean that's pooled by a stereotype, such as `@Service`, also gets the stereotype's `@Pooled` on its class: Quarkus'
ArC doesn't take a custom scope from a stereotype otherwise, and would make the bean `@Dependent`. A bean that declares
a scope of its own isn't pooled.

## Locks

A method of a `@Lock` bean takes the bean instance's read or write lock, as the method's `@Lock` specifies, else the
class'. When the lock isn't acquired as specified (within the access timeout, or at once when waiting isn't
permitted), the method throws a `LockTimeoutException`, which is an `IllegalStateException`.

A thread that holds a lock of the instance and calls one of its methods again, through other beans, keeps that lock:
it can take the read lock again, and with the write lock, take either. A read lock isn't upgraded, though: when such a
call needs the write lock, it fails at once with an `IllegalLoopbackException` (an `IllegalStateException`) rather than
waiting for itself, as Jakarta Concurrency's `@Lock` does. (Calls on `this` are only intercepted on Quarkus: Weld
doesn't intercept them.)

## When the application stops

OmniServices stops in two phases, within a time limit that the system property `org.omnifaces.services.shutdown.timeout`
sets in seconds (default: 10).

1. Before the application context is destroyed, new asynchronous invocations are refused with a
   `RejectedExecutionException`, and those that haven't started are cancelled, also on executor services of the
   container. Asynchronous invocations that have started, and pooled instances in use, are waited for, until the time
   limit. Executor services that OmniServices created are shut down.
2. After the application context is destroyed, the pools refuse calls with an `IllegalStateException`, also those
   waiting for an instance. Pooled instances that aren't in use are destroyed. Those still in use are destroyed when
   they're returned, so an instance is never destroyed while one of its methods runs.

So, on Quarkus, the `@PreDestroy` methods of application scoped beans can still use pooled beans, but not asynchronous
methods. Weld fires both events for a web application before it destroys the application context (WELD-2282), so there
these methods can't use pooled beans either.

Work that's still running after the time limit goes on, but by the time it ends, the container may have stopped services
it uses, such as transactions.

## Building and testing

The library is in `impl`, its tests are in `test`. The tests deploy an application that uses OmniServices, and load
OmniServices from the local Maven repository, so build with `install`:

```
mvn install
```

That tests on Quarkus. To test on Piranha Micro instead, use `mvn install -Ppiranha`. For now, this needs a local
Piranha build with two fixes that aren't upstream yet: starting Weld before the other initializers, and destroying the
web application when Micro stops.

## Future versions

The following features were being considered for future versions:

* EJB @Schedule - this is currently available in native Jakarta EE 11 via Jakarta Concurrency 3.1
