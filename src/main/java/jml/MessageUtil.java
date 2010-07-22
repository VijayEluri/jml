package jml;

import java.util.Enumeration;
import javax.jms.JMSException;
import javax.jms.Message;

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
        errorMessageFor( message ) + " is not of the expected type " + type.getName() +
        ". Actual Message Type: " + message.getClass().getName();
      throw new Exception( errorMessage );
    }
  }

  static String errorMessageFor( final Message message )
    throws Exception
  {
    return "Message with ID = " + message.getJMSMessageID();
  }

  public static void copyMessageHeaders( final Message from, final Message to )
      throws JMSException
  {
    //set the developer assigned headers
    to.setJMSCorrelationID(from.getJMSCorrelationID());
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
