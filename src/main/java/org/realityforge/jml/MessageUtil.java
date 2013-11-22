package org.realityforge.jml;

import java.util.Enumeration;
import javax.jms.JMSException;
import javax.jms.Message;

/**
 * Class containing utility methods.
 */
public final class MessageUtil
{
  @SuppressWarnings( { "unchecked" } )
  static <T> T castToType( final Message message, final Class<T> type )
    throws Exception
  {
    if( type.isInstance( message ) )
    {
      return (T)message;
    }
    else
    {
      final String errorMessage =
        "is not of the expected type " + type.getName() + ". Actual Message Type: " + message.getClass().getName();
      throw exceptionFor( message, errorMessage, null );
    }
  }

  static Exception exceptionFor( final Message message, final String description, final Exception e )
    throws Exception
  {
    return new Exception( "Message with ID = " + message.getJMSMessageID() + " " + description, e );
  }

  public static void copyMessageHeaders( final Message from, final Message to )
    throws JMSException
  {
    //set the developer assigned headers
    to.setJMSCorrelationID( from.getJMSCorrelationID() );
    to.setJMSReplyTo( from.getJMSReplyTo() );
    to.setJMSType( from.getJMSType() );

    // these are not used by the JMS provider ... but we use them to keep
    // track of values and then explicitly pass them to send method.
    to.setJMSDeliveryMode( from.getJMSDeliveryMode() );
    to.setJMSPriority( from.getJMSPriority() );
    to.setJMSExpiration( from.getJMSExpiration() );

    // copy all the application specific properties to new message
    final Enumeration propertyNames = from.getPropertyNames();
    while( propertyNames.hasMoreElements() )
    {
      final String name = (String)propertyNames.nextElement();
      final Object value = from.getObjectProperty( name );
      to.setObjectProperty( name, value );
    }
  }
}
