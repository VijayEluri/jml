package jml;

import javax.jms.Message;

class TestMessageVerifier
  extends MessageVerifier
{
  private final int m_limit;
  private long m_lastMessageTime;

  TestMessageVerifier( final int limit )
  {
    m_limit = limit;
  }

  long getLastMessageTime()
  {
    return m_lastMessageTime;
  }

  @Override
  public void verifyMessage( final Message message ) throws Exception
  {
    m_lastMessageTime = System.nanoTime();
    final int value = message.getIntProperty( TestHelper.HEADER_KEY );
    if( value > m_limit )
    {
      throw MessageUtil.exceptionFor( message, "has header value = " + value + " that exceeds " + m_limit, null );
    }
  }
}
