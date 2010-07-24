package jml;

import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.BytesMessage;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Session;
import javax.jms.StreamMessage;
import javax.jms.TextMessage;
import javax.jms.Topic;

/**
 * A base class that can be extended to receive messages from a specific source m_channel.
 * The m_channel can either be a queue or a topic in which case the m_channel name is prefixed
 * with "queue://" or "topic://" respectively. The messages delivered to the endpoint can be
 * be filtered by specifying a selector. The subscription can also be made durable for topic
 * channels by specifying the subscription name.
 *
 * <p>If an exception is raised during message processing then the endpoint will send a copy of
 * the message to the dead message queue if the dead message queue has been specified. Otherwise
 * the endpoint will rethrow the exception and rely on the message server to catch and log the
 * problem.</p> 
 */
public abstract class AbstractMessageEndpoint
{
  /// Logger used to log in the endpoint and subclasses.
  protected static final Logger LOG = Logger.getLogger( AbstractMessageEndpoint.class.getName() );

  private String m_name;
  private String m_subscriptionName;
  private String m_selector;
  private MessageVerifier m_inputVerifier;
  private String m_dmqName;
  private ChannelSpec m_source;
  private boolean m_isFrozen;

  private Session m_session;
  private MessageConsumer m_sourceConsumer;
  private MessageProducer m_dmqProducer;

  /** Specify the name of the endpoint. Used during debugging. */
  public final void setName( final String name )
  {
    ensureEditable();
    m_name = name;
  }

  /** Return the name of the endpoint. */
  public final String getName()
  {
    return m_name;
  }

  /**
   * Specify the source channel.
   *
   * @param channelName the channel specification.
   * @param subscription the subscription name if durable.
   * @param selector the selector if any.
   */
  public final void setSourceChannel( final String channelName, final String subscription, final String selector )
  {
    if( null == channelName ) throw new NullPointerException( "channelName" );
    if( null != subscription && !channelName.startsWith( ChannelSpec.TOPIC_PREFIX ) )
    {
      throw new IllegalStateException( "Channels supplied with subscriptions must be topics" );
    }
    ensureEditable();
    m_source = ChannelSpec.parseChannelSpec( channelName );
    m_subscriptionName = subscription;
    m_selector = selector;
  }

  /** Return the specification for the source channel. */
  public ChannelSpec getSource()
  {
    return m_source;
  }

  /** Return the durable subscription name, if any. */
  public final String getSubscriptionName()
  {
    return m_subscriptionName;
  }

  /** Return the selector string if any. */
  public String getSelector()
  {
    return m_selector;
  }

  /**
   * Specify the name of the dead message queue. If an exception occurs during message processing
   * the endpoint will attempt to route the message to this queue. Otherwise it will raise an
   * exception from handler and let the MOM middle-ware handle the failure.
   */
  public final void setDmqName( final String dmqName )
  {
    ensureEditable();
    m_dmqName = dmqName;
  }

  /** Return the dead message queue name, if any. */
  public final String getDmqName()
  {
    return m_dmqName;
  }

  /**
   * Specify the input message verifier. The input message verifier is invoked prior
   * to the handleMessage() method. If the message verifier raises an exception then
   * the message will not be passed onto the handleMessage method.
   */
  public final void setInputVerifier( final MessageVerifier inputVerifier )
  {
    ensureEditable();
    m_inputVerifier = inputVerifier;
  }

  /**
   * Invoked to activate the endpoint.
   * This is the method that actually connects to the JMS server attempts to
   * subscribe to the configured channels.
   *
   * @param session the JMS session that is exclusive to the endpoint.
   * @throws Exception if there is a problem starting connection.
   */
  public final void start( final Session session )
    throws Exception
  {
    if( null == session ) throw invalid( "session must not be null" );
    m_isFrozen = true;
    try
    {
      ensureValidConfig();

      m_session = session;

      final Destination inChannel = m_source.create( session );
      final Destination dmq = ( null != m_dmqName ) ? m_session.createQueue( m_dmqName ) : null;
      m_dmqProducer = ( null != dmq ) ? m_session.createProducer( dmq ) : null;

      preSubscribe( session );

      if( null != m_subscriptionName )
      {
        m_sourceConsumer = m_session.createDurableSubscriber( (Topic)inChannel, m_subscriptionName, m_selector, true );
      }
      else
      {
        m_sourceConsumer = m_session.createConsumer( inChannel, m_selector );
      }
      m_sourceConsumer.setMessageListener( new EndpointMessageListener() );
    }
    catch( final JMSException e )
    {
      m_isFrozen = false;
      warning( "Error starting endpoint", e );
      stop();
      throw e;
    }
  }

  /** Stop the endpoint, close the session and any consumers and producers. */
  public final void stop()
    throws Exception
  {
    try
    {
      if( null != m_sourceConsumer ) m_sourceConsumer.close();
    }
    catch( final JMSException e )
    {
      warning( "Closing consumer", e );
    }
    m_sourceConsumer = null;

    try
    {
      if( null != m_dmqProducer ) m_dmqProducer.close();
    }
    catch( final JMSException e )
    {
      warning( "Closing producer for dmq", e );
    }
    m_dmqProducer = null;

    preSessionClose();

    try
    {
      if( null != m_session ) m_session.close();
    }
    catch( final JMSException e )
    {
      warning( "Closing session", e );
    }
    m_session = null;

    m_isFrozen = false;
  }

  /**
   * Template method invoked prior to the endpoint subscribing to the input m_channel.
   *
   * @param session the associated session.
   * @throws Exception if there is a problem that will cause start to fail.
   */
  protected void preSubscribe( final Session session )
    throws Exception
  {
  }

  /**
   * Template method invoked during stop just prior to session being closed.
   */
  protected void preSessionClose()
  {
  }

  /**
   * Template method invoked prior to sending message to the dead message queue.
   */
  protected void preSendMessageToDMQ( final Message message )
    throws JMSException
  {
  }

  /**
   * Method to override to handle the message.
   *
   * @param session the associated JMS session.
   * @param message the message to handle.
   * @throws Exception if there is a problem handling the message.
   */
  protected abstract void handleMessage( final Session session, final Message message )
    throws Exception;

  /**
   * Handle failure as described in the class documentation.
   *
   * @param inMessage the message that caused the problem.
   * @param reason a textual description of the problem
   * @param t the exception (if any) raised.
   */
  protected final void handleFailure( final Message inMessage, final String reason, final Throwable t )
  {
    info( reason, t );
    if( null == m_dmqProducer )
    {
      final String message = "Unable to handle message and no DMQ to send message to. Message: " + inMessage;
      warning( message, null );
      throw new IllegalStateException( message );
    }
    try
    {
      final Message message = createMessageToSendToDMQ( inMessage, reason );
      m_dmqProducer.send( message,
                          message.getJMSDeliveryMode(),
                          message.getJMSPriority(),
                          message.getJMSExpiration() );
    }
    catch( final Exception e )
    {
      final String message =
        "Failed to send message to DMQ.\nOriginal Error: " + t + "\ninMessage: " + inMessage;
      warning( message, e );
      throw new IllegalStateException( message, e );
    }
  }

  /**
   * Raise an exception unless endpoint is editable.
   * Should be used in all mutators that modify configuration data. 
   */
  protected final void ensureEditable()
  {
    if( m_isFrozen ) throw invalid( "Attempting to edit active MessageLink" );
  }

  /**
   * Raise an exception if the configuration data is not valid. This method is invoked
   * prior to starting the end point. Overide and invoke super if more configuration data
   * is added to class. 
   */
  protected void ensureValidConfig()
    throws Exception
  {
    if( null == m_source ) throw invalid( "source not specified" );
    else if( null != m_subscriptionName && !m_source.isTopic() )
    {
      throw invalid( "subscriptionName should only be specified for topics" );
    }
  }

  /** Return an IllegalStateException for specified message. */
  protected final IllegalStateException invalid( final String message )
  {
    warning( message, null );
    return new IllegalStateException( "MessageLink (" + m_name + ") invalid. Reason: " + message );
  }

  /** Log an INFO level message. */
  protected final void info( final String message, final Throwable t )
  {
    log( Level.INFO, message, t );
  }

  /** Log an WARNING level message. */
  protected final void warning( final String message, final Throwable t )
  {
    log( Level.WARNING, message, t );
  }

  /** Log a message at specified log level. */
  protected final void log( final Level level, final String message, final Throwable t )
  {
    if( LOG.isLoggable( level ) )
    {
      LOG.log( level, "MessageLink (" + m_name + ") Problem: " + message, t );
    }
  }

  private void doMessage( final Message message )
  {
    if( LOG.isLoggable( Level.FINE ) )
    {
      log( Level.FINE, "Starting to process message: " + message, null );
    }
    try
    {
      if( null != m_inputVerifier ) m_inputVerifier.verifyMessage( message );
    }
    catch( final Exception e )
    {
      handleFailure( message, "Incoming message failed precondition check. Error: " + e, e );
      return;
    }
    try
    {
      handleMessage( m_session, message );
    }
    catch( final Exception e )
    {
      handleFailure( message, "Error handling message. Error: " + e, e );
      return;
    }
  }

  private Message createMessageToSendToDMQ( final Message inMessage, final String reason )
    throws Exception
  {
    final Message message = cloneMessageForDMQ( m_session, inMessage );
    message.setStringProperty( "JMLMessageLink", m_name );
    message.setStringProperty( "JMLFailureReason", reason );
    message.setStringProperty( "JMLSourceChannel", m_source.toSpec() );
    if( null != m_subscriptionName )
    {
      message.setStringProperty( "JMLInSubscriptionName", m_subscriptionName );
    }

    final String messageType;
    if( inMessage instanceof TextMessage ) messageType = "TextMessage";
    else if( inMessage instanceof MapMessage ) messageType = "MapMessage";
    else if( inMessage instanceof BytesMessage ) messageType = "BytesMessage";
    else if( inMessage instanceof ObjectMessage ) messageType = "ObjectMessage";
    else if( inMessage instanceof StreamMessage ) messageType = "StreamMessage";
    else messageType = "Unknown";
    message.setStringProperty( "JMLOriginalMessageType", messageType );

    preSendMessageToDMQ( message );
    return message;
  }

  private static Message cloneMessageForDMQ( final Session session, final Message from )
    throws Exception
  {
    final Message to;
    if( from instanceof TextMessage )
    {
      to = session.createTextMessage( ( (TextMessage)from ).getText() );
    }
    else if( from instanceof MapMessage )
    {
      final MapMessage fromMessage = (MapMessage)from;
      final Enumeration names = fromMessage.getMapNames();
      final MapMessage toMessage = session.createMapMessage();
      while( names.hasMoreElements() )
      {
        final String key = (String)names.nextElement();
        toMessage.setObjectProperty( key, fromMessage.getObject( key ) );
      }
      to = toMessage;
    }
    else if( from instanceof BytesMessage )
    {
      final BytesMessage fromMessage = (BytesMessage)from;
      final BytesMessage toMessage = session.createBytesMessage();
      final long length = fromMessage.getBodyLength();
      for( int i = 0; i < length; i++ )
      {
        toMessage.writeByte( fromMessage.readByte() );
      }
      to = toMessage;
    }
    else if( from instanceof ObjectMessage )
    {
      final ObjectMessage fromMessage = (ObjectMessage)from;
      // Warning - this assumes that the object can be deserialized
      // in the context of the link which may not be the case
      to = session.createObjectMessage( fromMessage.getObject() );
    }
    else //assume
    {
      //Ignore body as unable to copy across StreamMessage or any custom message types
      to = session.createMessage();
    }

    MessageUtil.copyMessageHeaders( from, to );
    return to;
  }

  private class EndpointMessageListener implements MessageListener
  {
    public void onMessage( final Message message )
    {
      doMessage( message );
    }
  }
}