package jml;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Session;

/**
 * Representation of a JMS m_channel. A topic or a queue.
 */
public final class ChannelSpec
{
  ///Prefix for queue type channels
  public static final String QUEUE_PREFIX = "queue://";

  ///Prefix for topic type channels
  public static final String TOPIC_PREFIX = "topic://";

  private final String m_channel;
  private final boolean m_isTopic;

  ChannelSpec( final String channel, final boolean topic )
  {
    this.m_channel = channel;
    m_isTopic = topic;
  }

  public String getChannel()
  {
    return m_channel;
  }

  public boolean isTopic()
  {
    return m_isTopic;
  }

  public String toSpec()
  {
    return (isTopic() ? TOPIC_PREFIX : QUEUE_PREFIX) + getChannel();
  }

  @Override
  public String toString()
  {
    return toSpec();
  }

  public Destination create( final Session session ) throws JMSException
  {
    return isTopic() ? session.createTopic( getChannel() ) : session.createQueue( getChannel() );
  }

  public static final ChannelSpec parseChannelSpec( final String channelName )
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
}
