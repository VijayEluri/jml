package jml;

import java.util.logging.Level;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;

/**
 * The message endpoint that routes a message from a source channel to a destination channel.
 * The message may pass through input message verifier, transformer and output message verifier
 * before being sent to the destination channel.
 */
public final class MessageLink
  extends AbstractMessageEndpoint
{
  private ChannelSpec _destination;
  private MessageVerifier _outputVerifier;
  private MessageTransformer _transformer;
  private MessageProducer _destinationProducer;

  /** Specify the destination channel. */
  public void setDestinationChannel( final String channelSpec )
  {
    _destination = ChannelSpec.parseChannelSpec( channelSpec );
  }

  /** Specify verifier that is invoked prior to sending message to the destination channel. */
  public void setOutputVerifier( final MessageVerifier outputVerifier )
  {
    ensureEditable();
    _outputVerifier = outputVerifier;
  }

  /**
   * Specify the transformer. The transformer is invoked prior to sending
   * message to destination and before output verifier.
   */
  public void setTransformer( final MessageTransformer transformer )
  {
    ensureEditable();
    _transformer = transformer;
  }

  @Override
  protected void preSubscribe( final Session session ) throws Exception
  {
    _destinationProducer = session.createProducer( _destination.create( session ) );
  }

  @Override
  protected void preSessionClose()
  {
    try
    {
      if( null != _destinationProducer ) _destinationProducer.close();
    }
    catch( final JMSException e )
    {
      warning( "Closing destination producer", e );
    }
    _destinationProducer = null;
  }

  @Override
  protected void handleMessage( final Session session, final Message message ) throws Exception
  {
    final Message output;
    try
    {
      if( null != _transformer ) output = _transformer.transformMessage( session, message );
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
    message.setStringProperty( "JMLDestinationChannel", _destination.toSpec() );
  }

  private void send( final Message inMessage, final Message outMessage )
  {
    try
    {
      if( null != _outputVerifier ) _outputVerifier.verifyMessage( outMessage );
    }
    catch( final Exception e )
    {
      handleFailure( inMessage, "Generated message failed send precondition check. Error: " + e, e );
    }
    try
    {
      _destinationProducer.send( outMessage,
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
    if( null == _destination ) throw invalid( "destination channel not specified" );
  }
}
