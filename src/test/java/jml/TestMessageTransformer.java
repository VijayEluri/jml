package jml;

import javax.jms.Message;
import javax.jms.Session;

class TestMessageTransformer
  extends MessageTransformer
{
  private final boolean m_fail;
  private long m_lastMessageTime;

  TestMessageTransformer( final boolean fail )
  {
    m_fail = fail;
  }

  long getLastMessageTime()
  {
    return m_lastMessageTime;
  }

  @Override
  public Message transformMessage( final Session session, final Message message ) throws Exception
  {
    m_lastMessageTime = System.nanoTime();
    if( m_fail ) throw new Exception();
    return message;
  }
}
