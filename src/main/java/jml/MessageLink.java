package jml;

import java.util.logging.Level;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

public final class MessageLink
  extends AbstractMessageEndpoint
{
  private String m_destinationName;
  private boolean m_isDestinationTopic;
  private MessageVerifier m_outputVerifier;
  private MessageTransformer m_transformer;
  private MessageProducer m_outProducer;

  public void setOutputChannel( final String channelName )
  {
    final ChannelSpec spec = parseChannelSpec( channelName );
    m_destinationName = spec.channel;
    m_isDestinationTopic = spec.isTopic;
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

  @Override
  protected void preSubscribe( final Session session ) throws Exception
  {
    final Destination outChannel =
      m_isDestinationTopic ? session.createTopic( m_destinationName ) : session.createQueue( m_destinationName );
    m_outProducer = session.createProducer( outChannel );
  }

  @Override
  protected void preSessionClose()
  {
    try
    {
      if( null != m_outProducer ) m_outProducer.close();
    }
    catch( final JMSException e )
    {
      warning( "Closing producer", e );
    }
    m_outProducer = null;
  }

  @Override
  protected void handleMessage( final Session session, final Message message ) throws Exception
  {
    final Message output;
    try
    {
      if( null != m_transformer ) output = m_transformer.transformMessage( session, message );
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
    if( LOG.isLoggable( Level.FINE ) )
    {
      log( Level.FINE, "Completed processing of message: " + message, null );
    }
  }

  @Override
  protected void preSendMessageToDMQ( final Message message ) throws JMSException
  {
    message.setStringProperty( "JMLOutChannelName", m_destinationName );
    message.setStringProperty( "JMLOutChannelType", m_isDestinationTopic ? "Topic" : "Queue" );
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

  protected void ensureValidConfig()
    throws Exception
  {
    super.ensureValidConfig();
    if( null == m_destinationName ) throw invalid( "sourceName not specified" );
  }
}
