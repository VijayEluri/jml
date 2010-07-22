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

public final class MessageLink
{
  private static final Logger LOG = Logger.getLogger( MessageLink.class.getName() );
  
  private String m_name;
  private String m_sourceName;
  private String m_subscriptionName;
  private String m_selector;
  private boolean m_isSourceTopic;
  private MessageVerifier m_inputVerifier;

  private String m_destinationName;
  private boolean m_isDestinationTopic;
  private MessageVerifier m_outputVerifier;

  private String m_dmqName;
  private MessageTransformer m_transformer;

  private boolean m_isFrozen;

  private Session m_session;
  private MessageConsumer m_inConsumer;
  private MessageProducer m_outProducer;
  private MessageProducer m_dmqProducer;

  public void setName( final String name )
  {
    ensureEditable();
    m_name = name;
  }

  public void setInputQueue( final String name, final String selector )
  {
    ensureEditable();
    m_sourceName = name;
    m_isSourceTopic = false;
    m_subscriptionName = null;
    m_selector = selector;
  }

  public void setInputTopic( final String name, final String subscription, final String selector )
  {
    ensureEditable();
    if( null == name ) throw new NullPointerException( "name" );
    m_sourceName = name;
    m_isSourceTopic = true;
    m_subscriptionName = subscription;
    m_selector = selector;
  }

  public void setOutputQueue( final String name )
  {
    ensureEditable();
    if( null == name ) throw new NullPointerException( "name" );
    m_destinationName = name;
    m_isDestinationTopic = false;
  }

  public void setOutputTopic( final String name )
  {
    ensureEditable();
    m_destinationName = name;
    m_isDestinationTopic = true;
  }

  public void setDmqName( final String dmqName )
  {
    ensureEditable();
    m_dmqName = dmqName;
  }

  public void setInputVerifier( final MessageVerifier inputVerifier )
  {
    ensureEditable();
    m_inputVerifier = inputVerifier;
  }

  public void setOutputVerifier( final MessageVerifier outputVerifier )
  {
    ensureEditable();
    m_outputVerifier = outputVerifier;
  }

  public void setTransformer( final MessageTransformer transformer )
  {
    ensureEditable();
    m_transformer = transformer;
  }

  public void start( final Session session )
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
      final Destination outChannel =
        m_isDestinationTopic ? m_session.createTopic( m_destinationName ) : m_session.createQueue( m_destinationName );
      final Destination dmq = ( null != m_dmqName ) ? m_session.createQueue( m_dmqName ) : null;

      m_outProducer = m_session.createProducer( outChannel );
      m_dmqProducer = ( null != dmq ) ? m_session.createProducer( dmq ) : null;

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

  public void stop()
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
      if( null != m_outProducer ) m_outProducer.close();
    }
    catch( final JMSException e )
    {
      warning( "Closing producer", e );
    }
    m_outProducer = null;

    try
    {
      if( null != m_dmqProducer ) m_dmqProducer.close();
    }
    catch( final JMSException e )
    {
      warning( "Closing producer for dmq", e );
    }
    m_dmqProducer = null;

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

  private void doMessage( final Message message )
  {
    try
    {
      if( null != m_inputVerifier ) m_inputVerifier.verifyMessage( message );
    }
    catch( final Exception e )
    {
      handleFailure( message, "Incoming message failed precondition check. Error: " + e, e );
      return;
    }

    final Message output;
    try
    {
      if( null != m_transformer ) output = m_transformer.transformMessage( m_session, message );
      else output = message;
    }
    catch( final Exception e )
    {
      handleFailure( message, "Incoming message failed during message transformation step. Error: " + e, e );
      return;
    }
    if( null != output )
    {
      send( message, output );
    }
  }

  private void handleFailure( final Message inMessage, final String reason, final Throwable t )
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
      final Message message = cloneMessageForDMQ( m_session, inMessage );
      message.setStringProperty( "JMLMessageLink", m_name );
      message.setStringProperty( "JMLFailureReason", reason );
      message.setStringProperty( "JMLInChannelName", m_sourceName );
      message.setStringProperty( "JMLInChannelType", m_isSourceTopic ? "Topic" : "Queue" );
      if( null != m_subscriptionName )
      {
        message.setStringProperty( "JMLInSubscriptionName", m_subscriptionName );
      }
      message.setStringProperty( "JMLOutChannelName", m_destinationName );
      message.setStringProperty( "JMLOutChannelType", m_isDestinationTopic ? "Topic" : "Queue" );

      final String messageType;
      if( inMessage instanceof TextMessage ) messageType = "TextMessage";
      else if( inMessage instanceof MapMessage ) messageType = "MapMessage";
      else if( inMessage instanceof BytesMessage ) messageType = "BytesMessage";
      else if( inMessage instanceof ObjectMessage ) messageType = "ObjectMessage";
      else if( inMessage instanceof StreamMessage ) messageType = "StreamMessage";
      else messageType = "Unknown";
      message.setStringProperty( "JMLOriginalMessageType", messageType );

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

  private void send( final Message inMessage, final Message outMessage )
  {
    try
    {
      if( null != m_outputVerifier ) m_outputVerifier.verifyMessage( outMessage );
    }
    catch( final Exception e )
    {
      handleFailure( inMessage, "Generated message failed send precondition check. Error: " + e, e );
    }
    try
    {
      m_outProducer.send( outMessage,
                          outMessage.getJMSDeliveryMode(),
                          outMessage.getJMSPriority(),
                          outMessage.getJMSExpiration() );
    }
    catch( final Exception e )
    {
      handleFailure( inMessage, "Failed to send generated message to destination. Error: " + e, e );
    }
  }

  private void ensureValidConfig()
    throws Exception
  {
    if( null == m_sourceName ) throw invalid( "sourceName not specified" );
    else if( null == m_destinationName ) throw invalid( "sourceName not specified" );
    else if( null != m_subscriptionName && !m_isSourceTopic )
    {
      throw invalid( "subscriptionName should only be specified for topics" );
    }
  }

  private IllegalStateException invalid( final String message )
  {
    warning( message, null );
    return new IllegalStateException( "MessageLink (" + m_name + ") invalid. Reason: " + message );
  }

  private void info( final String message, final Throwable t )
  {
    log( Level.INFO, message, t );
  }

  private void warning( final String message, final Throwable t )
  {
    log( Level.WARNING, message, t );
  }

  private void log( final Level level, final String message, final Throwable t )
  {
    if( LOG.isLoggable( level ) )
    {
      LOG.log( level, "MessageLink (" + m_name + ") Problem: " + message, t );
    }
  }

  private void ensureEditable()
  {
    if( m_isFrozen ) throw invalid( "Attempting to edit active MessageLink" );
  }

  static Message cloneMessageForDMQ( final Session session, final Message from )
      throws Exception
  {
    final Message to;
    if( from instanceof TextMessage )
    {
      to = session.createTextMessage( ( (TextMessage)from ).getText() );
    }
    else if ( from instanceof MapMessage )
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
    else if ( from instanceof BytesMessage )
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
    else if ( from instanceof ObjectMessage )
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

  private class LinkMessageListener implements MessageListener
  {
    public void onMessage( final Message message )
    {
      doMessage( message );
    }
  }
}
