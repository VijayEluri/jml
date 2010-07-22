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

public abstract class AbstractMessageEndpoint
{
  ///Prefix for queue type channels
  public static final String QUEUE_PREFIX = "queue://";
  ///Prefix for topic type channels
  public static final String TOPIC_PREFIX = "topic://";

  protected static final Logger LOG = Logger.getLogger( AbstractMessageEndpoint.class.getName() );

  private String m_name;
  private String m_sourceName;
  private String m_subscriptionName;
  private String m_selector;
  private boolean m_isSourceTopic;
  private MessageVerifier m_inputVerifier;

  private String m_dmqName;

  private boolean m_isFrozen;

  private Session m_session;
  private MessageConsumer m_inConsumer;
  private MessageProducer m_dmqProducer;

  public final void setName( final String name )
  {
    ensureEditable();
    m_name = name;
  }

  public final void setInputChannel( final String channelName, final String subscription, final String selector )
  {
    if( null == channelName ) throw new NullPointerException( "channelName" );
    if( null != subscription && !channelName.startsWith( TOPIC_PREFIX ) )
    {
      throw new IllegalStateException( "Channels supplied with subscriptions must be topics" );
    }
    ensureEditable();
    final ChannelSpec spec = parseChannelSpec( channelName );
    m_sourceName = spec.channel;
    m_isSourceTopic = spec.isTopic;
    m_subscriptionName = subscription;
    m_selector = selector;
  }

  public final void setDmqName( final String dmqName )
  {
    ensureEditable();
    m_dmqName = dmqName;
  }

  public final void setInputVerifier( final MessageVerifier inputVerifier )
  {
    ensureEditable();
    m_inputVerifier = inputVerifier;
  }

  public final void start( final Session session )
    throws Exception
  {
    if( null == session ) throw invalid( "session must not be null" );
    m_isFrozen = true;
    try
    {
      ensureValidConfig();

      m_session = session;

      final Destination inChannel =
        m_isSourceTopic ? m_session.createTopic( m_sourceName ) : m_session.createQueue( m_sourceName );
      final Destination dmq = ( null != m_dmqName ) ? m_session.createQueue( m_dmqName ) : null;
      m_dmqProducer = ( null != dmq ) ? m_session.createProducer( dmq ) : null;

      preSubscribe( session );

      if( null != m_subscriptionName )
      {
        m_inConsumer = m_session.createDurableSubscriber( (Topic)inChannel, m_subscriptionName, m_selector, true );
      }
      else
      {
        m_inConsumer = m_session.createConsumer( inChannel, m_selector );
      }
      m_inConsumer.setMessageListener( new LinkMessageListener() );
    }
    catch( final JMSException e )
    {
      m_isFrozen = false;
      warning( "Error starting MessageLink", e );
      stop();
      throw e;
    }
  }

  protected void preSubscribe(final Session session)
    throws Exception
  {
  }

  public final void stop()
    throws Exception
  {
    try
    {
      if( null != m_inConsumer ) m_inConsumer.close();
    }
    catch( final JMSException e )
    {
      warning( "Closing consumer", e );
    }
    m_inConsumer = null;

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

  protected void preSessionClose()
  {
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

  protected abstract void handleMessage( final Session session, final Message message )
    throws Exception;

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

  private Message createMessageToSendToDMQ( final Message inMessage, final String reason )
    throws Exception
  {
    final Message message = cloneMessageForDMQ( m_session, inMessage );
    message.setStringProperty( "JMLMessageLink", m_name );
    message.setStringProperty( "JMLFailureReason", reason );
    message.setStringProperty( "JMLInChannelName", m_sourceName );
    message.setStringProperty( "JMLInChannelType", m_isSourceTopic ? "Topic" : "Queue" );
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

  protected void preSendMessageToDMQ( final Message message )
    throws JMSException
  {
  }

  protected void ensureValidConfig()
    throws Exception
  {
    if( null == m_sourceName ) throw invalid( "sourceName not specified" );
    else if( null != m_subscriptionName && !m_isSourceTopic )
    {
      throw invalid( "subscriptionName should only be specified for topics" );
    }
  }

  protected final IllegalStateException invalid( final String message )
  {
    warning( message, null );
    return new IllegalStateException( "MessageLink (" + m_name + ") invalid. Reason: " + message );
  }

  protected final void info( final String message, final Throwable t )
  {
    log( Level.INFO, message, t );
  }

  protected final void warning( final String message, final Throwable t )
  {
    log( Level.WARNING, message, t );
  }

  protected final void log( final Level level, final String message, final Throwable t )
  {
    if( LOG.isLoggable( level ) )
    {
      LOG.log( level, "MessageLink (" + m_name + ") Problem: " + message, t );
    }
  }

  protected final void ensureEditable()
  {
    if( m_isFrozen ) throw invalid( "Attempting to edit active MessageLink" );
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
      to = session.createTextMessage();
    }

    MessageUtil.copyMessageHeaders( from, to );
    return to;
  }

  protected final ChannelSpec parseChannelSpec( final String channelName )
  {
    if( null == channelName ) throw new NullPointerException( "channelName" );
    final String channel;
    final boolean isTopic;
    if( channelName.startsWith( QUEUE_PREFIX ) )
    {
      channel = channelName.substring( QUEUE_PREFIX.length() );
      isTopic = false;
    }
    else if( channelName.startsWith( TOPIC_PREFIX ) )
    {
      channel = channelName.substring( TOPIC_PREFIX.length() );
      isTopic = true;
    }
    else
    {
      throw new IllegalStateException( "Invalid channel specification " + channelName );
    }
    return new ChannelSpec( channel, isTopic );
  }

  protected class ChannelSpec
  {
    protected final String channel;
    protected final boolean isTopic;

    ChannelSpec( final String channel, final boolean topic )
    {
      this.channel = channel;
      isTopic = topic;
    }
  }

  private class LinkMessageListener implements MessageListener
  {
    public void onMessage( final Message message )
    {
      doMessage( message );
    }
  }
}