JML
===

[![Build Status](https://secure.travis-ci.org/realityforge/jml.png?branch=master)](http://travis-ci.org/realityforge/jml)

JML grew out of the need to rapidly set up processors that consumed messages from one
channel and produced messages on another channel. Typically the processor may apply some
form of transformation or validation on the message as it is transmitted to the destination.

An example scenario is where one application sends a message to the queue "Fireweb.Wildfire"
in the format "Wildfire-1.1.xsd". Several applications want to receive it but in an older
format "Wildfire-1.0.xsd" from a topic named "Wildfire". The message coming out of the
"Fireweb.Wildfire" is validated against "Wildfire-1.1.xsd" before an xsl transform
("Wildfire-1.1-to-1.0.xsl") is applied and post transform the message is validated against
"Wildfire-1.0.xsd" before being sent onto the "Wildfire" topic. The processor that does this
is an example of perfect candidate for an instance of the JML MessageLink class.

TODO
====

* Test MessageUtil to ensure headers are copied
* Test MessageTransformer to ensure all headers are copied as appropriate
* Document MessageLink with oodles of examples
* Add Test for subclasses of AbstractMessageEndpoint
