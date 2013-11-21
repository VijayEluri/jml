package org.realityforge.jml;

import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

public class MessageLinkTestCase
  extends AbstractBrokerBasedTestCase
{
  @BeforeTest
  public void turnOffLogging()
  {
    MessageLink.LOG.setLevel( Level.OFF );
    // Turn off messages that result when verifier/transformer fails but no DMQ set
    Logger.getLogger( "org.apache.activemq.ActiveMQMessageConsumer" ).setLevel( Level.OFF );
  }

  @Test
  public void transferFromInputQueueToOutputQueue()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 5 );
    collector.expectMessageCount( 5 );
    link.stop();

    assertEquals( "TestLink", link.getName() );
    assertEquals( null, link.getDmqName() );
    assertEquals( null, link.getSelector() );
    assertEquals( null, link.getSubscriptionName() );
    assertEquals( TestHelper.QUEUE_1_SPEC, link.getSource().toSpec() );
  }

  @Test
  public void transferFromInputQueueToOutputQueueWithSelector()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, TestHelper.HEADER_KEY + " <= 2" );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 5 );
    collector.expectMessageCount( 3 );

    // Ensure that those not matching selector are still in source queue
    collectResults( TestHelper.QUEUE_1_NAME, false ).expectMessageCount( 2 );

    link.stop();

    assertEquals( TestHelper.HEADER_KEY + " <= 2", link.getSelector() );
  }

  @Test
  public void transferFromInputQueueToOutputTopic()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.TOPIC_2_NAME, true );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.TOPIC_2_SPEC );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 5 );
    collector.expectMessageCount( 5 );
    link.stop();
  }

  @Test
  public void transferFromInputTopicToOutputQueue()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.TOPIC_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.TOPIC_1_NAME, true, 5 );
    collector.expectMessageCount( 5 );
    link.stop();
  }

  @Test
  public void transferFromInputTopicToOutputQueueWithSelector()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );
    final MessageCollector inputCollector = collectResults( TestHelper.TOPIC_1_NAME, true );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.TOPIC_1_SPEC, null, TestHelper.HEADER_KEY + " <= 2" );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.TOPIC_1_NAME, true, 5 );
    collector.expectMessageCount( 3 );

    // Check that 5 went through input even if only 3 flowed through
    inputCollector.expectMessageCount( 5 );

    link.stop();
  }

  @Test
  public void transferFromInputTopicToOutputTopic()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.TOPIC_2_NAME, true );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.TOPIC_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.TOPIC_2_SPEC );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.TOPIC_1_NAME, true, 5 );
    collector.expectMessageCount( 5 );
    link.stop();
  }

  @Test
  public void transferFromInputTopicToOutputTopicWithDurableSubscription()
    throws Exception
  {
    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.TOPIC_1_SPEC, "MySubscriptionName", null );
    link.setDestinationChannel( TestHelper.TOPIC_2_SPEC );
    link.setName( "TestLink" );

    link.start( createSession() );
    link.stop();

    // Should work fine as durable subscription exists
    createSession().unsubscribe( "MySubscriptionName" );
    boolean fail;
    try
    {
      createSession().unsubscribe( "MySubscriptionName" );
      fail = true;
    }
    catch( Exception e )
    {
      fail = false;
    }
    if( fail ) fail( "Expected an exception to be thrown when unsubscribing for a second time" );

    assertEquals( "MySubscriptionName", link.getSubscriptionName() );
  }

  @Test
  public void transferFromInputQueueToOutputQueueWithInputVerifier()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );
    final MessageCollector dmqCollector = collectResults( TestHelper.DMQ_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setDmqName( TestHelper.DMQ_NAME );
    link.setInputVerifier( new TestMessageVerifier( 3 ) );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 5 );
    collector.expectMessageCount( 4 );
    final Message message = dmqCollector.expectMessageCount( 1 ).iterator().next();

    assertMessageProperty( message, "JMLMessageLink", "TestLink" );
    assertMessagePropertyNotNull( message, "JMLFailureReason" );
    assertMessageProperty( message, "JMLSourceChannel", TestHelper.QUEUE_1_SPEC );
    assertMessagePropertyNull( message, "JMLInSubscriptionName" );
    assertMessageProperty( message, "JMLDestinationChannel", TestHelper.QUEUE_2_SPEC );
    assertMessageProperty( message, "JMLOriginalMessageType", "TextMessage" );
    link.stop();

    assertEquals( "TestLink", link.getName() );
    assertEquals( TestHelper.DMQ_NAME, link.getDmqName() );
    assertEquals( null, link.getSelector() );
    assertEquals( null, link.getSubscriptionName() );
    assertEquals( TestHelper.QUEUE_1_SPEC, link.getSource().toSpec() );
  }

  @Test
  public void transferFromInputQueueToOutputQueueWithInputVerifierButNoDMQSet()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );
    final MessageCollector dmqCollector = collectResults( TestHelper.DMQ_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setInputVerifier( new TestMessageVerifier( 3 ) );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 5 );
    collector.expectMessageCount( 4 );
    dmqCollector.expectMessageCount( 0 );
    link.stop();
  }

  @Test
  public void transferMessageLinkOrdering()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );
    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    final TestMessageVerifier inputVerifier = new TestMessageVerifier( 3 );
    link.setInputVerifier( inputVerifier );
    final TestMessageTransformer transformer = new TestMessageTransformer( false );
    link.setTransformer( transformer );
    final TestMessageVerifier outputVerifier = new TestMessageVerifier( 3 );
    link.setOutputVerifier( outputVerifier );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 1 );
    collector.expectMessageCount( 1 );

    assertTrue( inputVerifier.getLastMessageTime() < transformer.getLastMessageTime(), "inputVerifier < transformer" );
    assertTrue( transformer.getLastMessageTime() < outputVerifier.getLastMessageTime(), "transformer < outputVerifier" );

    link.stop();
  }

  @Test
  public void transferFromInputQueueToOutputQueueWithOutputVerifier()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );
    final MessageCollector dmqCollector = collectResults( TestHelper.DMQ_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setDmqName( TestHelper.DMQ_NAME );
    link.setOutputVerifier( new TestMessageVerifier( 3 ) );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 5 );
    collector.expectMessageCount( 4 );
    final Message message = dmqCollector.expectMessageCount( 1 ).iterator().next();

    assertMessageProperty( message, "JMLMessageLink", "TestLink" );
    assertMessagePropertyNotNull( message, "JMLFailureReason" );
    assertMessageProperty( message, "JMLSourceChannel", TestHelper.QUEUE_1_SPEC );
    assertMessagePropertyNull( message, "JMLInSubscriptionName" );
    assertMessageProperty( message, "JMLDestinationChannel", TestHelper.QUEUE_2_SPEC );
    assertMessageProperty( message, "JMLOriginalMessageType", "TextMessage" );
    link.stop();
  }

  @Test
  public void transferFromInputQueueToOutputQueueWithOutputVerifierButNoDMQSet()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );
    final MessageCollector dmqCollector = collectResults( TestHelper.DMQ_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setOutputVerifier( new TestMessageVerifier( 3 ) );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 5 );
    collector.expectMessageCount( 4 );
    dmqCollector.expectMessageCount( 0 );
    link.stop();
  }

  @Test
  public void transferFromInputQueueToOutputQueueWithTransform()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );
    final MessageCollector dmqCollector = collectResults( TestHelper.DMQ_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setDmqName( TestHelper.DMQ_NAME );
    final TestMessageTransformer transformer = new TestMessageTransformer( false );
    link.setTransformer( transformer );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 1 );
    collector.expectMessageCount( 1 );
    dmqCollector.expectMessageCount( 0 );
    assertTrue( transformer.getLastMessageTime() != 0, "Transformer been invoked" );
    link.stop();
  }

  @Test
  public void transferFromInputQueueToOutputQueueWithTransformThaResultsInError()
    throws Exception
  {
    final MessageCollector collector = collectResults( TestHelper.QUEUE_2_NAME, false );
    final MessageCollector dmqCollector = collectResults( TestHelper.DMQ_NAME, false );

    final MessageLink link = new MessageLink();
    link.setSourceChannel( TestHelper.QUEUE_1_SPEC, null, null );
    link.setDestinationChannel( TestHelper.QUEUE_2_SPEC );
    link.setDmqName( TestHelper.DMQ_NAME );
    final TestMessageTransformer transformer = new TestMessageTransformer( true );
    link.setTransformer( transformer );
    link.setName( "TestLink" );
    link.start( createSession() );

    produceMessages( TestHelper.QUEUE_1_NAME, false, 1 );
    collector.expectMessageCount( 0 );
    dmqCollector.expectMessageCount( 1 );
    assertTrue( transformer.getLastMessageTime() != 0, "Transformer been invoked" );
    link.stop();
  }

  private static void publishMessage( final Session session,
                                      final Destination destination,
                                      final String messageContent,
                                      final Object headerValue )
    throws Exception
  {
    final MessageProducer producer = session.createProducer( destination );
    final Message message = session.createTextMessage( messageContent );
    message.setObjectProperty( TestHelper.HEADER_KEY, headerValue );

    // Disable generation of ids as we don't care about them
    // (Actually ignored by OMQ)
    producer.setDisableMessageID( true );
    // Disable generation of approximate transmit timestamps as we don't care about them
    producer.setDisableMessageTimestamp( true );
    producer.setPriority( 1 );
    producer.setDeliveryMode( DeliveryMode.NON_PERSISTENT );
    producer.send( message );
    producer.close();
  }

  private void produceMessages( final String channelName, final boolean topic, final int messageCount )
    throws Exception
  {
    final Session session = createSession();
    final Destination destination = createDestination( session, channelName, topic );
    for( int i = 0; i < messageCount; i++ )
    {
      publishMessage( session, destination, "Message-" + i, i );
    }
  }

  private MessageCollector collectResults( final String channelName, final boolean topic )
    throws Exception
  {
    final Session session = createSession();
    final Destination destination = createDestination( session, channelName, topic );
    final MessageConsumer consumer = session.createConsumer( destination );
    final MessageCollector collector = new MessageCollector();
    consumer.setMessageListener( collector );
    return collector;
  }

  private Destination createDestination( final Session session, final String channelName, final boolean topic )
    throws JMSException
  {
    return topic ? session.createTopic( channelName ) : session.createQueue( channelName );
  }

  private void assertMessageProperty( final Message message, final String key, final Object value )
    throws JMSException
  {
    assertEquals( value, message.getObjectProperty( key ), "Header: " + key );
  }

  private void assertMessagePropertyNotNull( final Message message, final String key )
    throws JMSException
  {
    assertNotNull( message.getObjectProperty( key ), "Header: " + key );
  }

  private void assertMessagePropertyNull( final Message message, final String key )
    throws JMSException
  {
    assertNull( message.getObjectProperty( key ), "Header: " + key );
  }
}
