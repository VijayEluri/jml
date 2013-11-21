package org.realityforge.jml;

import javax.jms.Message;

class TestMessageVerifier
  extends MessageVerifier
{
  private final int _limit;
  private long _lastMessageTime;

  TestMessageVerifier( final int limit )
  {
    _limit = limit;
  }

  long getLastMessageTime()
  {
    return _lastMessageTime;
  }

  @Override
  public void verifyMessage( final Message message ) throws Exception
  {
    _lastMessageTime = System.nanoTime();
    final int value = message.getIntProperty( TestHelper.HEADER_KEY );
    if( value > _limit )
    {
      throw MessageUtil.exceptionFor( message, "has header value = " + value + " that exceeds " + _limit, null );
    }
  }
}
