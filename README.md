# OmniServices [![Build Status](https://travis-ci.org/omnifaces/omniservices.svg?branch=develop)](https://travis-ci.org/omnifaces/omniservices)
Utility library that provides EJB3-like features for CDI beans

This project will attempt to implement CDI and Interceptor based versions of various EJB features and services. Currently the following features are implemented:

* @Asynchronous - @Asynchronous
* @Stateless - @Pooled
* @Stateless - @Service (stereotype combining @Pooled with @Transactional)

The following features are being considered for future versions:

* @Lock
* @Schedule